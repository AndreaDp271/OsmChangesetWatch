package org.osmwatch.changesetwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.osmwatch.changesetwatch.network.HistoryParser

class ElementHistoryTest {
    private val xml =
        """<osm>
        <node id="7" visible="true" version="1" changeset="100" timestamp="2020-01-01T00:00:00Z" user="a" uid="1" lat="45.0" lon="11.0">
          <tag k="shop" v="bakery"/></node>
        <node id="7" visible="true" version="2" changeset="200" timestamp="2021-01-01T00:00:00Z" user="b" uid="2" lat="45.001" lon="11.0">
          <tag k="shop" v="bakery"/><tag k="name" v="Forno"/></node>
        <node id="7" visible="false" version="3" changeset="300" timestamp="2022-01-01T00:00:00Z" user="c" uid="3"/>
        </osm>"""

    @Test
    fun parsesVersions() {
        val v = HistoryParser.parse(xml.byteInputStream())
        assertEquals(listOf(1, 2, 3), v.map { it.version })
        assertEquals(200L, v[1].changeset)
        assertEquals("b", v[1].user)
        assertEquals(mapOf("shop" to "bakery", "name" to "Forno"), v[1].data.tags)
        assertFalse(v[2].visible)
        assertNull(v[2].data.at)
    }

    @Test
    fun diffsNewestFirst() {
        val d =
            ElementHistory.diffs(
                ElementRef(ElementType.NODE, 7, null),
                HistoryParser.parse(xml.byteInputStream()),
            )
        assertEquals(listOf(3, 2, 1), d.map { it.first.version })
        assertEquals(ChangeAction.DELETE, d[0].second.action)
        assertEquals(ChangeAction.MODIFY, d[1].second.action)
        assertEquals(listOf(TagChange("name", null, "Forno")), d[1].second.tags)
        assertEquals(111.0, d[1].second.movedMeters!!, 1.0) // 0.001° di latitudine
        assertEquals(ChangeAction.CREATE, d[2].second.action)
        assertFalse(d[2].second.previousMissing)
    }
}
