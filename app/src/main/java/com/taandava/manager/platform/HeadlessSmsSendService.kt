package com.taandava.manager.platform

import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import com.taandava.manager.data.LocalDb
import com.taandava.manager.data.SmsRecordEntity
import com.taandava.manager.util.PhoneUtils
import com.taandava.manager.util.SmsClassifier

/**
 * Service for android.telephony.TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.
 * Strictly required for Android Default SMS app eligibility.
 *
 * Handles quick SMS responses (e.g., rejecting an incoming call with SMS or quick reply from Wear/Auto)
 * by sending the SMS, persisting it to system Telephony provider and the local SMS ledger,
 * and terminating gracefully.
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            val action = intent.action
            if (TelephonyManager.ACTION_RESPOND_VIA_MESSAGE == action) {

                val uri = intent.data
                val recipient = getRecipientFromUri(uri)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

                if (!recipient.isNullOrBlank() && text.isNotBlank()) {
                    sendSms(recipient, text)
                }
            }
        } catch (e: Exception) {
            Log.e("HeadlessSms", "Error handling quick reply: ${e.message}", e)
        } finally {
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private fun getRecipientFromUri(uri: Uri?): String? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase()
        return if (scheme in listOf("sms", "smsto", "mms", "mmsto")) {
            uri.schemeSpecificPart
        } else {
            null
        }
    }

    private fun sendSms(recipient: String, text: String) {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applicationContext.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        val parts = smsManager.divideMessage(text)
        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
        } else {
            smsManager.sendTextMessage(recipient, null, text, null, null)
        }

        val now = System.currentTimeMillis()
        // Save to Telephony Sent box
        val cv = ContentValues().apply {
            put(Telephony.Sms.Sent.ADDRESS, recipient)
            put(Telephony.Sms.Sent.BODY, text)
            put(Telephony.Sms.Sent.DATE, now)
            put(Telephony.Sms.Sent.READ, 1)
            put(Telephony.Sms.Sent.TYPE, Telephony.Sms.Sent.MESSAGE_TYPE_SENT)
        }
        val sentUri = try {
            contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, cv)
        } catch (e: Exception) {
            Log.e("HeadlessSms", "Error writing to Telephony.Sms.Sent: ${e.message}")
            null
        }
        val devSmsId = sentUri?.lastPathSegment?.toLongOrNull() ?: 0L

        // Save to local DB
        try {
            val db = LocalDb.getInstance(applicationContext)
            val norm = PhoneUtils.normalizePhone(recipient)
            val cat = SmsClassifier.categorizeBody(text)
            val isSaved = db.getActiveNormalizedPhones().contains(norm)
            val sType = SmsClassifier.determineSenderType(recipient, isSaved)
            val record = SmsRecordEntity(
                deviceSmsId = devSmsId,
                address = recipient,
                normalizedNumber = norm,
                body = text,
                date = now,
                type = 2, // SENT
                isSynced = false,
                deviceId = Build.MODEL ?: "",
                category = cat,
                senderType = sType
            )
            db.insertSmsBatch(listOf(record))

            // Automatically sync sent SMS to Hostinger cloud in background
            Thread {
                try {
                    val syncManager = com.taandava.manager.sync.CloudSyncManager(applicationContext)
                    val prefs = applicationContext.getSharedPreferences("manager_prefs", Context.MODE_PRIVATE)
                    val cloudUrl = prefs.getString("cloud_url", com.taandava.manager.sync.CloudSyncManager.DEFAULT_URL) ?: com.taandava.manager.sync.CloudSyncManager.DEFAULT_URL
                    val cloudToken = prefs.getString("cloud_token", com.taandava.manager.sync.CloudSyncManager.DEFAULT_TOKEN) ?: com.taandava.manager.sync.CloudSyncManager.DEFAULT_TOKEN
                    val devId = (Build.MODEL ?: "Android").trim().replace(" ", "_")
                    syncManager.syncSms(cloudUrl, cloudToken, devId)
                } catch (e: Exception) {
                    Log.w("HeadlessSms", "Automatic sent SMS cloud sync deferred: ${e.message}")
                }
            }.start()
        } catch (e: Exception) {
            Log.e("HeadlessSms", "Error saving sent SMS to LocalDb: ${e.message}")
        }
    }
}
