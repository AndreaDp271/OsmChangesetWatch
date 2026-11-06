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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesetsScreen(
    rows: List<ChangesetRow>,
    areas: List<WatchArea>,
    areaFilter: Long?,
    includeSeen: Boolean,
    refreshing: Boolean,
    error: String?,
    onAreaFilter: (Long?) -> Unit,
    onIncludeSeen: (Boolean) -> Unit,
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
                        selected = !includeSeen, onClick = { onIncludeSeen(!includeSeen) },
                        label = { Text(stringResource(R.string.unseen_only)) }, modifier = Modifier.padding(end = 8.dp),
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
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp, 4.dp))
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
                LazyColumn {
                    if (rows.isEmpty()) item {
                        Text(
                            stringResource(if (areas.isEmpty()) R.string.no_areas_home else R.string.no_changesets),
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
                            overlineContent = { Text("${r.user ?: "?"} · ${names[r.areaId] ?: ""}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            headlineContent = {
                                Text(r.comment ?: stringResource(R.string.no_comment), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                val ago = DateUtils.getRelativeTimeSpanString(r.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                                Text("${r.createdBy ?: "?"} · $ago · ${stringResource(R.string.changes_n, r.changesCount ?: 0)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = { Text(r.verdict.name, style = MaterialTheme.typography.labelSmall) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
