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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import org.osmdroid.config.Configuration
import org.osmwatch.changesetwatch.network.OsmConfig
import org.osmwatch.changesetwatch.ui.AreaEditorScreen
import org.osmwatch.changesetwatch.ui.AreasScreen
import org.osmwatch.changesetwatch.ui.AreasViewModel

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
private fun AppRoot(vm: AreasViewModel = viewModel()) {
    val areas by vm.areas.collectAsState()
    var editing by rememberSaveable { mutableStateOf<Long?>(null) } // null = lista, 0 = nuova, >0 = id area
    val id = editing
    if (id == null) {
        AreasScreen(
            areas = areas,
            onAdd = { editing = NEW_AREA },
            onEdit = { editing = it.id },
            onToggle = { a, on -> vm.setEnabled(a.id, on) },
            onDelete = { vm.delete(it.id) },
        )
    } else {
        val area = areas?.firstOrNull { it.id == id }
        if (id == NEW_AREA || area != null) {
            AreaEditorScreen(area = area, onSave = { vm.save(it); editing = null }, onBack = { editing = null })
        }
    }
}
