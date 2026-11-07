package org.osmwatch.changesetwatch.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.io.File
import org.osmwatch.changesetwatch.BuildConfig
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.data.AppSettings
import org.osmwatch.changesetwatch.data.Prefs
import org.osmwatch.changesetwatch.network.OsmConfig
import org.osmwatch.changesetwatch.network.Updater
import org.osmwatch.changesetwatch.work.PollWorker

private object SetColors { // stessi colori di gruppo di ParkMap
    val look = Color(0xFF7C3AED)
    val general = Color(0xFF0E8A8A)
    val data = Color(0xFF0369A1)
    val about = Color(0xFF475569)
}

private fun dirSize(f: File): Long =
    if (f.isFile) f.length() else f.listFiles()?.sumOf { dirSize(it) } ?: 0L

@Composable
fun SettingsScreen(
    loggedIn: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val theme by AppSettings.theme.collectAsState()
    val palette by AppSettings.palette.collectAsState()
    val lang by AppSettings.lang.collectAsState()
    var interval by remember { mutableIntStateOf(prefs.intervalMinutes) }
    var notif by remember { mutableStateOf(prefs.notificationsEnabled) }
    var maxChanges by remember { mutableIntStateOf(prefs.maxChanges) }
    var cacheDialog by remember { mutableStateOf(false) }
    var cacheTick by remember { mutableIntStateOf(0) }
    val tilesDir = File(ctx.cacheDir, "osmdroid")
    val cacheBytes = remember(cacheTick) { dirSize(tilesDir) }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull() ?: ""
    }

    Scaffold(topBar = { Bar(stringResource(R.string.settings), onBack = onBack) }) { pad ->
        Column(
            Modifier.padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppCard(version)

            Group(stringResource(R.string.group_account)) {
                Item(
                    Icons.Filled.AccountCircle,
                    SetColors.data,
                    stringResource(R.string.group_account),
                    stringResource(
                        if (loggedIn) R.string.account_logged_in else R.string.account_logged_out
                    ),
                    trailing = {
                        TextButton(onClick = if (loggedIn) onLogout else onLogin) {
                            Text(
                                stringResource(
                                    if (loggedIn) R.string.logout_osm else R.string.account_login
                                )
                            )
                        }
                    },
                )
            }

            Group(stringResource(R.string.group_look)) {
                Item(
                    Icons.Filled.DarkMode,
                    SetColors.look,
                    stringResource(R.string.theme),
                    stringResource(R.string.theme_desc),
                )
                Choice(
                    AppSettings.Theme.entries,
                    theme,
                    {
                        ctx.getString(
                            when (it) {
                                AppSettings.Theme.SYSTEM -> R.string.theme_system
                                AppSettings.Theme.LIGHT -> R.string.theme_light
                                AppSettings.Theme.DARK -> R.string.theme_dark
                            }
                        )
                    },
                ) {
                    AppSettings.setTheme(it)
                }
                Divider()
                Item(
                    Icons.Filled.Palette,
                    SetColors.look,
                    stringResource(R.string.colors),
                    stringResource(
                        when {
                            palette == AppSettings.Palette.DYNAMIC && Build.VERSION.SDK_INT >= 31 ->
                                R.string.palette_dynamic_desc
                            palette == AppSettings.Palette.DYNAMIC -> R.string.palette_dynamic_old
                            else -> R.string.palette_brand_desc
                        }
                    ),
                )
                Choice(
                    AppSettings.Palette.entries,
                    palette,
                    {
                        ctx.getString(
                            if (it == AppSettings.Palette.BRAND) R.string.palette_brand
                            else R.string.palette_dynamic
                        )
                    },
                ) {
                    AppSettings.setPalette(it)
                }
                Divider()
                Item(Icons.Filled.Language, SetColors.look, stringResource(R.string.language))
                Choice(
                    listOf("auto", "it", "en"),
                    lang,
                    {
                        when (it) {
                            "it" -> "Italiano"
                            "en" -> "English"
                            else -> ctx.getString(R.string.lang_auto)
                        }
                    },
                ) {
                    AppSettings.setLang(it)
                    (ctx as? Activity)
                        ?.recreate() // il contesto con la nuova lingua si crea in attachBaseContext
                }
            }

            Group(stringResource(R.string.group_notifications)) {
                Item(
                    Icons.Filled.Notifications,
                    SetColors.general,
                    stringResource(R.string.notifications),
                    trailing = {
                        Switch(
                            notif,
                            {
                                notif = it
                                prefs.notificationsEnabled = it
                            },
                        )
                    },
                    onClick = {
                        notif = !notif
                        prefs.notificationsEnabled = notif
                    },
                )
                if (Build.VERSION.SDK_INT >= 26) {
                    Divider()
                    Item(
                        Icons.Filled.NotificationsActive,
                        SetColors.general,
                        stringResource(R.string.channel_settings),
                        stringResource(R.string.channel_settings_desc),
                        trailing = { Chevron() },
                        onClick = {
                            PollWorker.ensureChannel(ctx)
                            ctx.startActivity(
                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                    .putExtra(Settings.EXTRA_CHANNEL_ID, PollWorker.CHANNEL_ID)
                            )
                        },
                    )
                }
                Divider()
                Item(
                    Icons.Filled.Schedule,
                    SetColors.general,
                    stringResource(R.string.poll_interval),
                    stringResource(R.string.doze_note),
                )
                Choice(Prefs.INTERVALS, interval, { if (it < 60) "$it min" else "${it / 60} h" }) {
                    interval = it
                    prefs.intervalMinutes = it
                    PollWorker.schedule(ctx, it)
                }
            }

            Group(stringResource(R.string.group_filter)) {
                Item(
                    Icons.Filled.FilterAlt,
                    SetColors.general,
                    stringResource(R.string.max_changes),
                    stringResource(R.string.max_changes_desc),
                )
                Choice(Prefs.MAX_CHANGES, maxChanges, { it.toString() }) {
                    maxChanges = it
                    prefs.maxChanges = it
                }
            }

            Group(stringResource(R.string.group_data)) {
                Item(
                    Icons.Filled.DeleteSweep,
                    SetColors.data,
                    stringResource(R.string.clear_cache),
                    Formatter.formatShortFileSize(ctx, cacheBytes),
                    onClick = { cacheDialog = true },
                )
            }

            if (BuildConfig.UPDATER) UpdatesGroup(prefs, version)

            Group(stringResource(R.string.group_info)) {
                Item(
                    Icons.Filled.Code,
                    SetColors.about,
                    stringResource(R.string.source_code),
                    OsmConfig.REPO.removePrefix("https://"),
                    onClick = { openUrl(ctx, OsmConfig.REPO) },
                )
                Divider()
                Item(
                    Icons.Filled.BugReport,
                    SetColors.about,
                    stringResource(R.string.report_issue),
                    onClick = { openUrl(ctx, OsmConfig.REPO + "/issues/new") },
                )
                Divider()
                Item(
                    Icons.Filled.Map,
                    SetColors.about,
                    stringResource(R.string.osm_copyright),
                    onClick = { openUrl(ctx, "https://www.openstreetmap.org/copyright") },
                )
            }
            Muted(
                stringResource(R.string.data_note),
                Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
    }

    if (cacheDialog)
        AlertDialog(
            onDismissRequest = { cacheDialog = false },
            title = { Text(stringResource(R.string.clear_cache)) },
            text = { Text(stringResource(R.string.clear_cache_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        cacheDialog = false
                        tilesDir.deleteRecursively()
                        cacheTick++
                    }
                ) {
                    Text(stringResource(R.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { cacheDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
}

/**
 * Nuove versioni dalle release di GitHub: controllo a mano, download con avanzamento,
 * installazione, controllo all'avvio.
 */
@Composable
private fun UpdatesGroup(prefs: Prefs, version: String) {
    val ctx = LocalContext.current
    val up by Updater.state.collectAsState()
    var auto by remember { mutableStateOf(prefs.autoUpdateCheck) }
    val release =
        when (val s = up) {
            is Updater.State.Available -> s.release
            is Updater.State.Downloading -> s.release
            is Updater.State.Installing -> s.release
            is Updater.State.Failed -> s.release
            else -> null
        }
    Group(stringResource(R.string.group_updates)) {
        Item(
            Icons.Filled.SystemUpdate,
            SetColors.about,
            stringResource(R.string.updates),
            when (val s = up) {
                is Updater.State.Checking -> stringResource(R.string.update_checking)
                is Updater.State.UpToDate -> stringResource(R.string.update_latest, version)
                is Updater.State.Failed -> s.message
                else ->
                    release?.let { stringResource(R.string.update_available, it.version) }
                        ?: stringResource(R.string.update_tap_to_check, version)
            },
            trailing = {
                if (up is Updater.State.Checking)
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            },
            onClick = { Updater.check(ctx) { prefs.lastUpdateCheck = System.currentTimeMillis() } },
        )
        if (release != null)
            Column(
                Modifier.padding(start = 72.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (release.notes.isNotBlank())
                    Text(
                        release.notes,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                when (val s = up) {
                    is Updater.State.Downloading ->
                        LinearProgressIndicator({ s.progress }, Modifier.fillMaxWidth())
                    is Updater.State.Installing -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> {
                        // si riattiva tornando dalle impostazioni di sistema: rileggi al resume
                        var allowed by remember { mutableStateOf(Updater.canInstall(ctx)) }
                        LifecycleResumeEffect(Unit) {
                            allowed = Updater.canInstall(ctx)
                            onPauseOrDispose {}
                        }
                        Button(
                            onClick = {
                                if (Updater.canInstall(ctx))
                                    Updater.downloadAndInstall(ctx, release)
                                else
                                    ctx.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            Uri.parse("package:${ctx.packageName}"),
                                        )
                                    )
                            }
                        ) {
                            Text(
                                stringResource(
                                    if (allowed) R.string.update_install else R.string.update_allow
                                )
                            )
                        }
                        if (!allowed) Muted(stringResource(R.string.update_allow_desc))
                    }
                }
            }
        Divider()
        Item(
            Icons.Filled.Autorenew,
            SetColors.about,
            stringResource(R.string.update_auto),
            stringResource(R.string.update_auto_desc),
            trailing = {
                Switch(
                    auto,
                    {
                        auto = it
                        prefs.autoUpdateCheck = it
                    },
                )
            },
            onClick = {
                auto = !auto
                prefs.autoUpdateCheck = auto
            },
        )
    }
    Muted(
        stringResource(R.string.update_note),
        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Intestazione con icona, nome e versione dell'app (stesso schema di ParkMap). */
@Composable
private fun AppCard(version: String) {
    Box(
        Modifier.fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFF3B82F6))))
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF2563EB)),
                contentAlignment = Alignment.Center,
            ) {
                // il disegno occupa il centro del riquadro di 108 dp: si ingrandisce e il bordo
                // vuoto resta fuori dal clip, come fa il launcher
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    null,
                    Modifier.requiredSize(96.dp),
                )
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    stringResource(R.string.version_fmt, version),
                    color = Color.White.copy(alpha = 0.9f),
                )
                Text(
                    stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}
