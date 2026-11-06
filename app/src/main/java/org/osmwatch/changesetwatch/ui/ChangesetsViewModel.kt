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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmwatch.changesetwatch.data.AppDatabase
import org.osmwatch.changesetwatch.data.ChangesetRow
import org.osmwatch.changesetwatch.data.WatchArea
import org.osmwatch.changesetwatch.data.ChangesetRepository
import org.osmwatch.changesetwatch.domain.ChangeGeometry
import org.osmwatch.changesetwatch.domain.GeometryResolver
import org.osmwatch.changesetwatch.network.OsmClient
import org.osmwatch.changesetwatch.network.OsmElementSource

@OptIn(ExperimentalCoroutinesApi::class)
class ChangesetsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val repo = ChangesetRepository(db)

    val areaFilter = MutableStateFlow<Long?>(null)
    val includeSeen = MutableStateFlow(true)
    val refreshing = MutableStateFlow(false)
    /** Messaggio d'errore dell'ultimo refresh, null se ok. */
    val error = MutableStateFlow<String?>(null)

    val areas: StateFlow<List<WatchArea>> =
        db.areaDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val changesets: StateFlow<List<ChangesetRow>> =
        combine(areaFilter, includeSeen) { a, s -> a to s }
            .flatMapLatest { (a, s) -> db.changesetDao().observe(a, s) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeOne(id: Long, areaId: Long): Flow<ChangesetRow?> = db.changesetDao().observeOne(id, areaId)


    // --- minimappa del changeset ---
    private val client = OsmClient()
    private val geoCache = HashMap<Long, GeoState>()
    val geoState = MutableStateFlow<GeoState>(GeoState.Loading)

    /** Scarica osmChange + poche richieste (una alla volta) e ricostruisce la geometria. Oltre [MAX_GEO_CHANGES] modifiche: non scarica. */
    fun loadGeometry(row: ChangesetRow) {
        geoCache[row.id]?.let { geoState.value = it; return }
        if ((row.changesCount ?: 0) > MAX_GEO_CHANGES) { geoState.value = GeoState.TooLarge; return }
        geoState.value = GeoState.Loading
        viewModelScope.launch {
            val s = try {
                val g = withContext(Dispatchers.IO) { GeometryResolver.resolve(client.fetchOsmChange(row.id), OsmElementSource(client)) }
                GeoState.Ready(g)
            } catch (e: Exception) {
                GeoState.Failed(e.message ?: e.javaClass.simpleName)
            }
            if (s is GeoState.Ready) geoCache[row.id] = s
            geoState.value = s
        }
    }

    fun markSeen(id: Long, areaId: Long) = viewModelScope.launch { db.changesetDao().markSeen(id, areaId) }

    /**
     * Scarica gli ultimi 100 changeset per ogni area abilitata, anche precedenti alla creazione dell'area.
     * Aree una alla volta con pausa di 1 s (rate limit). I duplicati sono ignorati dal DB.
     * Fase 3: tutti `UNCERTAIN` (il filtro vero è la fase 5). Paginazione/`from`: fase 4.
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
                        repo.refreshLatest(area)
                    } catch (e: Exception) {
                        failures += "${area.name}: ${e.message ?: e.javaClass.simpleName}"
                    }
                }
            } finally {
                error.update { failures.takeIf { it.isNotEmpty() }?.joinToString("\n") }
                refreshing.value = false
            }
        }
    }
}

const val MAX_GEO_CHANGES = 2000

sealed interface GeoState {
    data object Loading : GeoState
    data object TooLarge : GeoState
    data class Failed(val message: String) : GeoState
    data class Ready(val geometry: ChangeGeometry) : GeoState
}
