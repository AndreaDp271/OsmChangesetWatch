package org.osmwatch.changesetwatch.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.data.ChangesetRow
import org.osmwatch.changesetwatch.data.WatchArea
import org.osmwatch.changesetwatch.data.glyphRings
import org.osmwatch.changesetwatch.domain.ChangeKind
import org.osmwatch.changesetwatch.domain.Flag
import org.osmwatch.changesetwatch.domain.Flags
import org.osmwatch.changesetwatch.domain.RowTags
import org.osmwatch.changesetwatch.domain.Verdict

fun kindLabel(k: ChangeKind): Int = when (k) {
    ChangeKind.ANY -> R.string.kind_any
    ChangeKind.HAS_DELETES -> R.string.kind_has_deletes
    ChangeKind.ONLY_DELETES -> R.string.kind_only_deletes
    ChangeKind.ONLY_CREATES -> R.string.kind_only_creates
}

fun flagLabel(f: Flag): Int = when (f) {
    Flag.MANY_CHANGES -> R.string.flag_many_changes
    Flag.NO_COMMENT -> R.string.flag_no_comment
    Flag.SUSPECT_USER -> R.string.flag_suspect_user
    Flag.MASS_DELETE -> R.string.flag_mass_delete
    Flag.NEW_MAPPER -> R.string.flag_new_mapper
    Flag.PREVIOUSLY_BLOCKED -> R.string.flag_blocked
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesetsScreen(vm: ChangesetsViewModel, onOpen: (ChangesetRow) -> Unit, onAreas: () -> Unit, onSettings: () -> Unit) {
    val areasOnly by vm.areasOnly.collectAsState()
    val local by vm.changesets.collectAsState()
    val global by vm.globalResults.collectAsState()
    val rows = if (areasOnly) local else global
    val areas by vm.areas.collectAsState()
    val areaFilter by vm.areaFilter.collectAsState()
    val query by vm.query.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val searching by vm.searching.collectAsState()
    val error by vm.error.collectAsState()
    val progress by vm.progress.collectAsState()
    val suspects by vm.suspectUsers.collectAsState()
    var sheet by rememberSaveable { mutableStateOf(false) }
    val byId = areas.associateBy { it.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AreaPicker(areas, areaFilter, rows.size, areasOnly) { vm.areaFilter.value = it } },
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                    IconButton(onClick = onAreas) { Icon(Icons.Default.Place, stringResource(R.string.areas)) }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { vm.query.value = it }, singleLine = true,
                placeholder = { Text(stringResource(if (areasOnly) R.string.search_hint else R.string.search_hint_global)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (!areasOnly) vm.searchGlobal() }),
                leadingIcon = { Icon(if (areasOnly) Icons.Default.Search else Icons.Default.Public, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Default.Close, stringResource(R.string.clear)) } },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ActiveFilters(vm) { sheet = true }
            if (refreshing || searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            progress?.let { Muted(stringResource(R.string.verifying, it), Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) }
            error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) { Text(it, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp)) }
            }
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (rows.isEmpty()) item { EmptyState(areasOnly, areas.isEmpty(), onAreas) }
                    items(rows, key = { "${it.id}-${it.areaId}" }) { r ->
                        ChangesetCard(r, byId[r.areaId], Flags.header(r.changesCount, r.comment, r.user, suspects) + Flags.fromCounts(r.createdCount, r.modifiedCount, r.deletedCount)) { onOpen(r) }
                    }
                }
            }
        }
    }
    if (sheet) FilterSheet(vm) { sheet = false }
}

/** Titolo della barra: area scelta (o tutte) e numero di changeset; tocco = menu delle aree. */
@Composable
private fun AreaPicker(areas: List<WatchArea>, selected: Long?, count: Int, areasOnly: Boolean, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val title = when {
        !areasOnly -> stringResource(R.string.search_everywhere)
        selected == null -> stringResource(R.string.all_areas)
        else -> areas.firstOrNull { it.id == selected }?.name ?: stringResource(R.string.all_areas)
    }
    val pickable = areasOnly && areas.isNotEmpty()
    Box {
        Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = pickable) { open = true }.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (pickable) Icon(Icons.Default.ArrowDropDown, null)
            }
            Text(
                pluralStringResource(R.plurals.list_count, count, count),
                style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.all_areas)) }, onClick = { onSelect(null); open = false })
            areas.forEach { a ->
                DropdownMenuItem(
                    text = { Text(a.name) },
                    leadingIcon = { Icon(if (a.watchUser != null) Icons.Default.Person else Icons.Default.Place, null) },
                    onClick = { onSelect(a.id); open = false },
                )
            }
        }
    }
}

