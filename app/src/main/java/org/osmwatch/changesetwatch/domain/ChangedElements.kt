package org.osmwatch.changesetwatch.domain

data class ChangedElement(val ref: ElementRef, val action: ChangeAction, val label: String)

object ChangedElements {
    private val RANK = mapOf(ElementType.RELATION to 0, ElementType.WAY to 1, ElementType.NODE to 2)

    /** Nome e primo altro tag ("Via Roma · highway=residential"); vuoto se l'elemento non ha tag (es. nodo di una way, o elemento cancellato). */
    fun label(tags: Map<String, String>): String =
        listOfNotNull(tags["name"], tags.entries.firstOrNull { it.key != "name" }?.let { "${it.key}=${it.value}" }).joinToString(" · ")

    /** Relation, poi way, poi nodi; dentro ogni tipo prima gli elementi con tag (sort stabile). */
    fun list(c: OsmChangeSummary): List<ChangedElement> =
        (c.relations.map { ChangedElement(ElementRef(ElementType.RELATION, it.id, it.version), it.action, label(it.tags)) } +
            c.ways.map { ChangedElement(ElementRef(ElementType.WAY, it.id, it.version), it.action, label(it.tags)) } +
            c.nodes.map { ChangedElement(ElementRef(ElementType.NODE, it.id, it.version), it.action, label(it.tags)) })
            .sortedWith(compareBy({ RANK.getValue(it.ref.type) }, { it.label.isEmpty() }))
}
