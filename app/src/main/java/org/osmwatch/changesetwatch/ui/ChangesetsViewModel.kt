package org.osmwatch.changesetwatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmwatch.changesetwatch.data.AppDatabase
import org.osmwatch.changesetwatch.data.ChangesetRepository
import org.osmwatch.changesetwatch.data.ChangesetRow
import org.osmwatch.changesetwatch.data.Prefs
import org.osmwatch.changesetwatch.data.WatchArea
import org.osmwatch.changesetwatch.data.toRow
import org.osmwatch.changesetwatch.domain.ChangeAction
import org.osmwatch.changesetwatch.domain.ChangeGeometry
import org.osmwatch.changesetwatch.domain.ElementData
import org.osmwatch.changesetwatch.domain.ElementDiff
import org.osmwatch.changesetwatch.domain.ElementDiffer
import org.osmwatch.changesetwatch.domain.ElementRef
import org.osmwatch.changesetwatch.domain.ElementType
import org.osmwatch.changesetwatch.domain.GeometryResolver
import org.osmwatch.changesetwatch.domain.LatLon
import org.osmwatch.changesetwatch.domain.OsmChangeSummary
import org.osmwatch.changesetwatch.domain.Verdict
import org.osmwatch.changesetwatch.domain.VersionedId
import org.osmwatch.changesetwatch.network.OsmClient
import org.osmwatch.changesetwatch.network.OsmElementSource