/** Pulsante "Filtri" con il numero di filtri attivi, seguito da un chip rimovibile per ognuno. */
@Composable
private fun ActiveFilters(vm: ChangesetsViewModel, onOpen: () -> Unit) {
    val includeSeen by vm.includeSeen.collectAsState()
    val showAll by vm.showAll.collectAsState()
    val onlyFlagged by vm.onlyFlagged.collectAsState()
    val hideTrusted by vm.hideTrusted.collectAsState()
    val minChanges by vm.minChanges.collectAsState()
    val maxAge by vm.maxAgeHours.collectAsState()
    val editor by vm.editorFilter.collectAsState()
    val hashtag by vm.hashtagFilter.collectAsState()
    val kind by vm.kindFilter.collectAsState()
    val active = buildList<Pair<String, () -> Unit>> {
        if (!includeSeen) add(stringResource(R.string.unseen_only) to { vm.includeSeen.value = true })
        if (showAll) add(stringResource(R.string.show_unverified) to { vm.showAll.value = false })
        if (onlyFlagged) add(stringResource(R.string.only_flagged) to { vm.onlyFlagged.value = false })
        if (hideTrusted) add(stringResource(R.string.hide_trusted) to { vm.hideTrusted.value = false })
        if (minChanges > 0) add(stringResource(R.string.min_changes_n, minChanges) to { vm.minChanges.value = 0 })
        if (maxAge > 0) add(stringResource(if (maxAge == 24) R.string.age_24h else R.string.age_7d) to { vm.maxAgeHours.value = 0 })
        editor?.let { add(it to { vm.editorFilter.value = null }) }
        hashtag?.let { add(it to { vm.hashtagFilter.value = null }) }
        if (kind != ChangeKind.ANY) add(stringResource(kindLabel(kind)) to { vm.kindFilter.value = ChangeKind.ANY })
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            FilterChip(
                selected = active.isNotEmpty(), onClick = onOpen,
                leadingIcon = {
                    BadgedBox(badge = { if (active.isNotEmpty()) Badge { Text("${active.size}") } }) { Icon(Icons.Default.FilterList, null, Modifier.size(18.dp)) }
                },
                label = { Text(stringResource(R.string.filters)) },
            )
        }
        items(active.size) { i ->
            val (label, clear) = active[i]
            InputChip(
                selected = true, onClick = clear, label = { Text(label, maxLines = 1) },
                trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.clear), Modifier.size(InputChipDefaults.AvatarSize)) },
            )
        }
    }
}

