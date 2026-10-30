package com.taandava.manager.data

import com.taandava.manager.util.PhoneUtils
import java.util.UUID

data class ContactEntity(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val phone: String = "",
    val details: String = "",
    val saved: Boolean = false,
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = "LOCAL",
    val cloudId: String = "",
    val syncStatus: String = "PENDING",
    val deviceId: String = ""
) {
    fun getSearchable(): String {
        return PhoneUtils.normalizeSearch("$name $phone $details")
    }
}

data class SmsRecordEntity(
    val id: Long = 0L,
    val deviceSmsId: Long = 0L,
    val address: String = "",
    val normalizedNumber: String = "",
    val body: String = "",
    val date: Long = System.currentTimeMillis(),
    val type: Int = 1, // 1 = Inbox, 2 = Sent
    val isSynced: Boolean = false,
    val syncedAt: Long? = null,
    val deviceId: String = "",
    val category: String = "GENERAL", // OTP, TRANSACTION, NOTIFICATION, GENERAL
    val senderType: String = "Unknown",
    val extractedOtp: String? = null,
    val extractedAmount: String? = null,
    val isDebit: Boolean? = null
)

data class DuplicateGroup(
    val phone: String,
    val contacts: List<ContactEntity>
)

data class SameNameGroup(
    val nameKey: String,
    val displayName: String,
    val contacts: List<ContactEntity>
)

data class SameNameDisambiguateResult(
    val updatedContacts: List<ContactEntity>
)

data class AppStats(
    val totalContacts: Int = 0,
    val savedContacts: Int = 0,
    val cloudSyncedContacts: Int = 0,
    val pendingContacts: Int = 0,
    val totalDuplicates: Int = 0,
    val totalSms: Int = 0,
    val syncedSms: Int = 0,
    val otpCount: Int = 0,
    val txnCount: Int = 0,
    val dbSizeBytes: Long = 0L,
    val deviceCounts: Map<String, Int> = emptyMap()
)

data class SmartMergeResult(
    val survivor: ContactEntity,
    val deletedContacts: List<ContactEntity>,
    val normalizedPhone: String
)

data class CloudOverview(
    val totalContacts: Int = 0,
    val totalSms: Int = 0,
    val uniquePhones: Int = 0,
    val devices: List<String> = emptyList(),
    val dbSizeBytes: Long = 0L,
    val lastUpdated: Long = 0L
)

data class CloudContactItem(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val details: String = "",
    val saved: Boolean = false,
    val updatedAt: Long = 0L,
    val deviceId: String = ""
)

data class CloudSmsItem(
    val id: String = "",
    val address: String = "",
    val normalizedNumber: String = "",
    val body: String = "",
    val date: Long = 0L,
    val type: String = "INBOX",
    val deviceId: String = ""
)
