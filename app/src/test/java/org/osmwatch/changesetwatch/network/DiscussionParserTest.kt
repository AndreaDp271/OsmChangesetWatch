package org.osmwatch.changesetwatch.network

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscussionParserTest {
    private val xml = """<osm><changeset id="1" open="false"><tag k="comment" v="x"/><discussion>
        <comment date="2026-11-06T10:00:00Z" uid="1" user="Anna"><text>Ciao
 mondo</text></comment>
        <comment date="2026-11-06T11:00:00Z" uid="2" user="Bob" visible="false"><text>nascosto</text></comment>
        <comment date="2026-11-06T12:00:00Z" uid="3" user="Carla"><text>ok &amp; grazie</text></comment>
        </discussion></changeset></osm>"""

    @Test fun parsesVisibleCommentsInOrder() {
        val c = DiscussionParser.parse(xml.byteInputStream())
        assertEquals(listOf("Anna", "Carla"), c.map { it.user })
        assertEquals("Ciao\n mondo", c[0].text)
        assertEquals("ok & grazie", c[1].text)
        assertEquals("2026-11-06T10:00:00Z", c[0].date.toString())
    }

    @Test fun noDiscussionIsEmpty() {
        assertEquals(emptyList<Any>(), DiscussionParser.parse("""<osm><changeset id="1"/></osm>""".byteInputStream()))
    }
}
