package org.osmwatch.changesetwatch.network

import java.io.InputStream
import org.osmwatch.changesetwatch.domain.ChangeAction
import org.osmwatch.changesetwatch.domain.ChangedNode
import org.osmwatch.changesetwatch.domain.ChangedRelation
import org.osmwatch.changesetwatch.domain.ChangedWay
import org.osmwatch.changesetwatch.domain.Member
import org.osmwatch.changesetwatch.domain.OsmChangeSummary
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object OsmChangeParser {

    fun parse(input: InputStream): OsmChangeSummary {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        val nodes = mutableListOf<ChangedNode>()
        val ways = mutableListOf<ChangedWay>()
        val relationList = mutableListOf<ChangedRelation>()
        var relation: ChangedRelation? = null
        var members = mutableListOf<Member>()
        var relations = 0
        var action: ChangeAction? = null
        var node: ChangedNode? = null
        var way: ChangedWay? = null
        var refs = mutableListOf<Long>()
        var tags = mutableMapOf<String, String>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.END_TAG) {
                val n = node
                val w = way
                val r = relation
                when {
                    p.name in ACTIONS -> action = null
                    p.name == "node" && n != null -> {
                        nodes += n.copy(tags = tags)
                        node = null
                    }
                    p.name == "way" && w != null -> {
                        ways += w.copy(nodeRefs = refs, tags = tags)
                        way = null
                    }
                    p.name == "relation" && r != null -> {
                        relationList += r.copy(members = members, tags = tags)
                        relation = null
                    }
                }
                continue
            }
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "create" -> action = ChangeAction.CREATE
                "modify" -> action = ChangeAction.MODIFY
                "delete" -> action = ChangeAction.DELETE
                "node" ->
                    if (action != null) {
                        node =
                            ChangedNode(
                                id = p.getAttributeValue(null, "id").toLong(),
                                action = action,
                                lat = p.getAttributeValue(null, "lat")?.toDoubleOrNull(),
                                lon = p.getAttributeValue(null, "lon")?.toDoubleOrNull(),
                                version = p.getAttributeValue(null, "version")?.toIntOrNull(),
                            )
                        tags = mutableMapOf()
                    }
                "way" ->
                    if (action != null) {
                        way =
                            ChangedWay(
                                p.getAttributeValue(null, "id").toLong(),
                                action,
                                p.getAttributeValue(null, "version")?.toIntOrNull(),
                                emptyList(),
                            )
                        refs = mutableListOf()
                        tags = mutableMapOf()
                    }
                "nd" ->
                    if (way != null)
                        p.getAttributeValue(null, "ref")?.toLongOrNull()?.let { refs += it }
                "tag" ->
                    if (node != null || way != null || relation != null) {
                        val k = p.getAttributeValue(null, "k")
                        val v = p.getAttributeValue(null, "v")
                        if (k != null && v != null) tags[k] = v
                    }
                "relation" ->
                    if (action != null) {
                        relations++
                        relation =
                            ChangedRelation(
                                p.getAttributeValue(null, "id").toLong(),
                                action,
                                p.getAttributeValue(null, "version")?.toIntOrNull(),
                                emptyList(),
                            )
                        members = mutableListOf()
                        tags = mutableMapOf()
                    }
                "member" ->
                    if (relation != null) {
                        val ref = p.getAttributeValue(null, "ref")?.toLongOrNull()
                        if (ref != null)
                            members +=
                                Member(
                                    p.getAttributeValue(null, "type") ?: "",
                                    ref,
                                    p.getAttributeValue(null, "role") ?: "",
                                )
                    }
            }
        }
        return OsmChangeSummary(nodes, ways, relations, relationList)
    }

    private val ACTIONS = setOf("create", "modify", "delete")
}
