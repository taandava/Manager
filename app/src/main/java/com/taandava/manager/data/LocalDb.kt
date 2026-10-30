package com.taandava.manager.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.taandava.manager.util.ContactValidator
import com.taandava.manager.util.NameDisambiguator
import com.taandava.manager.util.PhoneUtils
import com.taandava.manager.util.SmsClassifier

@Suppress("UseKtx")
class LocalDb private constructor(context: Context) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    private val appContext = context.applicationContext

    companion object {
        private const val DATABASE_NAME = "contact_finder.db"
        private const val DATABASE_VERSION = 5

        const val TABLE_CONTACTS = "contacts"
        const val COL_C_ID = "id"
        const val COL_C_NAME = "name"
        const val COL_C_PHONE = "phone"
        const val COL_C_DETAILS = "details"
        const val COL_C_SAVED = "saved"
        const val COL_C_EDITED = "edited"
        const val COL_C_DELETED = "deleted"
        const val COL_C_CREATED_AT = "created_at"
        const val COL_C_UPDATED_AT = "updated_at"
        const val COL_C_SOURCE = "source"
        const val COL_C_CLOUD_ID = "cloud_id"
        const val COL_C_SYNC_STATUS = "sync_status"
        const val COL_C_DEVICE_ID = "device_id"

        const val TABLE_SMS = "sms_ledger"
        const val COL_S_ID = "id"
        const val COL_S_ADDRESS = "address"
        const val COL_S_NORMALIZED = "normalized_number"
        const val COL_S_BODY = "body"
        const val COL_S_DATE = "date"
        const val COL_S_TYPE = "type"
        const val COL_S_IS_SYNCED = "is_synced"
        const val COL_S_SYNCED_AT = "synced_at"
        const val COL_S_DEVICE_ID = "device_id"
        const val COL_S_DEVICE_SMS_ID = "device_sms_id"
        const val COL_S_CATEGORY = "category"
        const val COL_S_SENDER_TYPE = "sender_type"
        const val COL_S_OTP = "extracted_otp"
        const val COL_S_AMOUNT = "extracted_amount"
        const val COL_S_IS_DEBIT = "is_debit"

        // Deleted SMS Blacklist Table: prevents deleted device SMS from ever coming back
        const val TABLE_DELETED_SMS = "deleted_device_sms"
        const val COL_D_SMS_ID = "device_sms_id"
        const val COL_D_DELETED_AT = "deleted_at"

        // Deleted Contacts Tombstone & Blacklist Table: prevents deleted contacts from ever coming back
        const val TABLE_DELETED_CONTACTS = "deleted_contacts"
        const val COL_DC_ID = "id"
        const val COL_DC_PHONE = "phone"
        const val COL_DC_NORMALIZED = "normalized_number"
        const val COL_DC_DELETED_AT = "deleted_at"
        const val COL_DC_IS_SYNCED = "is_synced"

        @Volatile
        private var instance: LocalDb? = null

        fun getInstance(context: Context): LocalDb {
            return instance ?: synchronized(this) {
                instance ?: LocalDb(context).also { instance = it }
            }
        }
    }

    init {
        try {
            setWriteAheadLoggingEnabled(true)
            cleanupCorruptedPhoneBlacklist()
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error enabling WAL in init: ${e.message}")
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        try {
            db.enableWriteAheadLogging()
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error enabling WAL in onConfigure: ${e.message}")
        }
    }

    private data class CachedSmsStats(
        val totalSms: Int,
        val syncedSms: Int,
        val otpCount: Int,
        val txnCount: Int
    )

    @Volatile
    private var cachedSmsStats: CachedSmsStats? = null

    fun invalidateSmsStatsCache() {
        cachedSmsStats = null
    }

    override fun onCreate(db: SQLiteDatabase) {
        ensureSchema(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureSchema(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ensureSchema(db)
    }

    private fun ensureSchema(db: SQLiteDatabase) {
        try {
            // Contacts Table
            val createContacts = """
                CREATE TABLE IF NOT EXISTS $TABLE_CONTACTS (
                    $COL_C_ID TEXT PRIMARY KEY,
                    $COL_C_NAME TEXT,
                    $COL_C_PHONE TEXT,
                    $COL_C_DETAILS TEXT,
                    $COL_C_SAVED INTEGER DEFAULT 0,
                    $COL_C_EDITED INTEGER DEFAULT 0,
                    $COL_C_DELETED INTEGER DEFAULT 0,
                    $COL_C_CREATED_AT INTEGER,
                    $COL_C_UPDATED_AT INTEGER,
                    $COL_C_SOURCE TEXT,
                    $COL_C_CLOUD_ID TEXT,
                    $COL_C_SYNC_STATUS TEXT,
                    $COL_C_DEVICE_ID TEXT
                )
            """.trimIndent()
            db.execSQL(createContacts)

            try { db.execSQL("ALTER TABLE $TABLE_CONTACTS ADD COLUMN $COL_C_DEVICE_ID TEXT") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_phone ON $TABLE_CONTACTS($COL_C_PHONE)") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_name ON $TABLE_CONTACTS($COL_C_NAME)") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_updated ON $TABLE_CONTACTS($COL_C_UPDATED_AT)") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_saved ON $TABLE_CONTACTS($COL_C_SAVED)") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_deleted ON $TABLE_CONTACTS($COL_C_DELETED)") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_device ON $TABLE_CONTACTS($COL_C_DEVICE_ID)") } catch (_: Exception) {}

            // SMS Ledger Table
            val createSms = """
                CREATE TABLE IF NOT EXISTS $TABLE_SMS (
                    $COL_S_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_S_ADDRESS TEXT NOT NULL,
                    $COL_S_NORMALIZED TEXT NOT NULL,
                    $COL_S_BODY TEXT NOT NULL,
                    $COL_S_DATE INTEGER NOT NULL,
                    $COL_S_TYPE INTEGER NOT NULL DEFAULT 1,
                    $COL_S_IS_SYNCED INTEGER NOT NULL DEFAULT 0,
                    $COL_S_SYNCED_AT INTEGER,
                    $COL_S_DEVICE_ID TEXT NOT NULL DEFAULT '',
                    $COL_S_DEVICE_SMS_ID INTEGER DEFAULT 0,
                    $COL_S_CATEGORY TEXT NOT NULL DEFAULT 'GENERAL',
                    $COL_S_SENDER_TYPE TEXT NOT NULL DEFAULT 'Unknown',
                    $COL_S_OTP TEXT,
                    $COL_S_AMOUNT TEXT,
                    $COL_S_IS_DEBIT INTEGER,
                    UNIQUE($COL_S_ADDRESS, $COL_S_DATE, $COL_S_BODY)
                )
            """.trimIndent()
            db.execSQL(createSms)
            try { db.execSQL("ALTER TABLE $TABLE_SMS ADD COLUMN $COL_S_DEVICE_SMS_ID INTEGER DEFAULT 0") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_SMS ADD COLUMN $COL_S_CATEGORY TEXT NOT NULL DEFAULT 'GENERAL'") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_SMS ADD COLUMN $COL_S_SENDER_TYPE TEXT NOT NULL DEFAULT 'Unknown'") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_SMS ADD COLUMN $COL_S_OTP TEXT") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_SMS ADD COLUMN $COL_S_AMOUNT TEXT") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE_SMS ADD COLUMN $COL_S_IS_DEBIT INTEGER") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_sms_date ON $TABLE_SMS($COL_S_DATE DESC)") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_sms_norm ON $TABLE_SMS($COL_S_NORMALIZED)") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_sms_type ON $TABLE_SMS($COL_S_TYPE)") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_sms_synced ON $TABLE_SMS($COL_S_IS_SYNCED)") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_sms_device_sms_id ON $TABLE_SMS($COL_S_DEVICE_SMS_ID)") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_sms_category ON $TABLE_SMS($COL_S_CATEGORY)") } catch (_: Exception) {}

            // Deleted SMS Table
            val createDeletedSms = """
                CREATE TABLE IF NOT EXISTS $TABLE_DELETED_SMS (
                    $COL_D_SMS_ID INTEGER PRIMARY KEY,
                    $COL_D_DELETED_AT INTEGER
                )
            """.trimIndent()
            db.execSQL(createDeletedSms)

            // Deleted Contacts Blacklist / Tombstone Table
            val createDeletedContacts = """
                CREATE TABLE IF NOT EXISTS $TABLE_DELETED_CONTACTS (
                    $COL_DC_ID TEXT PRIMARY KEY,
                    $COL_DC_PHONE TEXT NOT NULL DEFAULT '',
                    $COL_DC_NORMALIZED TEXT NOT NULL DEFAULT '',
                    $COL_DC_DELETED_AT INTEGER NOT NULL,
                    $COL_DC_IS_SYNCED INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent()
            db.execSQL(createDeletedContacts)
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_dc_norm ON $TABLE_DELETED_CONTACTS($COL_DC_NORMALIZED)") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_dc_synced ON $TABLE_DELETED_CONTACTS($COL_DC_IS_SYNCED)") } catch (_: Exception) {}

            // Clean legacy duplicate device string saved into details column
            try {
                db.execSQL("UPDATE $TABLE_CONTACTS SET $COL_C_DETAILS = '' WHERE $COL_C_DETAILS LIKE 'Device: %'")
            } catch (_: Exception) {}

            // Purge invalid number contacts and mismatched phone entries from existing database
            try {
                purgeInvalidContactsInDb(db)
            } catch (e: Exception) {
                android.util.Log.e("LocalDb", "Error purging invalid contacts during ensureSchema: ${e.message}", e)
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error during ensureSchema: ${e.message}", e)
        }
    }

    /**
     * Purges contacts where name is derived from SMS (e.g. AD-AXISBK-S, VM-HDFCBK, SMS headers),
     * or where name is just numbers/references a completely different phone number.
     * Only genuine contact names from address book are retained.
     */
    fun purgeInvalidContactsInDb(db: SQLiteDatabase): Int {
        var deletedCount = 0
        val toDeleteIds = mutableListOf<String>()

        db.rawQuery("SELECT $COL_C_ID, $COL_C_NAME, $COL_C_PHONE, $COL_C_DETAILS, $COL_C_SOURCE FROM $TABLE_CONTACTS", null).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(COL_C_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(COL_C_NAME)
            val phoneIdx = cursor.getColumnIndexOrThrow(COL_C_PHONE)
            val detailsIdx = cursor.getColumnIndex(COL_C_DETAILS)
            val sourceIdx = cursor.getColumnIndex(COL_C_SOURCE)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: ""
                val phone = cursor.getString(phoneIdx) ?: ""
                val details = if (detailsIdx >= 0) cursor.getString(detailsIdx) ?: "" else ""
                val source = if (sourceIdx >= 0) cursor.getString(sourceIdx) ?: "" else ""

                if (!ContactValidator.isValidContact(name, phone, details, source)) {
                    toDeleteIds.add(id)
                }
            }
        }

        if (toDeleteIds.isNotEmpty()) {
            db.beginTransaction()
            try {
                val now = System.currentTimeMillis()
                for (chunk in toDeleteIds.chunked(500)) {
                    val placeholders = chunk.joinToString(",") { "?" }
                    // Mark as deleted and pending sync so Hostinger cloud CSV removes them as well
                    val updateArgs = mutableListOf<String>()
                    updateArgs.add(now.toString())
                    updateArgs.addAll(chunk)
                    db.execSQL(
                        "UPDATE $TABLE_CONTACTS SET $COL_C_DELETED = 1, $COL_C_EDITED = 1, $COL_C_SYNC_STATUS = 'PENDING', $COL_C_UPDATED_AT = ? WHERE $COL_C_ID IN ($placeholders)",
                        updateArgs.toTypedArray()
                    )
                    deletedCount += chunk.size
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            android.util.Log.i("LocalDb", "Purged $deletedCount SMS/invalid/mismatched number contacts from database")
        }
        return deletedCount
    }

    @Synchronized
    fun purgeInvalidContacts(): Int {
        return purgeInvalidContactsInDb(writableDatabase)
    }

    // =========================================================================
    // CONTACT OPERATIONS
    // =========================================================================

    @Synchronized
    fun upsertContact(c: ContactEntity) {
        if (!ContactValidator.isValidContact(c.name, c.phone, c.details, c.source)) {
            return // REJECT: Invalid, synthetic SMS, or mismatched number contact entry
        }
        if (!c.deleted && getDeletedContactIds().contains(c.id)) {
            return // REJECT: Contact was explicitly deleted by the user! Prevent resurrection!
        }
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_C_ID, c.id)
            put(COL_C_NAME, c.name)
            put(COL_C_PHONE, c.phone)
            put(COL_C_DETAILS, c.details)
            put(COL_C_SAVED, if (c.saved) 1 else 0)
            put(COL_C_EDITED, if (c.edited) 1 else 0)
            put(COL_C_DELETED, if (c.deleted) 1 else 0)
            put(COL_C_CREATED_AT, c.createdAt)
            put(COL_C_UPDATED_AT, c.updatedAt)
            put(COL_C_SOURCE, c.source)
            put(COL_C_CLOUD_ID, c.cloudId)
            put(COL_C_SYNC_STATUS, c.syncStatus)
            put(COL_C_DEVICE_ID, c.deviceId)
        }
        db.insertWithOnConflict(TABLE_CONTACTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun upsertContacts(list: List<ContactEntity>) {
        if (list.isEmpty()) return
        val db = writableDatabase
        val blacklistedIds = getDeletedContactIds()
        for (chunk in list.chunked(500)) {
            db.beginTransaction()
            try {
                for (c in chunk) {
                    if (!ContactValidator.isValidContact(c.name, c.phone, c.details, c.source)) {
                        continue // REJECT: Invalid, synthetic SMS, or mismatched number contact entry
                    }
                    if (!c.deleted && blacklistedIds.contains(c.id)) {
                        continue // REJECT: Contact was explicitly deleted by the user! Prevent resurrection!
                    }
                    val cv = ContentValues().apply {
                        put(COL_C_ID, c.id)
                        put(COL_C_NAME, c.name)
                        put(COL_C_PHONE, c.phone)
                        put(COL_C_DETAILS, c.details)
                        put(COL_C_SAVED, if (c.saved) 1 else 0)
                        put(COL_C_EDITED, if (c.edited) 1 else 0)
                        put(COL_C_DELETED, if (c.deleted) 1 else 0)
                        put(COL_C_CREATED_AT, c.createdAt)
                        put(COL_C_UPDATED_AT, c.updatedAt)
                        put(COL_C_SOURCE, c.source)
                        put(COL_C_CLOUD_ID, c.cloudId)
                        put(COL_C_SYNC_STATUS, c.syncStatus)
                        put(COL_C_DEVICE_ID, c.deviceId)
                    }
                    db.insertWithOnConflict(TABLE_CONTACTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    @Synchronized
    fun getAllContacts(savedOnly: Boolean = false, query: String = ""): List<ContactEntity> {
        val list = mutableListOf<ContactEntity>()
        try {
            val db = readableDatabase
            val selection = StringBuilder("$COL_C_DELETED = 0")
            if (savedOnly) {
                selection.append(" AND $COL_C_SAVED = 1")
            }

            val cursor = db.query(TABLE_CONTACTS, null, selection.toString(), null, null, null, "$COL_C_NAME COLLATE NOCASE ASC")
            cursor.use {
                val qNorm = PhoneUtils.normalizeSearch(query)
                val idIdx = it.getColumnIndex(COL_C_ID)
                val nameIdx = it.getColumnIndex(COL_C_NAME)
                val phoneIdx = it.getColumnIndex(COL_C_PHONE)
                val detailsIdx = it.getColumnIndex(COL_C_DETAILS)
                val savedIdx = it.getColumnIndex(COL_C_SAVED)
                val editedIdx = it.getColumnIndex(COL_C_EDITED)
                val deletedIdx = it.getColumnIndex(COL_C_DELETED)
                val createdAtIdx = it.getColumnIndex(COL_C_CREATED_AT)
                val updatedAtIdx = it.getColumnIndex(COL_C_UPDATED_AT)
                val sourceIdx = it.getColumnIndex(COL_C_SOURCE)
                val cloudIdIdx = it.getColumnIndex(COL_C_CLOUD_ID)
                val syncStatusIdx = it.getColumnIndex(COL_C_SYNC_STATUS)
                val devIdIdx = it.getColumnIndex(COL_C_DEVICE_ID)

                while (it.moveToNext()) {
                    val c = cursorToContactFast(it, idIdx, nameIdx, phoneIdx, detailsIdx, savedIdx, editedIdx, deletedIdx, createdAtIdx, updatedAtIdx, sourceIdx, cloudIdIdx, syncStatusIdx, devIdIdx)
                    if (com.taandava.manager.util.ContactValidator.shouldRejectContact(c.name, c.phone, c.details, c.source)) {
                        continue // Guardrail filter
                    }
                    if (qNorm.isBlank() || c.getSearchable().contains(qNorm) || PhoneUtils.normalizePhone(c.phone).contains(qNorm)) {
                        list.add(c)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in getAllContacts: ${e.message}", e)
        }
        return list
    }

    @Synchronized
    fun getAllContactsRaw(): List<ContactEntity> {
        val list = mutableListOf<ContactEntity>()
        try {
            val db = readableDatabase
            val cursor = db.query(TABLE_CONTACTS, null, null, null, null, null, null)
            cursor.use {
                val idIdx = it.getColumnIndex(COL_C_ID)
                val nameIdx = it.getColumnIndex(COL_C_NAME)
                val phoneIdx = it.getColumnIndex(COL_C_PHONE)
                val detailsIdx = it.getColumnIndex(COL_C_DETAILS)
                val savedIdx = it.getColumnIndex(COL_C_SAVED)
                val editedIdx = it.getColumnIndex(COL_C_EDITED)
                val deletedIdx = it.getColumnIndex(COL_C_DELETED)
                val createdAtIdx = it.getColumnIndex(COL_C_CREATED_AT)
                val updatedAtIdx = it.getColumnIndex(COL_C_UPDATED_AT)
                val sourceIdx = it.getColumnIndex(COL_C_SOURCE)
                val cloudIdIdx = it.getColumnIndex(COL_C_CLOUD_ID)
                val syncStatusIdx = it.getColumnIndex(COL_C_SYNC_STATUS)
                val devIdIdx = it.getColumnIndex(COL_C_DEVICE_ID)

                while (it.moveToNext()) {
                    val c = cursorToContactFast(it, idIdx, nameIdx, phoneIdx, detailsIdx, savedIdx, editedIdx, deletedIdx, createdAtIdx, updatedAtIdx, sourceIdx, cloudIdIdx, syncStatusIdx, devIdIdx)
                    if (com.taandava.manager.util.ContactValidator.shouldRejectContact(c.name, c.phone, c.details, c.source)) {
                        continue // Guardrail filter
                    }
                    list.add(c)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in getAllContactsRaw: ${e.message}", e)
        }
        return list
    }

    @Synchronized
    fun getContactById(id: String): ContactEntity? {
        try {
            val db = readableDatabase
            db.query(TABLE_CONTACTS, null, "$COL_C_ID = ?", arrayOf(id), null, null, null).use {
                if (it.moveToNext()) {
                    return cursorToContact(it)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in getContactById: ${e.message}", e)
        }
        return null
    }

    @Synchronized
    fun cleanupCorruptedPhoneBlacklist() {
        try {
            // Any phone that has an active (non-deleted) contact in TABLE_CONTACTS MUST NOT be in the blacklist!
            writableDatabase.execSQL("""
                UPDATE $TABLE_DELETED_CONTACTS 
                SET $COL_DC_NORMALIZED = '' 
                WHERE $COL_DC_NORMALIZED != '' 
                  AND $COL_DC_NORMALIZED IN (
                      SELECT $COL_C_PHONE FROM $TABLE_CONTACTS WHERE $COL_C_DELETED = 0
                  )
            """)
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in cleanupCorruptedPhoneBlacklist: ${e.message}")
        }
    }

    @Synchronized
    fun recordDeletedContacts(contacts: List<ContactEntity>, isSynced: Boolean = false, blacklistPhone: Boolean = true) {
        if (contacts.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for (c in contacts) {
                val cv = ContentValues().apply {
                    put(COL_DC_ID, c.id)
                    put(COL_DC_PHONE, c.phone)
                    put(COL_DC_NORMALIZED, if (blacklistPhone) PhoneUtils.normalizePhone(c.phone) else "")
                    put(COL_DC_DELETED_AT, now)
                    put(COL_DC_IS_SYNCED, if (isSynced) 1 else 0)
                }
                db.insertWithOnConflict(TABLE_DELETED_CONTACTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in recordDeletedContacts: ${e.message}", e)
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun recordDeletedContact(id: String, phone: String, isSynced: Boolean = false, blacklistPhone: Boolean = true) {
        try {
            val cv = ContentValues().apply {
                put(COL_DC_ID, id)
                put(COL_DC_PHONE, phone)
                put(COL_DC_NORMALIZED, if (blacklistPhone) PhoneUtils.normalizePhone(phone) else "")
                put(COL_DC_DELETED_AT, System.currentTimeMillis())
                put(COL_DC_IS_SYNCED, if (isSynced) 1 else 0)
            }
            writableDatabase.insertWithOnConflict(TABLE_DELETED_CONTACTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in recordDeletedContact: ${e.message}", e)
        }
    }

    @Synchronized
    fun getActiveNormalizedPhones(): Set<String> {
        val set = HashSet<String>()
        try {
            readableDatabase.rawQuery("SELECT $COL_C_PHONE FROM $TABLE_CONTACTS WHERE $COL_C_DELETED = 0", null).use { c ->
                while (c.moveToNext()) {
                    val p = c.getString(0)
                    if (!p.isNullOrBlank()) {
                        val norm = PhoneUtils.normalizePhone(p)
                        if (norm.isNotBlank()) set.add(norm)
                    }
                }
            }
        } catch (_: Exception) {}
        return set
    }

    @Synchronized
    fun getContactTimestampsMap(): Map<String, Long> {
        val map = HashMap<String, Long>()
        try {
            readableDatabase.rawQuery("SELECT $COL_C_ID, $COL_C_UPDATED_AT FROM $TABLE_CONTACTS WHERE $COL_C_DELETED = 0", null).use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    if (!id.isNullOrBlank()) {
                        map[id] = c.getLong(1)
                    }
                }
            }
        } catch (_: Exception) {}
        return map
    }

    @Synchronized
    fun getDeletedContactIds(): Set<String> {
        val set = mutableSetOf<String>()
        try {
            readableDatabase.rawQuery("SELECT $COL_DC_ID FROM $TABLE_DELETED_CONTACTS", null).use { c ->
                while (c.moveToNext()) {
                    set.add(c.getString(0))
                }
            }
        } catch (_: Exception) {}
        return set
    }

    @Synchronized
    fun getDeletedContactPhones(): Set<String> {
        val set = mutableSetOf<String>()
        try {
            readableDatabase.rawQuery("SELECT $COL_DC_NORMALIZED FROM $TABLE_DELETED_CONTACTS WHERE $COL_DC_NORMALIZED != ''", null).use { c ->
                while (c.moveToNext()) {
                    set.add(c.getString(0))
                }
            }
        } catch (_: Exception) {}
        return set
    }

    @Synchronized
    fun getPendingDeletedContactIds(): List<String> {
        val list = mutableListOf<String>()
        try {
            readableDatabase.rawQuery("SELECT $COL_DC_ID FROM $TABLE_DELETED_CONTACTS WHERE $COL_DC_IS_SYNCED = 0", null).use { c ->
                while (c.moveToNext()) {
                    list.add(c.getString(0))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    @Synchronized
    fun markDeletedContactsSynced(ids: List<String>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (chunk in ids.chunked(400)) {
                val placeholders = chunk.joinToString(",") { "?" }
                val cv = ContentValues().apply {
                    put(COL_DC_IS_SYNCED, 1)
                }
                db.update(TABLE_DELETED_CONTACTS, cv, "$COL_DC_ID IN ($placeholders)", chunk.toTypedArray())
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in markDeletedContactsSynced: ${e.message}", e)
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun deleteContact(id: String, soft: Boolean = true): ContactEntity? {
        try {
            val contact = getContactById(id) ?: return null
            val db = writableDatabase
            if (soft) {
                val cv = ContentValues().apply {
                    put(COL_C_DELETED, 1)
                    put(COL_C_EDITED, 1)
                    put(COL_C_SYNC_STATUS, "PENDING")
                    put(COL_C_UPDATED_AT, System.currentTimeMillis())
                }
                db.update(TABLE_CONTACTS, cv, "$COL_C_ID = ?", arrayOf(id))
            } else {
                db.delete(TABLE_CONTACTS, "$COL_C_ID = ?", arrayOf(id))
            }
            recordDeletedContact(contact.id, contact.phone, isSynced = false)
            return contact
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in deleteContact: ${e.message}", e)
            return null
        }
    }

    @Synchronized
    fun deleteContacts(ids: List<String>, soft: Boolean = true): List<ContactEntity> {
        if (ids.isEmpty()) return emptyList()
        val deleted = mutableListOf<ContactEntity>()
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (chunk in ids.chunked(400)) {
                val placeholders = chunk.joinToString(",") { "?" }
                db.rawQuery("SELECT * FROM $TABLE_CONTACTS WHERE $COL_C_ID IN ($placeholders)", chunk.toTypedArray()).use { c ->
                    while (c.moveToNext()) {
                        deleted.add(cursorToContact(c))
                    }
                }
                if (soft) {
                    val cv = ContentValues().apply {
                        put(COL_C_DELETED, 1)
                        put(COL_C_EDITED, 1)
                        put(COL_C_SYNC_STATUS, "PENDING")
                        put(COL_C_UPDATED_AT, System.currentTimeMillis())
                    }
                    db.update(TABLE_CONTACTS, cv, "$COL_C_ID IN ($placeholders)", chunk.toTypedArray())
                } else {
                    db.delete(TABLE_CONTACTS, "$COL_C_ID IN ($placeholders)", chunk.toTypedArray())
                }
            }
            recordDeletedContacts(deleted, isSynced = false)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in deleteContacts: ${e.message}", e)
        } finally {
            db.endTransaction()
        }
        return deleted
    }

    @Synchronized
    fun starContacts(ids: List<String>, star: Boolean): Int {
        if (ids.isEmpty()) return 0
        val db = writableDatabase
        var count = 0
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put(COL_C_SAVED, if (star) 1 else 0)
                put(COL_C_EDITED, 1)
                put(COL_C_SYNC_STATUS, "PENDING")
                put(COL_C_UPDATED_AT, System.currentTimeMillis())
            }
            for (chunk in ids.chunked(400)) {
                val placeholders = chunk.joinToString(",") { "?" }
                count += db.update(TABLE_CONTACTS, cv, "$COL_C_ID IN ($placeholders)", chunk.toTypedArray())
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    @Synchronized
    fun clearAllContacts() {
        writableDatabase.delete(TABLE_CONTACTS, null, null)
    }

    @Synchronized
    fun getDuplicateGroups(): List<DuplicateGroup> {
        val out = mutableListOf<DuplicateGroup>()
        try {
            val all = getAllContacts(savedOnly = false, query = "")
            val map = LinkedHashMap<String, MutableList<ContactEntity>>()
            for (c in all) {
                if (c.phone.isBlank()) continue
                val norm = PhoneUtils.normalizePhone(c.phone)
                // Strict check: only exact same valid phone numbers (at least 10 digits)
                if (norm.length >= 10 && norm.matches(Regex("\\+?[0-9]{10,15}"))) {
                    map.getOrPut(norm) { mutableListOf() }.add(c)
                }
            }
            for ((phone, group) in map) {
                // Ensure all contacts in group share the exact same normalized phone number
                val exactMatches = group.filter { PhoneUtils.normalizePhone(it.phone) == phone }
                if (exactMatches.size > 1) {
                    out.add(DuplicateGroup(phone, exactMatches))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in getDuplicateGroups: ${e.message}", e)
        }
        return out
    }

    @Synchronized
    fun smartMergeGroup(phone: String, config: SmartMergeConfig = SmartMergeConfig()): SmartMergeResult? {
        try {
            val dupes = getDuplicateGroups().firstOrNull { it.phone == phone } ?: return null
            if (dupes.contacts.size <= 1) return null

            val list = dupes.contacts
            val survivor = SmartMergeConfig.resolveSurvivor(list, phone, config)
            val toDelete = list.filter { it.id != survivor.id }

            val db = writableDatabase
            db.beginTransaction()
            try {
                val now = System.currentTimeMillis()
                val cv = ContentValues().apply {
                    put(COL_C_ID, survivor.id)
                    put(COL_C_NAME, survivor.name)
                    put(COL_C_PHONE, survivor.phone)
                    put(COL_C_DETAILS, survivor.details)
                    put(COL_C_SAVED, if (survivor.saved) 1 else 0)
                    put(COL_C_EDITED, 1)
                    put(COL_C_DELETED, 0)
                    put(COL_C_CREATED_AT, survivor.createdAt)
                    put(COL_C_UPDATED_AT, now)
                    put(COL_C_SOURCE, survivor.source)
                    put(COL_C_CLOUD_ID, survivor.cloudId)
                    put(COL_C_SYNC_STATUS, "PENDING")
                    put(COL_C_DEVICE_ID, survivor.deviceId)
                }
                db.insertWithOnConflict(TABLE_CONTACTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

                // Soft-delete duplicate contacts so pending sync will push their deletion to cloud!
                for (del in toDelete) {
                    val delCv = ContentValues().apply {
                        put(COL_C_DELETED, 1)
                        put(COL_C_EDITED, 1)
                        put(COL_C_SYNC_STATUS, "PENDING")
                        put(COL_C_UPDATED_AT, now)
                    }
                    db.update(TABLE_CONTACTS, delCv, "$COL_C_ID = ?", arrayOf(del.id))
                }

                // Record in blacklist tombstone table (blacklistPhone = false because survivor still has this phone!)
                recordDeletedContacts(toDelete, isSynced = false, blacklistPhone = false)

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            return SmartMergeResult(survivor, toDelete, phone)
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in smartMergeGroup: ${e.message}", e)
            return null
        }
    }

    @Synchronized
    fun smartMergeAllGroups(config: SmartMergeConfig = SmartMergeConfig()): List<SmartMergeResult> {
        val dupes = getDuplicateGroups()
        if (dupes.isEmpty()) return emptyList()
        val results = mutableListOf<SmartMergeResult>()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for (g in dupes) {
                if (g.contacts.size <= 1) continue
                val list = g.contacts
                val survivor = SmartMergeConfig.resolveSurvivor(list, g.phone, config)
                val toDelete = list.filter { it.id != survivor.id }

                val cv = ContentValues().apply {
                    put(COL_C_ID, survivor.id)
                    put(COL_C_NAME, survivor.name)
                    put(COL_C_PHONE, survivor.phone)
                    put(COL_C_DETAILS, survivor.details)
                    put(COL_C_SAVED, if (survivor.saved) 1 else 0)
                    put(COL_C_EDITED, 1)
                    put(COL_C_DELETED, 0)
                    put(COL_C_CREATED_AT, survivor.createdAt)
                    put(COL_C_UPDATED_AT, now)
                    put(COL_C_SOURCE, survivor.source)
                    put(COL_C_CLOUD_ID, survivor.cloudId)
                    put(COL_C_SYNC_STATUS, "PENDING")
                    put(COL_C_DEVICE_ID, survivor.deviceId)
                }
                db.insertWithOnConflict(TABLE_CONTACTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

                for (del in toDelete) {
                    val delCv = ContentValues().apply {
                        put(COL_C_DELETED, 1)
                        put(COL_C_EDITED, 1)
                        put(COL_C_SYNC_STATUS, "PENDING")
                        put(COL_C_UPDATED_AT, now)
                    }
                    db.update(TABLE_CONTACTS, delCv, "$COL_C_ID = ?", arrayOf(del.id))
                }

                results.add(SmartMergeResult(survivor, toDelete, g.phone))
            }

            val allDeleted = results.flatMap { it.deletedContacts }
            recordDeletedContacts(allDeleted, isSynced = false, blacklistPhone = false)

            db.setTransactionSuccessful()
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in smartMergeAllGroups: ${e.message}", e)
        } finally {
            db.endTransaction()
        }
        return results
    }

    @Synchronized
    fun cleanAllAppDuplicates(): List<SmartMergeResult> {
        return smartMergeAllGroups(SmartMergeConfig())
    }

    // =========================================================================
    // SAME NAME, MULTIPLE NUMBERS DISAMBIGUATION
    // =========================================================================

    @Synchronized
    fun getSameNameDifferentNumberGroups(): List<SameNameGroup> {
        val out = mutableListOf<SameNameGroup>()
        try {
            val all = getAllContacts(savedOnly = false, query = "")
            // Group by normalized name key (stripping prefixes and suffixes, case-insensitive)
            val map = LinkedHashMap<String, MutableList<ContactEntity>>()
            for (c in all) {
                val normPhone = PhoneUtils.normalizePhone(c.phone)
                if (normPhone.length >= 10 && normPhone.matches(Regex("\\+?[0-9]{10,15}"))) {
                    val key = NameDisambiguator.extractNameKey(c.name)
                    if (key.isNotBlank()) {
                        map.getOrPut(key) { mutableListOf() }.add(c)
                    }
                }
            }

            for ((key, list) in map) {
                // Must have multiple contacts with DIFFERENT phone numbers!
                val byPhone = list.groupBy { PhoneUtils.normalizePhone(it.phone) }
                if (byPhone.size > 1) {
                    // Pick the most recent contact representation per distinct phone number
                    val distinctPhoneContacts = byPhone.values.map { phoneGroup ->
                        phoneGroup.maxByOrNull { it.updatedAt } ?: phoneGroup[0]
                    }
                    val displayName = distinctPhoneContacts.firstOrNull { !NameDisambiguator.isPrefix(it.name) }?.name
                        ?: distinctPhoneContacts[0].name
                    out.add(SameNameGroup(key, displayName, distinctPhoneContacts))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in getSameNameDifferentNumberGroups: ${e.message}", e)
        }
        return out
    }

    @Synchronized
    fun disambiguateSameNameGroup(
        nameKey: String,
        applySentenceCase: Boolean = true
    ): List<ContactEntity> {
        val groups = getSameNameDifferentNumberGroups()
        val targetGroup = groups.firstOrNull { it.nameKey == nameKey } ?: return emptyList()
        val contacts = targetGroup.contacts
        if (contacts.size <= 1) return emptyList()

        val updatedList = mutableListOf<ContactEntity>()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            contacts.forEachIndexed { index, contact ->
                val newName = NameDisambiguator.disambiguateName(
                    rawName = contact.name,
                    extraRepeats = index, // 0 for 1st, 1 for 2nd, 2 for 3rd...
                    applySentenceCase = applySentenceCase
                )
                if (newName != contact.name) {
                    val updated = contact.copy(
                        name = newName,
                        edited = true,
                        updatedAt = now,
                        syncStatus = "PENDING"
                    )
                    val cv = ContentValues().apply {
                        put(COL_C_NAME, updated.name)
                        put(COL_C_EDITED, 1)
                        put(COL_C_UPDATED_AT, now)
                        put(COL_C_SYNC_STATUS, "PENDING")
                    }
                    db.update(TABLE_CONTACTS, cv, "$COL_C_ID = ?", arrayOf(contact.id))
                    updatedList.add(updated)
                } else {
                    updatedList.add(contact)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return updatedList
    }

    @Synchronized
    fun disambiguateAllSameNameGroups(
        applySentenceCase: Boolean = true
    ): List<ContactEntity> {
        val groups = getSameNameDifferentNumberGroups()
        if (groups.isEmpty()) return emptyList()
        val allUpdated = mutableListOf<ContactEntity>()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for (targetGroup in groups) {
                val contacts = targetGroup.contacts
                contacts.forEachIndexed { index, contact ->
                    val newName = NameDisambiguator.disambiguateName(
                        rawName = contact.name,
                        extraRepeats = index,
                        applySentenceCase = applySentenceCase
                    )
                    if (newName != contact.name) {
                        val updated = contact.copy(
                            name = newName,
                            edited = true,
                            updatedAt = now,
                            syncStatus = "PENDING"
                        )
                        val cv = ContentValues().apply {
                            put(COL_C_NAME, updated.name)
                            put(COL_C_EDITED, 1)
                            put(COL_C_UPDATED_AT, now)
                            put(COL_C_SYNC_STATUS, "PENDING")
                        }
                        db.update(TABLE_CONTACTS, cv, "$COL_C_ID = ?", arrayOf(contact.id))
                        allUpdated.add(updated)
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return allUpdated
    }

    private fun cursorToContactFast(
        c: Cursor,
        idIdx: Int,
        nameIdx: Int,
        phoneIdx: Int,
        detailsIdx: Int,
        savedIdx: Int,
        editedIdx: Int,
        deletedIdx: Int,
        createdAtIdx: Int,
        updatedAtIdx: Int,
        sourceIdx: Int,
        cloudIdIdx: Int,
        syncStatusIdx: Int,
        devIdIdx: Int
    ): ContactEntity {
        val devId = if (devIdIdx >= 0) c.getString(devIdIdx) ?: "" else ""
        val rawDetails = if (detailsIdx >= 0) c.getString(detailsIdx) ?: "" else ""
        val cleanDetails = if (rawDetails.startsWith("Device:", ignoreCase = true) &&
            (rawDetails.contains(devId, ignoreCase = true) || rawDetails.contains("SMS", ignoreCase = true) || devId.isNotBlank())
        ) {
            ""
        } else {
            rawDetails
        }
        return ContactEntity(
            id = if (idIdx >= 0) c.getString(idIdx) ?: "" else "",
            name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else "",
            phone = if (phoneIdx >= 0) c.getString(phoneIdx) ?: "" else "",
            details = cleanDetails,
            saved = savedIdx >= 0 && c.getInt(savedIdx) == 1,
            edited = editedIdx >= 0 && c.getInt(editedIdx) == 1,
            deleted = deletedIdx >= 0 && c.getInt(deletedIdx) == 1,
            createdAt = if (createdAtIdx >= 0) c.getLong(createdAtIdx) else 0L,
            updatedAt = if (updatedAtIdx >= 0) c.getLong(updatedAtIdx) else 0L,
            source = if (sourceIdx >= 0) c.getString(sourceIdx) ?: "LOCAL" else "LOCAL",
            cloudId = if (cloudIdIdx >= 0) c.getString(cloudIdIdx) ?: "" else "",
            syncStatus = if (syncStatusIdx >= 0) c.getString(syncStatusIdx) ?: "PENDING" else "PENDING",
            deviceId = devId
        )
    }

    private fun cursorToContact(c: Cursor): ContactEntity {
        return cursorToContactFast(
            c,
            c.getColumnIndex(COL_C_ID),
            c.getColumnIndex(COL_C_NAME),
            c.getColumnIndex(COL_C_PHONE),
            c.getColumnIndex(COL_C_DETAILS),
            c.getColumnIndex(COL_C_SAVED),
            c.getColumnIndex(COL_C_EDITED),
            c.getColumnIndex(COL_C_DELETED),
            c.getColumnIndex(COL_C_CREATED_AT),
            c.getColumnIndex(COL_C_UPDATED_AT),
            c.getColumnIndex(COL_C_SOURCE),
            c.getColumnIndex(COL_C_CLOUD_ID),
            c.getColumnIndex(COL_C_SYNC_STATUS),
            c.getColumnIndex(COL_C_DEVICE_ID)
        )
    }

    // =========================================================================
    // SMS OPERATIONS
    // =========================================================================

    @Synchronized
    fun insertSmsBatch(items: List<SmsRecordEntity>): Int {
        var count = 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (item in items) {
                val cv = ContentValues().apply {
                    put(COL_S_ADDRESS, item.address)
                    put(COL_S_NORMALIZED, item.normalizedNumber)
                    put(COL_S_BODY, item.body)
                    put(COL_S_DATE, item.date)
                    put(COL_S_TYPE, item.type)
                    put(COL_S_IS_SYNCED, if (item.isSynced) 1 else 0)
                    put(COL_S_DEVICE_ID, item.deviceId)
                    put(COL_S_DEVICE_SMS_ID, item.deviceSmsId)
                    put(COL_S_CATEGORY, item.category)
                    put(COL_S_SENDER_TYPE, item.senderType)
                    if (item.extractedOtp != null) put(COL_S_OTP, item.extractedOtp)
                    if (item.extractedAmount != null) put(COL_S_AMOUNT, item.extractedAmount)
                    if (item.isDebit != null) put(COL_S_IS_DEBIT, if (item.isDebit == true) 1 else 0)
                    if (item.syncedAt != null) put(COL_S_SYNCED_AT, item.syncedAt)
                }
                val rowId = db.insertWithOnConflict(TABLE_SMS, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                if (rowId != -1L) count++
            }
            db.setTransactionSuccessful()
            invalidateSmsStatsCache()
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in insertSmsBatch: ${e.message}", e)
        } finally {
            db.endTransaction()
        }
        return count
    }

    @Synchronized
    fun getSmsRecords(query: String = "", filter: String = "ALL", limit: Int = 50000): List<SmsRecordEntity> {
        val list = mutableListOf<SmsRecordEntity>()
        try {
            val db = readableDatabase
            val whereParts = mutableListOf<String>()
            val args = mutableListOf<String>()

            // 1. Basic type & category filtering directly in SQLite
            when (filter) {
                "INBOX" -> whereParts.add("$COL_S_TYPE = 1")
                "SENT" -> whereParts.add("$COL_S_TYPE = 2")
                "UNSYNCED" -> whereParts.add("$COL_S_IS_SYNCED = 0")
                "OTP" -> whereParts.add("$COL_S_CATEGORY = 'OTP'")
                "TRANSACTION" -> whereParts.add("$COL_S_CATEGORY = 'TRANSACTION'")
                "NOTIFICATION" -> whereParts.add("$COL_S_CATEGORY = 'NOTIFICATION'")
                "MOBILE" -> whereParts.add("length($COL_S_NORMALIZED) >= 10")
            }

            // 2. Query search
            if (query.isNotBlank()) {
                whereParts.add("($COL_S_BODY LIKE ? OR $COL_S_ADDRESS LIKE ? OR $COL_S_NORMALIZED LIKE ?)")
                val qLike = "%$query%"
                args.add(qLike)
                args.add(qLike)
                args.add(qLike)
            }

            val whereClause = if (whereParts.isEmpty()) "" else "WHERE " + whereParts.joinToString(" AND ")

            // 3. Saved numbers set for fast lookup
            val savedNumbers = mutableSetOf<String>()
            if (filter == "SAVED" || filter == "ALL") {
                db.rawQuery("SELECT $COL_C_PHONE FROM $TABLE_CONTACTS WHERE $COL_C_DELETED = 0", null).use { sc ->
                    while (sc.moveToNext()) {
                        val norm = PhoneUtils.normalizePhone(sc.getString(0))
                        if (norm.isNotBlank()) savedNumbers.add(norm)
                    }
                }
            }

            val rawList = mutableListOf<SmsRecordEntity>()
            db.rawQuery(
                "SELECT $COL_S_ID, $COL_S_ADDRESS, $COL_S_NORMALIZED, $COL_S_BODY, $COL_S_DATE, $COL_S_TYPE, $COL_S_IS_SYNCED, $COL_S_SYNCED_AT, $COL_S_DEVICE_ID, $COL_S_DEVICE_SMS_ID, $COL_S_CATEGORY, $COL_S_SENDER_TYPE, $COL_S_OTP, $COL_S_AMOUNT, $COL_S_IS_DEBIT FROM $TABLE_SMS $whereClause ORDER BY $COL_S_DATE DESC LIMIT $limit",
                if (args.isEmpty()) null else args.toTypedArray()
            ).use { c ->
                val idIdx = c.getColumnIndex(COL_S_ID)
                val addrIdx = c.getColumnIndex(COL_S_ADDRESS)
                val normIdx = c.getColumnIndex(COL_S_NORMALIZED)
                val bodyIdx = c.getColumnIndex(COL_S_BODY)
                val dateIdx = c.getColumnIndex(COL_S_DATE)
                val typeIdx = c.getColumnIndex(COL_S_TYPE)
                val syncedIdx = c.getColumnIndex(COL_S_IS_SYNCED)
                val syncedAtIdx = c.getColumnIndex(COL_S_SYNCED_AT)
                val devIdIdx = c.getColumnIndex(COL_S_DEVICE_ID)
                val devSmsIdIdx = c.getColumnIndex(COL_S_DEVICE_SMS_ID)
                val catIdx = c.getColumnIndex(COL_S_CATEGORY)
                val sTypeIdx = c.getColumnIndex(COL_S_SENDER_TYPE)
                val otpIdx = c.getColumnIndex(COL_S_OTP)
                val amtIdx = c.getColumnIndex(COL_S_AMOUNT)
                val isDebIdx = c.getColumnIndex(COL_S_IS_DEBIT)

                while (c.moveToNext()) {
                    val addr = if (addrIdx >= 0) c.getString(addrIdx) ?: "" else ""
                    val norm = if (normIdx >= 0) c.getString(normIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) c.getString(bodyIdx) ?: "" else ""
                    val isSaved = savedNumbers.contains(norm)
                    val cat = if (catIdx >= 0 && !c.isNull(catIdx)) c.getString(catIdx) else SmsClassifier.categorizeBody(body)
                    val sType = if (sTypeIdx >= 0 && !c.isNull(sTypeIdx)) {
                        val stored = c.getString(sTypeIdx)
                        if (isSaved) "Saved Contacts" else stored
                    } else SmsClassifier.determineSenderType(addr, isSaved)
                    val otp = if (otpIdx >= 0 && !c.isNull(otpIdx)) c.getString(otpIdx) else SmsClassifier.extractOtp(body)
                    val amtPair = if (amtIdx >= 0 && !c.isNull(amtIdx)) {
                        val a = c.getString(amtIdx)
                        val deb = if (isDebIdx >= 0 && !c.isNull(isDebIdx)) c.getInt(isDebIdx) == 1 else true
                        Pair(a, deb)
                    } else SmsClassifier.extractAmount(body)
                    val devSmsId = if (devSmsIdIdx >= 0 && !c.isNull(devSmsIdIdx)) c.getLong(devSmsIdIdx) else 0L

                    rawList.add(
                        SmsRecordEntity(
                            id = if (idIdx >= 0) c.getLong(idIdx) else 0L,
                            deviceSmsId = devSmsId,
                            address = addr,
                            normalizedNumber = norm,
                            body = body,
                            date = if (dateIdx >= 0) c.getLong(dateIdx) else 0L,
                            type = if (typeIdx >= 0) c.getInt(typeIdx) else 1,
                            isSynced = syncedIdx >= 0 && c.getInt(syncedIdx) == 1,
                            syncedAt = if (syncedAtIdx >= 0 && !c.isNull(syncedAtIdx)) c.getLong(syncedAtIdx) else null,
                            deviceId = if (devIdIdx >= 0) c.getString(devIdIdx) ?: "" else "",
                            category = cat,
                            senderType = sType,
                            extractedOtp = otp,
                            extractedAmount = amtPair?.first,
                            isDebit = amtPair?.second
                        )
                    )
                }
            }

            // 4. In-memory category/filter chips for dynamic conditions
            val filtered = rawList.filter { r ->
                when (filter) {
                    "SAVED" -> savedNumbers.contains(r.normalizedNumber)
                    "ALPHA" -> r.address.any { it.isLetter() }
                    else -> true
                }
            }
            list.addAll(filtered)
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error in getSmsRecords: ${e.message}", e)
        }
        return list
    }

    @Synchronized
    fun deleteSmsRecords(ids: List<Long>): Pair<Int, List<Long>> {
        if (ids.isEmpty()) return Pair(0, emptyList())
        var total = 0
        val deviceSmsIds = mutableListOf<Long>()
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (chunk in ids.chunked(500)) {
                val placeholders = chunk.joinToString(",") { "?" }
                val args = chunk.map { it.toString() }.toTypedArray()

                // Collect device SMS IDs before removing local records
                try {
                    db.rawQuery("SELECT $COL_S_DEVICE_SMS_ID FROM $TABLE_SMS WHERE $COL_S_ID IN ($placeholders)", args).use { c ->
                        while (c.moveToNext()) {
                            val devId = c.getLong(0)
                            if (devId > 0L) deviceSmsIds.add(devId)
                        }
                    }
                } catch (_: Exception) {}

                total += db.delete(TABLE_SMS, "$COL_S_ID IN ($placeholders)", args)
            }

            // Add to blacklist table so rescan NEVER re-imports these deleted device messages
            val now = System.currentTimeMillis()
            for (devId in deviceSmsIds) {
                val cv = ContentValues().apply {
                    put(COL_D_SMS_ID, devId)
                    put(COL_D_DELETED_AT, now)
                }
                db.insertWithOnConflict(TABLE_DELETED_SMS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }

            db.setTransactionSuccessful()
            invalidateSmsStatsCache()
        } finally {
            db.endTransaction()
        }
        return Pair(total, deviceSmsIds)
    }

    @Synchronized
    fun deleteFilteredSmsRecords(query: String, filter: String): Pair<Int, List<Long>> {
        val matching = getSmsRecords(query, filter, limit = 50000)
        return deleteSmsRecords(matching.map { it.id })
    }

    @Synchronized
    fun getDeletedDeviceSmsIds(): Set<Long> {
        val set = mutableSetOf<Long>()
        try {
            readableDatabase.rawQuery("SELECT $COL_D_SMS_ID FROM $TABLE_DELETED_SMS", null).use { c ->
                while (c.moveToNext()) {
                    set.add(c.getLong(0))
                }
            }
        } catch (_: Exception) {}
        return set
    }

    @Synchronized
    fun cleanDuplicateSms(): Int {
        val db = writableDatabase
        var deleted = 0
        db.beginTransaction()
        try {
            val countBefore = android.database.DatabaseUtils.queryNumEntries(db, TABLE_SMS)
            val sql = """
                DELETE FROM $TABLE_SMS
                WHERE $COL_S_ID NOT IN (
                    SELECT MIN($COL_S_ID)
                    FROM $TABLE_SMS
                    GROUP BY $COL_S_ADDRESS, $COL_S_DATE, $COL_S_BODY
                )
            """.trimIndent()
            db.execSQL(sql)
            val countAfter = android.database.DatabaseUtils.queryNumEntries(db, TABLE_SMS)
            deleted = (countBefore - countAfter).toInt().coerceAtLeast(0)
            db.setTransactionSuccessful()
            invalidateSmsStatsCache()
        } catch (_: Exception) {
        } finally {
            db.endTransaction()
        }
        return deleted
    }

    @Synchronized
    fun markSmsSynced(ids: List<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_S_IS_SYNCED, 1)
            put(COL_S_SYNCED_AT, System.currentTimeMillis())
        }
        for (chunk in ids.chunked(500)) {
            val placeholders = chunk.joinToString(",") { "?" }
            db.update(TABLE_SMS, cv, "$COL_S_ID IN ($placeholders)", chunk.map { it.toString() }.toTypedArray())
        }
        invalidateSmsStatsCache()
    }

    // =========================================================================
    // MAINTENANCE & STATS
    // =========================================================================

    @Synchronized
    fun vacuumDatabase() {
        try {
            writableDatabase.execSQL("VACUUM")
        } catch (_: Exception) {}
    }

    fun getDatabaseSizeBytes(): Long {
        return try {
            val dbFile = appContext.getDatabasePath(DATABASE_NAME)
            if (dbFile.exists()) dbFile.length() else 0L
        } catch (_: Exception) {
            0L
        }
    }

    @Synchronized
    fun getStats(): AppStats {
        var totalContacts = 0
        var savedContacts = 0
        var syncedContacts = 0
        var pendingContacts = 0
        val devCounts = mutableMapOf<String, Int>()

        try {
            val db = readableDatabase
            db.rawQuery("SELECT $COL_C_SAVED, $COL_C_SYNC_STATUS, $COL_C_DEVICE_ID FROM $TABLE_CONTACTS WHERE $COL_C_DELETED = 0", null).use { c ->
                while (c.moveToNext()) {
                    totalContacts++
                    if (c.getInt(0) == 1) savedContacts++
                    val status = c.getString(1) ?: ""
                    if (status == "SYNCED") syncedContacts++ else pendingContacts++
                    val dev = c.getString(2)?.ifBlank { "Unassigned" } ?: "Unassigned"
                    devCounts[dev] = (devCounts[dev] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalDb", "Error reading contact stats: ${e.message}", e)
        }

        val totalDupes = getDuplicateGroups().size

        // Efficient Cached SMS Stats (re-evaluated only when SMS table changes)
        val smsStats = cachedSmsStats ?: run {
            var totalSms = 0
            var syncedSms = 0
            var otpCount = 0
            var txnCount = 0

            try {
                val db = readableDatabase
                db.rawQuery("""
                    SELECT 
                        COUNT(*),
                        SUM(CASE WHEN $COL_S_IS_SYNCED = 1 THEN 1 ELSE 0 END),
                        SUM(CASE WHEN $COL_S_CATEGORY = 'OTP' THEN 1 ELSE 0 END),
                        SUM(CASE WHEN $COL_S_CATEGORY = 'TRANSACTION' THEN 1 ELSE 0 END)
                    FROM $TABLE_SMS
                """.trimIndent(), null).use { sc ->
                    if (sc.moveToNext()) {
                        totalSms = sc.getInt(0)
                        syncedSms = sc.getInt(1)
                        otpCount = sc.getInt(2)
                        txnCount = sc.getInt(3)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LocalDb", "Error reading SMS stats: ${e.message}", e)
            }

            val computed = CachedSmsStats(totalSms, syncedSms, otpCount, txnCount)
            cachedSmsStats = computed
            computed
        }

        return AppStats(
            totalContacts = totalContacts,
            savedContacts = savedContacts,
            cloudSyncedContacts = syncedContacts,
            pendingContacts = pendingContacts,
            totalDuplicates = totalDupes,
            totalSms = smsStats.totalSms,
            syncedSms = smsStats.syncedSms,
            otpCount = smsStats.otpCount,
            txnCount = smsStats.txnCount,
            dbSizeBytes = getDatabaseSizeBytes(),
            deviceCounts = devCounts
        )
    }
}
