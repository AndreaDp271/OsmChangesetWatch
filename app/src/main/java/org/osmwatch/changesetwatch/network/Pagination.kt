package org.osmwatch.changesetwatch.network

import java.time.Instant
import org.osmwatch.changesetwatch.domain.Changeset

const val PAGE_SIZE = 100

/**
 * Pagina all'indietro: se una pagina è piena (100), richiede la successiva con T2 = created_at più
 * vecchio + 1 s (il +1 s evita di perdere changeset creati nello stesso secondo; i doppioni sono
 * scartati per id). Si ferma se una pagina non porta id nuovi (100+ changeset nello stesso secondo)
 * o dopo [maxPages].
 */
fun fetchAllPages(
    maxPages: Int = 5,
    fetch: (createdBefore: Instant?) -> List<Changeset>,
): List<Changeset> {
    val out = LinkedHashMap<Long, Changeset>()
    var before: Instant? = null
    repeat(maxPages) {
        val page = fetch(before)
        val sizeBefore = out.size
        page.forEach { out.putIfAbsent(it.id, it) }
        if (page.size < PAGE_SIZE || out.size == sizeBefore) return out.values.toList()
        before = page.minOf { it.createdAt }.plusSeconds(1)
    }
    return out.values.toList()
}
