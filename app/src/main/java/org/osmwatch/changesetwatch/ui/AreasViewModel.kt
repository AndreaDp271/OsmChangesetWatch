package org.osmwatch.changesetwatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmwatch.changesetwatch.data.AppDatabase
import org.osmwatch.changesetwatch.data.WatchArea

class AreasViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).areaDao()

    /** null finché Room non ha emesso il primo valore. */
    val areas: StateFlow<List<WatchArea>?> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Monitoraggio di un utente OSM; il nome è esatto (maiuscole comprese). Ignora i doppioni. */
    fun watchUser(name: String) =
        viewModelScope.launch {
            val n = name.trim().removePrefix("@")
            if (n.isNotEmpty() && !dao.isWatched(n)) {
                dao.upsert(
                    WatchArea(
                        name = "@$n",
                        minLat = 0.0,
                        minLon = 0.0,
                        maxLat = 0.0,
                        maxLon = 0.0,
                        watchUser = n,
                    )
                )
            }
        }

    /** Non visti per area (id -> numero). */
    val unseen: StateFlow<Map<Long, Int>> =
        AppDatabase.get(app)
            .changesetDao()
            .observeUnseenCounts()
            .map { l -> l.associate { it.areaId to it.n } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun save(area: WatchArea) = viewModelScope.launch { dao.upsert(area) }

    fun setEnabled(id: Long, enabled: Boolean) =
        viewModelScope.launch { dao.setEnabled(id, enabled) }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }
}
