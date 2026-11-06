package org.osmwatch.changesetwatch.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos

/**
 * Forma di un'area osservata: il rettangolo [bbox], oppure un confine ([rings], es. un comune da Nominatim) racchiuso in [bbox].
 * I punti dentro il confine si decidono con la regola pari-dispari su tutti gli anelli: buchi ed exclavi funzionano
 * senza sapere quale anello è esterno. Il confine arriva semplificato da Nominatim (~20 m): vicino al bordo l'esito è approssimato.
 */
class AreaShape(val bbox: BBox, val rings: List<List<LatLon>> = emptyList()) {
    val isBoundary: Boolean get() = rings.isNotEmpty()

    fun containsPoint(lat: Double, lon: Double): Boolean =
        bbox.containsPoint(lat, lon) && (!isBoundary || insideRings(lat, lon))

    /** Prudente: con un confine dice solo che i rettangoli si toccano; il dettaglio lo decide il download. */
    fun intersects(b: BBox): Boolean = bbox.intersects(b)

    /** Rettangolo tutto dentro: angoli dentro e nessun lato del confine che lo attraversa. */
    fun containsBox(b: BBox): Boolean {
        if (!bbox.containsBox(b)) return false
        if (!isBoundary) return true
        val corners = listOf(b.minLat to b.minLon, b.minLat to b.maxLon, b.maxLat to b.minLon, b.maxLat to b.maxLon)
        return corners.all { (la, lo) -> insideRings(la, lo) } && edges().none { (p, q) -> b.intersectsSegment(p, q) }
    }

    fun intersectsSegment(a: LatLon, b: LatLon): Boolean {
        if (!bbox.intersectsSegment(a, b)) return false
        if (!isBoundary) return true
        return insideRings(a.lat, a.lon) || insideRings(b.lat, b.lon) || edges().any { (p, q) -> segmentsCross(a, b, p, q) }
    }

    /** Superficie approssimata in km²: anelli esterni meno buchi (un anello dentro un numero dispari di altri è un buco). */
    fun areaKm2(): Double {
        if (!isBoundary) return Geometry.areaKm2(bbox)
        val k = cos(Math.toRadians((bbox.minLat + bbox.maxLat) / 2)) * KM_PER_DEG * KM_PER_DEG
        return rings.withIndex().sumOf { (i, r) ->
            val a = abs(r.indices.sumOf { j -> val p = r[j]; val q = r[(j + 1) % r.size]; p.lon * q.lat - q.lon * p.lat }) / 2 * k
            val depth = rings.withIndex().count { (o, other) -> o != i && inside(other, r[0].lat, r[0].lon) }
            if (depth % 2 == 0) a else -a
        }
    }

    /** Al massimo [maxPoints] punti in tutto, per disegnare il glifo in lista senza costi. */
    fun simplified(maxPoints: Int): List<List<LatLon>> {
        val total = rings.sumOf { it.size }
        if (total <= maxPoints) return rings
        val step = ceil(total.toDouble() / maxPoints).toInt()
        return rings.mapNotNull { r -> r.filterIndexed { i, _ -> i % step == 0 }.takeIf { it.size >= 3 } }
    }

    private fun insideRings(lat: Double, lon: Double): Boolean = rings.count { inside(it, lat, lon) } % 2 == 1

    private fun edges(): Sequence<Pair<LatLon, LatLon>> =
        rings.asSequence().flatMap { r -> r.indices.asSequence().map { i -> r[i] to r[(i + 1) % r.size] } }

    companion object {
        private const val KM_PER_DEG = 111.32

        /** Ray casting su un anello (x = lon, y = lat). */
        private fun inside(r: List<LatLon>, lat: Double, lon: Double): Boolean {
            var c = false
            var j = r.size - 1
            for (i in r.indices) {
                val a = r[i]; val b = r[j]
                if ((a.lat > lat) != (b.lat > lat) && lon < (b.lon - a.lon) * (lat - a.lat) / (b.lat - a.lat) + a.lon) c = !c
                j = i
            }
            return c
        }

        private fun orient(a: LatLon, b: LatLon, c: LatLon): Double = (b.lon - a.lon) * (c.lat - a.lat) - (b.lat - a.lat) * (c.lon - a.lon)

        private fun onSegment(a: LatLon, b: LatLon, p: LatLon) =
            p.lon in minOf(a.lon, b.lon)..maxOf(a.lon, b.lon) && p.lat in minOf(a.lat, b.lat)..maxOf(a.lat, b.lat)

        /** Due segmenti si toccano (anche solo in un estremo o sovrapposti). */
        fun segmentsCross(a: LatLon, b: LatLon, c: LatLon, d: LatLon): Boolean {
            val d1 = orient(c, d, a); val d2 = orient(c, d, b); val d3 = orient(a, b, c); val d4 = orient(a, b, d)
            if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
            return (d1 == 0.0 && onSegment(c, d, a)) || (d2 == 0.0 && onSegment(c, d, b)) ||
                (d3 == 0.0 && onSegment(a, b, c)) || (d4 == 0.0 && onSegment(a, b, d))
        }

        /** Formato compatto per il DB: "lat,lon;lat,lon|anello successivo", 6 decimali (~10 cm). */
        fun encode(rings: List<List<LatLon>>): String =
            rings.joinToString("|") { r -> r.joinToString(";") { String.format(Locale.ROOT, "%.6f,%.6f", it.lat, it.lon) } }

        fun decode(s: String?): List<List<LatLon>> =
            s?.takeIf { it.isNotBlank() }?.split('|')?.mapNotNull { r ->
                r.split(';').mapNotNull { p ->
                    val xy = p.split(',')
                    val la = xy.getOrNull(0)?.toDoubleOrNull(); val lo = xy.getOrNull(1)?.toDoubleOrNull()
                    if (la != null && lo != null) LatLon(la, lo) else null
                }.takeIf { it.size >= 3 }
            } ?: emptyList()
    }
}
