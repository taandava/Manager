package com.taandava.manager.data

import com.taandava.manager.util.ContactValidator
import java.util.Locale

enum class MergeNameStrategy(val label: String, val description: String) {
    LONGEST("Keep Longest Name", "Keeps the most complete and informative contact name"),
    COMBINE("Merge All Names", "Combines different names with ' / ' (e.g. Alice / Alice Work)"),
    NEWEST("Most Recent Name", "Keeps the name from the most recently modified entry"),
    CLEAN_TITLE("Clean & Title Case", "Auto-capitalizes and removes special symbols/junk"),
    ALPHABETICAL("Alphabetical Order", "Selects the first name in alphabetical sorting")
}

enum class MergeNotesStrategy(val label: String, val description: String) {
    COMBINE_ALL("Combine All Notes", "Merges all notes and details separated by ' | '"),
    LONGEST_ONLY("Keep Longest Note", "Retains only the single most comprehensive note"),
    LATEST_ONLY("Most Recent Note", "Keeps note from the newest entry"),
    DISCARD_ALL("Discard Notes", "Clears notes to remove clutter")
}

data class SmartMergeConfig(
    val nameStrategy: MergeNameStrategy = MergeNameStrategy.LONGEST,
    val notesStrategy: MergeNotesStrategy = MergeNotesStrategy.COMBINE_ALL,
    val preserveStarred: Boolean = true,
    val combineSources: Boolean = true,
    val filterSpamInName: Boolean = true
) {
    companion object {
        fun resolveSurvivor(
            contacts: List<ContactEntity>,
            phone: String,
            config: SmartMergeConfig
        ): ContactEntity {
            if (contacts.isEmpty()) {
                return ContactEntity(name = phone, phone = phone)
            }
            if (contacts.size == 1) {
                return contacts[0]
            }

            // 1. Resolve Name
            val rawNames = contacts.map { it.name.trim() }.filter { it.isNotBlank() }
            val candidateNames = if (config.filterSpamInName) {
                val filtered = rawNames.filter { !ContactValidator.shouldRejectContact(it, phone) }
                if (filtered.isNotEmpty()) filtered else rawNames
            } else {
                rawNames
            }

            val finalName: String = when (config.nameStrategy) {
                MergeNameStrategy.LONGEST -> {
                    candidateNames.maxByOrNull { it.length } ?: phone
                }
                MergeNameStrategy.COMBINE -> {
                    val distinctNames = candidateNames.distinct()
                    if (distinctNames.size > 1) {
                        distinctNames.joinToString(" / ")
                    } else {
                        distinctNames.firstOrNull() ?: phone
                    }
                }
                MergeNameStrategy.NEWEST -> {
                    val newestContact = contacts.maxByOrNull { it.updatedAt }
                    newestContact?.name?.trim()?.takeIf { it.isNotBlank() }
                        ?: candidateNames.firstOrNull()
                        ?: phone
                }
                MergeNameStrategy.ALPHABETICAL -> {
                    candidateNames.sortedWith(String.CASE_INSENSITIVE_ORDER).firstOrNull() ?: phone
                }
                MergeNameStrategy.CLEAN_TITLE -> {
                    val best = candidateNames.maxByOrNull { it.length } ?: phone
                    toTitleCase(best.replace(Regex("[_#*~`|^]+"), " ").trim())
                }
            }

            // 2. Resolve Notes / Details
            val nonBlankDetails = contacts.map { it.details.trim() }.filter { it.isNotBlank() }
            val finalDetails: String = when (config.notesStrategy) {
                MergeNotesStrategy.COMBINE_ALL -> {
                    nonBlankDetails.distinct().joinToString(" | ")
                }
                MergeNotesStrategy.LONGEST_ONLY -> {
                    nonBlankDetails.maxByOrNull { it.length } ?: ""
                }
                MergeNotesStrategy.LATEST_ONLY -> {
                    val newestContact = contacts.maxByOrNull { it.updatedAt }
                    newestContact?.details?.trim() ?: nonBlankDetails.firstOrNull() ?: ""
                }
                MergeNotesStrategy.DISCARD_ALL -> {
                    ""
                }
            }

            // 3. Resolve Starred / Saved
            val isStarred = if (config.preserveStarred) {
                contacts.any { it.saved }
            } else {
                contacts.first().saved
            }

            // 4. Resolve Device & Source
            val bestDevice = contacts.firstOrNull { it.deviceId.isNotBlank() }?.deviceId ?: ""
            val finalSource = if (config.combineSources) {
                contacts.map { it.source.trim() }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                    .ifBlank { contacts.first().source }
            } else {
                contacts.first().source
            }

            val latestUpdate = contacts.maxOfOrNull { it.updatedAt } ?: System.currentTimeMillis()

            return contacts[0].copy(
                name = finalName.ifBlank { phone },
                phone = phone,
                details = finalDetails,
                saved = isStarred,
                source = finalSource,
                deviceId = bestDevice,
                updatedAt = latestUpdate,
                edited = true,
                syncStatus = "PENDING"
            )
        }

        private fun toTitleCase(input: String): String {
            if (input.isBlank()) return input
            return input.split(Regex("\\s+")).joinToString(" ") { word ->
                if (word.isBlank()) ""
                else word.lowercase(Locale.ROOT).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
            }
        }
    }
}
