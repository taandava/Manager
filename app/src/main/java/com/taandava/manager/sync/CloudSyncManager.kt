package com.taandava.manager.sync

import android.content.Context
import com.taandava.manager.data.CloudContactItem
import com.taandava.manager.data.CloudOverview
import com.taandava.manager.data.CloudSmsItem
import com.taandava.manager.data.ContactEntity
import com.taandava.manager.data.LocalDb
import com.taandava.manager.platform.ContactScanner
import com.taandava.manager.util.ContactValidator
import com.taandava.manager.util.PhoneUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

class CloudSyncManager(context: Context) {

    private val db = LocalDb.getInstance(context)
    private val scanner = ContactScanner(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        const val DEFAULT_URL = "https://white-jackal-528611.hostingersite.com/contacts/index.php"
        const val DEFAULT_TOKEN = "Jmd@2025"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    fun testConnection(targetUrl: String, token: String): Triple<Boolean, Int, Long> {
        val start = System.currentTimeMillis()
        val url = if (targetUrl.contains("?")) "$targetUrl&action=list_csvs&token=$token" else "$targetUrl?action=list_csvs&token=$token"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Manager-Android")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                Triple(response.isSuccessful, response.code, latency)
            }
        } catch (_: Exception) {
            val latency = System.currentTimeMillis() - start
            Triple(false, 0, latency)
        }
    }

    fun fetchCloudOverview(targetUrl: String, token: String): Result<CloudOverview> {
        val sep = if (targetUrl.contains("?")) "&" else "?"
        val url = "$targetUrl${sep}action=get_cloud_overview&token=$token"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Manager-Android")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("Server returned HTTP ${response.code}"))
                }
                val bodyStr = response.body.string()
                val json = JSONObject(bodyStr)
                if (!json.optBoolean("ok", false)) {
                    return Result.failure(Exception(json.optString("error", "Failed to fetch overview")))
                }

                val devicesArr = json.optJSONArray("devices")
                val deviceList = mutableListOf<String>()
                if (devicesArr != null) {
                    for (i in 0 until devicesArr.length()) {
                        val d = devicesArr.optString(i, "")
                        if (d.isNotBlank()) deviceList.add(d)
                    }
                }

                val overview = CloudOverview(
                    totalContacts = json.optInt("total_contacts", 0),
                    totalSms = json.optInt("total_sms", 0),
                    uniquePhones = json.optInt("unique_phones", 0),
                    devices = deviceList,
                    dbSizeBytes = json.optLong("db_size_bytes", 0L),
                    lastUpdated = json.optLong("last_updated", 0L)
                )
                Result.success(overview)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchCloudContacts(
        targetUrl: String,
        token: String,
        query: String = "",
        limit: Int = 100,
        offset: Int = 0
    ): Result<Pair<Int, List<CloudContactItem>>> {
        val encodedQ = URLEncoder.encode(query.trim(), "UTF-8")
        val sep = if (targetUrl.contains("?")) "&" else "?"
        val url = "$targetUrl${sep}action=get_cloud_contacts&token=$token&q=$encodedQ&limit=$limit&offset=$offset"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Manager-Android")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("Server returned HTTP ${response.code}"))
                }
                val bodyStr = response.body.string()
                val json = JSONObject(bodyStr)
                if (!json.optBoolean("ok", false)) {
                    return Result.failure(Exception(json.optString("error", "Failed to fetch cloud contacts")))
                }

                val total = json.optInt("total", 0)
                val rows = json.optJSONArray("rows") ?: JSONArray()
                val list = mutableListOf<CloudContactItem>()
                for (i in 0 until rows.length()) {
                    val obj = rows.getJSONObject(i)
                    list.add(
                        CloudContactItem(
                            id = obj.optString("id", ""),
                            name = obj.optString("name", ""),
                            phone = obj.optString("phone", ""),
                            details = obj.optString("details", ""),
                            saved = obj.optBoolean("saved", false),
                            updatedAt = obj.optLong("updated_at", 0L),
                            deviceId = obj.optString("device_id", "")
                        )
                    )
                }
                Result.success(Pair(total, list))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchCloudSms(
        targetUrl: String,
        token: String,
        query: String = "",
        limit: Int = 100,
        offset: Int = 0
    ): Result<Pair<Int, List<CloudSmsItem>>> {
        val encodedQ = URLEncoder.encode(query.trim(), "UTF-8")
        val sep = if (targetUrl.contains("?")) "&" else "?"
        val url = "$targetUrl${sep}action=get_cloud_sms&token=$token&q=$encodedQ&limit=$limit&offset=$offset"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Manager-Android")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("Server returned HTTP ${response.code}"))
                }
                val bodyStr = response.body.string()
                val json = JSONObject(bodyStr)
                if (!json.optBoolean("ok", false)) {
                    return Result.failure(Exception(json.optString("error", "Failed to fetch cloud SMS")))
                }

                val total = json.optInt("total", 0)
                val rows = json.optJSONArray("rows") ?: JSONArray()
                val list = mutableListOf<CloudSmsItem>()
                for (i in 0 until rows.length()) {
                    val obj = rows.getJSONObject(i)
                    list.add(
                        CloudSmsItem(
                            id = obj.optString("id", ""),
                            address = obj.optString("address", ""),
                            normalizedNumber = obj.optString("normalized_number", ""),
                            body = obj.optString("body", ""),
                            date = obj.optLong("date", 0L),
                            type = obj.optString("type", "INBOX"),
                            deviceId = obj.optString("device_id", "")
                        )
                    )
                }
                Result.success(Pair(total, list))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun syncContacts(targetUrl: String, token: String, deviceId: String): Triple<Int, Int, String> {
        var pulledCount = 0
        var pushedCount = 0

        // 1. PUSH local changes (updates, creations, merges, deletions) to Cloud DB in safe chunks
        try {
            val allLocal = db.getAllContactsRaw()
            val pendingActive = allLocal.filter { (it.edited || it.syncStatus == "PENDING") && !it.deleted }
            val pendingDeletedIds = (allLocal.filter { it.deleted }.map { it.id } + db.getPendingDeletedContactIds()).distinct()

            val chunkSize = 500
            val activeChunks = pendingActive.chunked(chunkSize)
            val deletedChunks = pendingDeletedIds.chunked(chunkSize)
            val maxBatches = maxOf(activeChunks.size, deletedChunks.size, 1)

            if (pendingActive.isNotEmpty() || pendingDeletedIds.isNotEmpty()) {
                for (b in 0 until maxBatches) {
                    val batchContacts = activeChunks.getOrNull(b) ?: emptyList()
                    val batchDeleted = deletedChunks.getOrNull(b) ?: emptyList()
                    if (batchContacts.isEmpty() && batchDeleted.isEmpty()) continue

                    val postPayload = JSONObject().apply {
                        put("token", token)
                        put("action", "sync_contacts")
                        put("device_id", deviceId)

                        val contactsArr = JSONArray()
                        for (c in batchContacts) {
                            contactsArr.put(
                                JSONObject().apply {
                                    put("id", c.id)
                                    put("name", c.name)
                                    put("phone", c.phone)
                                    put("details", c.details)
                                    put("saved", if (c.saved) 1 else 0)
                                    put("deleted", 0)
                                    put("updated_at", c.updatedAt)
                                    put("device_id", c.deviceId.ifBlank { deviceId })
                                }
                            )
                        }
                        put("contacts", contactsArr)

                        val delArr = JSONArray()
                        for (did in batchDeleted) {
                            delArr.put(did)
                        }
                        put("deleted_ids", delArr)
                    }

                    val pushRequest = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", "Manager-Android")
                        .header("Content-Type", "application/json")
                        .post(postPayload.toString().toRequestBody(JSON_MEDIA))
                        .build()

                    client.newCall(pushRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            pushedCount += batchContacts.size + batchDeleted.size

                            if (batchContacts.isNotEmpty()) {
                                val activeSynced = batchContacts.map { it.copy(edited = false, syncStatus = "SYNCED") }
                                db.upsertContacts(activeSynced)
                            }

                            if (batchDeleted.isNotEmpty()) {
                                db.markDeletedContactsSynced(batchDeleted)
                                db.deleteContacts(batchDeleted, soft = false)
                            }
                        } else {
                            return Triple(pushedCount, 0, "Push error HTTP ${response.code}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return Triple(pushedCount, 0, "Push error: ${e.message}")
        }

        // 2. PULL remote contacts from Cloud DB with ultra-fast in-memory indexing
        try {
            val getUrl = if (targetUrl.contains("?")) "$targetUrl&format=json&token=$token" else "$targetUrl?format=json&token=$token"
            val getRequest = Request.Builder()
                .url(getUrl)
                .header("User-Agent", "Manager-Android")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(getRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return Triple(pushedCount, 0, "Server returned HTTP ${response.code}")
                }
                val bodyStr = response.body.string()
                val json = JSONObject(bodyStr)
                if (json.optBoolean("ok", true) && json.has("rows")) {
                    val rows = json.getJSONArray("rows")
                    val pulledContacts = mutableListOf<ContactEntity>()
                    val serverActiveIds = mutableSetOf<String>()
                    val blacklistedIds = db.getDeletedContactIds()
                    val blacklistedPhones = db.getDeletedContactPhones()
                    val activePhoneSet = db.getActiveNormalizedPhones()
                    val localTimestamps = db.getContactTimestampsMap()

                    for (i in 0 until rows.length()) {
                        val obj = rows.getJSONObject(i)
                        val id = obj.optString("id", UUID.randomUUID().toString())
                        val name = obj.optString("name", "")
                        val phone = PhoneUtils.normalizePhone(obj.optString("phone", ""))
                        val details = obj.optString("details", "")
                        val saved = obj.optBoolean("saved", false) || obj.optInt("saved", 0) == 1
                        val deleted = obj.optBoolean("deleted", false) || obj.optInt("deleted", 0) == 1
                        val updated = obj.optLong("updated_at", System.currentTimeMillis())
                        val dev = obj.optString("device_id", "HOSTINGER")

                        if (!deleted && (name.isNotBlank() || phone.isNotBlank()) && !ContactValidator.shouldRejectContact(name, phone, details, "HOSTINGER_CSV")) {
                            serverActiveIds.add(id)

                            // Prevent resurrection: skip if user deleted this contact locally (unless an active contact exists with this phone)
                            if (blacklistedIds.contains(id) || (blacklistedPhones.contains(phone) && !activePhoneSet.contains(phone))) {
                                continue
                            }

                            // Incremental optimization: only pull if contact is new locally or server has newer updated_at timestamp
                            val localUpdated = localTimestamps[id]
                            if (localUpdated == null || updated > localUpdated) {
                                pulledContacts.add(
                                    ContactEntity(
                                        id = id,
                                        name = name,
                                        phone = phone,
                                        details = details,
                                        saved = saved,
                                        edited = false,
                                        deleted = false,
                                        updatedAt = updated,
                                        source = "HOSTINGER_CSV",
                                        syncStatus = "SYNCED",
                                        deviceId = dev
                                    )
                                )
                            }
                        }
                    }

                    // 3. RECONCILE SERVER-SIDE DELETIONS (Server side changes propagate safely)
                    if (serverActiveIds.isNotEmpty()) {
                        val localSynced = db.getAllContacts(savedOnly = false, query = "")
                            .filter { it.source == "HOSTINGER_CSV" && it.syncStatus == "SYNCED" && !it.edited && !it.deleted }

                        val serverDeleted = localSynced.filter { !serverActiveIds.contains(it.id) }
                        if (serverDeleted.isNotEmpty()) {
                            val serverDeletedIds = serverDeleted.map { it.id }
                            db.deleteContacts(serverDeletedIds, soft = true)
                            for (sd in serverDeleted) {
                                scanner.deleteDeviceContactsByPhone(sd.phone)
                            }
                        }
                    }

                    if (pulledContacts.isNotEmpty()) {
                        db.upsertContacts(pulledContacts)
                        pulledCount = pulledContacts.size
                    }
                }
            }
        } catch (e: Exception) {
            return Triple(pushedCount, 0, "Pull error: ${e.message}")
        }

        return Triple(pushedCount, pulledCount, "Success")
    }

    /**
     * Uploads SMS entries to the server database.
     * Guaranteed: deleting SMS from device NEVER deletes SMS entries on the server!
     * Server never returns SMS down to overwrite device.
     */
    fun syncSms(targetUrl: String, token: String, deviceId: String): Pair<Int, String> {
        var totalPushed = 0
        var lastMsg = "All SMS already synced"

        try {
            while (true) {
                val unsynced = db.getSmsRecords(filter = "UNSYNCED", limit = 500)
                if (unsynced.isEmpty()) break

                val postPayload = JSONObject().apply {
                    put("token", token)
                    put("action", "sync_sms")
                    put("device_id", deviceId)
                    val smsArr = JSONArray()
                    for (s in unsynced) {
                        smsArr.put(
                            JSONObject().apply {
                                put("id", s.id.toString())
                                put("address", s.address)
                                put("body", s.body)
                                put("date", s.date)
                                put("type", if (s.type == 1) "INBOX" else "SENT")
                                put("device_id", deviceId)
                            }
                        )
                    }
                    put("sms_records", smsArr)
                }

                val request = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Manager-Android")
                    .header("Content-Type", "application/json")
                    .post(postPayload.toString().toRequestBody(JSON_MEDIA))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        db.markSmsSynced(unsynced.map { it.id })
                        totalPushed += unsynced.size
                        lastMsg = "Synced $totalPushed SMS to cloud database"
                    } else {
                        return Pair(totalPushed, "HTTP Error ${response.code}")
                    }
                }

                if (unsynced.size < 500) break
            }
        } catch (e: Exception) {
            return Pair(totalPushed, "SMS sync error: ${e.message}")
        }

        return Pair(totalPushed, lastMsg)
    }
}
