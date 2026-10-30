package com.taandava.manager

import com.taandava.manager.util.ContactValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactValidatorTest {

    @Test
    fun testRejectsTraiSmsHeaders() {
        assertTrue(ContactValidator.shouldRejectContact("AD-AXISBK-S", "9876543210"))
        assertTrue(ContactValidator.shouldRejectContact("VM-HDFCBK", "9876543210"))
        assertTrue(ContactValidator.shouldRejectContact("VK-SBIINB", "9876543210"))
        assertTrue(ContactValidator.shouldRejectContact("BP-GOOGLE", "9876543210"))
        assertTrue(ContactValidator.shouldRejectContact("SMS-ALERT", "9876543210"))
        assertTrue(ContactValidator.shouldRejectContact("OTP-VERIFY", "9876543210"))
    }

    @Test
    fun testRejectsSyntheticSmsDetailsOrSource() {
        assertTrue(ContactValidator.shouldRejectContact("John Doe", "9876543210", rawSource = "SMS_SCAN"))
        assertTrue(ContactValidator.shouldRejectContact("John Doe", "9876543210", rawDetails = "Discovered via SMS"))
        assertTrue(ContactValidator.shouldRejectContact("John Doe", "9876543210", rawDetails = "SMS Extract"))
    }

    @Test
    fun testRejectsNameWithMismatchedPhoneNumber() {
        // Name references a specific 10-digit number, but phone is different
        assertTrue(ContactValidator.shouldRejectContact("Ramesh 9123456780", "9876543210"))
    }

    @Test
    fun testAcceptsNameWithMatchingPhoneNumber() {
        // Name references the same number as phone
        assertFalse(ContactValidator.shouldRejectContact("Ramesh 9876543210", "9876543210"))
    }

    @Test
    fun testPureDigitsName() {
        // Name is exactly same as phone -> ACCEPT
        assertFalse(ContactValidator.shouldRejectContact("9876543210", "9876543210"))
        assertFalse(ContactValidator.shouldRejectContact("+91 98765 43210", "9876543210"))

        // Name is purely digits but different from phone -> REJECT
        assertTrue(ContactValidator.shouldRejectContact("1234567890", "9876543210"))
    }

    @Test
    fun testAcceptsGenuineNames() {
        assertFalse(ContactValidator.shouldRejectContact("Alice Smith", "9876543210"))
        assertFalse(ContactValidator.shouldRejectContact("Rahul Sharma (Work)", "9876543210"))
        assertFalse(ContactValidator.shouldRejectContact("Dr. Mehta", "+919876543210"))
    }

    @Test
    fun testRejectsBlankEntries() {
        assertTrue(ContactValidator.shouldRejectContact("", ""))
        assertTrue(ContactValidator.shouldRejectContact("   ", "   "))
    }
}

