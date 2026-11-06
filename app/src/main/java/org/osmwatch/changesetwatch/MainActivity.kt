package org.osmwatch.changesetwatch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration as AndroidConfiguration
import java.util.Locale
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import org.osmdroid.config.Configuration
import org.osmwatch.changesetwatch.domain.Links
import org.osmwatch.changesetwatch.network.OsmAuth
import org.osmwatch.changesetwatch.network.OsmConfig
import org.osmwatch.changesetwatch.ui.EXTERNAL
import org.osmwatch.changesetwatch.ui.AreaEditorScreen
import org.osmwatch.changesetwatch.ui.AreasScreen
import org.osmwatch.changesetwatch.data.AppSettings
import org.osmwatch.changesetwatch.data.Prefs
import org.osmwatch.changesetwatch.ui.OsmWatchTheme
import org.osmwatch.changesetwatch.ui.AreasViewModel
import org.osmwatch.changesetwatch.ui.SettingsScreen
import org.osmwatch.changesetwatch.work.PollWorker
import org.osmwatch.changesetwatch.ui.ChangesetDetailScreen
import org.osmwatch.changesetwatch.ui.ChangesetsScreen
import org.osmwatch.changesetwatch.ui.ChangesetsViewModel

class MainActivity : ComponentActivity() {
    private val cvm: ChangesetsViewModel by viewModels()
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Lingua scelta nelle impostazioni ("auto" = sistema). */
    override fun attachBaseContext(base: Context) {
        val lang = base.getSharedPreferences("settings", MODE_PRIVATE).getString("lang", "auto") ?: "auto"
        if (lang == "auto") return super.attachBaseContext(base)
        val cfg = AndroidConfiguration(base.resources.configuration).apply { setLocale(Locale(lang)) }
        super.attachBaseContext(base.createConfigurationContext(cfg))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Tocco sulla notifica: lista filtrata sull'area. */
    private fun handle(intent: Intent?, fresh: Boolean = true) {
        val id = intent?.getLongExtra(PollWorker.EXTRA_AREA_ID, -1L) ?: -1L
        if (id > 0) cvm.areaFilter.value = id
        // link esterno: non rilanciarlo dopo una rotazione (fresh = false)
        if (fresh) {
            intent?.data?.takeIf { it.scheme == OsmAuth.SCHEME }?.let(cvm::handleOAuth)
            intent?.dataString?.let(Links::changesetId)?.let(cvm::openLink)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        // osmdroid: UA obbligatorio per la tile policy OSM; cache nella cache privata (nessun permesso storage)
        Configuration.getInstance().apply {
            userAgentValue = OsmConfig.USER_AGENT
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
            tileFileSystemCacheMaxBytes = 50L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 40L * 1024 * 1024
        }
        PollWorker.ensureChannel(this)
        PollWorker.schedule(this, Prefs(this).intervalMinutes)
        Prefs(this).let { p ->
            if (Build.VERSION.SDK_INT >= 33 && !p.askedNotificationPermission &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) { p.askedNotificationPermission = true; askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        handle(intent, fresh = savedInstanceState == null)
        setContent {
            OsmWatchTheme { AppRoot(cvm) }
        }
    }
}

private const val NEW_AREA = 0L

@Composable
private fun AppRoot(cvm: ChangesetsViewModel, vm: AreasViewModel = viewModel()) {
    val areas by vm.areas.collectAsState()
    var showAreas by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<Long?>(null) } // null = lista, 0 = nuova, >0 = id area
    var detail by rememberSaveable { mutableStateOf<List<Long>?>(null) } // [id changeset, id area]
    val id = editing
    val d = detail
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val req by cvm.openRequest.collectAsState()
    LaunchedEffect(req) {
        req?.let { showSettings = false; showAreas = false; editing = null; detail = it; cvm.openRequest.value = null }
    }
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    } else if (id == null && !showAreas && d == null) {
        val rows by cvm.changesets.collectAsState()
        val allAreas by cvm.areas.collectAsState()
        val filter by cvm.areaFilter.collectAsState()
        val seen by cvm.includeSeen.collectAsState()
        val refreshing by cvm.refreshing.collectAsState()
        val error by cvm.error.collectAsState()
        val showAll by cvm.showAll.collectAsState()
        val progress by cvm.progress.collectAsState()
        val query by cvm.query.collectAsState()
        val areasOnly by cvm.areasOnly.collectAsState()
        val global by cvm.globalResults.collectAsState()
        val searching by cvm.searching.collectAsState()
        ChangesetsScreen(
            query = query, onQuery = { cvm.query.value = it },
            areasOnly = areasOnly, onAreasOnly = { cvm.areasOnly.value = it }, onSearch = { if (!areasOnly) cvm.searchGlobal() },
            rows = if (areasOnly) rows else global, areas = allAreas, areaFilter = filter, includeSeen = seen, showAll = showAll, progress = progress,
            onlyFlagged = cvm.onlyFlagged.collectAsState().value, onOnlyFlagged = { cvm.onlyFlagged.value = it },
            hideTrusted = cvm.hideTrusted.collectAsState().value, onHideTrusted = { cvm.hideTrusted.value = it },
            suspects = cvm.suspectUsers.collectAsState().value,
            minChanges = cvm.minChanges.collectAsState().value, onMinChanges = cvm::cycleMinChanges,
            maxAgeHours = cvm.maxAgeHours.collectAsState().value, onMaxAge = cvm::cycleMaxAge,
            refreshing = refreshing || searching, error = error,
            onAreaFilter = { cvm.areaFilter.value = it }, onIncludeSeen = { cvm.includeSeen.value = it }, onShowAll = { cvm.showAll.value = it },
            onRefresh = cvm::refresh, onOpen = { detail = listOf(it.id, it.areaId) }, onAreas = { showAreas = true }, onSettings = { showSettings = true },
        )
    } else if (d != null) {
        val row by remember(d) { cvm.observeOne(d[0], d[1]) }.collectAsState(null)
        ChangesetDetailScreen(
            row = row, areaName = if (d[1] == EXTERNAL) null else cvm.areas.value.firstOrNull { it.id == d[1] }?.name,
            geo = cvm.geoState.collectAsState().value, diff = cvm.diffState.collectAsState().value, onLoadGeometry = cvm::loadGeometry,
            onElementTap = cvm::loadDiff, onDismissDiff = cvm::clearDiff,
            onMarkSeen = { cvm.markSeen(d[0], d[1]) }, onBack = { detail = null },
            suspects = cvm.suspectUsers.collectAsState().value, trusted = cvm.trustedUsers.collectAsState().value,
            onToggleSuspect = cvm::toggleSuspect, onToggleTrusted = cvm::toggleTrusted,
            loggedIn = cvm.loggedIn.collectAsState().value, commentState = cvm.commentState.collectAsState().value,
            onLogin = { try { ctx.startActivity(cvm.loginIntent()) } catch (_: android.content.ActivityNotFoundException) {} },
            onLogout = cvm::logout, onComment = { cvm.sendComment(d[0], it) }, onCommentReset = cvm::resetComment,
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
