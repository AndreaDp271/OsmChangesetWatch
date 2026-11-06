package org.osmwatch.changesetwatch

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration as AndroidConfiguration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.util.Locale
import org.osmdroid.config.Configuration
import org.osmwatch.changesetwatch.data.AppSettings
import org.osmwatch.changesetwatch.data.Prefs
import org.osmwatch.changesetwatch.domain.Links
import org.osmwatch.changesetwatch.network.OsmAuth
import org.osmwatch.changesetwatch.network.OsmConfig
import org.osmwatch.changesetwatch.network.Updater
import org.osmwatch.changesetwatch.ui.AreaEditorScreen
import org.osmwatch.changesetwatch.ui.AreasScreen
import org.osmwatch.changesetwatch.ui.AreasViewModel
import org.osmwatch.changesetwatch.ui.ChangesetDetailScreen
import org.osmwatch.changesetwatch.ui.ChangesetsScreen
import org.osmwatch.changesetwatch.ui.ChangesetsViewModel
import org.osmwatch.changesetwatch.ui.OsmWatchTheme
import org.osmwatch.changesetwatch.ui.SettingsScreen
import org.osmwatch.changesetwatch.ui.UsersScreen
import org.osmwatch.changesetwatch.work.PollWorker

class MainActivity : ComponentActivity() {
    private val cvm: ChangesetsViewModel by viewModels()
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Lingua scelta nelle impostazioni ("auto" = sistema). */
    override fun attachBaseContext(base: Context) {
        val lang =
            base.getSharedPreferences("settings", MODE_PRIVATE).getString("lang", "auto") ?: "auto"
        if (lang == "auto") return super.attachBaseContext(base)
        val cfg =
            AndroidConfiguration(base.resources.configuration).apply { setLocale(Locale(lang)) }
        super.attachBaseContext(base.createConfigurationContext(cfg))
    }

    override fun onStart() {
        super.onStart()
        // nuove versioni su GitHub: al massimo una volta al giorno, senza disturbare se non c'è
        // nulla
        val p = Prefs(this)
        if (
            BuildConfig.UPDATER &&
                p.autoUpdateCheck &&
                System.currentTimeMillis() - p.lastUpdateCheck > 24 * 3600_000L
        ) {
            Updater.check(this, quiet = true) { p.lastUpdateCheck = System.currentTimeMillis() }
        }
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
        // osmdroid: UA obbligatorio per la tile policy OSM; cache nella cache privata (nessun
        // permesso storage)
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
            if (
                Build.VERSION.SDK_INT >= 33 &&
                    !p.askedNotificationPermission &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
            ) {
                p.askedNotificationPermission = true
                askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        handle(intent, fresh = savedInstanceState == null)
        setContent { OsmWatchTheme { AppRoot(cvm) } }
    }
}

private const val NEW_AREA = 0L

@Composable
private fun AppRoot(cvm: ChangesetsViewModel, vm: AreasViewModel = viewModel()) {
    val areas by vm.areas.collectAsState()
    var showAreas by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showUsers by rememberSaveable { mutableStateOf(false) }
    // null = lista, 0 = nuova, >0 = id area
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var detail by rememberSaveable { mutableStateOf<List<Long>?>(null) } // [id changeset, id area]
    val id = editing
    val d = detail
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val login = {
        try {
            ctx.startActivity(cvm.loginIntent())
        } catch (_: ActivityNotFoundException) {
            // nessun browser installato: il login non può partire
        }
    }
    val req by cvm.openRequest.collectAsState()
    LaunchedEffect(req) {
        req?.let {
            showSettings = false
            showUsers = false
            showAreas = false
            editing = null
            detail = it
            cvm.openRequest.value = null
        }
    }
    if (showSettings) {
        SettingsScreen(
            loggedIn = cvm.loggedIn.collectAsState().value,
            onLogin = login,
            onLogout = cvm::logout,
            onBack = { showSettings = false },
        )
    } else if (showUsers) {
        UsersScreen(
            watched = areas.orEmpty().filter { it.watchUser != null },
            suspects = cvm.suspectUsers.collectAsState().value,
            trusted = cvm.trustedUsers.collectAsState().value,
            onOpen = {
                cvm.showUserChangesets(it)
                showUsers = false
            },
            onUnwatch = { vm.delete(it.id) },
            onUnsuspect = cvm::toggleSuspect,
            onUntrust = cvm::toggleTrusted,
            onBack = { showUsers = false },
        )
    } else if (id == null && !showAreas && d == null) {
        ChangesetsScreen(
            cvm,
            onOpen = { detail = listOf(it.id, it.areaId) },
            onAreas = { showAreas = true },
            onUsers = { showUsers = true },
            onSettings = { showSettings = true },
            listState = listState,
        )
    } else if (d != null) {
        ChangesetDetailScreen(
            cvm,
            id = d[0],
            areaId = d[1],
            area = areas?.firstOrNull { it.id == d[1] },
            onBack = { detail = null },
            onLogin = login,
        )
    } else if (id == null) {
        AreasScreen(
            areas = areas,
            unseen = vm.unseen.collectAsState().value,
            onAdd = { editing = NEW_AREA },
            onWatchUser = vm::watchUser,
            onEdit = { editing = it.id },
            onToggle = { a, on -> vm.setEnabled(a.id, on) },
            onDelete = { vm.delete(it.id) },
            onBack = { showAreas = false },
        )
    } else {
        val area = areas?.firstOrNull { it.id == id }
        if (id == NEW_AREA || area != null) {
            AreaEditorScreen(
                area = area,
                onSave = {
                    vm.save(it)
                    editing = null
                },
                onBack = { editing = null },
            )
        }
    }
}
