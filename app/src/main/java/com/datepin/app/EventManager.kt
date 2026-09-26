package com.datepin.app

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

data class DatePinEvent(
    val name: String,
    val date: LocalDate,
    val mode: String
)

object EventManager {
    const val MODE_COUNTDOWN = "countdown"
    const val MODE_SINCE = "since"
    const val PIN_TODAY = "today"
    const val PIN_EVENT = "event"

    private const val PREFS = "datepin_events"
    private const val KEY_HAS_EVENT = "has_event"
    private const val KEY_NAME = "event_name"
    private const val KEY_DATE = "event_date"
    private const val KEY_MODE = "event_mode"
    private const val KEY_PINNED = "pinned_target"

    fun getEvent(context: Context): DatePinEvent? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_EVENT, false)) return null

        val name = prefs.getString(KEY_NAME, null) ?: return null
        val rawDate = prefs.getString(KEY_DATE, null) ?: return null
        val mode = prefs.getString(KEY_MODE, MODE_COUNTDOWN) ?: MODE_COUNTDOWN

        return runCatching {
            DatePinEvent(name, LocalDate.parse(rawDate), mode)
        }.getOrNull()
    }

    fun saveEvent(context: Context, event: DatePinEvent) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_EVENT, true)
            .putString(KEY_NAME, event.name.trim())
            .putString(KEY_DATE, event.date.toString())
            .putString(KEY_MODE, event.mode)
            .putString(KEY_PINNED, PIN_EVENT)
            .apply()

        DatePinManager.refreshIfEnabled(context)
    }

    fun deleteEvent(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HAS_EVENT)
            .remove(KEY_NAME)
            .remove(KEY_DATE)
            .remove(KEY_MODE)
            .putString(KEY_PINNED, PIN_TODAY)
            .apply()

        DatePinManager.refreshIfEnabled(context)
    }

    fun pinnedTarget(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PINNED, PIN_TODAY) ?: PIN_TODAY
        return if (saved == PIN_EVENT && getEvent(context) != null) PIN_EVENT else PIN_TODAY
    }

    fun setPinnedTarget(context: Context, target: String) {
        val safeTarget = if (target == PIN_EVENT && getEvent(context) != null) {
            PIN_EVENT
        } else {
            PIN_TODAY
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PINNED, safeTarget)
            .apply()

        DatePinManager.refreshIfEnabled(context)
    }

    fun eventValue(event: DatePinEvent, today: LocalDate = LocalDate.now()): Long {
        return when (event.mode) {
            MODE_SINCE -> ChronoUnit.DAYS.between(event.date, today).coerceAtLeast(0)
            else -> ChronoUnit.DAYS.between(today, event.date).coerceAtLeast(0)
        }
    }

    fun iconTextForEvent(event: DatePinEvent): String {
        val days = eventValue(event)
        return when {
            days <= 9_999 -> days.toString()
            days < 100_000 -> "${days / 1000}k"
            else -> "99k+"
        }
    }

    fun eventDescription(context: Context, event: DatePinEvent): String {
        val days = eventValue(event).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val dateText = event.date.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
        )

        if (days == 0) {
            return if (event.mode == MODE_SINCE) {
                context.getString(R.string.started_today, dateText)
            } else {
                context.getString(R.string.event_is_today, dateText)
            }
        }

        val resource = if (event.mode == MODE_SINCE) {
            R.plurals.days_since
        } else {
            R.plurals.days_left
        }

        return context.resources.getQuantityString(
            resource,
            days,
            days,
            dateText
        )
    }
}
