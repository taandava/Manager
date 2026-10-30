package com.taandava.manager

import com.taandava.manager.data.*
import com.taandava.manager.util.ContactValidator
import com.taandava.manager.util.PhoneUtils
import org.junit.Assert.*
import org.junit.Test

class DeduplicationLogicTest {

    @Test
    fun testDuplicateGroupingLogic() {
        val contacts = listOf(
            ContactEntity(id = "1", name = "Alice Doe", phone = "+91 9876543210"),
            ContactEntity(id = "2", name = "Alice", phone = "098765 43210"),
            ContactEntity(id = "3", name = "Bob Smith", phone = "+91 9123456780"),
            ContactEntity(id = "4", name = "Alice Work", phone = "9876543210")
        )

        val map = LinkedHashMap<String, MutableList<ContactEntity>>()
        for (c in contacts) {
            val norm = PhoneUtils.normalizePhone(c.phone)
            if (norm.length >= 10) {
                map.getOrPut(norm) { mutableListOf() }.add(c)
            }
        }

        val duplicateGroups = mutableListOf<DuplicateGroup>()
        for ((phone, group) in map) {
            if (group.size > 1) {
                duplicateGroups.add(DuplicateGroup(phone, group))
            }
        }

        assertEquals(1, duplicateGroups.size)
        val aliceGroup = duplicateGroups[0]
        assertEquals("+919876543210", aliceGroup.phone)
        assertEquals(3, aliceGroup.contacts.size)
    }

    @Test
    fun testOptimisticDuplicateRemoval() {
        val group1 = DuplicateGroup(
            phone = "9876543210",
            contacts = listOf(
                ContactEntity(id = "c1", name = "Alice Main", phone = "9876543210"),
                ContactEntity(id = "c2", name = "Alice Duplicate", phone = "9876543210")
            )
        )
        val group2 = DuplicateGroup(
            phone = "9998887770",
            contacts = listOf(
                ContactEntity(id = "c3", name = "Charlie 1", phone = "9998887770"),
                ContactEntity(id = "c4", name = "Charlie 2", phone = "9998887770"),
                ContactEntity(id = "c5", name = "Charlie 3", phone = "9998887770")
            )
        )

        var currentGroups = listOf(group1, group2)

        // User deletes c2 from group1
        val deletingId = "c2"
        currentGroups = currentGroups.mapNotNull { group ->
            if (group.contacts.any { it.id == deletingId }) {
                val remaining = group.contacts.filterNot { it.id == deletingId }
                if (remaining.size > 1) group.copy(contacts = remaining) else null
            } else {
                group
            }
        }

        // Group 1 should be completely resolved (only 1 survivor left -> no longer a duplicate group)
        assertEquals(1, currentGroups.size)
        assertEquals("9998887770", currentGroups[0].phone)

        // Now user deletes c5 from group2
        val deletingId2 = "c5"
        currentGroups = currentGroups.mapNotNull { group ->
            if (group.contacts.any { it.id == deletingId2 }) {
                val remaining = group.contacts.filterNot { it.id == deletingId2 }
                if (remaining.size > 1) group.copy(contacts = remaining) else null
            } else {
                group
            }
        }

        // Group 2 should still exist with 2 contacts
        assertEquals(1, currentGroups.size)
        assertEquals(2, currentGroups[0].contacts.size)
        assertTrue(currentGroups[0].contacts.none { it.id == "c5" })
    }

    @Test
    fun testContactValidatorFiltering() {
        assertTrue(ContactValidator.shouldRejectContact("VM-HDFCBK", "9876543210", "Bank SMS"))
        assertFalse(ContactValidator.shouldRejectContact("Ramesh Kumar", "9876543210", "Friend"))
    }

    @Test
    fun testSmartMergeCustomizationStrategies() {
        val c1 = ContactEntity(
            id = "1",
            name = "Ramesh",
            phone = "+919876543210",
            details = "Office manager",
            saved = false,
            updatedAt = 1000L,
            source = "Device",
            deviceId = "DeviceA"
        )
        val c2 = ContactEntity(
            id = "2",
            name = "Ramesh Kumar Sharma",
            phone = "+919876543210",
            details = "Friend from college",
            saved = true,
            updatedAt = 2000L,
            source = "Google",
            deviceId = ""
        )
        val c3 = ContactEntity(
            id = "3",
            name = "Ramesh Work",
            phone = "+919876543210",
            details = "Accounts department",
            saved = false,
            updatedAt = 3000L,
            source = "WhatsApp",
            deviceId = "DeviceB"
        )
        val group = listOf(c1, c2, c3)

        // 1. Longest Name Strategy
        val longestConfig = SmartMergeConfig(
            nameStrategy = MergeNameStrategy.LONGEST,
            notesStrategy = MergeNotesStrategy.COMBINE_ALL,
            preserveStarred = true,
            combineSources = true
        )
        val survivor1 = SmartMergeConfig.resolveSurvivor(group, "+919876543210", longestConfig)
        assertEquals("Ramesh Kumar Sharma", survivor1.name)
        assertTrue(survivor1.saved) // preserved star
        assertTrue(survivor1.details.contains("Office manager"))
        assertTrue(survivor1.details.contains("Friend from college"))
        assertTrue(survivor1.details.contains("Accounts department"))
        assertTrue(survivor1.source.contains("Device") && survivor1.source.contains("Google") && survivor1.source.contains("WhatsApp"))

        // 2. Combine Names Strategy
        val combineConfig = SmartMergeConfig(
            nameStrategy = MergeNameStrategy.COMBINE,
            notesStrategy = MergeNotesStrategy.LONGEST_ONLY
        )
        val survivor2 = SmartMergeConfig.resolveSurvivor(group, "+919876543210", combineConfig)
        assertEquals("Ramesh / Ramesh Kumar Sharma / Ramesh Work", survivor2.name)
        assertEquals("Friend from college", survivor2.details) // longest note

        // 3. Newest Name Strategy
        val newestConfig = SmartMergeConfig(
            nameStrategy = MergeNameStrategy.NEWEST,
            notesStrategy = MergeNotesStrategy.LATEST_ONLY
        )
        val survivor3 = SmartMergeConfig.resolveSurvivor(group, "+919876543210", newestConfig)
        assertEquals("Ramesh Work", survivor3.name) // c3 had updatedAt = 3000L
        assertEquals("Accounts department", survivor3.details)

        // 4. Clean Title Case Strategy
        val messy = listOf(
            ContactEntity(id = "m1", name = "ramesh kumar", phone = "+919876543210"),
            ContactEntity(id = "m2", name = "RAMESH", phone = "+919876543210")
        )
        val titleConfig = SmartMergeConfig(
            nameStrategy = MergeNameStrategy.CLEAN_TITLE
        )
        val survivor4 = SmartMergeConfig.resolveSurvivor(messy, "+919876543210", titleConfig)
        assertEquals("Ramesh Kumar", survivor4.name)

        // 5. Discard Notes Strategy
        val discardConfig = SmartMergeConfig(
            notesStrategy = MergeNotesStrategy.DISCARD_ALL
        )
        val survivor5 = SmartMergeConfig.resolveSurvivor(group, "+919876543210", discardConfig)
        assertEquals("", survivor5.details)
    }

