package org.osmwatch.changesetwatch.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var intervalMinutes: Int
        get() = sp.getInt("interval", 60)
        set(v) = sp.edit().putInt("interval", v).apply()

    var notificationsEnabled: Boolean
        get() = sp.getBoolean("notifications", true)
        set(v) = sp.edit().putBoolean("notifications", v).apply()

    /** Oltre questo numero di modifiche un changeset non viene scaricato/verificato (SKIPPED_TOO_LARGE). */
    var maxChanges: Int
        get() = sp.getInt("max_changes", 2000)
        set(v) = sp.edit().putInt("max_changes", v).apply()

    var askedNotificationPermission: Boolean
        get() = sp.getBoolean("asked_notif", false)
        set(v) = sp.edit().putBoolean("asked_notif", v).apply()

    companion object {
        val INTERVALS = listOf(15, 30, 60, 180, 360) // minuti; 15 = minimo di WorkManager
        val MAX_CHANGES = listOf(500, 1000, 2000, 5000)
    }
}
