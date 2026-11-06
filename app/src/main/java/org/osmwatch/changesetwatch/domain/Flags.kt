package org.osmwatch.changesetwatch.domain

enum class Flag { MANY_CHANGES, NO_COMMENT, SUSPECT_USER, MASS_DELETE }

/** Flag euristici stile OSMCha, calcolati in locale: dall'intestazione del changeset o dall'osmChange già scaricato. */
object Flags {
    const val MANY_CHANGES_MIN = 500
    const val MASS_DELETE_MIN = 20

    fun header(changesCount: Int?, comment: String?, user: String?, suspects: Set<String>): List<Flag> = buildList {
        if ((changesCount ?: 0) >= MANY_CHANGES_MIN) add(Flag.MANY_CHANGES)
        if (comment.isNullOrBlank()) add(Flag.NO_COMMENT)
        if (user != null && user in suspects) add(Flag.SUSPECT_USER)
    }

    /** Cancellazione di massa: almeno [MASS_DELETE_MIN] elementi cancellati e almeno la metà del changeset. */
    fun fromChange(c: OsmChangeSummary): List<Flag> {
        val total = c.nodes.size + c.ways.size
        val deleted = c.nodes.count { it.action == ChangeAction.DELETE } + c.ways.count { it.action == ChangeAction.DELETE }
        return if (deleted >= MASS_DELETE_MIN && deleted * 2 >= total) listOf(Flag.MASS_DELETE) else emptyList()
    }
}
