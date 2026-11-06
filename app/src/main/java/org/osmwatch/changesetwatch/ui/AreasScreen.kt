package org.osmwatch.changesetwatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.data.WatchArea
import org.osmwatch.changesetwatch.data.glyphRings
import org.osmwatch.changesetwatch.data.shape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreasScreen(
    areas: List<WatchArea>?,
    unseen: Map<Long, Int>,
    onAdd: () -> Unit,
    onWatchUser: (String) -> Unit,
    onEdit: (WatchArea) -> Unit,
    onToggle: (WatchArea, Boolean) -> Unit,
    onDelete: (WatchArea) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var toDelete by remember { mutableStateOf<WatchArea?>(null) }
    var userDialog by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.areas), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { userDialog = true }) {
                        Icon(Icons.Default.PersonAdd, stringResource(R.string.watch_user))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.new_area)) },
            )
        },
    ) { pad ->
        val (places, users) = areas.orEmpty().partition { it.watchUser == null }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (areas?.isEmpty() == true)
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.Place,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            stringResource(R.string.no_areas),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            if (places.isNotEmpty()) item { SectionTitle(stringResource(R.string.section_places)) }
            items(places, key = { it.id }) { a ->
                AreaCard(a, unseen[a.id] ?: 0, { onEdit(a) }, { onToggle(a, it) }) { toDelete = a }
            }
            if (users.isNotEmpty()) item { SectionTitle(stringResource(R.string.section_users)) }
            items(users, key = { it.id }) { a ->
                AreaCard(a, unseen[a.id] ?: 0, null, { onToggle(a, it) }) { toDelete = a }
            }
        }
    }
    if (userDialog)
        AlertDialog(
            onDismissRequest = { userDialog = false },
            icon = { Icon(Icons.Default.PersonAdd, null) },
            title = { Text(stringResource(R.string.watch_user)) },
            text = {
                Column {
                    Text(stringResource(R.string.watch_user_desc))
                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.osm_user_name)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = userName.isNotBlank(),
                    onClick = {
                        onWatchUser(userName)
                        userName = ""
                        userDialog = false
                    },
                ) {
                    Text(stringResource(R.string.watch_user_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { userDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    toDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_area_title, a.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(a)
                        toDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp),
    )
}

/**
 * Scheda di un'area (o di un utente seguito): impronta, nome, dimensione, ultimo controllo, non
 * visti, interruttore.
 */
@Composable
private fun AreaCard(
    a: WatchArea,
    unseen: Int,
    onEdit: (() -> Unit)?,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val user = a.watchUser != null
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .then(if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Footprint(
                area = if (user) null else a.bbox,
                change = null,
                color = MaterialTheme.colorScheme.primary,
                areaRings = a.glyphRings,
                fillArea = true,
                fallbackIcon = Icons.Default.Person,
                modifier = Modifier.alpha(if (a.enabled) 1f else 0.45f),
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (unseen > 0)
                        Pill(
                            stringResource(R.string.unseen_n, unseen),
                            MaterialTheme.colorScheme.primary,
                            Modifier.padding(start = 8.dp),
                        )
                }
                Row(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (user) Meta(Icons.Default.Person, stringResource(R.string.watched_user_desc))
                    else
                        Meta(
                            if (a.polygon != null) Icons.Default.LocationCity
                            else Icons.Default.SquareFoot,
                            stringResource(R.string.area_km2, a.shape.areaKm2()),
                        )
                    Meta(
                        Icons.Default.Schedule,
                        a.lastCheckedAt?.let { relativeTime(it) }
                            ?: stringResource(R.string.never_checked),
                    )
                }
            }
            Switch(checked = a.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
