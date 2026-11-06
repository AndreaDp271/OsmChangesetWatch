package org.osmwatch.changesetwatch.domain

import java.time.Instant

/** Una versione di un elemento OSM; [visible] false = versione di cancellazione (senza tag né coordinate). */
data class ElementVersion(
    val version: Int,
    val changeset: Long?,
    val user: String?,
    val timestamp: Instant?,
    val visible: Boolean,
    val data: ElementData,
)

object ElementHistory {
    /**
     * Ogni versione con il confronto rispetto alla precedente, dalla più recente.
     * La prima versione (o una che ripristina un elemento cancellato) è una creazione; una non visibile è una cancellazione.
     */
    fun diffs(ref: ElementRef, versions: List<ElementVersion>): List<Pair<ElementVersion, ElementDiff>> {
        val sorted = versions.sortedBy { it.version }
        return sorted.mapIndexed { i, v ->
            val prev = sorted.getOrNull(i - 1)
            val action = when {
                !v.visible -> ChangeAction.DELETE
                prev == null || !prev.visible -> ChangeAction.CREATE
                else -> ChangeAction.MODIFY
            }
            v to ElementDiffer.diff(
                ref.copy(version = v.version), action,
                current = if (v.visible) v.data else null,
                previous = if (prev?.visible == true) prev.data else null,
            )
        }.reversed()
    }
}
