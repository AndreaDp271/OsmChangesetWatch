package org.osmwatch.changesetwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AreaShapeTest {
    private fun ll(lat: Double, lon: Double) = LatLon(lat, lon)

    // "L" dentro il quadrato 0..10: manca il quadrante in alto a destra (lat 5..10, lon 5..10)
    private val l =
        listOf(
            ll(0.0, 0.0),
            ll(0.0, 10.0),
            ll(5.0, 10.0),
            ll(5.0, 5.0),
            ll(10.0, 5.0),
            ll(10.0, 0.0),
            ll(0.0, 0.0),
        )
    private val shape = AreaShape(BBox(0.0, 0.0, 10.0, 10.0), listOf(l))

    @Test
    fun pointInPolygonNotJustBbox() {
        assertTrue(shape.containsPoint(2.0, 2.0))
        assertTrue(shape.containsPoint(2.0, 8.0))
        assertFalse(shape.containsPoint(8.0, 8.0)) // nella bbox ma fuori dalla L
        assertFalse(shape.containsPoint(11.0, 1.0))
    }

    @Test
    fun holeIsOutside() {
        val outer = listOf(ll(0.0, 0.0), ll(0.0, 10.0), ll(10.0, 10.0), ll(10.0, 0.0))
        val hole = listOf(ll(4.0, 4.0), ll(4.0, 6.0), ll(6.0, 6.0), ll(6.0, 4.0))
        val s = AreaShape(BBox(0.0, 0.0, 10.0, 10.0), listOf(outer, hole))
        assertFalse(s.containsPoint(5.0, 5.0))
        assertTrue(s.containsPoint(1.0, 1.0))
        assertEquals(
            100.0 - 4.0,
            s.areaKm2() / (111.32 * 111.32 * Math.cos(Math.toRadians(5.0))),
            1e-6,
        )
    }

    @Test
    fun boxContainment() {
        assertTrue(shape.containsBox(BBox(1.0, 1.0, 4.0, 9.0)))
        assertFalse(shape.containsBox(BBox(1.0, 1.0, 9.0, 9.0))) // angolo nella parte mancante
        // angoli quasi tutti dentro, ma il bordo della L lo taglia
        assertFalse(shape.containsBox(BBox(4.0, 4.0, 6.0, 6.0)))
    }

    @Test
    fun segments() {
        assertTrue(shape.intersectsSegment(ll(8.0, 8.0), ll(8.0, 2.0))) // entra nella L
        // resta nella parte mancante
        assertFalse(shape.intersectsSegment(ll(9.0, 6.0), ll(6.0, 9.0)))
        assertTrue(shape.intersectsSegment(ll(2.0, 2.0), ll(3.0, 3.0))) // tutto dentro
    }

    @Test
    fun rectangleBehavesAsBefore() {
        val r = AreaShape(BBox(0.0, 0.0, 10.0, 10.0))
        assertTrue(r.containsPoint(8.0, 8.0))
        assertTrue(r.containsBox(BBox(1.0, 1.0, 9.0, 9.0)))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val back = AreaShape.decode(AreaShape.encode(listOf(l, l.take(4))))
        assertEquals(2, back.size)
        assertEquals(l.size, back[0].size)
        assertEquals(5.0, back[0][3].lat, 1e-9)
        assertEquals(emptyList<List<LatLon>>(), AreaShape.decode(null))
        assertEquals(emptyList<List<LatLon>>(), AreaShape.decode("1,2;3,4")) // anello degenere
    }
}
