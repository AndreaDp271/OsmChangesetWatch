package org.osmwatch.changesetwatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmwatch.changesetwatch.data.AppDatabase
import org.osmwatch.changesetwatch.data.WatchArea

class AreasViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).areaDao()

    /** null finché Room non ha emesso il primo valore. */
    val areas: StateFlow<List<WatchArea>?> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(area: WatchArea) = viewModelScope.launch { dao.upsert(area) }
    fun setEnabled(id: Long, enabled: Boolean) = viewModelScope.launch { dao.setEnabled(id, enabled) }
    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }
}
