package org.osmwatch.changesetwatch.network

import java.io.InputStream
import java.time.Instant
import org.osmwatch.changesetwatch.domain.BBox
import org.osmwatch.changesetwatch.domain.Changeset
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object ChangesetParser {

    fun parse(input: InputStream): List<Changeset> {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        val result = mutableListOf<Changeset>()
        var current: Changeset? = null
        val tags = mutableMapOf<String, String>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "changeset" -> { current = readHeader(p); tags.clear() }
                    "tag" -> if (current != null) {
                        val k = p.getAttributeValue(null, "k")
                        val v = p.getAttributeValue(null, "v")
                        if (k != null && v != null) tags[k] = v
                    }
                }
                XmlPullParser.END_TAG -> if (p.name == "changeset" && current != null) {
                    result += current.copy(tags = tags.toMap())
                    current = null
                }
            }
        }
        return result
    }

    private fun readHeader(p: XmlPullParser): Changeset {
        fun s(n: String) = p.getAttributeValue(null, n)
        fun d(n: String) = s(n)?.toDoubleOrNull()
        val minLat = d("min_lat"); val minLon = d("min_lon")
        val maxLat = d("max_lat"); val maxLon = d("max_lon")
        // un changeset senza modifiche non ha bbox
        val bbox = if (minLat != null && minLon != null && maxLat != null && maxLon != null)
            BBox(minLat, minLon, maxLat, maxLon) else null
        return Changeset(
            id = s("id").toLong(),
            user = s("user"),
            uid = s("uid")?.toLongOrNull(),
            createdAt = Instant.parse(s("created_at")),
            closedAt = s("closed_at")?.let { Instant.parse(it) },
            open = s("open") == "true",
            bbox = bbox,
            changesCount = s("changes_count")?.toIntOrNull(),
            commentsCount = s("comments_count")?.toIntOrNull(),
            tags = emptyMap(),
            createdCount = s("created_count")?.toIntOrNull(),
            modifiedCount = s("modified_count")?.toIntOrNull(),
            deletedCount = s("deleted_count")?.toIntOrNull(),
        )
    }
}
