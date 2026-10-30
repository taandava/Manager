package com.taandava.manager.util

import java.util.Locale

object NameDisambiguator {

    val PREFIXES: Set<String> = setOf(
        "mr", "mr.", "mrs", "mrs.", "ms", "ms.", "miss",
        "dr", "dr.", "prof", "prof.", "er", "er.",
        "shri", "shree", "smt", "smt.", "adv", "adv.",
        "rev", "rev.", "capt", "capt.", "col", "col.",
        "maj", "maj.", "lt", "lt.", "hon", "hon."
    )

    val SUFFIXES: Set<String> = setOf(
        "jr", "jr.", "sr", "sr.", "ii", "iii", "iv", "v",
        "phd", "ph.d", "ph.d.", "md", "m.d.", "esq", "esq.",
        "bhai", "ji", "sahab", "saheb", "seth"
    )

    fun isPrefix(word: String): Boolean {
        return PREFIXES.contains(word.lowercase(Locale.ROOT).trim())
    }

    fun isSuffix(word: String): Boolean {
        return SUFFIXES.contains(word.lowercase(Locale.ROOT).trim())
    }

    fun canonicalPrefix(word: String): String {
        val lower = word.lowercase(Locale.ROOT).trim()
        return when (lower) {
            "mr", "mr." -> "Mr."
            "mrs", "mrs." -> "Mrs."
            "ms", "ms." -> "Ms."
            "miss" -> "Miss"
            "dr", "dr." -> "Dr."
            "prof", "prof." -> "Prof."
            "er", "er." -> "Er."
            "shri" -> "Shri"
            "shree" -> "Shree"
            "smt", "smt." -> "Smt."
            "adv", "adv." -> "Adv."
            "rev", "rev." -> "Rev."
            "capt", "capt." -> "Capt."
            "col", "col." -> "Col."
            "maj", "maj." -> "Maj."
            "lt", "lt." -> "Lt."
            "hon", "hon." -> "Hon."
            else -> toSentenceCase(word)
        }
    }

    fun canonicalSuffix(word: String): String {
        val lower = word.lowercase(Locale.ROOT).trim()
        return when (lower) {
            "jr", "jr." -> "Jr."
            "sr", "sr." -> "Sr."
            "ii" -> "II"
            "iii" -> "III"
            "iv" -> "IV"
            "v" -> "V"
            "phd", "ph.d", "ph.d." -> "Ph.D."
            "md", "m.d." -> "M.D."
            "esq", "esq." -> "Esq."
            "bhai" -> "Bhai"
            "ji" -> "Ji"
            "sahab" -> "Sahab"
            "saheb" -> "Saheb"
            "seth" -> "Seth"
            else -> toSentenceCase(word)
        }
    }

    fun toSentenceCase(word: String): String {
        if (word.isBlank()) return ""
        return word.lowercase(Locale.ROOT).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
    }

    /**
     * Extracts pure alphabetic words (ignoring case, punctuation, prefixes and suffixes)
     * to test if two names represent the exact same person name.
     */
    fun extractNameKey(name: String): String {
        if (name.isBlank()) return ""
        val tokens = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val coreWords = tokens.filter { !isPrefix(it) && !isSuffix(it) }
            .map { it.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "") }
            .filter { it.isNotBlank() }

        return if (coreWords.isNotEmpty()) {
            coreWords.joinToString(" ")
        } else {
            tokens.map { it.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "") }.joinToString(" ")
        }
    }

    /**
     * Disambiguates a duplicate contact name by repeating the last alphabet of the first word
     * `extraRepeats` times, applying sentence case to all regular words, and preserving prefixes and suffixes.
     *
     * @param rawName The original name (e.g. "dr ramesh kumar jr")
     * @param extraRepeats Number of extra last alphabets to add (0 = baseline, 1 = repeat once e.g. Rameshh, 2 = Rameshhh)
     * @param applySentenceCase Whether to format non-prefix/suffix words in sentence case
     * @return Formatted disambiguated name (e.g. "Dr. Rameshh Kumar Jr.")
     */
    fun disambiguateName(
        rawName: String,
        extraRepeats: Int,
        applySentenceCase: Boolean = true
    ): String {
        val trimmed = rawName.trim().replace(Regex("[_#*~`|^]+"), " ")
        if (trimmed.isBlank()) return rawName

        val rawTokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (rawTokens.isEmpty()) return rawName

        // Identify prefix tokens at start
        var firstWordIdx = 0
        while (firstWordIdx < rawTokens.size - 1 && isPrefix(rawTokens[firstWordIdx])) {
            firstWordIdx++
        }

        val formattedTokens = rawTokens.mapIndexed { idx, token ->
            when {
                // Is a prefix
                idx < firstWordIdx && isPrefix(token) -> {
                    canonicalPrefix(token)
                }
                // Is a suffix at the end
                idx == rawTokens.size - 1 && isSuffix(token) && rawTokens.size > 1 -> {
                    canonicalSuffix(token)
                }
                // Is the first actual name word (target for repeating the last alphabet)
                idx == firstWordIdx -> {
                    val baseWord = if (applySentenceCase) toSentenceCase(token) else token
                    if (extraRepeats > 0) {
                        appendLastAlphabet(baseWord, extraRepeats)
                    } else {
                        baseWord
                    }
                }
                // Normal intermediate/last word
                else -> {
                    if (applySentenceCase) toSentenceCase(token) else token
                }
            }
        }

        return formattedTokens.joinToString(" ")
    }

    /**
     * Repeats the last alphabetic character in the word `count` times.
     * e.g. appendLastAlphabet("Ramesh", 1) -> "Rameshh"
     *      appendLastAlphabet("Ramesh", 2) -> "Rameshhh"
     *      appendLastAlphabet("Ramesh.", 1) -> "Rameshh."
     */
    fun appendLastAlphabet(word: String, count: Int): String {
        if (count <= 0 || word.isBlank()) return word

        // Find last alphabetic character index
        var lastLetterIndex = -1
        for (i in word.length - 1 downTo 0) {
            if (word[i].isLetter()) {
                lastLetterIndex = i
                break
            }
        }

        if (lastLetterIndex == -1) {
            // No letter found (e.g. all digits or symbols), just repeat the last character
            val lastChar = word.last()
            return word + lastChar.toString().repeat(count)
        }

        val targetChar = word[lastLetterIndex].lowercaseChar()
        val before = word.substring(0, lastLetterIndex + 1)
        val after = word.substring(lastLetterIndex + 1)
        val repeated = targetChar.toString().repeat(count)

        return before + repeated + after
    }
}

