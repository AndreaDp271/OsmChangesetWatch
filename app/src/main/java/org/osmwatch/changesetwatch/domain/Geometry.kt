package org.osmwatch.changesetwatch.domain

import kotlin.math.abs
import kotlin.math.cos

object Geometry {
    /** Oltre questa superficie l'editor avvisa che l'area è molto grande (valore prudente, modificabile). */
    const val WARN_AREA_KM2 = 2500.0

    private const val KM_PER_DEG = 111.32

    /** Superficie approssimata (piana, cos(lat media)): ok per aree piccole/medie, non per i poli. */
    fun areaKm2(b: BBox): Double {
        val midLat = Math.toRadians((b.minLat + b.maxLat) / 2)
        return abs(b.maxLat - b.minLat) * KM_PER_DEG * abs(b.maxLon - b.minLon) * KM_PER_DEG * cos(midLat)
    }
}

fun BBox.containsPoint(lat: Double, lon: Double) = lat in minLat..maxLat && lon in minLon..maxLon
fun BBox.containsBox(o: BBox) = o.minLat >= minLat && o.maxLat <= maxLat && o.minLon >= minLon && o.maxLon <= maxLon
fun BBox.intersects(o: BBox) = !(o.minLon > maxLon || o.maxLon < minLon || o.minLat > maxLat || o.maxLat < minLat)

/** Il segmento a-b tocca il rettangolo (Liang-Barsky; x = lon, y = lat). */
fun BBox.intersectsSegment(a: LatLon, b: LatLon): Boolean {
    var t0 = 0.0
    var t1 = 1.0
    val dx = b.lon - a.lon
    val dy = b.lat - a.lat
    fun clip(p: Double, q: Double): Boolean {
        if (p == 0.0) return q >= 0
        val r = q / p
        if (p < 0) { if (r > t1) return false; if (r > t0) t0 = r } else { if (r < t0) return false; if (r < t1) t1 = r }
        return true
    }
    return clip(-dx, a.lon - minLon) && clip(dx, maxLon - a.lon) && clip(-dy, a.lat - minLat) && clip(dy, maxLat - a.lat)
}

fun distanceMeters(a: LatLon, b: LatLon): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLon / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(h))
}
