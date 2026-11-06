package org.osmwatch.changesetwatch.ui

import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.data.ChangesetRow
import org.osmwatch.changesetwatch.data.WatchArea
import org.osmwatch.changesetwatch.domain.ChangeAction
import org.osmwatch.changesetwatch.domain.ChangedElement
import org.osmwatch.changesetwatch.domain.ChangedElements
import org.osmwatch.changesetwatch.domain.ChangesetComment
import org.osmwatch.changesetwatch.domain.ElementDiff
import org.osmwatch.changesetwatch.domain.ElementRef
import org.osmwatch.changesetwatch.domain.ElementType
import org.osmwatch.changesetwatch.domain.Flags
import org.osmwatch.changesetwatch.domain.Links
import org.osmwatch.changesetwatch.domain.RowTags
import org.osmwatch.changesetwatch.domain.UserActivity
import org.osmwatch.changesetwatch.domain.UserInfo
import org.osmwatch.changesetwatch.domain.Verdict

private fun fmt(t: Long) = DateFormat.format("yyyy-MM-dd HH:mm", t).toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesetDetailScreen(vm: ChangesetsViewModel, id: Long, areaId: Long, area: WatchArea?, onBack: () -> Unit, onLogin: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val row by remember(id, areaId) { vm.observeOne(id, areaId) }.collectAsState(null)
    val geo by vm.geoState.collectAsState()
    val diff by vm.diffState.collectAsState()
    val discussion by vm.discussion.collectAsState()
    val userInfo by vm.userInfo.collectAsState()
    val suspects by vm.suspectUsers.collectAsState()
    val trusted by vm.trustedUsers.collectAsState()
    val loggedIn by vm.loggedIn.collectAsState()
    val commentState by vm.commentState.collectAsState()
    var showComment by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    val activity by vm.activity.collectAsState()
    val activityLoading by vm.activityLoading.collectAsState()
    LaunchedEffect(profileOpen, row?.uid) { if (profileOpen) row?.uid?.let(vm::loadActivity) }

    LaunchedEffect(row?.id) { row?.let { vm.loadGeometry(it); vm.loadDiscussion(it.id); vm.loadUser(it.uid) } }
    LaunchedEffect(commentState) {
        if (commentState is CommentState.Done) {
            showComment = false; vm.resetComment()
            Toast.makeText(ctx, R.string.comment_sent, Toast.LENGTH_SHORT).show()
        }
    }
    val history by vm.historyState.collectAsState()
    diff?.let { DiffDialog(it, vm::clearDiff, onHistory = vm::loadHistory) }
    history?.let { h ->
        HistoryDialog(
            h, onClose = vm::clearHistory,
            onOpenChangeset = { cs -> vm.clearHistory(); vm.clearDiff(); if (cs != id) vm.openLink(cs) },
            onRetry = { vm.loadHistory(h.ref) },
        )
    }
    if (showComment && loggedIn) CommentDialog(commentState, { vm.sendComment(id, it) }, vm::logout) { showComment = false; vm.resetComment() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("#$id", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge.tabular())
                        row?.let {
                            Text(
                                relativeTime(it.createdAt),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    row?.takeIf { it.areaId != EXTERNAL }?.let { r ->
                        IconButton(onClick = { vm.markSeen(id, areaId) }, enabled = !r.seen) {
                            Icon(Icons.Default.DoneAll, stringResource(if (r.seen) R.string.seen else R.string.mark_seen))
                        }
                    }
                    ExternalMenu(id)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        val r = row ?: return@Scaffold
        val change = (geo as? GeoState.Ready)?.change
        val elements = remember(change) { change?.let { ChangedElements.list(it) } }
        val flags = (Flags.header(r.changesCount, r.comment, r.user, suspects) + Flags.fromUser(userInfo) +
            Flags.fromCounts(r.createdCount, r.modifiedCount, r.deletedCount) + (change?.let { Flags.fromChange(it) } ?: emptyList())).distinct()
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            HeroMap(r, geo, vm::loadDiff)
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    r.comment ?: stringResource(R.string.no_comment), style = MaterialTheme.typography.titleLarge,
                    color = if (r.comment == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (flags.isNotEmpty() || r.areaId != EXTERNAL) {
                    Pills(Modifier.padding(top = 10.dp)) {
                        if (r.areaId != EXTERNAL) Pill(
                            verdictText(r.verdict),
                            if (r.verdict == Verdict.CONFIRMED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        flags.forEach { Pill(stringResource(flagLabel(it)), MaterialTheme.colorScheme.error) }
                    }
                }
                r.user?.let { u ->
                    AuthorCard(
                        u, r.uid, userInfo, u in suspects, u in trusted,
                        onWatch = { vm.watchUser(u); Toast.makeText(ctx, ctx.getString(R.string.watch_user_added, u), Toast.LENGTH_SHORT).show() },
                        onSuspect = { vm.toggleSuspect(u) }, onTrusted = { vm.toggleTrusted(u) },
                        onHdyc = { openUrl(ctx, "https://hdyc.neis-one.org/?" + android.net.Uri.encode(u)) },
                        profileOpen = profileOpen, onToggleProfile = { profileOpen = !profileOpen },
                        activity = activity, activityLoading = activityLoading,
                    )
                }
            }
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, modifier = Modifier.padding(top = 16.dp)) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_overview)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tabLabel(R.string.tab_elements, elements?.size)) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(tabLabel(R.string.tab_discussion, discussion?.size)) })
            }
            Column(Modifier.padding(16.dp)) {
                when (tab) {
                    0 -> Overview(r, area)
                    1 -> if (elements != null) ElementsList(elements, vm::loadDiff) else Muted(stringResource(geoNote(geo) ?: R.string.geo_loading))
                    else -> Discussion(discussion, r.closedAt != null, loggedIn, onComment = { if (loggedIn) showComment = true else onLogin() })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun tabLabel(res: Int, n: Int?) = stringResource(res) + (n?.let { " $it" } ?: "")

private fun geoNote(geo: GeoState): Int? = when (geo) {
    is GeoState.Loading -> R.string.geo_loading
    is GeoState.TooLarge -> R.string.geo_too_large
    is GeoState.Failed -> R.string.geo_failed
    is GeoState.Ready -> when {
        geo.geometry.isEmpty -> R.string.geo_empty
        geo.geometry.partial -> R.string.geo_partial
        else -> null
    }
}

/** Visualizzatori esterni del changeset, raccolti in un menu invece di tre pulsanti a tutta larghezza. */
@Composable
private fun ExternalMenu(id: Long) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.open_in)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(Links.OSM to R.string.open_osm, Links.OSMCHA to R.string.open_osmcha, Links.ACHAVI to R.string.open_achavi).forEach { (tpl, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { open = false; openUrl(ctx, Links.url(tpl, id)) })
            }
        }
    }
}

