package org.osmwatch.changesetwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmwatch.changesetwatch.domain.ChangeAction.CREATE
import org.osmwatch.changesetwatch.domain.ChangeAction.DELETE
import org.osmwatch.changesetwatch.domain.ChangeAction.MODIFY

class VerifierTest {
    private val area = BBox(10.0, 10.0, 11.0, 11.0) // minLat, minLon, maxLat, maxLon
    private var downloads = 0
    private fun node(id: Long, lat: Double?, lon: Double?, a: ChangeAction = CREATE, v: Int? = 1) = ChangedNode(id, a, lat, lon, v)
    private fun verifier(change: OsmChangeSummary, src: ElementSource = object : ElementSource {
        override fun nodes(ids: List<VersionedId>) = emptyMap<Long, LatLon>()
        override fun ways(ids: List<VersionedId>) = emptyMap<Long, List<Long>>()
    }, max: Int = 2000) = ChangesetVerifier(max, { downloads++; change }, src)

    private val empty = OsmChangeSummary(emptyList(), emptyList(), 0)

    @Test fun bboxInsideArea_confirmedWithoutDownload() {
        val v = verifier(empty).verify(1, BBox(10.2, 10.2, 10.4, 10.4), 5, area)
        assertEquals(Verdict.CONFIRMED, v); assertEquals(0, downloads)
    }

    @Test fun hugeChangesetCrossingArea_isNotConfirmed_whenNoElementInside() {
        val change = OsmChangeSummary(listOf(node(1, 50.0, 50.0), node(2, -20.0, 30.0)), emptyList(), 0)
        val v = verifier(change).verify(1, BBox(-30.0, -10.0, 60.0, 60.0), 800, area)
        assertEquals(Verdict.OUTSIDE, v) // bbox mondiale che attraversa l'area, ma nessun elemento dentro
    }

    @Test fun nodeInside_confirmed() {
        val change = OsmChangeSummary(listOf(node(1, 50.0, 50.0), node(2, 10.5, 10.5)), emptyList(), 0)
        assertEquals(Verdict.CONFIRMED, verifier(change).verify(1, BBox(0.0, 0.0, 60.0, 60.0), 10, area))
    }

    @Test fun tooManyChanges_skippedWithoutDownload() {
        assertEquals(Verdict.SKIPPED_TOO_LARGE, verifier(empty, max = 100).verify(1, BBox(0.0, 0.0, 60.0, 60.0), 101, area))
        assertEquals(0, downloads)
    }

    @Test fun disjointBbox_outside() {
        assertEquals(Verdict.OUTSIDE, verifier(empty).verify(1, BBox(20.0, 20.0, 21.0, 21.0), 1, area))
    }

    @Test fun longWayCrossingAreaWithBothEndsOutside_isConfirmed() {
        // nodi della way entrambi fuori (a sinistra e a destra dell'area) ma il tratto attraversa
        val change = OsmChangeSummary(
            listOf(node(1, 10.5, 5.0, MODIFY, 2), node(2, 10.5, 20.0, MODIFY, 2)),
            listOf(ChangedWay(9, MODIFY, 2, listOf(1, 2))), 0,
        )
        assertEquals(Verdict.CONFIRMED, verifier(change).verify(1, BBox(0.0, 0.0, 30.0, 30.0), 3, area))
    }

    @Test fun wayWithMissingNodes_notInside_isUncertain() {
        val change = OsmChangeSummary(emptyList(), listOf(ChangedWay(9, MODIFY, 2, listOf(1, 2))), 0) // nodi non risolvibili
        assertEquals(Verdict.UNCERTAIN, verifier(change).verify(1, BBox(0.0, 0.0, 30.0, 30.0), 1, area))
    }

    @Test fun deletedNodeInsideArea_foundViaPreviousVersion() {
        val src = object : ElementSource {
            override fun nodes(ids: List<VersionedId>) = mapOf(7L to LatLon(10.5, 10.5))
            override fun ways(ids: List<VersionedId>) = emptyMap<Long, List<Long>>()
        }
        val change = OsmChangeSummary(listOf(node(7, null, null, DELETE, 4)), emptyList(), 0)
        assertEquals(Verdict.CONFIRMED, verifier(change, src).verify(1, BBox(0.0, 0.0, 30.0, 30.0), 1, area))
    }

    @Test fun segmentIntersection() {
        assertTrue(area.intersectsSegment(LatLon(10.5, 5.0), LatLon(10.5, 20.0)))
        assertFalse(area.intersectsSegment(LatLon(12.0, 5.0), LatLon(12.0, 20.0)))
        assertTrue(area.intersectsSegment(LatLon(9.0, 9.0), LatLon(12.0, 12.0))) // diagonale
        assertFalse(area.intersectsSegment(LatLon(9.0, 9.0), LatLon(9.5, 12.0)))
    }

    @Test fun diff_tagsMovedAndNodes() {
        val ref = ElementRef(ElementType.NODE, 1, 3)
        val prev = ElementData(LatLon(10.0, 10.0), emptyList(), mapOf("name" to "A", "shop" to "bakery"))
        val cur = ElementData(LatLon(10.0001, 10.0), emptyList(), mapOf("name" to "B", "cuisine" to "x"))
        val d = ElementDiffer.diff(ref, MODIFY, cur, prev)
        assertEquals(listOf("cuisine", "name", "shop"), d.tags.map { it.key })
        assertEquals(TagChange("name", "A", "B"), d.tags[1])
        assertEquals(null, d.tags[2].new)
        assertEquals(11.1, d.movedMeters!!, 0.5)
        val w = ElementDiffer.diff(ElementRef(ElementType.WAY, 2, 2), MODIFY, ElementData(null, listOf(1, 2, 3), emptyMap()), ElementData(null, listOf(1, 2, 9), emptyMap()))
        assertEquals(1, w.nodesAdded); assertEquals(1, w.nodesRemoved)
        assertTrue(ElementDiffer.diff(ref, DELETE, null, null).previousMissing)
    }
}
