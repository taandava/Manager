package com.taandava.manager.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.taandava.manager.MainActivity
import com.taandava.manager.R
import com.taandava.manager.data.LocalDb
import com.taandava.manager.data.SmsRecordEntity
import com.taandava.manager.util.PhoneUtils
import com.taandava.manager.util.SmsClassifier

/**
 * BroadcastReceiver for android.provider.Telephony.SMS_DELIVER.
 * Strictly required for Android Default SMS app eligibility.
 *
 * When this app is the Default SMS App, the Android system delegates message writing
 * entirely to this receiver. This receiver writes incoming SMS to the system Telephony provider
 * and the local ledger, ensuring zero dropped messages.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "manager_sms_inbox"
        const val CHANNEL_NAME = "Incoming Messages"
    }

    @Suppress("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val originatingAddress = messages[0].displayOriginatingAddress
                ?: messages[0].originatingAddress ?: "Unknown"
            val timestamp = messages[0].timestampMillis

            val bodyBuilder = StringBuilder()
            for (msg in messages) {
                bodyBuilder.append(msg.displayMessageBody ?: msg.messageBody ?: "")
            }
            val fullBody = bodyBuilder.toString()

            // 1. Write incoming message to Android Telephony provider (Telephony.Sms.Inbox)
            val cv = ContentValues().apply {
                put(Telephony.Sms.Inbox.ADDRESS, originatingAddress)
                put(Telephony.Sms.Inbox.BODY, fullBody)
                put(Telephony.Sms.Inbox.DATE, timestamp)
                put(Telephony.Sms.Inbox.DATE_SENT, messages[0].timestampMillis)
                put(Telephony.Sms.Inbox.READ, 0)
                put(Telephony.Sms.Inbox.SEEN, 0)
                put(Telephony.Sms.Inbox.TYPE, Telephony.Sms.Inbox.MESSAGE_TYPE_INBOX)
            }

            val insertedUri = try {
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, cv)
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error writing to Telephony.Sms.Inbox: ${e.message}", e)
                null
            }
            val devSmsId = insertedUri?.lastPathSegment?.toLongOrNull() ?: 0L

            // 2. Ingest into LocalDb
            val db = LocalDb.getInstance(context)
            val norm = PhoneUtils.normalizePhone(originatingAddress)
            val cat = SmsClassifier.categorizeBody(fullBody)
            val isSaved = db.getActiveNormalizedPhones().contains(norm)
            val sType = SmsClassifier.determineSenderType(originatingAddress, isSaved)
            val otp = SmsClassifier.extractOtp(fullBody)
            val amt = SmsClassifier.extractAmount(fullBody)

            val record = SmsRecordEntity(
                deviceSmsId = devSmsId,
                address = originatingAddress,
                normalizedNumber = norm,
                body = fullBody,
                date = timestamp,
                type = 1, // INBOX
                isSynced = false,
                deviceId = Build.MODEL ?: "",
                category = cat,
                senderType = sType,
                extractedOtp = otp,
                extractedAmount = amt?.first,
                isDebit = amt?.second
            )
            db.insertSmsBatch(listOf(record))

            // 3. Post system notification
            showNotification(context, originatingAddress, fullBody, otp)

            // 4. Automatically sync new incoming SMS to Hostinger cloud in background
            Thread {
                try {
                    val syncManager = com.taandava.manager.sync.CloudSyncManager(context)
                    val prefs = context.getSharedPreferences("manager_prefs", Context.MODE_PRIVATE)
                    val cloudUrl = prefs.getString("cloud_url", com.taandava.manager.sync.CloudSyncManager.DEFAULT_URL) ?: com.taandava.manager.sync.CloudSyncManager.DEFAULT_URL
                    val cloudToken = prefs.getString("cloud_token", com.taandava.manager.sync.CloudSyncManager.DEFAULT_TOKEN) ?: com.taandava.manager.sync.CloudSyncManager.DEFAULT_TOKEN
                    val devId = (Build.MODEL ?: "Android").trim().replace(" ", "_")
                    syncManager.syncSms(cloudUrl, cloudToken, devId)
                } catch (e: Exception) {
                    Log.w("SmsReceiver", "Automatic SMS cloud sync deferred: ${e.message}")
                }
            }.start()

        } catch (e: Exception) {
            Log.e("SmsReceiver", "Error processing incoming SMS: ${e.message}", e)
        }
    }

    private fun showNotification(context: Context, sender: String, body: String, otp: String?) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for incoming SMS messages"
                    enableLights(true)
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("tab", 1) // SMS Ledger tab
                putExtra("sender", sender)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                sender.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (otp != null) "OTP: $otp ($sender)" else sender
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            nm.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Notification error: ${e.message}")
        }
    }
}
