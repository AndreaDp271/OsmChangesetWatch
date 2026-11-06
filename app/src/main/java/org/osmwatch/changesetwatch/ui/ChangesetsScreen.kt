package org.osmwatch.changesetwatch.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.data.ChangesetRow
import org.osmwatch.changesetwatch.data.WatchArea
import org.osmwatch.changesetwatch.domain.Flag
import org.osmwatch.changesetwatch.domain.Flags

fun flagLabel(f: Flag): Int = when (f) {
    Flag.MANY_CHANGES -> R.string.flag_many_changes
    Flag.NO_COMMENT -> R.string.flag_no_comment
    Flag.SUSPECT_USER -> R.string.flag_suspect_user
    Flag.MASS_DELETE -> R.string.flag_mass_delete
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesetsScreen(
    query: String,
    onQuery: (String) -> Unit,
    areasOnly: Boolean,
    onAreasOnly: (Boolean) -> Unit,
    onSearch: () -> Unit,
    rows: List<ChangesetRow>,
    areas: List<WatchArea>,
    areaFilter: Long?,
    includeSeen: Boolean,
    showAll: Boolean,
    onlyFlagged: Boolean,
    onOnlyFlagged: (Boolean) -> Unit,
    hideTrusted: Boolean,
    onHideTrusted: (Boolean) -> Unit,
    suspects: Set<String>,
    minChanges: Int,
    onMinChanges: () -> Unit,
    maxAgeHours: Int,
    onMaxAge: () -> Unit,
    progress: String?,
    refreshing: Boolean,
    error: String?,
    onAreaFilter: (Long?) -> Unit,
    onIncludeSeen: (Boolean) -> Unit,
    onShowAll: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (ChangesetRow) -> Unit,
    onAreas: () -> Unit,
    onSettings: () -> Unit,
) {
    val names = areas.associate { it.id to it.name }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                    IconButton(onClick = onAreas) { Icon(Icons.Default.Place, stringResource(R.string.areas)) }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                },
            )
        },
    ) { pad ->
        androidx.compose.foundation.layout.Column(Modifier.padding(pad)) {
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                item {
                    FilterChip(
                        selected = areasOnly, onClick = { onAreasOnly(!areasOnly) },
                        label = { Text(stringResource(R.string.areas_only)) }, modifier = Modifier.padding(end = 8.dp),
                    )
                }
                item {
                    FilterChip(
                        selected = !includeSeen, onClick = { onIncludeSeen(!includeSeen) },
                        label = { Text(stringResource(R.string.unseen_only)) }, modifier = Modifier.padding(end = 8.dp),
                    )
                }
                item {
                    FilterChip(
                        selected = showAll, onClick = { onShowAll(!showAll) },
                        label = { Text(stringResource(R.string.show_unverified)) }, modifier = Modifier.padding(end = 8.dp),
                    )
                }
                item {
                    FilterChip(
                        selected = onlyFlagged, onClick = { onOnlyFlagged(!onlyFlagged) },
                        label = { Text(stringResource(R.string.only_flagged)) }, modifier = Modifier.padding(end = 8.dp),
                    )
                }
                item {
                    FilterChip(
                        selected = hideTrusted, onClick = { onHideTrusted(!hideTrusted) },
                        label = { Text(stringResource(R.string.hide_trusted)) }, modifier = Modifier.padding(end = 8.dp),
                    )
                }
                item {
                    FilterChip(
                        selected = minChanges > 0, onClick = onMinChanges,
                        label = { Text(if (minChanges == 0) stringResource(R.string.min_changes_any) else stringResource(R.string.min_changes_n, minChanges)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                item {
                    FilterChip(
                        selected = maxAgeHours > 0, onClick = onMaxAge,
                        label = { Text(stringResource(when (maxAgeHours) { 24 -> R.string.age_24h; 168 -> R.string.age_7d; else -> R.string.age_any })) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                item {
                    FilterChip(
                        selected = areaFilter == null, onClick = { onAreaFilter(null) },
                        label = { Text(stringResource(R.string.all_areas)) }, modifier = Modifier.padding(end = 8.dp),
                    )
                }
                items(areas, key = { it.id }) { a ->
                    FilterChip(
                        selected = areaFilter == a.id, onClick = { onAreaFilter(a.id) },
                        label = { Text(a.name) }, modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            OutlinedTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                placeholder = { Text(stringResource(if (areasOnly) R.string.search_hint else R.string.search_hint_global)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, stringResource(R.string.clear)) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            if (progress != null) Text(stringResource(R.string.verifying, progress), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 4.dp))
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp, 4.dp))
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
                LazyColumn {
                    if (rows.isEmpty()) item {
                        Text(
                            stringResource(if (!areasOnly) R.string.search_global_empty else if (areas.isEmpty()) R.string.no_areas_home else R.string.no_changesets),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    items(rows, key = { "${it.id}-${it.areaId}" }) { r ->
                        ListItem(
                            modifier = Modifier.clickable { onOpen(r) },
                            leadingContent = {
                                if (!r.seen) Row(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) {}
                                else Row(Modifier.size(10.dp)) {}
                            },
                            overlineContent = { Text(listOfNotNull(r.user ?: "?", names[r.areaId]).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            headlineContent = {
                                Text(r.comment ?: stringResource(R.string.no_comment), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                val ago = DateUtils.getRelativeTimeSpanString(r.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                                val flags = Flags.header(r.changesCount, r.comment, r.user, suspects).map { stringResource(flagLabel(it)) }.joinToString(", ")
                                Text("${r.createdBy ?: "?"} · $ago · ${androidx.compose.ui.res.pluralStringResource(R.plurals.changes_n, r.changesCount ?: 0, r.changesCount ?: 0)}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (flags.isNotEmpty()) Text("⚑ $flags", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                            },
                            trailingContent = { if (r.areaId != EXTERNAL) Text(r.verdict.name, style = MaterialTheme.typography.labelSmall) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
