package org.osmwatch.changesetwatch.network

import java.io.InputStream
import java.time.Instant
import org.osmwatch.changesetwatch.domain.ElementData
import org.osmwatch.changesetwatch.domain.ElementVersion
import org.osmwatch.changesetwatch.domain.LatLon
import org.osmwatch.changesetwatch.domain.Member
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Risposta di `GET /{node|way|relation}/ID/history`: tutte le versioni, nell'ordine del server (dalla prima). */
object HistoryParser {
    fun parse(input: InputStream): List<ElementVersion> {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        val out = mutableListOf<ElementVersion>()
        var head: ElementVersion? = null
        var at: LatLon? = null
        var refs = mutableListOf<Long>()
        var members = mutableListOf<Member>()
        var tags = mutableMapOf<String, String>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.END_TAG && p.name in TYPES) {
                head?.let { out += it.copy(data = ElementData(at, refs, tags, members)) }
                head = null
                continue
            }
            if (p.eventType != XmlPullParser.START_TAG) continue
            fun a(n: String) = p.getAttributeValue(null, n)
            when (p.name) {
                in TYPES -> {
                    head = ElementVersion(
                        version = a("version")?.toIntOrNull() ?: 0,
                        changeset = a("changeset")?.toLongOrNull(),
                        user = a("user"),
                        timestamp = a("timestamp")?.let { runCatching { Instant.parse(it) }.getOrNull() },
                        visible = a("visible") != "false",
                        data = ElementData(null, emptyList(), emptyMap()),
                    )
                    val lat = a("lat")?.toDoubleOrNull(); val lon = a("lon")?.toDoubleOrNull()
                    at = if (lat != null && lon != null) LatLon(lat, lon) else null
                    refs = mutableListOf(); members = mutableListOf(); tags = mutableMapOf()
                }
                "nd" -> if (head != null) a("ref")?.toLongOrNull()?.let { refs += it }
                "member" -> if (head != null) a("ref")?.toLongOrNull()?.let { members += Member(a("type") ?: "", it, a("role") ?: "") }
                "tag" -> if (head != null) {
                    val k = a("k"); val v = a("v")
                    if (k != null && v != null) tags[k] = v
                }
            }
        }
        return out
    }

    private val TYPES = setOf("node", "way", "relation")
}
