package org.osmwatch.changesetwatch.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserActivityTest {
    private fun cs(
        id: Long,
        at: String,
        changes: Int,
        editor: String,
        comment: String?,
        bbox: BBox?,
        hashtags: String? = null,
    ) =
        Changeset(
            id,
            "u",
            1,
            Instant.parse(at),
            null,
            false,
            bbox,
            changes,
            0,
            listOfNotNull(
                    "created_by" to editor,
                    comment?.let { "comment" to it },
                    hashtags?.let { "hashtags" to it },
                )
                .toMap(),
        )

    @Test
    fun summary() {
        val area = BBox(45.0, 11.0, 46.0, 12.0)
        val a =
            UserActivityCalc.of(
                listOf(
                    cs(
                        1,
                        "2026-11-01T10:00:00Z",
                        10,
                        "iD 2.30",
                        "fix #Roma",
                        BBox(45.5, 11.5, 45.6, 11.6),
                        "#roma;#hot",
                    ),
                    cs(
                        2,
                        "2026-11-01T18:00:00Z",
                        30,
                        "iD 2.31",
                        null,
                        BBox(10.0, 10.0, 10.1, 10.1),
                    ),
                    cs(3, "2026-11-03T09:00:00Z", 2, "StreetComplete 60", "survey", null),
                    cs(
                        4,
                        "2026-11-04T09:00:00Z",
                        6,
                        "iD 2.31",
                        "x #hot",
                        BBox(45.9, 11.9, 46.5, 12.5),
                    ),
                ),
                listOf(area),
            )!!
        assertEquals(4, a.sample)
        assertEquals(3, a.activeDays) // 1, 3, 4 novembre
        assertEquals(listOf("iD" to 75, "StreetComplete" to 25), a.editors)
        assertEquals(12, a.avgChanges)
        assertEquals(30, a.maxChanges)
        assertEquals(25, a.noCommentPct)
        assertEquals(2, a.inAreas) // 1 dentro, 4 a cavallo del bordo
        // 2 changeset, "#roma" contato una volta sola nel primo
        assertEquals("#hot", a.hashtags.first())
        assertEquals(setOf("#hot", "#roma"), a.hashtags.toSet())
        assertEquals(Instant.parse("2026-11-01T10:00:00Z"), a.from)
    }

    @Test
    fun emptyIsNull() {
        assertNull(UserActivityCalc.of(emptyList(), emptyList()))
    }
}
