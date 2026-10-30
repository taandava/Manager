package com.taandava.manager.util

import java.util.regex.Pattern

object ContactValidator {

    private val TEN_DIGIT_PHONE_REGEX = Pattern.compile("(?:\\+?91)?[6-9]\\d{9}")
    
    // Indian TRAI Commercial/Transactional SMS Sender ID Format:
    // 2-character telecom prefix, hyphen, 3-12 character principal entity / header code, optional subcode (e.g. AD-AXISBK-S, VM-HDFCBK, VK-SBI, BP-GOOGLE)
    private val TRAI_SMS_HEADER_REGEX = Pattern.compile("^[A-Za-z]{2}-[A-Za-z0-9]{3,14}(?:-[A-Za-z0-9]+)?$")

    /**
     * Checks if a string looks like an SMS sender header / alpha tag.
     */
    fun isSmsSenderHeader(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        if (TRAI_SMS_HEADER_REGEX.matcher(trimmed).matches()) return true
        if (trimmed.startsWith("SMS", ignoreCase = true)) return true
        if (trimmed.startsWith("MSG-", ignoreCase = true) ||
            trimmed.startsWith("OTP-", ignoreCase = true) ||
            trimmed.startsWith("TXN-", ignoreCase = true) ||
            trimmed.startsWith("PROMO-", ignoreCase = true)
        ) return true
        return false
    }

    /**
     * Determines whether a contact entry should be rejected based on the user's rules:
     * 1. REJECT if derived from SMS or name matches SMS sender IDs / bank headers.
     * 2. REJECT if name contains a phone number but the contact phone is completely different.
     * 3. REJECT if name is just numbers/contact number (unless name and phone are exactly the same number).
     * 4. ONLY KEEP if name is genuine text OR (name and number are exactly the same number).
     */
    fun shouldRejectContact(
        rawName: String,
        rawPhone: String,
        rawDetails: String = "",
        rawSource: String = ""
    ): Boolean {
        val name = rawName.trim()
        val phone = rawPhone.trim()
        val details = rawDetails.trim()
        val source = rawSource.trim()

        // 0. Reject synthetic SMS sources or details
        if (source.equals("SMS_SCAN", ignoreCase = true) ||
            source.equals("DEVICE_SMS", ignoreCase = true) ||
            details.contains("Discovered via SMS", ignoreCase = true) ||
            details.contains("Added from SMS", ignoreCase = true) ||
            details.contains("SMS Extract", ignoreCase = true)
        ) {
            return true
        }

        // 1. If both are empty, definitely reject
        if (name.isBlank() && phone.isBlank()) return true

        // 2. Reject TRAI SMS headers & SMS sender codes (e.g. AD-AXISBK-S, VM-HDFCBK)
        if (isSmsSenderHeader(name)) {
            return true
        }

        val normPhone = PhoneUtils.normalizePhone(phone)
        val normName = PhoneUtils.normalizePhone(name)

        // Check if name contains any letters (English or Unicode letters)
        val hasLetters = name.any { it.isLetter() }

        // 3. If name has NO letters (purely digits, symbols, punctuation, spaces)
        if (!hasLetters) {
            // "ONLY KEEP CONTACTS WHERE NAME IS TEXT OR NAME AND NUMBER IS EXACTLY SAME NUMBER"
            if (normPhone.isBlank() || normName.isBlank() || normName != normPhone) {
                return true // REJECT: Name is just numbers/different number
            }
            return false // KEEP: Name and phone are exactly the same number
        }

        // 4. Name has letters: Check if name references a specific 10-digit phone number
        val matcher = TEN_DIGIT_PHONE_REGEX.matcher(name)
        if (matcher.find()) {
            val phoneInName = PhoneUtils.normalizePhone(matcher.group())
            if (phoneInName.isNotBlank() && normPhone.isNotBlank() && phoneInName != normPhone) {
                return true // REJECT: Name references a specific number, but contact phone is completely different!
            }
        }

        return false // KEEP: Genuine text name
    }

    /**
     * Returns true if contact is valid to keep.
     */
    fun isValidContact(
        name: String,
        phone: String,
        details: String = "",
        source: String = ""
    ): Boolean {
        return !shouldRejectContact(name, phone, details, source)
    }
}
