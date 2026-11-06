package org.osmwatch.changesetwatch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmwatch.changesetwatch.network.OsmClient
import org.osmwatch.changesetwatch.network.fetchAllPages

class ChangesetRepository(private val db: AppDatabase, private val client: OsmClient = OsmClient()) {

    /** Sovrapposizione di sicurezza sul bordo temporale; i doppioni sono scartati dal DB. */
    private val overlapMs = 5 * 60_000L

    /** Ultimi 100 changeset dell'area, anche precedenti alla sua creazione. Ritorna le righe nuove. */
    suspend fun refreshLatest(area: WatchArea): List<ChangesetRow> {
        val now = System.currentTimeMillis()
        val fetched = withContext(Dispatchers.IO) { client.fetchChangesets(area.bbox) }
        return store(area, fetched.map { it.toRow(area.id, Verdict.UNCERTAIN, now) }, now)
    }

    /**
     * Controllo periodico: changeset chiusi dopo lastCheckedAt (−5 min) o ancora aperti, con paginazione.
     * Primo controllo (lastCheckedAt null): solo "seed" degli ultimi 100, restituisce lista vuota (niente notifica a pioggia).
     */
    suspend fun refreshIncremental(area: WatchArea): List<ChangesetRow> {
        val last = area.lastCheckedAt ?: return refreshLatest(area).let { emptyList() }
        val now = System.currentTimeMillis()
        val since = java.time.Instant.ofEpochMilli(last - overlapMs)
        val fetched = withContext(Dispatchers.IO) {
            fetchAllPages { before -> client.fetchChangesets(area.bbox, closedAfter = since, createdBefore = before) }
        }
        return store(area, fetched.map { it.toRow(area.id, Verdict.UNCERTAIN, now) }, now)
    }

    /** Inserisce e aggiorna lastCheckedAt (l'istante *prima* della richiesta). Ritorna solo le righe davvero nuove. */
    private suspend fun store(area: WatchArea, rows: List<ChangesetRow>, checkedAt: Long): List<ChangesetRow> {
        val ids = db.changesetDao().insertAll(rows)
        db.areaDao().setLastChecked(area.id, checkedAt)
        return rows.filterIndexed { i, _ -> ids[i] != -1L }
    }
}
