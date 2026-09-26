package com.datepin.app

import android.content.Context
import android.content.pm.ApplicationInfo

object PremiumManager {
    private const val PREFS = "datepin_premium"
    private const val KEY_PREMIUM = "premium_unlocked"

    fun isPremium(context: Context): Boolean {
        val isDebugBuild =
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        return isDebugBuild ||
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_PREMIUM, false)
    }

    /*
     * Temporary internal hook for development.
     * Real release entitlement will later come from Google Play Billing.
     */
    fun setPremiumForTesting(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREMIUM, enabled)
            .apply()
    }
}
