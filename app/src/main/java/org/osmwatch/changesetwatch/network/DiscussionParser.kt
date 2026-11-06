package org.osmwatch.changesetwatch.network

import java.io.InputStream
import java.time.Instant
import org.osmwatch.changesetwatch.domain.ChangesetComment
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Commenti di `GET /changeset/ID?include_discussion=true`, dal più vecchio al più recente; i
 * nascosti (visible="false") sono saltati.
 */
object DiscussionParser {
    fun parse(input: InputStream): List<ChangesetComment> {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        val out = mutableListOf<ChangesetComment>()
        var user: String? = null
        var date: Instant? = null
        var visible = true
        var inText = false
        var text = StringBuilder()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG ->
                    when (p.name) {
                        "comment" -> {
                            user = p.getAttributeValue(null, "user")
                            date =
                                p.getAttributeValue(null, "date")?.let {
                                    runCatching { Instant.parse(it) }.getOrNull()
                                }
                            visible = p.getAttributeValue(null, "visible") != "false"
                            text = StringBuilder()
                        }
                        "text" -> inText = true
                    }
                XmlPullParser.TEXT -> if (inText) text.append(p.text)
                XmlPullParser.END_TAG ->
                    when (p.name) {
                        "text" -> inText = false
                        "comment" ->
                            if (visible) out += ChangesetComment(user, date, text.toString().trim())
                    }
            }
        }
        return out
    }
}
