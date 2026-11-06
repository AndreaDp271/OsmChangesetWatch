package org.osmwatch.changesetwatch.domain

data class LatLon(val lat: Double, val lon: Double)

data class VersionedId(val id: Long, val version: Int?)

data class DrawPoint(val action: ChangeAction, val at: LatLon, val ref: ElementRef)
data class DrawLine(val action: ChangeAction, val points: List<LatLon>, val ref: ElementRef)

/** [partial]: qualche elemento non è stato disegnato (coordinate non ricavabili o limite di richieste). */
data class ChangeGeometry(
    val points: List<DrawPoint>, val lines: List<DrawLine>, val partial: Boolean,
    /** Forma PRIMA della modifica (way modificate / nodi spostati): disegnata in grigio sotto quella nuova. */
    val beforeLines: List<DrawLine> = emptyList(), val beforePoints: List<LatLon> = emptyList(),
) {
    val isEmpty get() = points.isEmpty() && lines.isEmpty()
    fun allPoints(): List<LatLon> = points.map { it.at } + lines.flatMap { it.points } + beforeLines.flatMap { it.points } + beforePoints
}

/** Accesso agli elementi OSM (implementato dal client di rete; finto nei test). Nodi cancellati/ignoti: assenti dal risultato. */
interface ElementSource {
    fun nodes(ids: List<VersionedId>): Map<Long, LatLon>
    fun ways(ids: List<VersionedId>): Map<Long, List<Long>>

    /** Stato completo (coordinate, nodi, tag) di versioni precise: serve al dettaglio di un elemento toccato sulla mappa. */
    fun details(type: ElementType, ids: List<VersionedId>): Map<Long, ElementData> = emptyMap()
}

/**
 * Ricostruisce la geometria di un changeset dal solo osmChange + poche richieste:
 * - nodi creati/modificati: coordinate nel osmChange; cancellati: versione precedente (v-1);
 * - way create/modificate: nodi dal osmChange, gli altri (non modificati) dalla versione corrente;
 * - way cancellate: nodi della versione precedente, posizione attuale dei nodi (può differire dall'originale).
 * Limiti: le relation non sono disegnate; i nodi già referenziati da una way sono mostrati solo come tratto.
 */
object GeometryResolver {
    const val MAX_LOOKUPS = 700 // id richiesti in totale oltre a quelli del osmChange (ognuno ~ una frazione di richiesta da 100)

    fun resolve(change: OsmChangeSummary, src: ElementSource, includeBefore: Boolean = false): ChangeGeometry {
        var partial = false
        var budget = MAX_LOOKUPS
        fun take(ids: List<VersionedId>): List<VersionedId> {
            if (ids.size > budget) partial = true
            return ids.take(budget).also { budget -= it.size }
        }

        val known = HashMap<Long, LatLon>()
        change.nodes.forEach { n -> if (n.lat != null && n.lon != null) known[n.id] = LatLon(n.lat, n.lon) }

        // nodi cancellati: posizione prima della cancellazione
        val deletedNodes = change.nodes.filter { it.action == ChangeAction.DELETE }
        val prevNodes = take(deletedNodes.filter { (it.version ?: 0) > 1 }.map { VersionedId(it.id, it.version!! - 1) })
        val deletedAt = if (prevNodes.isEmpty()) emptyMap() else src.nodes(prevNodes)
        if (deletedNodes.any { it.id !in deletedAt }) partial = true
        known.putAll(deletedAt)

        // way cancellate: nodi dalla versione precedente
        val deletedWays = change.ways.filter { it.action == ChangeAction.DELETE }
        val prevWays = take(deletedWays.filter { (it.version ?: 0) > 1 }.map { VersionedId(it.id, it.version!! - 1) })
        val deletedRefs = if (prevWays.isEmpty()) emptyMap() else src.ways(prevWays)
        val refsOf = change.ways.associate { w -> w.id to (if (w.action == ChangeAction.DELETE) deletedRefs[w.id] ?: emptyList() else w.nodeRefs) }
        if (deletedWays.any { deletedRefs[it.id] == null }) partial = true

        // nodi delle way non presenti: posizione corrente
        val missing = refsOf.values.flatten().filter { it !in known }.distinct()
        val lookup = take(missing.map { VersionedId(it, null) })
        if (lookup.isNotEmpty()) known.putAll(src.nodes(lookup))

        val lines = ArrayList<DrawLine>()
        for (w in change.ways) {
            val refs = refsOf[w.id].orEmpty()
            var run = ArrayList<LatLon>()
            for (r in refs) {
                val at = known[r]
                if (at != null) run += at else { partial = true; if (run.size >= 2) lines += DrawLine(w.action, run, ElementRef(ElementType.WAY, w.id, w.version)); run = ArrayList() }
            }
            if (run.size >= 2) lines += DrawLine(w.action, run, ElementRef(ElementType.WAY, w.id, w.version))
        }

        val inWays = refsOf.values.flatten().toSet()
        val points = change.nodes.filter { it.id !in inWays }.mapNotNull { n -> known[n.id]?.let { DrawPoint(n.action, it, ElementRef(ElementType.NODE, n.id, n.version)) } }
        // geometria PRIMA della modifica (solo per la minimappa): way modificate e nodi spostati
        var beforeLines = emptyList<DrawLine>()
        var beforePoints = emptyList<LatLon>()
        if (includeBefore) {
            val modNodes = change.nodes.filter { it.action == ChangeAction.MODIFY && (it.version ?: 0) > 1 }
            val modWays = change.ways.filter { it.action == ChangeAction.MODIFY && (it.version ?: 0) > 1 }
            val prevAt = take(modNodes.map { VersionedId(it.id, it.version!! - 1) }).let { if (it.isEmpty()) emptyMap() else src.nodes(it) }
            val prevRefs = take(modWays.map { VersionedId(it.id, it.version!! - 1) }).let { if (it.isEmpty()) emptyMap() else src.ways(it) }
            val need = prevRefs.values.flatten().filter { it !in known && it !in prevAt }.distinct()
            val extra = take(need.map { VersionedId(it, null) }).let { if (it.isEmpty()) emptyMap() else src.nodes(it) }
            val before = known + extra + prevAt // i nodi spostati hanno la posizione v-1
            val bl = ArrayList<DrawLine>()
            for (w in modWays) {
                val pr = prevRefs[w.id] ?: continue
                val moved = (pr + w.nodeRefs).any { prevAt[it] != null && prevAt[it] != known[it] }
                if (pr == w.nodeRefs && !moved) continue // geometria invariata (es. solo tag)
                var run = ArrayList<LatLon>()
                for (r in pr) {
                    val at = before[r]
                    if (at != null) run += at else { if (run.size >= 2) bl += DrawLine(ChangeAction.MODIFY, run, ElementRef(ElementType.WAY, w.id, w.version)); run = ArrayList() }
                }
                if (run.size >= 2) bl += DrawLine(ChangeAction.MODIFY, run, ElementRef(ElementType.WAY, w.id, w.version))
            }
            beforeLines = bl
            beforePoints = modNodes.filter { it.id !in inWays }.mapNotNull { n ->
                val a = prevAt[n.id]; val b = known[n.id]
                if (a != null && b != null && distanceMeters(a, b) >= 0.5) a else null
            }
        }
        return ChangeGeometry(points, lines, partial, beforeLines, beforePoints)
    }
}
