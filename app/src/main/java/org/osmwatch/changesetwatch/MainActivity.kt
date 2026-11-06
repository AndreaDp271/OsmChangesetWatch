package org.osmwatch.changesetwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import org.osmdroid.config.Configuration
import org.osmwatch.changesetwatch.network.OsmConfig
import org.osmwatch.changesetwatch.ui.AreaEditorScreen
import org.osmwatch.changesetwatch.ui.AreasScreen
import org.osmwatch.changesetwatch.ui.AreasViewModel
import org.osmwatch.changesetwatch.ui.ChangesetDetailScreen
import org.osmwatch.changesetwatch.ui.ChangesetsScreen
import org.osmwatch.changesetwatch.ui.ChangesetsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid: UA obbligatorio per la tile policy OSM; cache nella cache privata (nessun permesso storage)
        Configuration.getInstance().apply {
            userAgentValue = OsmConfig.USER_AGENT
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
            tileFileSystemCacheMaxBytes = 50L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 40L * 1024 * 1024
        }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) { AppRoot() }
        }
    }
}

private const val NEW_AREA = 0L

@Composable
private fun AppRoot(vm: AreasViewModel = viewModel(), cvm: ChangesetsViewModel = viewModel()) {
    val areas by vm.areas.collectAsState()
    var showAreas by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<Long?>(null) } // null = lista, 0 = nuova, >0 = id area
    var detail by rememberSaveable { mutableStateOf<List<Long>?>(null) } // [id changeset, id area]
    val id = editing
    val d = detail
    if (id == null && !showAreas && d == null) {
        val rows by cvm.changesets.collectAsState()
        val allAreas by cvm.areas.collectAsState()
        val filter by cvm.areaFilter.collectAsState()
        val seen by cvm.includeSeen.collectAsState()
        val refreshing by cvm.refreshing.collectAsState()
        val error by cvm.error.collectAsState()
        ChangesetsScreen(
            rows = rows, areas = allAreas, areaFilter = filter, includeSeen = seen, refreshing = refreshing, error = error,
            onAreaFilter = { cvm.areaFilter.value = it }, onIncludeSeen = { cvm.includeSeen.value = it },
            onRefresh = cvm::refresh, onOpen = { detail = listOf(it.id, it.areaId) }, onAreas = { showAreas = true },
        )
    } else if (d != null) {
        val row by remember(d) { cvm.observeOne(d[0], d[1]) }.collectAsState(null)
        ChangesetDetailScreen(
            row = row, areaName = cvm.areas.value.firstOrNull { it.id == d[1] }?.name,
            onMarkSeen = { cvm.markSeen(d[0], d[1]) }, onBack = { detail = null },
        )
    } else if (id == null) {
        AreasScreen(
            areas = areas,
            onAdd = { editing = NEW_AREA },
            onEdit = { editing = it.id },
            onToggle = { a, on -> vm.setEnabled(a.id, on) },
            onDelete = { vm.delete(it.id) },
            onBack = { showAreas = false },
        )
    } else {
        val area = areas?.firstOrNull { it.id == id }
        if (id == NEW_AREA || area != null) {
            AreaEditorScreen(area = area, onSave = { vm.save(it); editing = null }, onBack = { editing = null })
        }
    }
}
