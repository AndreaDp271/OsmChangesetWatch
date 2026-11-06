package org.osmwatch.changesetwatch.network

import java.io.InputStream
import org.osmwatch.changesetwatch.domain.ChangeAction
import org.osmwatch.changesetwatch.domain.ChangedNode
import org.osmwatch.changesetwatch.domain.ChangedWay
import org.osmwatch.changesetwatch.domain.OsmChangeSummary
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object OsmChangeParser {

    fun parse(input: InputStream): OsmChangeSummary {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        val nodes = mutableListOf<ChangedNode>()
        val ways = mutableListOf<ChangedWay>()
        var relations = 0
        var action: ChangeAction? = null
        var way: ChangedWay? = null
        var refs = mutableListOf<Long>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.END_TAG) {
                if (p.name in ACTIONS) action = null
                val w = way
                if (p.name == "way" && w != null) { ways += w.copy(nodeRefs = refs); way = null }
                continue
            }
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "create" -> action = ChangeAction.CREATE
                "modify" -> action = ChangeAction.MODIFY
                "delete" -> action = ChangeAction.DELETE
                "node" -> action?.let {
                    nodes += ChangedNode(
                        id = p.getAttributeValue(null, "id").toLong(),
                        action = it,
                        lat = p.getAttributeValue(null, "lat")?.toDoubleOrNull(),
                        lon = p.getAttributeValue(null, "lon")?.toDoubleOrNull(),
                        version = p.getAttributeValue(null, "version")?.toIntOrNull(),
                    )
                }
                "way" -> if (action != null) {
                    way = ChangedWay(p.getAttributeValue(null, "id").toLong(), action, p.getAttributeValue(null, "version")?.toIntOrNull(), emptyList())
                    refs = mutableListOf()
                }
                "nd" -> if (way != null) p.getAttributeValue(null, "ref")?.toLongOrNull()?.let { refs += it }
                "relation" -> if (action != null) relations++
            }
        }
        return OsmChangeSummary(nodes, ways, relations)
    }

    private val ACTIONS = setOf("create", "modify", "delete")
}
