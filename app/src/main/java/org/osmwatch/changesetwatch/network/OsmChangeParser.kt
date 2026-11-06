package org.osmwatch.changesetwatch.network

import java.io.InputStream
import org.osmwatch.changesetwatch.domain.ChangeAction
import org.osmwatch.changesetwatch.domain.ChangedNode
import org.osmwatch.changesetwatch.domain.OsmChangeSummary
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object OsmChangeParser {

    fun parse(input: InputStream): OsmChangeSummary {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        val nodes = mutableListOf<ChangedNode>()
        var ways = 0
        var relations = 0
        var action: ChangeAction? = null
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.END_TAG && p.name in ACTIONS) action = null
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
                    )
                }
                "way" -> if (action != null) ways++
                "relation" -> if (action != null) relations++
            }
        }
        return OsmChangeSummary(nodes, ways, relations)
    }

    private val ACTIONS = setOf("create", "modify", "delete")
}
