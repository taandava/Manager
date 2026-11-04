package com.taandava.manager.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for android.provider.Telephony.WAP_PUSH_DELIVER.
 * Required for Android Default SMS app eligibility.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if ("android.provider.Telephony.WAP_PUSH_DELIVER" != intent.action) {
            return
        }
        try {
            Log.i("MmsReceiver", "Handled WAP_PUSH_DELIVER broadcast")
        } catch (e: Exception) {
            Log.e("MmsReceiver", "Error receiving MMS WAP push: ${e.message}", e)
        }
    }
}
