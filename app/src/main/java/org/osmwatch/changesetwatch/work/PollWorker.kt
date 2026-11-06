package org.osmwatch.changesetwatch.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.osmwatch.changesetwatch.MainActivity
import org.osmwatch.changesetwatch.R
import org.osmwatch.changesetwatch.data.AppDatabase
import org.osmwatch.changesetwatch.data.ChangesetRepository
import org.osmwatch.changesetwatch.data.Prefs

/** Un solo lavoro periodico: itera sulle aree abilitate, una alla volta (pausa 1 s), notifica una volta per area. */
class PollWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val repo = ChangesetRepository(db)
        val areas = db.areaDao().observeAll().first().filter { it.enabled }
        var failures = 0
        areas.forEachIndexed { i, area ->
            if (i > 0) delay(1_000)
            try {
                val fresh = repo.refreshIncremental(area)
                Log.i(TAG, "area=${area.name} nuovi=${fresh.size}")
                if (fresh.isNotEmpty()) notify(area.id, area.name, fresh.size)
            } catch (e: Exception) {
                failures++
                Log.w(TAG, "area=${area.name} errore: ${e.message}")
            }
        }
        // tutte fallite (rete/server): riprova col backoff di WorkManager, lastCheckedAt non è avanzato
        return if (areas.isNotEmpty() && failures == areas.size) Result.retry() else Result.success()
    }

    private fun notify(areaId: Long, areaName: String, count: Int) {
        if (!Prefs(applicationContext).notificationsEnabled) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(applicationContext)
        val open = PendingIntent.getActivity(
            applicationContext, areaId.toInt(),
            Intent(applicationContext, MainActivity::class.java)
                .putExtra(EXTRA_AREA_ID, areaId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(areaName)
            .setContentText(applicationContext.resources.getQuantityString(R.plurals.new_changesets, count, count))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setGroup(CHANNEL_ID)
            .build()
        // id = area: una sola notifica per area, aggiornata (non una per changeset)
        applicationContext.getSystemService(NotificationManager::class.java).notify(areaId.toInt(), n)
    }

    companion object {
        private const val TAG = "PollWorker"
        const val CHANNEL_ID = "changesets"
        const val EXTRA_AREA_ID = "areaId"
        private const val WORK_NAME = "poll"

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        /** UPDATE: cambia l'intervallo senza duplicare il lavoro. Vincolo: rete connessa. */
        fun schedule(ctx: Context, minutes: Int) {
            val req = PeriodicWorkRequestBuilder<PollWorker>(minutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
