package com.datepin.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

data class DatePinEvent(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val date: LocalDate,
    val mode: String,
    val recurrence: String = EventManager.RECURRENCE_NONE
)

object EventManager {
    const val MODE_COUNTDOWN = "countdown"
    const val MODE_SINCE = "since"

    const val RECURRENCE_NONE = "none"
    const val RECURRENCE_ANNUAL = "annual"
    const val RECURRENCE_MONTHLY = "monthly"

    const val PIN_TODAY = "today"
    private const val PIN_EVENT_PREFIX = "event:"

    private const val PREFS = "datepin_events"
    private const val KEY_EVENTS_JSON = "events_json"
    private const val KEY_PINNED = "pinned_target"
    private const val KEY_MIGRATED_TO_LIST = "migrated_to_list_v1"

    // Legacy 0.4.x keys. Kept only for one-time migration.
    private const val LEGACY_HAS_EVENT = "has_event"
    private const val LEGACY_NAME = "event_name"
    private const val LEGACY_DATE = "event_date"
    private const val LEGACY_MODE = "event_mode"
    private const val LEGACY_PIN_EVENT = "event"

    fun getEvents(context: Context): List<DatePinEvent> {
        migrateLegacyIfNeeded(context)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_EVENTS_JSON, "[]") ?: "[]"

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    parseEvent(item)?.let(::add)
                }
            }
        }.getOrElse { emptyList() }
    }

    fun canCreateEvent(context: Context): Boolean {
        return PremiumManager.isPremium(context) || getEvents(context).isEmpty()
    }

    fun saveEvent(context: Context, event: DatePinEvent): Boolean {
        val events = getEvents(context).toMutableList()
        val existingIndex = events.indexOfFirst { it.id == event.id }

        if (existingIndex >= 0) {
            events[existingIndex] = event.copy(name = event.name.trim())
        } else {
            if (!canCreateEvent(context)) return false
            events.add(event.copy(name = event.name.trim()))
        }

        persistEvents(context, events)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PINNED, eventTarget(event.id))
            .apply()

        DatePinManager.refreshIfEnabled(context)
        return true
    }

    fun deleteEvent(context: Context, eventId: String) {
        val events = getEvents(context)
            .filterNot { it.id == eventId }

        persistEvents(context, events)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pinned = prefs.getString(KEY_PINNED, PIN_TODAY)

        if (pinned == eventTarget(eventId)) {
            prefs.edit()
                .putString(KEY_PINNED, PIN_TODAY)
                .apply()
        }

        DatePinManager.refreshIfEnabled(context)
    }

    fun pinnedTarget(context: Context): String {
        migrateLegacyIfNeeded(context)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PINNED, PIN_TODAY) ?: PIN_TODAY

        if (saved == PIN_TODAY) return PIN_TODAY

        val eventId = eventIdFromTarget(saved) ?: return PIN_TODAY
        return if (getEvents(context).any { it.id == eventId }) {
            saved
        } else {
            PIN_TODAY
        }
    }

    fun pinnedEventId(context: Context): String? =
        eventIdFromTarget(pinnedTarget(context))

    fun getPinnedEvent(context: Context): DatePinEvent? {
        val eventId = pinnedEventId(context) ?: return null
        return getEvents(context).firstOrNull { it.id == eventId }
    }

    fun isPinnedEvent(target: String, eventId: String): Boolean =
        target == eventTarget(eventId)

    fun setPinnedToday(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PINNED, PIN_TODAY)
            .apply()

        DatePinManager.refreshIfEnabled(context)
    }

    fun setPinnedEvent(context: Context, eventId: String) {
        if (getEvents(context).none { it.id == eventId }) return

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PINNED, eventTarget(eventId))
            .apply()

        DatePinManager.refreshIfEnabled(context)
    }

    fun effectiveDate(
        event: DatePinEvent,
        today: LocalDate = LocalDate.now()
    ): LocalDate {
        return when (event.recurrence) {
            RECURRENCE_ANNUAL -> {
                if (event.mode == MODE_SINCE) {
                    previousAnnualOccurrence(event.date, today)
                } else {
                    nextAnnualOccurrence(event.date, today)
                }
            }

            RECURRENCE_MONTHLY -> {
                if (event.mode == MODE_SINCE) {
                    previousMonthlyOccurrence(event.date, today)
                } else {
                    nextMonthlyOccurrence(event.date, today)
                }
            }

            else -> event.date
        }
    }

    fun eventValue(event: DatePinEvent, today: LocalDate = LocalDate.now()): Long {
        val target = effectiveDate(event, today)

        return when (event.mode) {
            MODE_SINCE -> ChronoUnit.DAYS.between(target, today).coerceAtLeast(0)
            else -> ChronoUnit.DAYS.between(today, target).coerceAtLeast(0)
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
        val displayDate = effectiveDate(event)
        val dateText = displayDate.format(
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

    private fun nextAnnualOccurrence(base: LocalDate, today: LocalDate): LocalDate {
        var candidate = safeDate(today.year, base.monthValue, base.dayOfMonth)
        if (candidate.isBefore(today)) {
            candidate = safeDate(today.year + 1, base.monthValue, base.dayOfMonth)
        }
        return candidate
    }

    private fun previousAnnualOccurrence(base: LocalDate, today: LocalDate): LocalDate {
        var candidate = safeDate(today.year, base.monthValue, base.dayOfMonth)
        if (candidate.isAfter(today)) {
            candidate = safeDate(today.year - 1, base.monthValue, base.dayOfMonth)
        }
        return candidate
    }

    private fun nextMonthlyOccurrence(base: LocalDate, today: LocalDate): LocalDate {
        var candidate = safeDate(today.year, today.monthValue, base.dayOfMonth)
        if (candidate.isBefore(today)) {
            val nextMonth = today.plusMonths(1)
            candidate = safeDate(nextMonth.year, nextMonth.monthValue, base.dayOfMonth)
        }
        return candidate
    }

    private fun previousMonthlyOccurrence(base: LocalDate, today: LocalDate): LocalDate {
        var candidate = safeDate(today.year, today.monthValue, base.dayOfMonth)
        if (candidate.isAfter(today)) {
            val previousMonth = today.minusMonths(1)
            candidate = safeDate(
                previousMonth.year,
                previousMonth.monthValue,
                base.dayOfMonth
            )
        }
        return candidate
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
        val first = LocalDate.of(year, month, 1)
        val safeDay = day.coerceAtMost(first.lengthOfMonth())
        return first.withDayOfMonth(safeDay)
    }

    private fun eventTarget(eventId: String): String =
        "$PIN_EVENT_PREFIX$eventId"

    private fun eventIdFromTarget(target: String): String? =
        target.takeIf { it.startsWith(PIN_EVENT_PREFIX) }
            ?.removePrefix(PIN_EVENT_PREFIX)
            ?.takeIf { it.isNotBlank() }

    private fun persistEvents(context: Context, events: List<DatePinEvent>) {
        val array = JSONArray()

        events.forEach { event ->
            array.put(
                JSONObject()
                    .put("id", event.id)
                    .put("name", event.name)
                    .put("date", event.date.toString())
                    .put("mode", event.mode)
                    .put("recurrence", event.recurrence)
            )
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EVENTS_JSON, array.toString())
            .apply()
    }

    private fun parseEvent(item: JSONObject): DatePinEvent? {
        return runCatching {
            val id = item.optString("id").ifBlank { UUID.randomUUID().toString() }
            val name = item.getString("name")
            val date = LocalDate.parse(item.getString("date"))
            val mode = item.optString("mode", MODE_COUNTDOWN)
            val recurrence = item.optString("recurrence", RECURRENCE_NONE)

            DatePinEvent(
                id = id,
                name = name,
                date = date,
                mode = mode,
                recurrence = recurrence
            )
        }.getOrNull()
    }

    private fun migrateLegacyIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED_TO_LIST, false)) return

        val editor = prefs.edit()

        if (!prefs.contains(KEY_EVENTS_JSON) && prefs.getBoolean(LEGACY_HAS_EVENT, false)) {
            val name = prefs.getString(LEGACY_NAME, null)
            val rawDate = prefs.getString(LEGACY_DATE, null)
            val mode = prefs.getString(LEGACY_MODE, MODE_COUNTDOWN) ?: MODE_COUNTDOWN

            if (!name.isNullOrBlank() && !rawDate.isNullOrBlank()) {
                val migratedEvent = runCatching {
                    DatePinEvent(
                        id = "migrated-event",
                        name = name,
                        date = LocalDate.parse(rawDate),
                        mode = mode
                    )
                }.getOrNull()

                if (migratedEvent != null) {
                    val array = JSONArray().put(
                        JSONObject()
                            .put("id", migratedEvent.id)
                            .put("name", migratedEvent.name)
                            .put("date", migratedEvent.date.toString())
                            .put("mode", migratedEvent.mode)
                            .put("recurrence", migratedEvent.recurrence)
                    )

                    editor.putString(KEY_EVENTS_JSON, array.toString())

                    if (prefs.getString(KEY_PINNED, PIN_TODAY) == LEGACY_PIN_EVENT) {
                        editor.putString(KEY_PINNED, eventTarget(migratedEvent.id))
                    }
                }
            }
        }

        editor
            .putBoolean(KEY_MIGRATED_TO_LIST, true)
            .remove(LEGACY_HAS_EVENT)
            .remove(LEGACY_NAME)
            .remove(LEGACY_DATE)
            .remove(LEGACY_MODE)
            .apply()
    }
}
