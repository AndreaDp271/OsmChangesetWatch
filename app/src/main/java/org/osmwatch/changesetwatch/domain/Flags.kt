package org.osmwatch.changesetwatch.domain

enum class Flag {
    MANY_CHANGES,
    NO_COMMENT,
    SUSPECT_USER,
    MASS_DELETE,
    NEW_MAPPER,
    PREVIOUSLY_BLOCKED,
}

/**
 * Flag euristici stile OSMCha, calcolati in locale: dall'intestazione del changeset o
 * dall'osmChange già scaricato.
 */
object Flags {
    const val MANY_CHANGES_MIN = 500
    const val MASS_DELETE_MIN = 20
    const val NEW_MAPPER_MAX_CHANGESETS = 10

    fun header(
        changesCount: Int?,
        comment: String?,
        user: String?,
        suspects: Set<String>,
    ): List<Flag> = buildList {
        if ((changesCount ?: 0) >= MANY_CHANGES_MIN) add(Flag.MANY_CHANGES)
        if (comment.isNullOrBlank()) add(Flag.NO_COMMENT)
        if (user != null && user in suspects) add(Flag.SUSPECT_USER)
    }

    /**
     * Dal profilo pubblico: pochi changeset all'attivo (con questo compreso) o già bloccato in
     * passato.
     */
    fun fromUser(u: UserInfo?): List<Flag> = buildList {
        if (u?.changesets != null && u.changesets < NEW_MAPPER_MAX_CHANGESETS) add(Flag.NEW_MAPPER)
        if ((u?.blocksReceived ?: 0) > 0) add(Flag.PREVIOUSLY_BLOCKED)
    }

    /**
     * Stessa regola di [fromChange], ma dai conteggi dell'intestazione: disponibile già in lista.
     */
    fun fromCounts(created: Int?, modified: Int?, deleted: Int?): List<Flag> {
        if (created == null || modified == null || deleted == null) return emptyList()
        val total = created + modified + deleted
        return if (deleted >= MASS_DELETE_MIN && deleted * 2 >= total) listOf(Flag.MASS_DELETE)
        else emptyList()
    }

    /**
     * Cancellazione di massa: almeno [MASS_DELETE_MIN] elementi cancellati e almeno la metà del
     * changeset.
     */
    fun fromChange(c: OsmChangeSummary): List<Flag> {
        val total = c.nodes.size + c.ways.size
        val deleted =
            c.nodes.count { it.action == ChangeAction.DELETE } +
                c.ways.count { it.action == ChangeAction.DELETE }
        return if (deleted >= MASS_DELETE_MIN && deleted * 2 >= total) listOf(Flag.MASS_DELETE)
        else emptyList()
    }
}