@Composable
private fun ChangesetCard(r: ChangesetRow, area: WatchArea?, flags: List<Flag>, onClick: () -> Unit) {
    val external = r.areaId == EXTERNAL
    val userWatch = area?.watchUser != null
    Surface(
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp)) {
            Footprint(
                area = area?.takeIf { !userWatch }?.bbox, change = r.bbox,
                areaRings = area?.takeIf { !userWatch }?.glyphRings ?: emptyList(),
                color = footprintColor(if (external) Verdict.CONFIRMED else r.verdict, flags.isNotEmpty()),
                unseen = !r.seen && !external,
                fallbackIcon = if (userWatch) Icons.Default.Person else Icons.Default.Public,
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    r.comment ?: stringResource(R.string.no_comment),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (r.seen || external) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (r.comment == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.user ?: "?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (area != null && !userWatch) {
                        Text(
                            stringResource(R.string.in_area, area.name), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false),
                        )
                    }
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Meta(Icons.Default.Schedule, DateUtils.getRelativeTimeSpanString(r.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString())
                    Meta(Icons.Default.EditNote, pluralStringResource(R.plurals.changes_n, r.changesCount ?: 0, r.changesCount ?: 0))
                    r.createdBy?.let { Meta(Icons.Default.Build, RowTags.editor(it) ?: it) }
                }
                CountsBar(r.createdCount, r.modifiedCount, r.deletedCount, Modifier.padding(top = 8.dp))
                val showVerdict = !external && r.verdict != Verdict.CONFIRMED
                if (flags.isNotEmpty() || showVerdict) {
                    Pills(Modifier.padding(top = 8.dp)) {
                        if (showVerdict) Pill(verdictText(r.verdict), MaterialTheme.colorScheme.onSurfaceVariant)
                        flags.forEach { Pill(stringResource(flagLabel(it)), MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

/** Riga di etichette che va a capo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Pills(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun EmptyState(areasOnly: Boolean, noAreas: Boolean, onAreas: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (areasOnly) Icons.Default.Place else Icons.Default.Public, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Text(
            stringResource(if (!areasOnly) R.string.search_global_empty else if (noAreas) R.string.no_areas_home else R.string.no_changesets),
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
        )
        if (areasOnly && noAreas) TextButton(onClick = onAreas, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.new_area)) }
    }
}

/** Tutti i filtri della lista, raggruppati come le Impostazioni. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(vm: ChangesetsViewModel, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editors by vm.editors.collectAsState()
    val hashtags by vm.hashtags.collectAsState()
    val editor by vm.editorFilter.collectAsState()
    val hashtag by vm.hashtagFilter.collectAsState()
    val areasOnly by vm.areasOnly.collectAsState()
    val includeSeen by vm.includeSeen.collectAsState()
    val showAll by vm.showAll.collectAsState()
    val onlyFlagged by vm.onlyFlagged.collectAsState()
    val hideTrusted by vm.hideTrusted.collectAsState()
    val minChanges by vm.minChanges.collectAsState()
    val maxAge by vm.maxAgeHours.collectAsState()
    val ageLabels = mapOf(0 to stringResource(R.string.age_any), 24 to stringResource(R.string.age_24h), 168 to stringResource(R.string.age_7d))
    val anySize = stringResource(R.string.size_any)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.filters), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                TextButton(onClick = vm::resetFilters) { Text(stringResource(R.string.reset_filters)) }
            }
            Group(stringResource(R.string.group_show)) {
                SwitchItem(Icons.Default.Public, stringResource(R.string.search_everywhere), stringResource(R.string.search_everywhere_desc), !areasOnly) { vm.areasOnly.value = !it }
                Divider()
                SwitchItem(Icons.Default.VisibilityOff, stringResource(R.string.unseen_only), null, !includeSeen) { vm.includeSeen.value = !it }
                Divider()
                SwitchItem(Icons.Default.Verified, stringResource(R.string.show_unverified), stringResource(R.string.show_unverified_desc), showAll) { vm.showAll.value = it }
                Divider()
                SwitchItem(Icons.Default.Flag, stringResource(R.string.only_flagged), null, onlyFlagged) { vm.onlyFlagged.value = it }
                Divider()
                SwitchItem(Icons.Default.Person, stringResource(R.string.hide_trusted), null, hideTrusted) { vm.hideTrusted.value = it }
            }
            Group(stringResource(R.string.group_size_time)) {
                Item(Icons.Default.EditNote, MaterialTheme.colorScheme.primary, stringResource(R.string.min_changes_title))
                Choice(MIN_CHANGES_STEPS, minChanges, { if (it == 0) anySize else "≥ $it" }) { vm.minChanges.value = it }
                Item(Icons.Default.Schedule, MaterialTheme.colorScheme.primary, stringResource(R.string.period_title))
                Choice(MAX_AGE_STEPS, maxAge, { ageLabels.getValue(it) }) { vm.maxAgeHours.value = it }
            }
            Group(stringResource(R.string.kind_title)) {
                val kind by vm.kindFilter.collectAsState()
                FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChangeKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { vm.kindFilter.value = k }, label = { Text(stringResource(kindLabel(k))) }) }
                }
                Muted(stringResource(R.string.kind_note), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
            }
            if (editors.isNotEmpty()) Group(stringResource(R.string.filter_editor)) {
                FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    editors.forEach { e -> FilterChip(selected = editor == e, onClick = { vm.editorFilter.value = if (editor == e) null else e }, label = { Text(e) }) }
                }
            }
            if (hashtags.isNotEmpty()) Group(stringResource(R.string.filter_hashtag)) {
                FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    hashtags.take(40).forEach { h -> FilterChip(selected = hashtag == h, onClick = { vm.hashtagFilter.value = if (hashtag == h) null else h }, label = { Text(h) }) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwitchItem(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Item(icon, MaterialTheme.colorScheme.primary, title, subtitle, trailing = { Switch(checked, onChange) }, onClick = { onChange(!checked) })
}
