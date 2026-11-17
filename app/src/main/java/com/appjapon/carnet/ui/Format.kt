package com.appjapon.carnet.ui

import android.graphics.drawable.GradientDrawable
import android.view.View
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
    private val shortFmt = DateTimeFormatter.ofPattern("dd/MM", Locale.FRENCH)

    fun longDate(d: LocalDate) = dayFmt.format(d).replaceFirstChar { it.uppercase() }
    fun shortDate(d: LocalDate): String = shortFmt.format(d)

    /** "06h30" → 06:30 */
    fun parseTime(s: String): LocalTime? {
        val m = Regex("(\\d{1,2})h(\\d{2})").find(s) ?: return null
        return LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }

    fun minutes(m: Long): String = when {
        m < 60 -> "$m min"
        else -> "${m / 60}h${(m % 60).toString().padStart(2, '0')}"
    }

    fun circle(view: View, color: Int) {
        view.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    }
}
