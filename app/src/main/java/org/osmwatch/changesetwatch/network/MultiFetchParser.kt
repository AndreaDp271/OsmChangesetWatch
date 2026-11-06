package org.osmwatch.changesetwatch.network

import java.io.InputStream
import org.osmwatch.changesetwatch.domain.LatLon
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Risposta di `GET /nodes?nodes=...` o `/ways?ways=...`. Nodi senza lat/lon (visible=false) sono omessi. */
data class Elements(val nodes: Map<Long, LatLon>, val ways: Map<Long, List<Long>>)

object MultiFetchParser {
    fun parse(input: InputStream): Elements {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        val nodes = HashMap<Long, LatLon>()
        val ways = HashMap<Long, List<Long>>()
        var wayId = -1L
        var refs = mutableListOf<Long>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.END_TAG && p.name == "way") { ways[wayId] = refs; wayId = -1 }
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "node" -> {
                    val lat = p.getAttributeValue(null, "lat")?.toDoubleOrNull()
                    val lon = p.getAttributeValue(null, "lon")?.toDoubleOrNull()
                    if (lat != null && lon != null) nodes[p.getAttributeValue(null, "id").toLong()] = LatLon(lat, lon)
                }
                "way" -> { wayId = p.getAttributeValue(null, "id").toLong(); refs = mutableListOf() }
                "nd" -> if (wayId >= 0) p.getAttributeValue(null, "ref")?.toLongOrNull()?.let { refs += it }
            }
        }
        return Elements(nodes, ways)
    }
}
