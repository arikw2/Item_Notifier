package com.arikw.itemnotifier.data

import android.content.Context

/** Small settings store; just the background check interval for now. */
object Prefs {
    private const val FILE = "item_notifier_prefs"
    private const val KEY_INTERVAL_MINUTES = "check_interval_minutes"

    /** WorkManager's minimum periodic interval is 15 minutes. */
    val INTERVAL_CHOICES_MINUTES = listOf(15L, 30L, 60L, 180L, 360L)
    const val DEFAULT_INTERVAL_MINUTES = 30L

    fun checkIntervalMinutes(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getLong(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)

    fun setCheckIntervalMinutes(context: Context, minutes: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_INTERVAL_MINUTES, minutes)
            .apply()
    }
}
