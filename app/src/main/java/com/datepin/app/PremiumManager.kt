package com.datepin.app

import android.content.Context

object PremiumManager {
    private const val PREFS = "datepin_premium"
    private const val KEY_PREMIUM = "premium_unlocked"

    fun isPremium(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREMIUM, false)

    /*
     * Temporary internal hook for development.
     * Real entitlement will later come from Google Play Billing.
     */
    fun setPremiumForTesting(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREMIUM, enabled)
            .apply()
    }
}
