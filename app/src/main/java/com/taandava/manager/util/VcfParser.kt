@file:Suppress("DestructuringDeclaration")

package com.taandava.manager.util

import com.taandava.manager.data.ContactEntity
import java.io.BufferedReader
import java.io.StringReader
import java.util.Locale
import java.util.UUID

/**
 * High-performance, lightweight vCard (VCF) 2.1 / 3.0 parser and generator.
 * RFC 2426 and RFC 6350 compliant with multiline unfolding support.
 * Zero external dependencies.
 */
object VcfParser {

    fun exportToVcf(contacts: List<ContactEntity>): String {
        val sb = StringBuilder()
        for (c in contacts) {
            sb.append("BEGIN:VCARD\r\n")
            sb.append("VERSION:3.0\r\n")
            val displayName = c.name.ifBlank { "Contact ${PhoneUtils.formatIndianPhone(c.phone)}" }
            sb.append("FN:$displayName\r\n")
            sb.append("N:;${displayName};;;\r\n")
            if (c.phone.isNotBlank()) {
                sb.append("TEL;TYPE=CELL:${c.phone}\r\n")
            }
            if (c.details.isNotBlank()) {
                val escapedNotes = c.details.replace("\r", "").replace("\n", "\\n")
                sb.append("NOTE:$escapedNotes\r\n")
            }
            if (c.saved) {
                sb.append("X-STARRED:1\r\n")
            }
            sb.append("UID:${c.id}\r\n")
            sb.append("END:VCARD\r\n")
        }
        return sb.toString()
    }

    fun parseVcf(vcfContent: String): List<ContactEntity> {
        val result = mutableListOf<ContactEntity>()
        if (vcfContent.isBlank()) return result

        // 1. Unfold multiline RFC 2426 / RFC 6350 folded lines (continuation lines starting with space or tab)
        val rawLines = vcfContent.lines()
        val unfoldedLines = mutableListOf<String>()
        for (raw in rawLines) {
            if (raw.isNotEmpty() && (raw.startsWith(" ") || raw.startsWith("\t"))) {
                if (unfoldedLines.isNotEmpty()) {
                    val last = unfoldedLines.removeAt(unfoldedLines.size - 1)
                    unfoldedLines.add(last + raw.substring(1))
                } else {
                    unfoldedLines.add(raw.trim())
                }
            } else {
                unfoldedLines.add(raw)
            }
        }

        var currentName = ""
        var currentPhone = ""
        var currentNotes = ""
        var currentUid = ""
        var isStarred = false
        var inVCard = false

        for (line in unfoldedLines) {
            val l = line.trim()
            if (l.isEmpty()) continue

            if (l.equals("BEGIN:VCARD", ignoreCase = true)) {
                inVCard = true
                currentName = ""
                currentPhone = ""
                currentNotes = ""
                currentUid = ""
                isStarred = false
                continue
            }

            if (l.equals("END:VCARD", ignoreCase = true)) {
                if (inVCard && (currentPhone.isNotBlank() || currentName.isNotBlank())) {
                    val norm = PhoneUtils.normalizePhone(currentPhone)
                    val trimmedName = currentName.trim()
                    if (!ContactValidator.shouldRejectContact(trimmedName, norm)) {
                        result.add(
                            ContactEntity(
                                id = currentUid.ifBlank { UUID.randomUUID().toString() },
                                name = trimmedName,
                                phone = norm,
                                details = currentNotes,
                                saved = isStarred,
                                edited = true,
                                syncStatus = "PENDING",
                                source = "VCF_IMPORT"
                            )
                        )
                    }
                }
                inVCard = false
                continue
            }

            if (!inVCard) continue

            val upper = l.uppercase(Locale.ROOT)
            when {
                upper.startsWith("FN:") || upper.startsWith("FN;") -> {
                    val colonIdx = l.indexOf(':')
                    if (colonIdx >= 0) {
                        currentName = l.substring(colonIdx + 1).trim()
                    }
                }
                (upper.startsWith("N:") || upper.startsWith("N;")) && currentName.isBlank() -> {
                    val colonIdx = l.indexOf(':')
                    if (colonIdx >= 0) {
                        val parts = l.substring(colonIdx + 1).split(";")
                        val lastName = parts.getOrNull(0)?.trim() ?: ""
                        val firstName = parts.getOrNull(1)?.trim() ?: ""
                        val fullName = "$firstName $lastName".trim()
                        if (fullName.isNotBlank()) currentName = fullName
                    }
                }
                upper.startsWith("TEL") -> {
                    val colonIdx = l.indexOf(':')
                    if (colonIdx >= 0) {
                        val num = l.substring(colonIdx + 1).trim()
                        if (currentPhone.isBlank() && num.isNotBlank()) {
                            currentPhone = num
                        }
                    }
                }
                upper.startsWith("NOTE:") || upper.startsWith("NOTE;") -> {
                    val colonIdx = l.indexOf(':')
                    if (colonIdx >= 0) {
                        val note = l.substring(colonIdx + 1)
                            .replace("\\n", "\n")
                            .replace("\\N", "\n")
                            .replace("\\,", ",")
                            .replace("\\;", ";")
                            .trim()
                        currentNotes = if (currentNotes.isBlank()) note else "$currentNotes\n$note"
                    }
                }
                upper.startsWith("UID:") -> {
                    currentUid = l.substring(4).trim()
                }
                upper.startsWith("X-STARRED:") -> {
                    isStarred = l.substring(10).trim() == "1"
                }
            }
        }
        return result
    }
}
