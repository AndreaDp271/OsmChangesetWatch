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

    private fun node(id: Long, lat: Double?, lon: Double?, a: ChangeAction = CREATE, v: Int? = 1) =
        ChangedNode(id, a, lat, lon, v)

    private fun verifier(
        change: OsmChangeSummary,
        src: ElementSource =
            object : ElementSource {
                override fun nodes(ids: List<VersionedId>) = emptyMap<Long, LatLon>()

                override fun ways(ids: List<VersionedId>) = emptyMap<Long, List<Long>>()
            },
        max: Int = 2000,
    ) =
        ChangesetVerifier(
            max,
            {
                downloads++
                change
            },
            src,
        )

    private val empty = OsmChangeSummary(emptyList(), emptyList(), 0)

    @Test
    fun bboxInsideArea_confirmedWithoutDownload() {
        val v = verifier(empty).verify(1, BBox(10.2, 10.2, 10.4, 10.4), 5, area)
        assertEquals(Verdict.CONFIRMED, v)
        assertEquals(0, downloads)
    }

    @Test
    fun hugeChangesetCrossingArea_isNotConfirmed_whenNoElementInside() {
        val change =
            OsmChangeSummary(listOf(node(1, 50.0, 50.0), node(2, -20.0, 30.0)), emptyList(), 0)
        val v = verifier(change).verify(1, BBox(-30.0, -10.0, 60.0, 60.0), 800, area)
        // bbox mondiale che attraversa l'area, ma nessun elemento dentro
        assertEquals(Verdict.OUTSIDE, v)
    }

    @Test
    fun nodeInside_confirmed() {
        val change =
            OsmChangeSummary(listOf(node(1, 50.0, 50.0), node(2, 10.5, 10.5)), emptyList(), 0)
        assertEquals(
            Verdict.CONFIRMED,
            verifier(change).verify(1, BBox(0.0, 0.0, 60.0, 60.0), 10, area),
        )
    }

    @Test
    fun tooManyChanges_skippedWithoutDownload() {
        assertEquals(
            Verdict.SKIPPED_TOO_LARGE,
            verifier(empty, max = 100).verify(1, BBox(0.0, 0.0, 60.0, 60.0), 101, area),
        )
        assertEquals(0, downloads)
    }

    @Test
    fun disjointBbox_outside() {
        assertEquals(
            Verdict.OUTSIDE,
            verifier(empty).verify(1, BBox(20.0, 20.0, 21.0, 21.0), 1, area),
        )
    }

    @Test
    fun longWayCrossingAreaWithBothEndsOutside_isConfirmed() {
        // nodi della way entrambi fuori (a sinistra e a destra dell'area) ma il tratto attraversa
        val change =
            OsmChangeSummary(
                listOf(node(1, 10.5, 5.0, MODIFY, 2), node(2, 10.5, 20.0, MODIFY, 2)),
                listOf(ChangedWay(9, MODIFY, 2, listOf(1, 2))),
                0,
            )
        assertEquals(
            Verdict.CONFIRMED,
            verifier(change).verify(1, BBox(0.0, 0.0, 30.0, 30.0), 3, area),
        )
    }

    @Test
    fun wayWithMissingNodes_notInside_isUncertain() {
        val change =
            // nodi non risolvibili
            OsmChangeSummary(emptyList(), listOf(ChangedWay(9, MODIFY, 2, listOf(1, 2))), 0)
        assertEquals(
            Verdict.UNCERTAIN,
            verifier(change).verify(1, BBox(0.0, 0.0, 30.0, 30.0), 1, area),
        )
    }

    @Test
    fun deletedNodeInsideArea_foundViaPreviousVersion() {
        val src =
            object : ElementSource {
                override fun nodes(ids: List<VersionedId>) = mapOf(7L to LatLon(10.5, 10.5))

                override fun ways(ids: List<VersionedId>) = emptyMap<Long, List<Long>>()
            }
        val change = OsmChangeSummary(listOf(node(7, null, null, DELETE, 4)), emptyList(), 0)
        assertEquals(
            Verdict.CONFIRMED,
            verifier(change, src).verify(1, BBox(0.0, 0.0, 30.0, 30.0), 1, area),
        )
    }

    @Test
    fun segmentIntersection() {
        assertTrue(area.intersectsSegment(LatLon(10.5, 5.0), LatLon(10.5, 20.0)))
        assertFalse(area.intersectsSegment(LatLon(12.0, 5.0), LatLon(12.0, 20.0)))
        assertTrue(area.intersectsSegment(LatLon(9.0, 9.0), LatLon(12.0, 12.0))) // diagonale
        assertFalse(area.intersectsSegment(LatLon(9.0, 9.0), LatLon(9.5, 12.0)))
    }

    @Test
    fun diff_tagsMovedAndNodes() {
        val ref = ElementRef(ElementType.NODE, 1, 3)
        val prev =
            ElementData(LatLon(10.0, 10.0), emptyList(), mapOf("name" to "A", "shop" to "bakery"))
        val cur =
            ElementData(LatLon(10.0001, 10.0), emptyList(), mapOf("name" to "B", "cuisine" to "x"))
        val d = ElementDiffer.diff(ref, MODIFY, cur, prev)
        assertEquals(listOf("cuisine", "name", "shop"), d.tags.map { it.key })
        assertEquals(TagChange("name", "A", "B"), d.tags[1])
        assertEquals(null, d.tags[2].new)
        assertEquals(11.1, d.movedMeters!!, 0.5)
        val m =
            ElementDiffer.diff(
                ElementRef(ElementType.RELATION, 3, 2),
                MODIFY,
                ElementData(
                    null,
                    emptyList(),
                    emptyMap(),
                    listOf(Member("way", 1, "outer"), Member("way", 2, "inner")),
                ),
                ElementData(
                    null,
                    emptyList(),
                    emptyMap(),
                    listOf(Member("way", 1, "outer"), Member("way", 9, "inner")),
                ),
            )
        assertEquals(1, m.membersAdded)
        assertEquals(1, m.membersRemoved)
        val w =
            ElementDiffer.diff(
                ElementRef(ElementType.WAY, 2, 2),
                MODIFY,
                ElementData(null, listOf(1, 2, 3), emptyMap()),
                ElementData(null, listOf(1, 2, 9), emptyMap()),
            )
        assertEquals(1, w.nodesAdded)
        assertEquals(1, w.nodesRemoved)
        assertTrue(ElementDiffer.diff(ref, DELETE, null, null).previousMissing)
    }
}

