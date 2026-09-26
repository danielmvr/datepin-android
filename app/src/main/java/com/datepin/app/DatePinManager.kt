package com.datepin.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DatePinManager {
    private const val PREFS = "datepin_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_SYSTEM_EVENT = "last_system_event"
    private const val KEY_LAST_SYSTEM_EVENT_TIME = "last_system_event_time"
    private const val KEY_LAST_NOTIFICATION_RESULT = "last_notification_result"
    private const val KEY_LAST_NOTIFICATION_TIME = "last_notification_time"
    private const val KEY_ICON_STYLE = "icon_style"
    private const val BOOT_RETRY_REQUEST_1 = 2710
    private const val BOOT_RETRY_REQUEST_2 = 2711
    private const val CHANNEL_ID = "datepin_status"
    private const val NOTIFICATION_ID = 2509
    private const val MIDNIGHT_REQUEST_CODE = 2609

    fun recordSystemEvent(context: Context, action: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SYSTEM_EVENT, action.substringAfterLast('.'))
            .putLong(KEY_LAST_SYSTEM_EVENT_TIME, System.currentTimeMillis())
            .apply()
    }

    fun lastSystemEvent(context: Context): Pair<String?, Long> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_SYSTEM_EVENT, null) to
            prefs.getLong(KEY_LAST_SYSTEM_EVENT_TIME, 0L)
    }

    fun recordNotificationResult(context: Context, result: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_NOTIFICATION_RESULT, result)
            .putLong(KEY_LAST_NOTIFICATION_TIME, System.currentTimeMillis())
            .apply()
    }

    fun lastNotificationResult(context: Context): Pair<String?, Long> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_NOTIFICATION_RESULT, null) to
            prefs.getLong(KEY_LAST_NOTIFICATION_TIME, 0L)
    }

    fun scheduleBootRetries(context: Context) {
        scheduleBootRetry(context, BOOT_RETRY_REQUEST_1, 15_000L, "BOOT_RETRY_15S")
        scheduleBootRetry(context, BOOT_RETRY_REQUEST_2, 45_000L, "BOOT_RETRY_45S")
    }

    private fun scheduleBootRetry(
        context: Context,
        requestCode: Int,
        delayMillis: Long,
        label: String
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, BootRetryReceiver::class.java)
            .putExtra("retry_label", label)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + delayMillis,
            pendingIntent
        )
    }

    fun iconStyle(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ICON_STYLE, "bold") ?: "bold"

    fun setIconStyle(context: Context, style: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ICON_STYLE, style)
            .apply()

        refreshIfEnabled(context)
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun notificationsAllowed(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

        if (enabled) {
            showNotification(context)
            scheduleNextUpdate(context)
        } else {
            cancelNotification(context)
            cancelScheduledUpdate(context)
        }
    }

    fun refreshIfEnabled(context: Context) {
        if (!isEnabled(context)) {
            recordNotificationResult(context, "IGNORED_DISABLED")
            return
        }

        if (!notificationsAllowed(context)) {
            recordNotificationResult(context, "BLOCKED_NO_PERMISSION")
            return
        }

        try {
            showNotification(context)
            recordNotificationResult(context, "POSTED_OK")
            scheduleNextUpdate(context)
        } catch (t: Throwable) {
            recordNotificationResult(
                context,
                "ERROR_${t::class.java.simpleName}: ${t.message ?: "sem mensagem"}"
            )
        }
    }

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun showNotification(context: Context) {
        createNotificationChannel(context)

        val today = LocalDate.now()
        val manager = context.getSystemService(NotificationManager::class.java)

        val fullDate = today.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                .withLocale(Locale.getDefault())
        ).replaceFirstChar { it.uppercase() }

        val pinnedEvent = EventManager.getPinnedEvent(context)

        val iconText: String
        val notificationTitle: String
        val notificationBody: String

        if (pinnedEvent != null) {
            iconText = EventManager.iconTextForEvent(pinnedEvent)
            notificationTitle = pinnedEvent.name
            notificationBody = EventManager.eventDescription(context, pinnedEvent)
        } else {
            iconText = today.dayOfMonth.toString()
            notificationTitle = context.getString(R.string.notification_title)
            notificationBody = fullDate
        }

        val launchIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(createNumberIcon(iconText, iconStyle(context)))
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun cancelNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(NOTIFICATION_ID)
    }

    private fun scheduleNextUpdate(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, MidnightReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MIDNIGHT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextUpdate = ZonedDateTime.now(ZoneId.systemDefault())
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .plusMinutes(1)

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextUpdate.toInstant().toEpochMilli(),
            pendingIntent
        )
    }

    private fun cancelScheduledUpdate(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, MidnightReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MIDNIGHT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun createNumberIcon(text: String, style: String): Icon {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val length = text.length
        val startingSize = when (length) {
            1 -> 74f
            2 -> 62f
            3 -> 50f
            4 -> 42f
            else -> 38f
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER

            typeface = when {
                length >= 3 -> Typeface.create(
                    "sans-serif-condensed",
                    if (style == "clean") Typeface.NORMAL else Typeface.BOLD
                )
                style == "clean" -> Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                style == "compact" -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
                else -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            textSize = when {
                length >= 3 -> startingSize
                style == "clean" -> startingSize - 4f
                style == "compact" -> startingSize + 4f
                else -> startingSize
            }
        }

        // Keep the text inside one Android status-bar icon.
        // This lets 1–4 digit counters use as much space as possible
        // without relying on multiple notification icons.
        val maxTextWidth = size * 0.90f
        while (paint.measureText(text) > maxTextWidth && paint.textSize > 24f) {
            paint.textSize -= 1f
        }

        val baseline = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(text, size / 2f, baseline, paint)

        return Icon.createWithBitmap(bitmap)
    }
}
