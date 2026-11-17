package com.appjapon.carnet.data

import android.content.Context

/** Progression (étapes cochées) et préférences, persistées localement. */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    fun done(day: Int): Set<Int> =
        prefs.getStringSet("day_$day", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()

    fun isDone(day: Int, step: Int) = step in done(day)

    fun setDone(day: Int, step: Int, value: Boolean) {
        val set = done(day).toMutableSet()
        if (value) set += step else set -= step
        prefs.edit().putStringSet("day_$day", set.map { it.toString() }.toSet()).apply()
    }

    fun reset(day: Int) = prefs.edit().remove("day_$day").apply()

    var autoCheck: Boolean
        get() = prefs.getBoolean("auto_check", true)
        set(v) = prefs.edit().putBoolean("auto_check", v).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen", false)
        set(v) = prefs.edit().putBoolean("keep_screen", v).apply()

    var lastDay: Int
        get() = prefs.getInt("last_day", 0)
        set(v) = prefs.edit().putInt("last_day", v).apply()
}