class BeforeGeometryTest {
    @Test
    fun modifiedWay_showsPreviousShape_andMovedNodeShowsOldPosition() {
        // way v2 era [1,2,3]; ora [1,2,4]; il nodo 2 è stato spostato da (5,5) a (6,6)
        val change =
            OsmChangeSummary(
                nodes =
                    listOf(
                        ChangedNode(2, ChangeAction.MODIFY, 6.0, 6.0, 3),
                        ChangedNode(4, ChangeAction.CREATE, 7.0, 7.0, 1),
                        ChangedNode(9, ChangeAction.MODIFY, 20.0, 20.0, 2),
                    ),
                ways = listOf(ChangedWay(100, ChangeAction.MODIFY, 2, listOf(1, 2, 4))),
                relationCount = 0,
            )
        val src =
            object : ElementSource {
                override fun nodes(ids: List<VersionedId>): Map<Long, LatLon> {
                    val all = mapOf(1L to LatLon(4.0, 4.0), 3L to LatLon(8.0, 8.0))
                    val prev =
                        // v-1 dei nodi modificati
                        mapOf(2L to LatLon(5.0, 5.0), 9L to LatLon(19.0, 19.0))
                    return ids.mapNotNull { id ->
                            (if (id.version != null) prev[id.id] else all[id.id])?.let {
                                id.id to it
                            }
                        }
                        .toMap()
                }

                override fun ways(ids: List<VersionedId>) = mapOf(100L to listOf(1L, 2L, 3L))
            }
        val g = GeometryResolver.resolve(change, src, includeBefore = true)
        assertEquals(1, g.beforeLines.size)
        // forma precedente con nodo 2 alla vecchia posizione
        assertEquals(listOf(4.0, 5.0, 8.0), g.beforeLines[0].points.map { it.lat })
        assertEquals(listOf(19.0), g.beforePoints.map { it.lat }) // nodo isolato spostato
        // di default niente "prima"
        assertEquals(0, GeometryResolver.resolve(change, src).beforeLines.size)
    }

    @Test
    fun boundaryRejectsNodeInBboxButOutsidePolygon() {
        // triangolo: la metà in alto a destra della bbox è fuori
        val tri =
            AreaShape(
                BBox(0.0, 0.0, 10.0, 10.0),
                listOf(listOf(LatLon(0.0, 0.0), LatLon(0.0, 10.0), LatLon(10.0, 0.0))),
            )
        val outside = OsmChangeSummary(listOf(ChangedNode(1, MODIFY, 9.0, 9.0)), emptyList(), 0)
        val inside = OsmChangeSummary(listOf(ChangedNode(1, MODIFY, 1.0, 1.0)), emptyList(), 0)
        fun v(c: OsmChangeSummary) =
            ChangesetVerifier(
                    2000,
                    { c },
                    object : ElementSource {
                        override fun nodes(ids: List<VersionedId>) = emptyMap<Long, LatLon>()

                        override fun ways(ids: List<VersionedId>) = emptyMap<Long, List<Long>>()

                        override fun details(type: ElementType, ids: List<VersionedId>) =
                            emptyMap<Long, ElementData>()
                    },
                )
                .verify(1, BBox(0.5, 0.5, 9.5, 9.5), 1, tri)
        assertEquals(Verdict.OUTSIDE, v(outside))
        assertEquals(Verdict.CONFIRMED, v(inside))
    }
}