@OptIn(ExperimentalCoroutinesApi::class)
class ChangesetsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val client = OsmClient()
    private val prefs = Prefs(app)
    private val repo = ChangesetRepository(db, client, maxChanges = { prefs.maxChanges })

    val areaFilter = MutableStateFlow<Long?>(null)
    val includeSeen = MutableStateFlow(true)
    /** false (default): solo i changeset VERIFICATI dentro l'area; true: anche in attesa/incerti/troppo grandi/fuori. */
    val showAll = MutableStateFlow(false)
    val refreshing = MutableStateFlow(false)
    /** "Verifica 3/25 …" durante il refresh, null altrimenti. */
    val progress = MutableStateFlow<String?>(null)
    /** Messaggio d'errore dell'ultimo refresh, null se ok. */
    val error = MutableStateFlow<String?>(null)

    val areas: StateFlow<List<WatchArea>> =
        db.areaDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Testo di ricerca: utente, commento, editor o id (filtro locale sulla lista). */
    val query = MutableStateFlow("")

    val changesets: StateFlow<List<ChangesetRow>> =
        combine(areaFilter, includeSeen, showAll) { a, s, all -> Triple(a, s, all) }
            .flatMapLatest { (a, s, all) ->
                val verdicts = if (all) Verdict.entries.map { it.name } else listOf(Verdict.CONFIRMED.name)
                db.changesetDao().observe(a, s, verdicts)
            }
            .combine(query) { rows, q ->
                val t = q.trim()
                if (t.isEmpty()) rows else rows.filter {
                    it.id.toString().contains(t) || listOf(it.user, it.comment, it.createdBy).any { f -> f?.contains(t, ignoreCase = true) == true }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Changeset aperto da link esterno e non presente in lista: non salvato nel DB (areaId = EXTERNAL). */
    private val external = MutableStateFlow<Map<Long, ChangesetRow>>(emptyMap())

    fun observeOne(id: Long, areaId: Long): Flow<ChangesetRow?> =
        if (areaId == EXTERNAL) external.map { it[id] } else db.changesetDao().observeOne(id, areaId)

    /** true (default): cerca solo tra i changeset delle aree; false: ricerca globale su OSM (utente esatto o id). */
    val areasOnly = MutableStateFlow(true)
    val globalResults = MutableStateFlow<List<ChangesetRow>>(emptyList())
    val searching = MutableStateFlow(false)

    /** Ricerca globale: numero = id changeset, altrimenti nome utente OSM esatto (l'API non cerca nei commenti). Max 50. */
    fun searchGlobal() {
        val q = query.value.trim()
        if (q.isEmpty() || searching.value) return
        viewModelScope.launch {
            searching.value = true
            error.value = null
            try {
                val found = withContext(Dispatchers.IO) {
                    val id = q.removePrefix("#").toLongOrNull()
                    if (id != null) listOfNotNull(client.fetchChangeset(id)) else client.fetchChangesetsByUser(q, 50)
                }
                val now = System.currentTimeMillis()
                val rows = found.map { it.toRow(EXTERNAL, Verdict.PENDING, now).copy(seen = true) }
                external.update { it + rows.associateBy { r -> r.id } }
                globalResults.value = rows
                if (rows.isEmpty()) error.value = "“$q”: not found"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "“$q”: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                searching.value = false
            }
        }
    }

    /** Da richiesta di apertura (link) a [id, areaId] da mostrare; la UI lo consuma e lo azzera. */
    val openRequest = MutableStateFlow<List<Long>?>(null)

    /** Link `.../changeset/ID`: usa la riga in DB se c'è, altrimenti scarica solo l'intestazione dall'API. */
    fun openLink(id: Long) {
        viewModelScope.launch {
            error.value = null
            try {
                val known = db.changesetDao().findAny(id)
                if (known != null) { openRequest.value = listOf(id, known.areaId); return@launch }
                val cs = withContext(Dispatchers.IO) { client.fetchChangeset(id) }
                if (cs == null) { error.value = "#$id: not found"; return@launch }
                external.update { it + (id to cs.toRow(EXTERNAL, Verdict.PENDING, System.currentTimeMillis()).copy(seen = true)) }
                openRequest.value = listOf(id, EXTERNAL)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "#$id: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    // --- minimappa del changeset ---
    private val geoCache = HashMap<Long, GeoState>()
    val geoState = MutableStateFlow<GeoState>(GeoState.Loading)

    /** Scarica osmChange + poche richieste (una alla volta) e ricostruisce la geometria. Oltre `maxChanges`: non scarica. */
    fun loadGeometry(row: ChangesetRow) {
        diffState.value = null
        geoCache[row.id]?.let { geoState.value = it; return }
        if ((row.changesCount ?: 0) > prefs.maxChanges) { geoState.value = GeoState.TooLarge; return }
        geoState.value = GeoState.Loading
        viewModelScope.launch {
            val s = try {
                withContext(Dispatchers.IO) {
                    val change = client.fetchOsmChange(row.id)
                    GeoState.Ready(GeometryResolver.resolve(change, OsmElementSource(client), includeBefore = true), change)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                GeoState.Failed(e.message ?: e.javaClass.simpleName)
            }
            if (s is GeoState.Ready) geoCache[row.id] = s
            geoState.value = s
        }
    }

    // --- cosa è cambiato in un elemento toccato sulla mappa ---
    val diffState = MutableStateFlow<DiffState?>(null)

    fun clearDiff() { diffState.value = null }

    /** Stato attuale dal osmChange + versione precedente (v-1, una richiesta) per il confronto. */
    fun loadDiff(ref: ElementRef) {
        val change = (geoState.value as? GeoState.Ready)?.change ?: return
        val (action, current) = currentOf(change, ref) ?: return
        diffState.value = DiffState.Loading(ref)
        viewModelScope.launch {
            diffState.value = try {
                val previous = if (action != ChangeAction.CREATE && (ref.version ?: 0) > 1) {
                    withContext(Dispatchers.IO) {
                        OsmElementSource(client).details(ref.type, listOf(VersionedId(ref.id, ref.version!! - 1)))[ref.id]
                    }
                } else null
                DiffState.Ready(ElementDiffer.diff(ref, action, current, previous))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                DiffState.Failed(ref, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun currentOf(change: OsmChangeSummary, ref: ElementRef): Pair<ChangeAction, ElementData?>? = when (ref.type) {
        ElementType.NODE -> change.nodes.firstOrNull { it.id == ref.id }?.let {
            it.action to if (it.action == ChangeAction.DELETE) null else ElementData(
                if (it.lat != null && it.lon != null) LatLon(it.lat, it.lon) else null, emptyList(), it.tags,
            )
        }
        ElementType.WAY -> change.ways.firstOrNull { it.id == ref.id }?.let {
            it.action to if (it.action == ChangeAction.DELETE) null else ElementData(null, it.nodeRefs, it.tags)
        }
    }

    fun markSeen(id: Long, areaId: Long) = viewModelScope.launch { db.changesetDao().markSeen(id, areaId) }

    /**
     * Scarica gli ultimi 100 changeset per ogni area abilitata, anche precedenti alla creazione dell'area, e li verifica
     * (dentro l'area davvero?). Aree una alla volta con pausa di 1 s (rate limit). I duplicati sono ignorati dal DB.
     */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            val failures = mutableListOf<String>()
            try {
                val enabled = db.areaDao().observeAll().first().filter { it.enabled }
                enabled.forEachIndexed { i, area ->
                    if (i > 0) delay(1_000)
                    try {
                        repo.refreshLatest(area) { done, total -> progress.value = if (total > 0) "${area.name}: $done/$total" else null }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures += "${area.name}: ${e.message ?: e.javaClass.simpleName}"
                    }
                }
            } finally {
                progress.value = null
                error.update { failures.takeIf { it.isNotEmpty() }?.joinToString("\n") }
                refreshing.value = false
            }
        }
    }
}

/** areaId fittizio dei changeset aperti da link e non appartenenti a nessuna area. */
const val EXTERNAL = -1L

sealed interface GeoState {
    data object Loading : GeoState
    data object TooLarge : GeoState
    data class Failed(val message: String) : GeoState
    data class Ready(val geometry: ChangeGeometry, val change: OsmChangeSummary) : GeoState
}

sealed interface DiffState {
    data class Loading(val ref: ElementRef) : DiffState
    data class Failed(val ref: ElementRef, val message: String) : DiffState
    data class Ready(val diff: ElementDiff) : DiffState
}
