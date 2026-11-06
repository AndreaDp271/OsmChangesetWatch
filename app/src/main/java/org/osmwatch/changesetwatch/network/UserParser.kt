package org.osmwatch.changesetwatch.network

import java.io.InputStream
import java.time.Instant
import org.osmwatch.changesetwatch.domain.UserInfo
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object UserParser {
    fun parse(input: InputStream): UserInfo? {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        var found = false
        var name: String? = null
        var created: Instant? = null
        var changesets: Int? = null
        var blocks: Int? = null
        var inBlocks = false
        var inRoles = false
        val roles = mutableListOf<String>()
        var traces: Int? = null
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.END_TAG && p.name == "blocks") inBlocks = false
            if (p.eventType == XmlPullParser.END_TAG && p.name == "roles") inRoles = false
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "user" -> {
                    found = true
                    name = p.getAttributeValue(null, "display_name")
                    created =
                        p.getAttributeValue(null, "account_created")?.let {
                            runCatching { Instant.parse(it) }.getOrNull()
                        }
                }
                "changesets" ->
                    if (found && changesets == null)
                        changesets = p.getAttributeValue(null, "count")?.toIntOrNull()
                "blocks" -> inBlocks = true
                "roles" -> inRoles = true
                "traces" -> if (found) traces = p.getAttributeValue(null, "count")?.toIntOrNull()
                "received" ->
                    if (inBlocks) blocks = p.getAttributeValue(null, "count")?.toIntOrNull()
                else -> if (inRoles) roles += p.name
            }
        }
        return if (found) UserInfo(name, created, changesets, blocks, roles, traces) else null
    }
}
