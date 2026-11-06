package org.osmwatch.changesetwatch.domain

data class TagChange(val key: String, val old: String?, val new: String?)

/** Cosa è cambiato in un elemento: tag, spostamento (nodi), nodi aggiunti/tolti (way). */
data class ElementDiff(
    val ref: ElementRef,
    val action: ChangeAction,
    val tags: List<TagChange>,
    val movedMeters: Double?,
    val nodesAdded: Int,
    val nodesRemoved: Int,
    val previousMissing: Boolean,
)

object ElementDiffer {
    /** [current]: stato nel changeset (null se cancellato); [previous]: versione v-1 (null se creato o non recuperabile). */
    fun diff(ref: ElementRef, action: ChangeAction, current: ElementData?, previous: ElementData?): ElementDiff {
        val old = previous?.tags.orEmpty()
        val new = current?.tags.orEmpty()
        val tags = (old.keys + new.keys).sorted().mapNotNull { k ->
            if (old[k] == new[k]) null else TagChange(k, old[k], new[k])
        }
        val moved = if (ref.type == ElementType.NODE && current?.at != null && previous?.at != null) distanceMeters(previous.at, current.at) else null
        val oldRefs = previous?.refs.orEmpty().toSet()
        val newRefs = current?.refs.orEmpty().toSet()
        return ElementDiff(
            ref, action, tags, moved?.takeIf { it >= 0.5 },
            nodesAdded = if (previous == null) 0 else (newRefs - oldRefs).size,
            nodesRemoved = if (previous == null) 0 else (oldRefs - newRefs).size,
            previousMissing = action != ChangeAction.CREATE && previous == null,
        )
    }
}
