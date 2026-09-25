package com.datepin.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DatePinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                DatePinManager.recordSystemEvent(context, "BOOT_COMPLETED")
                DatePinManager.refreshIfEnabled(context)
                DatePinManager.scheduleBootRetries(context)
            }

            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                DatePinManager.recordSystemEvent(context, action.substringAfterLast('.'))
                DatePinManager.refreshIfEnabled(context)
            }
        }
    }
}
