package com.taandava.manager

import com.taandava.manager.data.CloudContactItem
import com.taandava.manager.data.CloudOverview
import com.taandava.manager.data.CloudSmsItem
import org.junit.Assert.*
import org.junit.Test

class CloudDataModelsTest {

    @Test
    fun testCloudOverviewDefaults() {
        val overview = CloudOverview()
        assertEquals(0, overview.totalContacts)
        assertEquals(0, overview.totalSms)
        assertEquals(0, overview.uniquePhones)
        assertTrue(overview.devices.isEmpty())
        assertEquals(0L, overview.dbSizeBytes)
        assertEquals(0L, overview.lastUpdated)
    }

    @Test
    fun testCloudOverviewCreation() {
        val overview = CloudOverview(
            totalContacts = 1250,
            totalSms = 4800,
            uniquePhones = 1100,
            devices = listOf("SM-G935F", "WEB_ADMIN"),
            dbSizeBytes = 2450000L,
            lastUpdated = 1729000000000L
        )

        assertEquals(1250, overview.totalContacts)
        assertEquals(4800, overview.totalSms)
        assertEquals(1100, overview.uniquePhones)
        assertEquals(2, overview.devices.size)
        assertEquals("SM-G935F", overview.devices[0])
        assertEquals("WEB_ADMIN", overview.devices[1])
        assertEquals(2450000L, overview.dbSizeBytes)
        assertEquals(1729000000000L, overview.lastUpdated)
    }

    @Test
    fun testCloudContactItemProperties() {
        val contact = CloudContactItem(
            id = "c123",
            name = "Ramesh Kumar",
            phone = "+919876543210",
            details = "Loan: ₹50,000",
            saved = true,
            updatedAt = 1729000000000L,
            deviceId = "SM-G935F"
        )

        assertEquals("c123", contact.id)
        assertEquals("Ramesh Kumar", contact.name)
        assertEquals("+919876543210", contact.phone)
        assertEquals("Loan: ₹50,000", contact.details)
        assertTrue(contact.saved)
        assertEquals(1729000000000L, contact.updatedAt)
        assertEquals("SM-G935F", contact.deviceId)

        val copied = contact.copy(name = "Suresh Kumar", saved = false)
        assertEquals("Suresh Kumar", copied.name)
        assertFalse(copied.saved)
        assertEquals("c123", copied.id)
    }

    @Test
    fun testCloudSmsItemProperties() {
        val sms = CloudSmsItem(
            id = "s999",
            address = "VK-HDFCBK",
            normalizedNumber = "VK-HDFCBK",
            body = "Rs 2500 debited from a/c **1234 on 30-Oct-26. UPI Ref: 123456",
            date = 1729000000000L,
            type = "INBOX",
            deviceId = "SM-G935F"
        )

        assertEquals("s999", sms.id)
        assertEquals("VK-HDFCBK", sms.address)
        assertEquals("VK-HDFCBK", sms.normalizedNumber)
        assertTrue(sms.body.contains("Rs 2500 debited"))
        assertEquals(1729000000000L, sms.date)
        assertEquals("INBOX", sms.type)
        assertEquals("SM-G935F", sms.deviceId)

        val sentSms = sms.copy(type = "SENT")
        assertEquals("SENT", sentSms.type)
    }

    @Test
    fun testCloudContactItemFallbackAndAppendModeProperties() {
        val blankNameContact = CloudContactItem(
            id = "",
            name = "",
            phone = "+919876543210",
            details = "Call Log",
            saved = false,
            updatedAt = 1730000000000L,
            deviceId = "OPPO_CPH2781"
        )

        // When name is blank, the phone number should serve as fallback display name
        val normPhone = com.taandava.manager.util.PhoneUtils.normalizePhone(blankNameContact.phone)
        val displayName = blankNameContact.name.trim().ifBlank { normPhone }
        assertEquals("+919876543210", displayName)

        // Deterministic UUID for append mode when id is blank
        val generatedId = java.util.UUID.nameUUIDFromBytes("cloud_$normPhone".toByteArray()).toString()
        assertNotNull(generatedId)
        assertTrue(generatedId.isNotBlank())

        val contactEntity = com.taandava.manager.data.ContactEntity(
            id = generatedId,
            name = displayName,
            phone = normPhone,
            details = blankNameContact.details,
            saved = blankNameContact.saved,
            source = "HOSTINGER_CSV",
            syncStatus = "SYNCED",
            deviceId = blankNameContact.deviceId
        )

        assertEquals("+919876543210", contactEntity.name)
        assertEquals("+919876543210", contactEntity.phone)
        assertEquals("HOSTINGER_CSV", contactEntity.source)
        assertEquals("SYNCED", contactEntity.syncStatus)
        assertTrue(com.taandava.manager.util.ContactValidator.isValidContact(contactEntity.name, contactEntity.phone, contactEntity.details, contactEntity.source))
    }
}
