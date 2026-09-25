package com.datepin.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DatePinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DatePinManager.refreshIfEnabled(context)
    }
}
