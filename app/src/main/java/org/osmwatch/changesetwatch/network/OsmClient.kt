package org.osmwatch.changesetwatch.network

import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.osmwatch.changesetwatch.domain.BBox
import org.osmwatch.changesetwatch.domain.Changeset
import org.osmwatch.changesetwatch.domain.ElementData
import org.osmwatch.changesetwatch.domain.ElementSource
import org.osmwatch.changesetwatch.domain.ElementType
import org.osmwatch.changesetwatch.domain.LatLon
import org.osmwatch.changesetwatch.domain.VersionedId
import org.osmwatch.changesetwatch.domain.OsmChangeSummary

class OsmHttpException(val code: Int, message: String) : IOException(message)

/** Client sincrono (da chiamare fuori dal main thread). Una richiesta alla volta. */
class OsmClient(
    private val http: OkHttpClient = defaultHttp(),
    private val baseUrl: String = OsmConfig.BASE_URL,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {

    /**
     * Changeset la cui bbox interseca [bbox], dal più recente per creazione. Max 100 (limite API).
     * `closedAfter` = `time=T1`: chiusi dopo T1 O ancora aperti (cattura anche i changeset lunghi).
     * `createdBefore` = `time=T1,T2` (T2 usato per la paginazione); ignorato senza `closedAfter`.
     */
    fun fetchChangesets(bbox: BBox, closedAfter: Instant? = null, createdBefore: Instant? = null, limit: Int = 100): List<Changeset> {
        val url = "$baseUrl/changesets".toHttpUrl().newBuilder()
            .addQueryParameter("bbox", bbox.toApiParam())
            .addQueryParameter("limit", limit.toString())
            .apply {
                if (closedAfter != null) {
                    addQueryParameter("time", if (createdBefore != null) "$closedAfter,$createdBefore" else closedAfter.toString())
                }
            }
            .build()
        return execute(url) { ChangesetParser.parse(it.body!!.byteStream()) }
    }

    fun fetchChangeset(id: Long): Changeset? =
        execute("$baseUrl/changeset/$id".toHttpUrl()) { ChangesetParser.parse(it.body!!.byteStream()).firstOrNull() }

    fun fetchChangesetsByUser(displayName: String, limit: Int = 50): List<Changeset> =
        execute("$baseUrl/changesets".toHttpUrl().newBuilder()
            .addQueryParameter("display_name", displayName).addQueryParameter("limit", limit.toString()).build()) {
            ChangesetParser.parse(it.body!!.byteStream())
        }

    fun fetchOsmChange(changesetId: Long): OsmChangeSummary =
        execute("$baseUrl/changeset/$changesetId/download".toHttpUrl()) {
            OsmChangeParser.parse(it.body!!.byteStream())
        }

    fun fetchElements(kind: String, ids: List<VersionedId>): Elements {
        val param = ids.joinToString(",") { if (it.version != null) "${it.id}v${it.version}" else "${it.id}" }
        val url = "$baseUrl/$kind".toHttpUrl().newBuilder().addQueryParameter(kind, param).build()
        return execute(url) { MultiFetchParser.parse(it.body!!.byteStream()) }
    }

    private fun <T> execute(url: HttpUrl, parse: (Response) -> T): T {
        val request = Request.Builder().url(url).header("User-Agent", OsmConfig.USER_AGENT).build()
        var attempt = 0
        while (true) {
            try {
                http.newCall(request).execute().use { r ->
                    when {
                        r.isSuccessful -> return parse(r)
                        r.code == 429 || r.code >= 500 -> {
                            if (attempt >= OsmConfig.MAX_RETRIES) throw OsmHttpException(r.code, "HTTP ${r.code} dopo $attempt retry")
                            sleep(backoffMs(attempt, r.header("Retry-After")))
                        }
                        else -> throw OsmHttpException(r.code, "HTTP ${r.code}: ${r.body?.string()?.take(200)}")
                    }
                }
            } catch (e: OsmHttpException) {
                throw e
            } catch (e: IOException) { // errori di rete: stesso backoff
                if (attempt >= OsmConfig.MAX_RETRIES) throw e
                sleep(backoffMs(attempt, null))
            }
            attempt++
        }
    }

    companion object {
        /** Retry-After in secondi (shortcut: la forma "data HTTP" non è gestita, si usa l'esponenziale). */
        fun backoffMs(attempt: Int, retryAfter: String?): Long =
            retryAfter?.trim()?.toLongOrNull()?.let { it.coerceIn(0, 300) * 1000 }
                ?: (1000L shl attempt.coerceAtMost(6))

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(OsmConfig.CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(OsmConfig.READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Multi fetch (`/nodes?nodes=123v2,456`, `/ways?ways=...`), a blocchi di 100 id, con pausa tra le richieste.
 * Un 404 (elemento redatto) salta il blocco: il risultato resta parziale invece di fallire.
 */
class OsmElementSource(private val client: OsmClient, private val pauseMs: Long = 500) : ElementSource {
    override fun nodes(ids: List<VersionedId>): Map<Long, LatLon> =
        details(ElementType.NODE, ids).mapNotNull { (k, v) -> v.at?.let { k to it } }.toMap()

    override fun ways(ids: List<VersionedId>): Map<Long, List<Long>> =
        details(ElementType.WAY, ids).mapValues { it.value.refs }

    override fun details(type: ElementType, ids: List<VersionedId>): Map<Long, ElementData> {
        val kind = when (type) { ElementType.NODE -> "nodes"; ElementType.WAY -> "ways"; ElementType.RELATION -> "relations" }
        val out = HashMap<Long, ElementData>()
        ids.chunked(100).forEachIndexed { i, chunk ->
            if (i > 0) Thread.sleep(pauseMs)
            try {
                out += client.fetchElements(kind, chunk).items
            } catch (e: OsmHttpException) {
                if (e.code != 404) throw e
            }
        }
        return out
    }
}