    @Test
    fun testExactPhoneDuplicateFiltering() {
        val phoneRegex = Regex("^\\+?[0-9]{10,15}$")

        val contacts = listOf(
            ContactEntity(id = "1", name = "Valid Exact 1", phone = "+919876543210"),
            ContactEntity(id = "2", name = "Valid Exact 2", phone = "9876543210"), // Normalizes to +919876543210
            ContactEntity(id = "3", name = "Invalid Short", phone = "12345"),
            ContactEntity(id = "4", name = "Invalid Blank", phone = ""),
            ContactEntity(id = "5", name = "Another Valid", phone = "+919123456789")
        )

        val groups = contacts
            .mapNotNull { c ->
                val norm = PhoneUtils.normalizePhone(c.phone)
                if (norm.matches(phoneRegex)) norm to c else null
            }
            .groupBy({ it.first }, { it.second })
            .filter { it.value.size > 1 }
            .map { DuplicateGroup(it.key, it.value) }

        assertEquals(1, groups.size)
        assertEquals("+919876543210", groups[0].phone)
        assertEquals(2, groups[0].contacts.size)
    }

    @Test
    fun testSameNameDifferentNumbersGroupingAndDisambiguation() {
        val contacts = listOf(
            ContactEntity(id = "1", name = "Dr. Ramesh Kumar", phone = "+919876543210"),
            ContactEntity(id = "2", name = "dr. ramesh kumar", phone = "+919123456789"),
            ContactEntity(id = "3", name = "DR RAMESH KUMAR", phone = "+919988776655"),
            ContactEntity(id = "4", name = "Suresh Verma", phone = "+919000011111")
        )

        // Group by NameDisambiguator.extractNameKey
        val grouped = contacts
            .groupBy { com.taandava.manager.util.NameDisambiguator.extractNameKey(it.name) }
            .filter { it.key.isNotBlank() && it.value.map { c -> PhoneUtils.normalizePhone(c.phone) }.distinct().size > 1 }
            .map { (key, list) ->
                SameNameGroup(
                    nameKey = key,
                    displayName = list.first().name,
                    contacts = list
                )
            }

        assertEquals(1, grouped.size)
        val rameshGroup = grouped[0]
        assertEquals("ramesh kumar", rameshGroup.nameKey)
        assertEquals(3, rameshGroup.contacts.size)

        // Verify sequential disambiguation with sentence casing and prefix preservation
        val disambiguatedNames = rameshGroup.contacts.mapIndexed { idx, c ->
            com.taandava.manager.util.NameDisambiguator.disambiguateName(
                rawName = c.name,
                extraRepeats = idx,
                applySentenceCase = true
            )
        }

        assertEquals("Dr. Ramesh Kumar", disambiguatedNames[0])   // 0 repeats -> base sentence case
        assertEquals("Dr. Rameshh Kumar", disambiguatedNames[1])  // 1 repeat -> 2 h's
        assertEquals("Dr. Rameshhh Kumar", disambiguatedNames[2]) // 2 repeats -> 3 h's
    }

    @Test
    fun testSameNameSuffixPreservationDisambiguation() {
        val contacts = listOf(
            ContactEntity(id = "1", name = "John Smith Jr.", phone = "+919876543210"),
            ContactEntity(id = "2", name = "john smith jr", phone = "+919123456789")
        )

        val disambiguated = contacts.mapIndexed { idx, c ->
            com.taandava.manager.util.NameDisambiguator.disambiguateName(
                rawName = c.name,
                extraRepeats = idx,
                applySentenceCase = true
            )
        }

        assertEquals("John Smith Jr.", disambiguated[0])
        assertEquals("Johnn Smith Jr.", disambiguated[1])
    }

    @Test
    fun testSelectionBatchOperations() {
        val allPhones = setOf("+919876543210", "+919123456789", "+919988776655")
        var selected = emptySet<String>()

        // Toggle one
        selected = selected + "+919876543210"
        assertEquals(1, selected.size)

        // Select all
        selected = allPhones
        assertEquals(3, selected.size)

        // Deselect one
        selected = selected - "+919123456789"
        assertEquals(2, selected.size)

        // Clear all
        selected = emptySet()
        assertTrue(selected.isEmpty())
    }
}
