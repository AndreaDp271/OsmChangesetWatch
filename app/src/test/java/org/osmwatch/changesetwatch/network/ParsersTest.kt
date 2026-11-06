package org.osmwatch.changesetwatch.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.osmwatch.changesetwatch.domain.ChangeAction

class ParsersTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!

    @Test fun changesets_parsedWithBboxAndTags() {
        val list = ChangesetParser.parse(res("changesets_sample.xml"))
        assertEquals(3, list.size)
        val c = list[0]
        assertEquals(191551665L, c.id)
        assertEquals("MartaMag", c.user)
        assertEquals(50, c.changesCount)
        assertFalse(c.open)
        assertNotNull(c.closedAt)
        assertEquals(41.9068477, c.bbox!!.minLat, 1e-9)
        assertEquals(12.4826473, c.bbox!!.maxLon, 1e-9)
        assertEquals("added sidewalks and crossings", c.comment)
        assertEquals("iD 2.42.3", c.createdBy)
        assertEquals("survey", list[2].source)
        assertEquals(41, c.createdCount)
        assertEquals(9, c.modifiedCount)
        assertEquals(0, c.deletedCount)
        assertNull(c.source)
    }

    @Test fun changeset_withoutBbox_hasNullBbox() {
        val c = ChangesetParser.parse(res("changesets_nobbox.xml")).single()
        assertNull(c.bbox)
        assertNull(c.closedAt)
        assertEquals(true, c.open)
        assertEquals(emptyMap<String, String>(), c.tags)
    }

    @Test fun osmChange_countsNodesWaysAndActions() {
        val s = OsmChangeParser.parse(res("osmchange_sample.xml"))
        assertEquals(11, s.nodes.size)
        assertEquals(5, s.wayCount)
        assertEquals(0, s.relationCount)
        assertEquals(true, s.nodes.all { it.lat != null && it.lon != null })
        assertEquals(true, s.nodes.any { it.action == ChangeAction.CREATE })
    }

    @Test fun osmChange_deleteWithoutCoordsAndRelation() {
        val s = OsmChangeParser.parse(res("osmchange_delete.xml"))
        assertEquals(2, s.nodes.size)
        assertNull(s.nodes[0].lat)
        assertEquals(41.9, s.nodes[1].lat!!, 1e-9)
        assertEquals(ChangeAction.DELETE, s.nodes[1].action)
        assertEquals(1, s.relationCount)
        val r = s.relations.single()
        assertEquals(99L, r.id)
        assertEquals(4, r.version)
        assertEquals(listOf(org.osmwatch.changesetwatch.domain.Member("way", 5, "")), r.members)
    }
}
