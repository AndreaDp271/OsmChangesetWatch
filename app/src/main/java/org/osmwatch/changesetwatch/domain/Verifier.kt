package org.osmwatch.changesetwatch.domain

/**
 * Decide se un changeset tocca DAVVERO l'area (l'API restituisce quelli la cui bbox interseca l'area, anche enormi):
 * 1. bbox del changeset dentro l'area -> CONFIRMED (senza scaricare); bbox disgiunta -> OUTSIDE;
 * 2. troppe modifiche -> SKIPPED_TOO_LARGE (non scarica);
 * 3. osmChange: un nodo creato/modificato dentro l'area -> CONFIRMED;
 * 4. altrimenti ricostruisce way e nodi cancellati ([GeometryResolver]): un punto dentro o un tratto che attraversa l'area -> CONFIRMED;
 * 5. nessun contatto: OUTSIDE se la ricostruzione è completa, UNCERTAIN se parziale o ci sono relation (non risolvibili).
 */
class ChangesetVerifier(
    private val maxChanges: Int,
    private val fetchChange: (Long) -> OsmChangeSummary,
    private val src: ElementSource,
) {
    fun verify(id: Long, csBbox: BBox?, changesCount: Int?, area: BBox): Verdict {
        if (csBbox != null) {
            if (!area.intersects(csBbox)) return Verdict.OUTSIDE
            if (area.containsBox(csBbox)) return Verdict.CONFIRMED
        }
        if ((changesCount ?: 0) > maxChanges) return Verdict.SKIPPED_TOO_LARGE
        val change = fetchChange(id)
        if (change.nodes.any { it.lat != null && it.lon != null && area.containsPoint(it.lat, it.lon) }) return Verdict.CONFIRMED
        val needsGeometry = change.ways.isNotEmpty() || change.nodes.any { it.lat == null }
        if (!needsGeometry) return if (change.relationCount > 0) Verdict.UNCERTAIN else Verdict.OUTSIDE
        val g = GeometryResolver.resolve(change, src)
        val hit = g.points.any { area.containsPoint(it.at.lat, it.at.lon) } ||
            g.lines.any { l -> l.points.zipWithNext().any { (a, b) -> area.intersectsSegment(a, b) } }
        return when {
            hit -> Verdict.CONFIRMED
            g.partial || change.relationCount > 0 -> Verdict.UNCERTAIN
            else -> Verdict.OUTSIDE
        }
    }
}
