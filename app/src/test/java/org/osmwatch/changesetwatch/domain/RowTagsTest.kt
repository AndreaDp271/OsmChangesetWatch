package org.osmwatch.changesetwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RowTagsTest {
    @Test
    fun editorNames() {
        assertEquals("iD", RowTags.editor("iD 2.30.1"))
        assertEquals("JOSM", RowTags.editor("JOSM/1.5 (19396 it)"))
        assertEquals("StreetComplete", RowTags.editor(" StreetComplete 55.0 "))
        assertNull(RowTags.editor(null))
        assertNull(RowTags.editor("  "))
    }

    @Test
    fun hashtagsFromComment() {
        assertEquals(
            listOf("#hotosm-project-12", "#roma"),
            RowTags.hashtags("fix #HOTosm-project-12 e #Roma, ancora #roma"),
        )
        assertEquals(emptyList<String>(), RowTags.hashtags(null))
        assertEquals(emptyList<String>(), RowTags.hashtags("nessun tag # qui"))
    }
}