/** Mappa a tutta larghezza in testa al dettaglio, con legenda sovrapposta e pulsante schermo intero. */
@Composable
private fun HeroMap(row: ChangesetRow, geo: GeoState, onElementTap: (ElementRef) -> Unit) {
    val g = (geo as? GeoState.Ready)?.geometry
    var full by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        MiniMap(g, row.bbox, onElementTap, Modifier.fillMaxSize())
        FullscreenButton(expanded = false, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) { full = true }
        LegendCard(Modifier.align(Alignment.BottomStart).padding(12.dp))
    }
    geoNote(geo)?.let { Muted(stringResource(it), Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
    if (full) Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize()) {
            MiniMap(g, row.bbox, onElementTap, Modifier.fillMaxSize())
            FullscreenButton(expanded = true, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) { full = false }
            LegendCard(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(12.dp))
        }
    }
}

@Composable
private fun LegendCard(modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend(ChangeStyle.NEW, R.string.legend_new)
            Legend(ChangeStyle.MODIFIED, R.string.legend_modified)
            Legend(ChangeStyle.DELETED, R.string.legend_deleted)
            Legend(ChangeStyle.BEFORE, R.string.legend_before)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuthorCard(
    name: String, uid: Long?, info: UserInfo?, suspect: Boolean, trusted: Boolean,
    onWatch: () -> Unit, onSuspect: () -> Unit, onTrusted: () -> Unit, onHdyc: () -> Unit,
    profileOpen: Boolean, onToggleProfile: () -> Unit, activity: UserActivity?, activityLoading: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    uid?.let { Text("uid $it", style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    info?.roles?.takeIf { it.isNotEmpty() }?.let { r ->
                        Pills(Modifier.padding(top = 4.dp)) { r.forEach { Pill(roleLabel(it), MaterialTheme.colorScheme.primary) } }
                    }
                }
                TextButton(onClick = onHdyc) { Text(stringResource(R.string.open_hdyc)) }
            }
            if (info != null) {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Stat("${info.changesets ?: 0}", stringResource(R.string.stat_changesets), Modifier.weight(1f))
                    Stat(info.createdAt?.let { fmt(it.toEpochMilli()).take(4) } ?: "?", stringResource(R.string.stat_since), Modifier.weight(1f))
                    Stat("${info.blocksReceived ?: 0}", stringResource(R.string.stat_blocks), Modifier.weight(1f))
                }
            }
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onWatch) {
                    Icon(Icons.Default.NotificationsActive, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.watch_user_btn))
                }
                FilterChip(selected = suspect, onClick = onSuspect, label = { Text(stringResource(R.string.chip_suspect)) }, leadingIcon = { Icon(Icons.Default.Flag, null, Modifier.size(18.dp)) })
                FilterChip(selected = trusted, onClick = onTrusted, label = { Text(stringResource(R.string.chip_trusted)) }, leadingIcon = { Icon(Icons.Default.Verified, null, Modifier.size(18.dp)) })
            }
            if (uid != null) {
                TextButton(onClick = onToggleProfile, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.Insights, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.profile_activity))
                    Icon(if (profileOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                }
                if (profileOpen) ActivitySection(activity, activityLoading)
            }
        }
    }
}

