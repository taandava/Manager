package com.taandava.manager

import com.taandava.manager.util.SmsClassifier
import org.junit.Assert.*
import org.junit.Test

class SmsClassifierTest {

    @Test
    fun testCategorizeBody() {
        assertEquals("OTP", SmsClassifier.categorizeBody("Your verification OTP is 482910."))
        assertEquals("TRANSACTION", SmsClassifier.categorizeBody("Acct 1234 debited by INR 1,500.00 on 2026-10-30."))
        assertEquals("NOTIFICATION", SmsClassifier.categorizeBody("Your order has been shipped and is scheduled for delivery."))
        assertEquals("GENERAL", SmsClassifier.categorizeBody("Hey, let's catch up this weekend for coffee."))
    }

    @Test
    fun testExtractOtp_explicit() {
        assertEquals("482910", SmsClassifier.extractOtp("Your Google verification OTP is 482910. Do not share it."))
        assertEquals("94821", SmsClassifier.extractOtp("Your login code: 94821."))
        assertEquals("123456", SmsClassifier.extractOtp("Your one-time passcode: 123456"))
    }

    @Test
    fun testExtractOtp_following() {
        assertEquals("849201", SmsClassifier.extractOtp("849201 is your OTP for HDFC NetBanking."))
    }

    @Test
    fun testExtractOtp_standaloneWithYearFilter() {
        // Message contains a year (2025) and an OTP (591243)
        val otp = SmsClassifier.extractOtp("Welcome to our service in 2025! Your verification code is 591243.")
        assertEquals("591243", otp)
    }

    @Test
    fun testExtractAmount_debit() {
        val result = SmsClassifier.extractAmount("Your account has been debited for Rs. 2,500.50 at Amazon.")
        assertNotNull(result)
        assertEquals("₹2,500.50", result?.first)
        assertTrue(result?.second == true) // isDebit = true
    }

    @Test
    fun testExtractAmount_credit() {
        val result = SmsClassifier.extractAmount("Salary of INR 75,000 credited to your savings account.")
        assertNotNull(result)
        assertEquals("₹75,000", result?.first)
        assertFalse(result?.second == true) // isDebit = false
    }

    @Test
    fun testExtractAmount_rupeeSymbol() {
        val result = SmsClassifier.extractAmount("₹1,200 paid towards electricity bill.")
        assertNotNull(result)
        assertEquals("₹1,200", result?.first)
        assertTrue(result?.second == true)
    }

    @Test
    fun testDetermineSenderType() {
        assertEquals("Saved Contacts", SmsClassifier.determineSenderType("9876543210", isSaved = true))
        assertEquals("Shortcodes / Alpha", SmsClassifier.determineSenderType("AD-HDFCBK", isSaved = false))
        assertEquals("Landline / Toll-Free", SmsClassifier.determineSenderType("1800123456", isSaved = false))
        assertEquals("Mobile Numbers", SmsClassifier.determineSenderType("+919876543210", isSaved = false))
        assertEquals("Mobile Numbers", SmsClassifier.determineSenderType("9876543210", isSaved = false))
    }
}

