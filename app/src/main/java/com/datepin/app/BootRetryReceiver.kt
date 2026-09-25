package com.datepin.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val label = intent?.getStringExtra("retry_label") ?: "BOOT_RETRY"
        DatePinManager.recordSystemEvent(context, label)
        DatePinManager.refreshIfEnabled(context)
    }
}