@Composable
private fun roleLabel(role: String) = when (role) {
    "moderator" -> stringResource(R.string.role_moderator)
    "administrator" -> stringResource(R.string.role_administrator)
    "importer" -> stringResource(R.string.role_importer)
    else -> role
}

/** Toni del colore primario per gli editor, dal più usato; l'ultimo raccoglie il resto. */
@Composable
private fun editorShades(): List<Color> = listOf(1f, 0.62f, 0.38f, 0.2f).map { MaterialTheme.colorScheme.primary.copy(alpha = it) }

/** Riassunto stile HDYC sugli ultimi changeset: numeri, barra degli editor, hashtag, legame con le tue aree. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivitySection(a: UserActivity?, loading: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        when {
            loading -> { androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)); return@Column }
            a == null -> { Muted(stringResource(R.string.activity_unavailable)); return@Column }
        }
        a!!
        Muted(stringResource(R.string.activity_sample, a.sample, fmt(a.from.toEpochMilli()).take(10), fmt(a.to.toEpochMilli()).take(10)))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Stat("${a.activeDays}", stringResource(R.string.stat_active_days), Modifier.weight(1f))
            Stat("${a.avgChanges}", stringResource(R.string.stat_avg_changes), Modifier.weight(1f))
            Stat("${a.maxChanges}", stringResource(R.string.stat_max_changes), Modifier.weight(1f))
        }
        // barra degli editor: le prime tre voci, poi "altri"
        val shades = editorShades()
        val top = a.editors.take(3).toMutableList()
        val rest = 100 - top.sumOf { it.second }
        if (a.editors.size > 3 && rest > 0) top += stringResource(R.string.editors_other) to rest
        Text(stringResource(R.string.activity_editors), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            top.forEachIndexed { i, (_, pct) -> if (pct > 0) Box(Modifier.weight(pct.toFloat()).fillMaxSize().background(shades[i.coerceAtMost(3)])) }
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            top.forEachIndexed { i, (name, pct) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(shades[i.coerceAtMost(3)]))
                    Text("$name $pct%", style = MaterialTheme.typography.bodySmall.tabular(), modifier = Modifier.padding(start = 5.dp))
                }
            }
        }
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Meta(Icons.Default.Place, stringResource(R.string.activity_in_areas, a.inAreas, a.sample))
            Meta(Icons.Default.EditNote, stringResource(R.string.activity_no_comment, a.noCommentPct))
        }
        if (a.hashtags.isNotEmpty()) Pills(Modifier.padding(top = 10.dp)) { a.hashtags.forEach { Pill(it, MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

/** Dati del changeset come righe icona / etichetta / valore. */
@Composable
private fun Overview(r: ChangesetRow, area: WatchArea?) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            if (area != null) { InfoRow(if (area.watchUser != null) Icons.Default.Person else Icons.Default.Place, R.string.f_area, area.name); Divider() }
            InfoRow(Icons.Default.Build, R.string.f_editor, r.createdBy ?: "?"); Divider()
            r.source?.let { InfoRow(Icons.Default.Source, R.string.f_source, it); Divider() }
            InfoRow(Icons.Default.EditNote, R.string.f_changes, pluralStringResource(R.plurals.changes_n, r.changesCount ?: 0, r.changesCount ?: 0))
            CountsBar(r.createdCount, r.modifiedCount, r.deletedCount, Modifier.padding(start = 52.dp, end = 16.dp, bottom = 12.dp)); Divider()
            InfoRow(Icons.Default.Event, R.string.f_created, fmt(r.createdAt)); Divider()
            InfoRow(Icons.Default.EventAvailable, R.string.f_closed, r.closedAt?.let(::fmt) ?: stringResource(R.string.f_open))
            RowTags.hashtags(r.comment).takeIf { it.isNotEmpty() }?.let { Divider(); InfoRow(Icons.Default.Flag, R.string.filter_hashtag, it.joinToString("  ")) }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: Int, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Discussion(comments: List<ChangesetComment>?, closed: Boolean, loggedIn: Boolean, onComment: () -> Unit) {
    when {
        comments == null -> Muted(stringResource(R.string.geo_loading))
        comments.isEmpty() -> Muted(stringResource(R.string.no_comments))
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            comments.forEach { c ->
                Surface(shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.user ?: "?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            c.date?.let { Text(fmt(it.toEpochMilli()), style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Text(c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
    Button(onClick = onComment, enabled = closed, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(stringResource(if (loggedIn) R.string.comment_btn else R.string.login_osm))
    }
    if (!closed) Muted(stringResource(R.string.comment_open_changeset), Modifier.padding(top = 6.dp))
}

@Composable
private fun CommentDialog(state: CommentState, onSend: (String) -> Unit, onLogout: () -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val sending = state is CommentState.Sending
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.comment_title)) },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, enabled = !sending, minLines = 3, modifier = Modifier.fillMaxWidth())
                if (state is CommentState.Error) Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSend(text) }, enabled = text.isNotBlank() && !sending) { Text(stringResource(R.string.comment_send)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onLogout) { Text(stringResource(R.string.logout_osm)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
    )
}

@Composable
private fun Legend(color: Int, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(color)))
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
private fun DiffDialog(state: DiffState, onDismiss: () -> Unit, onHistory: (ElementRef) -> Unit) {
    val ref = when (state) { is DiffState.Loading -> state.ref; is DiffState.Failed -> state.ref; is DiffState.Ready -> state.diff.ref }
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { onHistory(ref) }) { Text(stringResource(R.string.el_history)) }
                TextButton(onClick = { openUrl(ctx, Links.editInId(ref.type, ref.id)) }) { Text(stringResource(R.string.el_edit_id)) }
            }
        },
        title = { Text("${stringResource(kindLabel(ref.type))} #${ref.id}", style = MaterialTheme.typography.titleLarge.tabular()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (state) {
                    is DiffState.Loading -> Text(stringResource(R.string.geo_loading))
                    is DiffState.Failed -> Text(stringResource(R.string.diff_failed))
                    is DiffState.Ready -> DiffBody(state.diff)
                }
            }
        },
    )
}

