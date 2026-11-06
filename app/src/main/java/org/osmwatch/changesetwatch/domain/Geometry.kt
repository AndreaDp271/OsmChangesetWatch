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
