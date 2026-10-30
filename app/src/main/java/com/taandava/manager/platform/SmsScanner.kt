package com.taandava.manager.platform

import android.content.Context
import androidx.core.net.toUri
import android.os.Build
import com.taandava.manager.data.LocalDb
import com.taandava.manager.data.SmsRecordEntity
import com.taandava.manager.util.PhoneUtils
import com.taandava.manager.util.SmsClassifier

class SmsScanner(private val context: Context) {

    private val db = LocalDb.getInstance(context)

    fun getDeviceModelName(): String {
        val m = Build.MANUFACTURER ?: ""
        val mod = Build.MODEL ?: ""
        val name = if (mod.startsWith(m, ignoreCase = true)) mod else "$m $mod"
        return name.trim().replace(" ", "_")
    }

    /**
     * Scans device SMS inbox and sent messages into the local SMS Ledger.
     * 
     * Key Safety & Integrity Guarantees:
     * 1. NEVER creates contacts from SMS (contacts strictly originate from device contacts book).
     * 2. Checks deleted SMS blacklist table: previously deleted messages are skipped and NEVER re-imported.
     * 3. No arbitrary 1000/2500 cap: handles 13,000+ messages smoothly via streaming 500-item transactions.
     * 4. Retains device telephony _id for permanent device deletion.
     */
    fun scanDeviceSms(onProgress: ((scanned: Int, inserted: Int) -> Unit)? = null): Int {
        val deviceModel = getDeviceModelName()
        val smsBatch = mutableListOf<SmsRecordEntity>()
        val existingPhones = mutableSetOf<String>()
        val blacklistedIds = db.getDeletedDeviceSmsIds()

        try {
            val existingContacts = db.getAllContactsRaw()
            for (c in existingContacts) {
                val norm = PhoneUtils.normalizePhone(c.phone)
                if (norm.isNotBlank()) existingPhones.add(norm)
            }
        } catch (e: Exception) {
            android.util.Log.e("SmsScanner", "Error loading existing contacts: ${e.message}", e)
        }

        var totalInserted = 0
        var totalScanned = 0

        try {
            val smsUri = "content://sms".toUri()
            val projection = arrayOf("_id", "address", "body", "date", "type")

            context.contentResolver.query(smsUri, projection, null, null, "date DESC")?.use { cursor ->
                val idIdx = cursor.getColumnIndex("_id")
                val addrIdx = cursor.getColumnIndex("address")
                val bodyIdx = cursor.getColumnIndex("body")
                val dateIdx = cursor.getColumnIndex("date")
                val typeIdx = cursor.getColumnIndex("type")

                while (cursor.moveToNext()) {
                    totalScanned++
                    val devSmsId = if (idIdx >= 0) cursor.getLong(idIdx) else 0L

                    // Skip messages explicitly deleted by the user
                    if (devSmsId > 0L && blacklistedIds.contains(devSmsId)) {
                        continue
                    }

                    val address = if (addrIdx >= 0) cursor.getString(addrIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else 1

                    val norm = PhoneUtils.normalizePhone(address)
                    val cat = SmsClassifier.categorizeBody(body)
                    val sType = SmsClassifier.determineSenderType(address, existingPhones.contains(norm))
                    val otp = SmsClassifier.extractOtp(body)
                    val amt = SmsClassifier.extractAmount(body)

                    smsBatch.add(
                        SmsRecordEntity(
                            deviceSmsId = devSmsId,
                            address = address,
                            normalizedNumber = norm,
                            body = body,
                            date = date,
                            type = type,
                            deviceId = deviceModel,
                            category = cat,
                            senderType = sType,
                            extractedOtp = otp,
                            extractedAmount = amt?.first,
                            isDebit = amt?.second
                        )
                    )

                    // Stream to SQLite in 500-item chunks for ultra-fast, memory-safe ingestion of 13,000+ messages
                    if (smsBatch.size >= 500) {
                        totalInserted += db.insertSmsBatch(smsBatch)
                        smsBatch.clear()
                        onProgress?.invoke(totalScanned, totalInserted)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SmsScanner", "Error querying SMS: ${e.message}", e)
        }

        // Insert remaining batch
        if (smsBatch.isNotEmpty()) {
            totalInserted += db.insertSmsBatch(smsBatch)
            smsBatch.clear()
            onProgress?.invoke(totalScanned, totalInserted)
        }

        return totalInserted
    }
}
