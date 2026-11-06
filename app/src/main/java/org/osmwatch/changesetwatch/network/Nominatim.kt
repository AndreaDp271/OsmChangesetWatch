package org.osmwatch.changesetwatch.network

import java.io.InputStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmwatch.changesetwatch.domain.BBox
import org.osmwatch.changesetwatch.domain.LatLon
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

enum class PlaceKind { REGION, PROVINCE, MUNICIPALITY, LOCALITY, OTHER }

/** Risultato di ricerca: [outline] = anelli del confine semplificato (vuoto se Nominatim dà solo un punto). */
data class Place(
    val name: String,
    val detail: String,
    val kind: PlaceKind,
    val bbox: BBox,
    val outline: List<List<LatLon>>,
    /** "R45549" ecc., per chiedere il confine meno semplificato con [Nominatim.lookup]. */
    val osmRef: String? = null,
)

/**
 * Ricerca di luoghi su Nominatim (geocoder di OSM). Policy: una richiesta al secondo al massimo, User-Agent dell'app,
 * niente autocompletamento: si cerca solo su invio esplicito. https://operations.osmfoundation.org/policies/nominatim/
 */
object Nominatim {
    private const val URL = "https://nominatim.openstreetmap.org/search"

    fun search(http: OkHttpClient, query: String, lang: String): List<Place> {
        val url = URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "xml")
            .addQueryParameter("featureType", "settlement")
            .addQueryParameter("limit", "8")
            .addQueryParameter("accept-language", lang)
            .addQueryParameter("polygon_text", "1")
            .addQueryParameter("polygon_threshold", "0.001") // contorno semplificato: risposta leggera
            .build()
        val req = Request.Builder().url(url).header("User-Agent", OsmConfig.USER_AGENT).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw OsmHttpException(r.code, "HTTP ${r.code}")
            return parse(r.body!!.byteStream())
        }
    }

    /** Lo stesso luogo con il confine semplificato a ~20 m invece di ~100 m (una richiesta, dopo la scelta). */
    fun lookup(http: OkHttpClient, osmRef: String, lang: String): Place? {
        val url = "https://nominatim.openstreetmap.org/lookup".toHttpUrl().newBuilder()
            .addQueryParameter("osm_ids", osmRef)
            .addQueryParameter("format", "xml")
            .addQueryParameter("accept-language", lang)
            .addQueryParameter("polygon_text", "1")
            .addQueryParameter("polygon_threshold", "0.0002")
            .build()
        val req = Request.Builder().url(url).header("User-Agent", OsmConfig.USER_AGENT).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw OsmHttpException(r.code, "HTTP ${r.code}")
            return parse(r.body!!.byteStream()).firstOrNull()
        }
    }

    /** Un risultato per nome completo; tra doppioni (confine e punto del centro abitato) vince il confine. */
    fun parse(input: InputStream): List<Place> {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        val out = LinkedHashMap<String, Pair<Boolean, Place>>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG || p.name != "place") continue
            fun a(n: String) = p.getAttributeValue(null, n)
            val bb = a("boundingbox")?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() } ?: continue
            if (bb.size != 4) continue
            val display = a("display_name") ?: continue
            val boundary = a("class") == "boundary"
            val place = Place(
                name = display.substringBefore(',').trim(),
                detail = display.substringAfter(',', "").trim(),
                kind = kindOf(a("class"), a("type"), a("place_rank")?.toIntOrNull()),
                bbox = BBox(bb[0], bb[2], bb[1], bb[3]), // Nominatim: min_lat, max_lat, min_lon, max_lon
                outline = a("geotext")?.let(::rings) ?: emptyList(),
                osmRef = a("osm_type")?.firstOrNull()?.uppercaseChar()?.let { t -> a("osm_id")?.let { "$t$it" } },
            )
            val prev = out[display]
            if (prev == null || (boundary && !prev.first)) out[display] = boundary to place
        }
        return out.values.map { it.second }
    }

    /** place_rank: 8 regione/stato, 10-12 provincia, 13-16 comune; i punti "place" sono località. */
    private fun kindOf(cls: String?, type: String?, rank: Int?): PlaceKind = when {
        cls == "boundary" && type == "administrative" && rank != null -> when {
            rank <= 8 -> PlaceKind.REGION
            rank <= 12 -> PlaceKind.PROVINCE
            rank <= 16 -> PlaceKind.MUNICIPALITY
            else -> PlaceKind.LOCALITY
        }
        cls == "place" -> PlaceKind.LOCALITY
        else -> PlaceKind.OTHER
    }

    private val RING = Regex("""\(([-0-9.eE ,]+)\)""")

    /** Anelli di un WKT POLYGON / MULTIPOLYGON ("lon lat, lon lat, ..."); POINT e anelli degeneri sono scartati. */
    fun rings(wkt: String): List<List<LatLon>> = RING.findAll(wkt).mapNotNull { m ->
        m.groupValues[1].split(',').mapNotNull { pair ->
            val xy = pair.trim().split(' ').mapNotNull { it.toDoubleOrNull() }
            if (xy.size == 2) LatLon(xy[1], xy[0]) else null
        }.takeIf { it.size >= 3 }
    }.toList()
}
