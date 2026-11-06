package org.osmwatch.changesetwatch.network

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.domain.attempt
import org.osmwatch.changesetwatch.domain.reason

/**
 * Aggiornamenti dalle release GitHub del progetto: controllo, download in cache, verifica e
 * installazione. Si installa solo un APK con lo stesso package, firmato con la stessa chiave
 * dell'app e con versionCode più alto.
 */
object Updater {
    private const val API =
        "https://api.github.com/repos/AndreaDp271/OsmChangesetWatch/releases/latest"

    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val pageUrl: String,
    )

    sealed interface State {
        data object Idle : State

        data object Checking : State

        data object UpToDate : State

        data class Available(val release: Release) : State

        data class Downloading(val release: Release, val progress: Float) : State

        data class Installing(val release: Release) : State

        data class Failed(val message: String, val release: Release? = null) : State
    }

    val state = MutableStateFlow<State>(State.Idle)
    private val scope =
        // il download continua anche se si esce dalle impostazioni
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Confronto numerico "1.10.0" > "1.9.2"; le parti mancanti o non numeriche valgono 0. */
    fun isNewer(remote: String, local: String): Boolean {
        val a = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val b = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    fun parse(json: String): Release {
        val o = JSONObject(json)
        val assets = o.optJSONArray("assets")
        val apk =
            (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?: throw IOException("no apk")
        return Release(
            version = o.getString("tag_name").removePrefix("v"),
            notes = o.optString("body").trim(),
            apkUrl = apk.getString("browser_download_url"),
            pageUrl = o.optString("html_url"),
        )
    }

    private fun installedVersion(ctx: Context) =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    /**
     * Chiede a GitHub l'ultima release; [quiet] = controllo automatico, che non mostra errori né
     * "sei aggiornato".
     */
    fun check(ctx: Context, quiet: Boolean = false, onDone: (() -> Unit)? = null) {
        val s = state.value
        if (s is State.Checking || s is State.Downloading || s is State.Installing) return
        if (!quiet) state.value = State.Checking
        val app = ctx.applicationContext
        scope.launch {
            val result =
                attempt {
                        val req =
                            Request.Builder()
                                .url(API)
                                .header("Accept", "application/vnd.github+json")
                                .header("User-Agent", OsmConfig.USER_AGENT)
                                .build()
                        val rel =
                            http.newCall(req).execute().use { r ->
                                when {
                                    r.code == 404 -> null // nessuna release pubblicata
                                    !r.isSuccessful ->
                                        throw IOException(
                                            app.getString(R.string.update_http, r.code)
                                        )
                                    else -> parse(r.body!!.string())
                                }
                            }
                        onDone?.invoke()
                        if (rel != null && isNewer(rel.version, installedVersion(app)))
                            State.Available(rel)
                        else State.UpToDate
                    }
                    .getOrElse { State.Failed(it.reason) }
            if (!quiet || result is State.Available) state.value = result
        }
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun downloadAndInstall(ctx: Context, rel: Release) {
        val app = ctx.applicationContext
        state.value = State.Downloading(rel, 0f)
        scope.launch {
            attempt {
                    val dir =
                        File(app.cacheDir, "update").apply {
                            deleteRecursively()
                            mkdirs()
                        }
                    val file = File(dir, "OsmChangesetWatch-${rel.version}.apk")
                    http
                        .newCall(
                            Request.Builder()
                                .url(rel.apkUrl)
                                .header("User-Agent", OsmConfig.USER_AGENT)
                                .build()
                        )
                        .execute()
                        .use { r ->
                            if (!r.isSuccessful)
                                throw IOException(
                                    app.getString(R.string.update_download_failed, r.code)
                                )
                            val body = r.body!!
                            val total = body.contentLength().takeIf { it > 0 }
                            body.byteStream().use { input ->
                                file.outputStream().use { out ->
                                    val buf = ByteArray(64 * 1024)
                                    var done = 0L
                                    while (true) {
                                        val n = input.read(buf)
                                        if (n < 0) break
                                        out.write(buf, 0, n)
                                        done += n
                                        if (total != null)
                                            state.value =
                                                State.Downloading(rel, done.toFloat() / total)
                                    }
                                }
                            }
                        }
                    verify(app, file)
                    state.value = State.Installing(rel)
                    install(app, file)
                }
                .onFailure { state.value = State.Failed(it.reason, rel) }
        }
    }

    private fun signers(info: PackageInfo?): Set<String> {
        val sigs =
            if (Build.VERSION.SDK_INT >= 28) info?.signingInfo?.apkContentsSigners
            else @Suppress("DEPRECATION") info?.signatures
        val sha = MessageDigest.getInstance("SHA-256")
        return sigs
            .orEmpty()
            .map { s -> sha.digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }
            .toSet()
    }

    private fun versionCode(info: PackageInfo) =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()

    /**
     * Stesso package, stessa firma, versione più alta: altrimenti il file si cancella e non si
     * installa.
     */
    private fun verify(ctx: Context, file: File) {
        val flag =
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val apk = ctx.packageManager.getPackageArchiveInfo(file.path, flag)
        val me = ctx.packageManager.getPackageInfo(ctx.packageName, flag)
        val fail =
            when {
                apk == null -> R.string.update_invalid_file
                apk.packageName != ctx.packageName ||
                    signers(me).isEmpty() ||
                    signers(apk) != signers(me) -> R.string.update_bad_signature
                versionCode(apk) <= versionCode(me) -> R.string.update_not_newer
                else -> null
            }
        if (fail != null) {
            file.delete()
            throw IOException(ctx.getString(fail))
        }
    }

    private fun install(ctx: Context, file: File) {
        val pi = ctx.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                if (Build.VERSION.SDK_INT >= 31)
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                s.fsync(out)
            }
            val flags =
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(
                PendingIntent.getBroadcast(ctx, id, Intent(ctx, UpdateReceiver::class.java), flags)
                    .intentSender
            )
        }
    }

    internal fun onResult(ctx: Context, status: Int, confirm: Intent?) {
        val rel = (state.value as? State.Installing)?.release
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            PackageInstaller.STATUS_SUCCESS ->
                state.value =
                    State.UpToDate // di solito l'app viene riavviata prima di arrivare qui
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                state.value = rel?.let { State.Available(it) } ?: State.Idle
            else -> {
                val msg =
                    ctx.getString(
                        when (status) {
                            PackageInstaller.STATUS_FAILURE_STORAGE -> R.string.update_no_space
                            PackageInstaller.STATUS_FAILURE_CONFLICT,
                            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                                R.string.update_bad_signature
                            else -> R.string.update_install_failed
                        }
                    )
                state.value = State.Failed(msg, rel)
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** Esito della sessione di installazione (conferma dell'utente, successo, errore). */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val confirm =
            if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
        Updater.onResult(
            context,
            intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE),
            confirm,
        )
    }
}
