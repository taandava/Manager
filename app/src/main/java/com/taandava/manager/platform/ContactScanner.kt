package com.taandava.manager.platform

import android.content.ContentProviderOperation
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.ContactsContract
import com.taandava.manager.data.ContactEntity
import com.taandava.manager.data.LocalDb
import com.taandava.manager.util.PhoneUtils
import java.util.UUID

class ContactScanner(private val context: Context) {

    private val db = LocalDb.getInstance(context)

    fun getDeviceModelName(): String {
        val m = Build.MANUFACTURER ?: ""
        val mod = Build.MODEL ?: ""
        val name = if (mod.startsWith(m, ignoreCase = true)) mod else "$m $mod"
        return name.trim().replace(" ", "_")
    }

    fun scanDeviceContacts(): Int {
        val deviceModel = getDeviceModelName()
        val existing = db.getAllContactsRaw()
        val existingMap = mutableSetOf<String>()
        val blacklistedPhones = db.getDeletedContactPhones()
        for (c in existing) {
            val norm = PhoneUtils.normalizePhone(c.phone)
            if (norm.isNotBlank()) existingMap.add(norm)
        }

        val newContacts = mutableListOf<ContactEntity>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                val rawPhone = if (phoneIdx >= 0) cursor.getString(phoneIdx) ?: "" else ""
                val norm = PhoneUtils.normalizePhone(rawPhone)

                if (norm.length >= 10 && !existingMap.contains(norm) && !blacklistedPhones.contains(norm)) {
                    val trimmedName = name.trim()
                    if (!com.taandava.manager.util.ContactValidator.shouldRejectContact(trimmedName, norm)) {
                        existingMap.add(norm)
                        newContacts.add(
                            ContactEntity(
                                id = UUID.randomUUID().toString(),
                                name = trimmedName,
                                phone = norm,
                                details = "",
                                source = "DEVICE_SCAN",
                                deviceId = deviceModel,
                                edited = true,
                                syncStatus = "PENDING"
                            )
                        )
                    }
                }
            }
        }

        if (newContacts.isNotEmpty()) {
            db.upsertContacts(newContacts)
        }
        return newContacts.size
    }

    fun saveAllToDeviceContacts(): Int {
        val appContacts = db.getAllContacts(savedOnly = false, query = "")
        if (appContacts.isEmpty()) return 0

        // Get existing numbers on device
        val existingNumbers = mutableSetOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { c ->
            val idx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                val num = if (idx >= 0) c.getString(idx) ?: "" else ""
                val norm = PhoneUtils.normalizePhone(num)
                if (norm.isNotBlank()) existingNumbers.add(norm)
            }
        }

        var savedCount = 0
        var ops = ArrayList<ContentProviderOperation>()

        for (contact in appContacts) {
            val norm = PhoneUtils.normalizePhone(contact.phone)
            if (norm.length < 10 || existingNumbers.contains(norm)) continue

            val rawContactInsertIndex = ops.size

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name.ifBlank { "Contact $norm" })
                    .build()
            )

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, contact.phone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )

            existingNumbers.add(norm)
            savedCount++

            if (ops.size >= 150) {
                try {
                    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                } catch (_: Exception) {}
                ops = ArrayList()
            }
        }

        if (ops.isNotEmpty()) {
            try {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (_: Exception) {}
        }

        return savedCount
    }

    fun saveSingleContactToDevice(contact: ContactEntity): Boolean {
        try {
            val ops = ArrayList<ContentProviderOperation>()
            val rawContactInsertIndex = 0

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name.ifBlank { "Contact ${contact.phone}" })
                    .build()
            )

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, contact.phone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )

            if (contact.details.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Note.NOTE, contact.details)
                        .build()
                )
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            return true
        } catch (e: Exception) {
            android.util.Log.e("ContactScanner", "Error saving single contact to device: ${e.message}", e)
            return false
        }
    }

    fun cleanDeviceDuplicates(survivorNames: Map<String, String> = emptyMap()): Int {
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            val phoneToRawContacts = mutableMapOf<String, MutableList<Long>>()

            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                val rawIdIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (c.moveToNext()) {
                    val rawId = if (rawIdIdx >= 0) c.getLong(rawIdIdx) else -1L
                    val num = if (numIdx >= 0) c.getString(numIdx) ?: "" else ""
                    val norm = PhoneUtils.normalizePhone(num)

                    if (rawId > 0 && norm.length >= 10) {
                        phoneToRawContacts.getOrPut(norm) { mutableListOf() }.add(rawId)
                    }
                }
            }

            var deleted = 0
            var ops = ArrayList<ContentProviderOperation>()

            for ((normPhone, rawIds) in phoneToRawContacts) {
                val distinctIds = rawIds.distinct()
                val keepName = survivorNames[normPhone]
                if (distinctIds.size > 1) {
                    val survivorRawId = distinctIds[0]
                    if (!keepName.isNullOrBlank()) {
                        updateDeviceContactName(survivorRawId, keepName)
                    }
                    // Keep the first raw contact, delete others
                    for (i in 1 until distinctIds.size) {
                        val rawId = distinctIds[i]
                        ops.add(
                            ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                                .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawId.toString()))
                                .build()
                        )
                        deleted++

                        if (ops.size >= 100) {
                            try {
                                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                            } catch (_: Exception) {}
                            ops = ArrayList()
                        }
                    }
                } else if (distinctIds.size == 1 && !keepName.isNullOrBlank()) {
                    updateDeviceContactName(distinctIds[0], keepName)
                }
            }

            if (ops.isNotEmpty()) {
                try {
                    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                } catch (_: Exception) {}
            }

            return deleted
        } catch (e: Exception) {
            android.util.Log.e("ContactScanner", "Error cleaning device duplicates: ${e.message}", e)
            return 0
        }
    }

    fun deleteDeviceContactsByPhone(phone: String): Int {
        try {
            val norm = PhoneUtils.normalizePhone(phone)
            if (norm.length < 10) return 0

            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val rawIds = mutableSetOf<Long>()
            val selection = "${ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER} = ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} = ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            val selectionArgs = arrayOf(norm, phone, "%${norm.takeLast(10)}")

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { c ->
                val rawIdIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    val rawId = if (rawIdIdx >= 0) c.getLong(rawIdIdx) else -1L
                    val num = if (numIdx >= 0) c.getString(numIdx) ?: "" else ""
                    if (rawId > 0 && PhoneUtils.normalizePhone(num) == norm) {
                        rawIds.add(rawId)
                    }
                }
            }

            if (rawIds.isEmpty()) return 0

            var deleted = 0
            var ops = ArrayList<ContentProviderOperation>()
            for (rawId in rawIds) {
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                        .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawId.toString()))
                        .build()
                )
                deleted++
                if (ops.size >= 100) {
                    try {
                        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                    } catch (_: Exception) {}
                    ops = ArrayList()
                }
            }
            if (ops.isNotEmpty()) {
                try {
                    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                } catch (_: Exception) {}
            }
            return deleted
        } catch (e: Exception) {
            android.util.Log.e("ContactScanner", "Error deleting device contacts for $phone: ${e.message}", e)
            return 0
        }
    }

    fun deleteDeviceDuplicatesForPhone(phone: String, keepName: String? = null): Int {
        try {
            val norm = PhoneUtils.normalizePhone(phone)
            if (norm.length < 10) return 0

            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val rawIds = mutableListOf<Long>()
            val selection = "${ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER} = ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} = ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            val selectionArgs = arrayOf(norm, phone, "%${norm.takeLast(10)}")

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { c ->
                val rawIdIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    val rawId = if (rawIdIdx >= 0) c.getLong(rawIdIdx) else -1L
                    val num = if (numIdx >= 0) c.getString(numIdx) ?: "" else ""
                    if (rawId > 0 && PhoneUtils.normalizePhone(num) == norm) {
                        rawIds.add(rawId)
                    }
                }
            }

            val distinctRawIds = rawIds.distinct()
            if (distinctRawIds.size <= 1) {
                if (distinctRawIds.size == 1 && !keepName.isNullOrBlank()) {
                    updateDeviceContactName(distinctRawIds[0], keepName)
                }
                return 0
            }

            val survivorRawId = distinctRawIds[0]
            if (!keepName.isNullOrBlank()) {
                updateDeviceContactName(survivorRawId, keepName)
            }

            var deleted = 0
            var ops = ArrayList<ContentProviderOperation>()
            for (i in 1 until distinctRawIds.size) {
                val rawId = distinctRawIds[i]
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                        .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawId.toString()))
                        .build()
                )
                deleted++
                if (ops.size >= 100) {
                    try {
                        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                    } catch (_: Exception) {}
                    ops = ArrayList()
                }
            }

            if (ops.isNotEmpty()) {
                try {
                    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                } catch (_: Exception) {}
            }
            return deleted
        } catch (e: Exception) {
            android.util.Log.e("ContactScanner", "Error deleting device duplicates for $phone: ${e.message}", e)
            return 0
        }
    }

    fun updateDeviceContactName(rawContactId: Long, newName: String): Boolean {
        try {
            val cv = android.content.ContentValues().apply {
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
            }
            val where = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
            val args = arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            val updated = context.contentResolver.update(ContactsContract.Data.CONTENT_URI, cv, where, args)
            if (updated == 0) {
                val insertCv = android.content.ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
                }
                context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, insertCv)
            }
            return true
        } catch (e: Exception) {
            android.util.Log.e("ContactScanner", "Error updating name for raw contact $rawContactId: ${e.message}", e)
            return false
        }
    }

    fun updateDeviceContactNameForPhone(phone: String, newName: String): Boolean {
        try {
            val norm = PhoneUtils.normalizePhone(phone)
            if (norm.length < 10) return false
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
            val selection = "${ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER} = ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} = ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            val selectionArgs = arrayOf(norm, phone, "%${norm.takeLast(10)}")
            val rawIds = mutableSetOf<Long>()
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { c ->
                val rawIdIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
                while (c.moveToNext()) {
                    if (rawIdIdx >= 0) rawIds.add(c.getLong(rawIdIdx))
                }
            }
            var anyUpdated = false
            for (rawId in rawIds) {
                if (updateDeviceContactName(rawId, newName)) {
                    anyUpdated = true
                }
            }
            return anyUpdated
        } catch (e: Exception) {
            android.util.Log.e("ContactScanner", "Error updating device contact for phone $phone: ${e.message}", e)
            return false
        }
    }
}