private fun kindLabel(t: ElementType) = when (t) { ElementType.NODE -> R.string.el_node; ElementType.WAY -> R.string.el_way; ElementType.RELATION -> R.string.el_relation }

private fun actionColor(a: ChangeAction) = Color(ChangeStyle.color(a))

private fun actionSign(a: ChangeAction) = when (a) { ChangeAction.CREATE -> "+"; ChangeAction.MODIFY -> "~"; ChangeAction.DELETE -> "−" }

/** Tutti gli elementi toccati dal changeset, filtrabili per tipo di azione; il tocco apre il dettaglio (tag prima/dopo). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ElementsList(all: List<ChangedElement>, onTap: (ElementRef) -> Unit) {
    if (all.isEmpty()) { Muted(stringResource(R.string.geo_empty)); return }
    var actions by rememberSaveable { mutableStateOf(ChangeAction.entries.map { it.name }) }
    var limit by rememberSaveable { mutableIntStateOf(50) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ChangeAction.CREATE to R.string.legend_new, ChangeAction.MODIFY to R.string.legend_modified, ChangeAction.DELETE to R.string.legend_deleted).forEach { (a, label) ->
            val n = all.count { it.action == a }
            FilterChip(
                selected = a.name in actions, enabled = n > 0,
                onClick = { actions = if (a.name in actions) actions - a.name else actions + a.name; limit = 50 },
                leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(actionColor(a))) },
                label = { Text("${stringResource(label)} $n", style = MaterialTheme.typography.labelLarge.tabular()) },
            )
        }
    }
    val shown = all.filter { it.action.name in actions }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            shown.take(limit).forEachIndexed { i, e ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().clickable { onTap(e.ref) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(actionSign(e.action), color = actionColor(e.action), fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.label.ifEmpty { stringResource(R.string.el_untagged) }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (e.label.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        Text("${stringResource(kindLabel(e.ref.type))} ${e.ref.id}", style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (shown.size > limit) TextButton(onClick = { limit += 100 }) { Text(stringResource(R.string.show_more, shown.size - limit)) }
}

/** Cronologia a schermo intero: una scheda per versione, dalla più recente, con le differenze rispetto alla precedente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDialog(state: HistoryState, onClose: () -> Unit, onOpenChangeset: (Long) -> Unit, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    val ref = state.ref
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.history_title), fontWeight = FontWeight.SemiBold)
                            Text(
                                "${stringResource(kindLabel(ref.type))} ${ref.id}", style = MaterialTheme.typography.bodySmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                    actions = {
                        TextButton(onClick = { openUrl(ctx, Links.elementHistory(ref.type, ref.id)) }) { Text("osm.org") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                when (state) {
                    is HistoryState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
                    is HistoryState.Failed -> Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.history_failed, state.message), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                    is HistoryState.Ready -> androidx.compose.foundation.lazy.LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { Muted(androidx.compose.ui.res.pluralStringResource(R.plurals.history_count, state.versions.size, state.versions.size)) }
                        items(state.versions.size) { i ->
                            val (v, d) = state.versions[i]
                            VersionCard(v, d, current = ref.version == v.version, onOpenChangeset = onOpenChangeset)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VersionCard(v: org.osmwatch.changesetwatch.domain.ElementVersion, d: ElementDiff, current: Boolean, onOpenChangeset: (Long) -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp,
        color = if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("v${v.version}", style = MaterialTheme.typography.titleMedium.tabular(), fontWeight = FontWeight.SemiBold)
                Text(
                    v.user ?: "?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                v.timestamp?.let { Text(fmt(it.toEpochMilli()), style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (current) Pill(stringResource(R.string.history_this_changeset), MaterialTheme.colorScheme.primary, Modifier.padding(top = 6.dp))
            v.changeset?.let { cs ->
                TextButton(onClick = { onOpenChangeset(cs) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
                    Text(stringResource(R.string.history_changeset, cs), style = MaterialTheme.typography.labelLarge.tabular())
                }
            }
            DiffBody(d)
        }
    }
}

@Composable
private fun DiffBody(d: ElementDiff) {
    val green = Color(ChangeStyle.NEW); val orange = Color(ChangeStyle.MODIFIED); val red = Color(ChangeStyle.DELETED)
    val label = when (d.action) { ChangeAction.CREATE -> R.string.legend_new; ChangeAction.MODIFY -> R.string.legend_modified; ChangeAction.DELETE -> R.string.legend_deleted }
    Pill(stringResource(label), actionColor(d.action))
    if (d.previousMissing) Muted(stringResource(R.string.diff_no_previous), Modifier.padding(top = 8.dp))
    d.movedMeters?.let { Text(stringResource(R.string.diff_moved, it.roundToInt()), Modifier.padding(top = 8.dp)) }
    if (d.nodesAdded > 0 || d.nodesRemoved > 0) Text(stringResource(R.string.diff_nodes, d.nodesAdded, d.nodesRemoved), Modifier.padding(top = 8.dp))
    if (d.membersAdded > 0 || d.membersRemoved > 0) Text(stringResource(R.string.diff_members, d.membersAdded, d.membersRemoved), Modifier.padding(top = 8.dp))
    if (d.tags.isEmpty() && d.movedMeters == null && d.nodesAdded == 0 && d.nodesRemoved == 0 && d.membersAdded == 0 && d.membersRemoved == 0 && !d.previousMissing) {
        Text(stringResource(R.string.diff_none), Modifier.padding(top = 8.dp))
    }
    d.tags.forEach { t ->
        val (sign, c) = when {
            t.old == null -> "+" to green
            t.new == null -> "−" to red
            else -> "~" to orange
        }
        Row(Modifier.padding(top = 8.dp)) {
            Text(sign, color = c, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
            Column {
                Text(t.key, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                when {
                    t.old == null -> Text(t.new ?: "", style = MaterialTheme.typography.bodyMedium)
                    t.new == null -> Text(t.old, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        Text(t.old, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(t.new, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenButton(expanded: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), modifier = modifier) {
        IconButton(onClick = onClick) {
            Icon(
                if (expanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                stringResource(if (expanded) R.string.exit_fullscreen else R.string.fullscreen),
            )
        }
    }
}
