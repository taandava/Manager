package com.taandava.manager.util

import java.util.Locale

object PhoneUtils {
    fun normalizePhone(s: String?): String {
        if (s.isNullOrBlank()) return ""
        val trimmed = s.trim()
        var digits = trimmed.replace(Regex("[^0-9+]"), "")
        if (digits.startsWith("00")) {
            digits = "+" + digits.substring(2)
        } else if (digits.matches(Regex("0[0-9]{10}"))) {
            digits = digits.substring(1)
        }
        if (digits.matches(Regex("[0-9]{10}"))) {
            digits = "+91$digits"
        } else if (digits.matches(Regex("91[0-9]{10}"))) {
            digits = "+$digits"
        }
        return digits.ifEmpty { trimmed }
    }

    fun formatIndianPhone(phone: String?): String {
        val norm = normalizePhone(phone)
        if (norm.matches(Regex("\\+91[0-9]{10}"))) {
            val digits = norm.substring(3)
            return "+91 " + digits.substring(0, 5) + " " + digits.substring(5, 10)
        }
        if (norm.matches(Regex("[0-9]{10}"))) {
            return norm.substring(0, 5) + " " + norm.substring(5, 10)
        }
        return phone ?: ""
    }

    fun normalizeSearch(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "")
    }
}
