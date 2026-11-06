package org.osmwatch.changesetwatch.domain

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmwatch.changesetwatch.domain.ChangeAction.CREATE
import org.osmwatch.changesetwatch.domain.ChangeAction.DELETE
import org.osmwatch.changesetwatch.domain.ChangeAction.MODIFY
import org.osmwatch.changesetwatch.network.MultiFetchParser
import org.osmwatch.changesetwatch.network.OsmChangeParser

class ChangeGeometryTest {
    private class Fake(val nodes: Map<Long, LatLon> = emptyMap(), val ways: Map<Long, List<Long>> = emptyMap()) : ElementSource {
        val nodeCalls = mutableListOf<List<VersionedId>>()
        override fun nodes(ids: List<VersionedId>): Map<Long, LatLon> { nodeCalls += ids; return nodes.filterKeys { k -> ids.any { it.id == k } } }
        override fun ways(ids: List<VersionedId>): Map<Long, List<Long>> = ways.filterKeys { k -> ids.any { it.id == k } }
    }

    private fun n(id: Long, a: ChangeAction, lat: Double? = null, v: Int? = 1) = ChangedNode(id, a, lat, lat?.let { it + 1 }, v)

    @Test fun createdWay_usesOwnNodes_andNodesInWaysAreNotDrawnAsPoints() {
        val c = OsmChangeSummary(
            nodes = listOf(n(1, CREATE, 10.0), n(2, CREATE, 11.0), n(3, CREATE, 50.0)),
            ways = listOf(ChangedWay(100, CREATE, 1, listOf(1, 2))),
            relationCount = 0,
        )
        val g = GeometryResolver.resolve(c, Fake())
        assertEquals(1, g.lines.size)
        assertEquals(CREATE, g.lines[0].action)
        assertEquals(listOf(10.0, 11.0), g.lines[0].points.map { it.lat })
        assertEquals(listOf(50.0), g.points.map { it.at.lat }) // solo il nodo 3, isolato
        assertEquals(false, g.partial)
    }

    @Test fun deletedNode_locatedFromPreviousVersion() {
        val fake = Fake(nodes = mapOf(7L to LatLon(1.0, 2.0)))
        val g = GeometryResolver.resolve(OsmChangeSummary(listOf(n(7, DELETE, null, v = 4)), emptyList(), 0), fake)
        assertEquals(listOf(VersionedId(7, 3)), fake.nodeCalls.single()) // v-1
        assertEquals(DELETE, g.points.single().action)
    }

    @Test fun modifiedWay_fetchesUnchangedNodes_andSplitsAtUnknownOnes() {
        val fake = Fake(nodes = mapOf(2L to LatLon(5.0, 5.0))) // il nodo 3 non esiste più
        val c = OsmChangeSummary(
            nodes = listOf(n(1, MODIFY, 4.0)),
            ways = listOf(ChangedWay(100, MODIFY, 2, listOf(1, 2, 3, 1))),
            relationCount = 0,
        )
        val g = GeometryResolver.resolve(c, fake)
        assertEquals(listOf(VersionedId(2, null), VersionedId(3, null)), fake.nodeCalls.single())
        assertEquals(1, g.lines.size) // [1,2] disegnata; [1] da solo scartata
        assertEquals(2, g.lines[0].points.size)
        assertTrue(g.partial)
    }

    @Test fun deletedWay_usesPreviousVersionRefs() {
        val fake = Fake(nodes = mapOf(1L to LatLon(0.0, 0.0), 2L to LatLon(1.0, 1.0)), ways = mapOf(100L to listOf(1L, 2L)))
        val g = GeometryResolver.resolve(OsmChangeSummary(emptyList(), listOf(ChangedWay(100, DELETE, 3, emptyList())), 0), fake)
        assertEquals(DELETE, g.lines.single().action)
    }

    @Test fun osmChange_parsesWayRefs() {
        val xml = """<osmChange><modify><way id="9" version="2"><nd ref="1"/><nd ref="2"/></way></modify></osmChange>"""
        val w = OsmChangeParser.parse(ByteArrayInputStream(xml.toByteArray())).ways.single()
        assertEquals(listOf(1L, 2L), w.nodeRefs)
        assertEquals(2, w.version)
    }

    @Test fun multiFetch_skipsNodesWithoutCoordinates() {
        val xml = """<osm><node id="1" visible="true" lat="1.5" lon="2.5"/><node id="2" visible="false"/><way id="9"><nd ref="1"/><nd ref="2"/></way></osm>"""
        val e = MultiFetchParser.parse(ByteArrayInputStream(xml.toByteArray()))
        assertEquals(1.5, e.items[1L]!!.at!!.lat, 1e-9)
        assertEquals(null, e.items[2L]!!.at) // cancellato: nessuna coordinata
        assertEquals(listOf(1L, 2L), e.items[9L]!!.refs)
    }
}
