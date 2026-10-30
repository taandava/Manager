package com.taandava.manager.util

object SmsClassifier {
    private val OTP_KEYWORD_REGEX = Regex("\\b(otp|code|verify|verification|one time password|passcode|secret code|pin)\\b", RegexOption.IGNORE_CASE)
    private val TRANSACTION_KEYWORD_REGEX = Regex("\\b(debited|credited|rs\\.?|inr|₹|emi|balance|loan|transaction|acct|stmt|spent|withdrawn|deposit|payment|paid)\\b", RegexOption.IGNORE_CASE)
    private val NOTIFICATION_KEYWORD_REGEX = Regex("\\b(alert|update|order|delivery|shipped|booked|reminder|invoice|bill|scheduled)\\b", RegexOption.IGNORE_CASE)
    private val NON_DIGIT_REGEX = Regex("\\D")

    // Intelligent OTP Extraction Patterns
    private val OTP_EXPLICIT_REGEX = Regex("""(?i)\b(?:otp|code|pin|passcode|verification)\s*(?:is|:|-)?\s*([0-9]{4,8})\b""")
    private val OTP_FOLLOWING_REGEX = Regex("""\b([0-9]{4,8})\s*(?:is\s*(?:your|the)\s*(?:otp|code|secret|verification))""", RegexOption.IGNORE_CASE)
    private val STANDALONE_OTP_REGEX = Regex("""\b([0-9]{4,6})\b""")

    // Intelligent Transaction Extraction Patterns
    private val AMOUNT_REGEX = Regex("""(?i)(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)|([\d,]+(?:\.\d{1,2})?)\s*(?:rs\.?|inr|rupees)""")
    private val DEBIT_REGEX = Regex("""(?i)\b(debited|spent|withdrawn|paid|payment of|dr\.?)\b""")
    private val CREDIT_REGEX = Regex("""(?i)\b(credited|received|deposit|deposited|cr\.?|refund)\b""")

    fun categorizeBody(body: String): String {
        return when {
            OTP_KEYWORD_REGEX.containsMatchIn(body) -> "OTP"
            TRANSACTION_KEYWORD_REGEX.containsMatchIn(body) -> "TRANSACTION"
            NOTIFICATION_KEYWORD_REGEX.containsMatchIn(body) -> "NOTIFICATION"
            else -> "GENERAL"
        }
    }

    fun extractOtp(body: String): String? {
        if (!OTP_KEYWORD_REGEX.containsMatchIn(body)) return null

        OTP_EXPLICIT_REGEX.find(body)?.let {
            val code = it.groupValues[1]
            if (code.length in 4..8) return code
        }
        OTP_FOLLOWING_REGEX.find(body)?.let {
            val code = it.groupValues[1]
            if (code.length in 4..8) return code
        }
        val matches = STANDALONE_OTP_REGEX.findAll(body).map { it.groupValues[1] }.toList()
        for (m in matches) {
            val num = m.toIntOrNull() ?: continue
            if (num in 2020..2030 && m.length == 4) continue // Skip years
            return m
        }
        return null
    }

    fun extractAmount(body: String): Pair<String, Boolean>? {
        val match = AMOUNT_REGEX.find(body) ?: return null
        val rawAmount = (match.groupValues[1].ifEmpty { match.groupValues[2] }).trim()
        if (rawAmount.isBlank() || rawAmount == "0") return null

        val isDebit = when {
            DEBIT_REGEX.containsMatchIn(body) -> true
            CREDIT_REGEX.containsMatchIn(body) -> false
            else -> true
        }

        val formatted = if (rawAmount.startsWith("₹")) rawAmount else "₹$rawAmount"
        return Pair(formatted, isDebit)
    }

    fun determineSenderType(address: String, isSaved: Boolean): String {
        if (isSaved) return "Saved Contacts"
        if (address.isBlank() || address.lowercase() in listOf("unknown", "null", "parse error")) return "Unknown"
        if (address.any { it.isLetter() }) return "Shortcodes / Alpha"

        val clean = address.replace(NON_DIGIT_REGEX, "")
        if (clean.startsWith("140") || clean.startsWith("160") || clean.startsWith("1800")) {
            return "Landline / Toll-Free"
        }

        val core = when {
            clean.startsWith("91") && clean.length > 10 -> clean.substring(2)
            clean.startsWith("0") && clean.length > 10 -> clean.substring(1)
            else -> clean
        }

        if (core.length == 10 && core.first() in listOf('6', '7', '8', '9')) {
            return "Mobile Numbers"
        }
        return "Landline / Other"
    }
}
