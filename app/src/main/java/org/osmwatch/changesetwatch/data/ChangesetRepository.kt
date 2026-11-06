package org.osmwatch.changesetwatch.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmwatch.changesetwatch.domain.ChangesetVerifier
import org.osmwatch.changesetwatch.domain.Verdict
import org.osmwatch.changesetwatch.network.OsmClient
import org.osmwatch.changesetwatch.network.OsmElementSource
import org.osmwatch.changesetwatch.network.fetchAllPages

class ChangesetRepository(
    private val db: AppDatabase,
    private val client: OsmClient = OsmClient(),
    private val maxChanges: () -> Int = { 2000 },
) {

    /** Sovrapposizione di sicurezza sul bordo temporale; i doppioni sono scartati dal DB. */
    private val overlapMs = 5 * 60_000L

    /**
     * Quanti download di osmChange al massimo per area e per giro; il resto resta PENDING per il giro dopo.
     * I changeset che si decidono dalla sola bbox (tutti dentro o tutti fuori) non consumano il budget.
     */
    private val verifyBudget = 25

    /** Quanti PENDING esaminare per giro (quelli risolti dalla bbox costano zero). */
    private val pendingScan = 300

    /**
     * Ultimi 100 changeset dell'area, anche precedenti alla sua creazione, poi verifica geometrica.
     * Ritorna i changeset che risultano davvero nell'area (CONFIRMED) verificati in questo giro.
     */
    suspend fun refreshLatest(area: WatchArea, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): List<ChangesetRow> {
        val now = System.currentTimeMillis()
        if (area.watchUser != null) {
            val fetched = withContext(Dispatchers.IO) { client.fetchChangesetsByUser(area.watchUser, 100) }
            return storeUser(area, fetched.map { it.toRow(area.id, Verdict.CONFIRMED, now) }, now)
        }
        val fetched = withContext(Dispatchers.IO) { client.fetchChangesets(area.bbox) }
        store(area, fetched.map { it.toRow(area.id, Verdict.PENDING, now) }, now)
        return verifyPending(area, onProgress)
    }

    /**
     * Controllo periodico: changeset chiusi dopo lastCheckedAt (−5 min) o ancora aperti, con paginazione.
     * Primo controllo (lastCheckedAt null): solo "seed" degli ultimi 100, restituisce lista vuota (niente notifica a pioggia).
     */
    suspend fun refreshIncremental(area: WatchArea): List<ChangesetRow> {
        val last = area.lastCheckedAt ?: return refreshLatest(area).let { emptyList() }
        val now = System.currentTimeMillis()
        val since = java.time.Instant.ofEpochMilli(last - overlapMs)
        if (area.watchUser != null) {
            val byUser = withContext(Dispatchers.IO) { client.fetchChangesetsByUser(area.watchUser, 100, since) }
            return storeUser(area, byUser.map { it.toRow(area.id, Verdict.CONFIRMED, now) }, now)
        }
        val fetched = withContext(Dispatchers.IO) {
            fetchAllPages { before -> client.fetchChangesets(area.bbox, closedAfter = since, createdBefore = before) }
        }
        store(area, fetched.map { it.toRow(area.id, Verdict.PENDING, now) }, now)
        return verifyPending(area)
    }

    /** Verifica i PENDING dell'area, dal più recente. Un errore di rete interrompe il giro: restano PENDING. */
    suspend fun verifyPending(area: WatchArea, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<ChangesetRow> {
        val pending = db.changesetDao().pending(area.id, pendingScan)
        var downloads = 0
        val verifier = ChangesetVerifier(
            maxChanges = maxChanges(),
            fetchChange = { id -> downloads++; Thread.sleep(1_000); client.fetchOsmChange(id) }, // un osmChange alla volta
            src = OsmElementSource(client),
        )
        val confirmed = mutableListOf<ChangesetRow>()
        for ((i, row) in pending.withIndex()) {
            if (downloads >= verifyBudget) break
            onProgress(i, pending.size)
            val bbox = if (row.bboxMinLat != null) org.osmwatch.changesetwatch.domain.BBox(row.bboxMinLat, row.bboxMinLon!!, row.bboxMaxLat!!, row.bboxMaxLon!!) else null
            val verdict = try {
                withContext(Dispatchers.IO) { verifier.verify(row.id, bbox, row.changesCount, area.shape) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Repo", "verifica ${row.id} fallita: ${e.message}")
                return confirmed
            }
            db.changesetDao().setVerdict(row.id, area.id, verdict.name)
            if (verdict == Verdict.CONFIRMED) confirmed += row.copy(verdict = verdict)
        }
        onProgress(pending.size, pending.size)
        return confirmed
    }

    /**
     * "Carica altri": i 100 changeset creati prima del più vecchio già salvato per l'area, poi la verifica.
     * Non tocca lastCheckedAt (il controllo periodico continua da dove era). Senza nulla di salvato equivale a [refreshLatest].
     */
    suspend fun loadOlder(area: WatchArea, onProgress: (Int, Int) -> Unit = { _, _ -> }): OlderResult {
        val oldest = db.changesetDao().oldestCreatedAt(area.id)
            ?: return refreshLatest(area, onProgress).let { OlderResult(it.size, it.size) }
        val before = java.time.Instant.ofEpochMilli(oldest)
        val now = System.currentTimeMillis()
        val fetched = withContext(Dispatchers.IO) {
            if (area.watchUser != null) client.fetchChangesetsByUser(area.watchUser, 100, java.time.Instant.EPOCH, before)
            else client.fetchChangesets(area.bbox, closedAfter = java.time.Instant.EPOCH, createdBefore = before)
        }
        val rows = fetched.map { it.toRow(area.id, if (area.watchUser != null) Verdict.CONFIRMED else Verdict.PENDING, now) }
        val inserted = db.changesetDao().insertAll(rows)
        fillCounts(rows)
        val fresh = inserted.count { it != -1L }
        if (area.watchUser != null) return OlderResult(fresh, fresh)
        return OlderResult(fresh, verifyPending(area, onProgress).size)
    }

    /** [fetched] changeset nuovi scaricati, [confirmed] quelli verificati nell'area in questo giro. */
    data class OlderResult(val fetched: Int, val confirmed: Int)

    /** Monitoraggio utente: niente verifica geometrica (valgono tutti); ritorna solo le righe davvero nuove. */
    private suspend fun storeUser(area: WatchArea, rows: List<ChangesetRow>, checkedAt: Long): List<ChangesetRow> {
        val inserted = db.changesetDao().insertAll(rows)
        fillCounts(rows)
        db.areaDao().setLastChecked(area.id, checkedAt)
        return rows.filterIndexed { i, _ -> inserted[i] != -1L }
    }

    /** Inserisce e aggiorna lastCheckedAt (l'istante *prima* della richiesta). */
    private suspend fun store(area: WatchArea, rows: List<ChangesetRow>, checkedAt: Long) {
        db.changesetDao().insertAll(rows)
        fillCounts(rows)
        db.areaDao().setLastChecked(area.id, checkedAt)
    }

    private suspend fun fillCounts(rows: List<ChangesetRow>) = rows.forEach { r ->
        if (r.createdCount != null && r.modifiedCount != null && r.deletedCount != null) {
            db.changesetDao().fillCounts(r.id, r.createdCount, r.modifiedCount, r.deletedCount)
        }
    }
}
