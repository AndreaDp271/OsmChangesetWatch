package org.osmwatch.changesetwatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.data.WatchArea
import org.osmwatch.changesetwatch.domain.Geometry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreasScreen(
    areas: List<WatchArea>?,
    onAdd: () -> Unit,
    onEdit: (WatchArea) -> Unit,
    onToggle: (WatchArea, Boolean) -> Unit,
    onDelete: (WatchArea) -> Unit,
) {
    var toDelete by remember { mutableStateOf<WatchArea?>(null) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.areas)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, stringResource(R.string.new_area)) }
        },
    ) { pad ->
        if (areas?.isEmpty() == true) {
            Text(stringResource(R.string.no_areas), modifier = Modifier.padding(pad).padding(16.dp))
        }
        LazyColumn(Modifier.padding(pad)) {
            items(areas.orEmpty(), key = { it.id }) { a ->
                ListItem(
                    modifier = Modifier.clickable { onEdit(a) },
                    headlineContent = { Text(a.name) },
                    supportingContent = { Text(stringResource(R.string.area_km2, Geometry.areaKm2(a.bbox))) },
                    trailingContent = {
                        Row {
                            Switch(checked = a.enabled, onCheckedChange = { onToggle(a, it) })
                            IconButton(onClick = { toDelete = a }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
    toDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_area_title, a.name)) },
            confirmButton = { TextButton(onClick = { onDelete(a); toDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
