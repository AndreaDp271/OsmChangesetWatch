package org.osmwatch.changesetwatch.network

import java.io.InputStream
import org.osmwatch.changesetwatch.domain.ElementData
import org.osmwatch.changesetwatch.domain.LatLon
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Risposta di `GET /nodes?nodes=...` o `/ways?ways=...`, per id (una richiesta = un solo tipo). Nodi cancellati: `at` = null. */
data class Elements(val items: Map<Long, ElementData>)

object MultiFetchParser {
    fun parse(input: InputStream): Elements {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        val out = HashMap<Long, ElementData>()
        var id = -1L
        var at: LatLon? = null
        var refs = mutableListOf<Long>()
        var tags = mutableMapOf<String, String>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.END_TAG && (p.name == "node" || p.name == "way") && id >= 0) {
                out[id] = ElementData(at, refs, tags)
                id = -1
            }
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "node", "way" -> {
                    id = p.getAttributeValue(null, "id").toLong()
                    val lat = p.getAttributeValue(null, "lat")?.toDoubleOrNull()
                    val lon = p.getAttributeValue(null, "lon")?.toDoubleOrNull()
                    at = if (lat != null && lon != null) LatLon(lat, lon) else null
                    refs = mutableListOf()
                    tags = mutableMapOf()
                }
                "nd" -> if (id >= 0) p.getAttributeValue(null, "ref")?.toLongOrNull()?.let { refs += it }
                "tag" -> if (id >= 0) {
                    val k = p.getAttributeValue(null, "k")
                    val v = p.getAttributeValue(null, "v")
                    if (k != null && v != null) tags[k] = v
                }
            }
        }
        return Elements(out)
    }
}
