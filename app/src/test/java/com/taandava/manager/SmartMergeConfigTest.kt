package com.taandava.manager

import com.taandava.manager.data.*
import org.junit.Assert.*
import org.junit.Test

class SmartMergeConfigTest {

    @Test
    fun testNameStrategyLongest() {
        val contacts = listOf(
            ContactEntity(id = "1", name = "Alex", phone = "+919876543210", updatedAt = 1000L),
            ContactEntity(id = "2", name = "Alexander Great", phone = "+919876543210", updatedAt = 2000L),
            ContactEntity(id = "3", name = "Alex G", phone = "+919876543210", updatedAt = 3000L)
        )
        val config = SmartMergeConfig(nameStrategy = MergeNameStrategy.LONGEST)
        val survivor = SmartMergeConfig.resolveSurvivor(contacts, "+919876543210", config)
        assertEquals("Alexander Great", survivor.name)
    }

    @Test
    fun testNameStrategyAlphabetical() {
        val contacts = listOf(
            ContactEntity(id = "1", name = "Charlie", phone = "+919876543210"),
            ContactEntity(id = "2", name = "Alice", phone = "+919876543210"),
            ContactEntity(id = "3", name = "Bob", phone = "+919876543210")
        )
        val config = SmartMergeConfig(nameStrategy = MergeNameStrategy.ALPHABETICAL)
        val survivor = SmartMergeConfig.resolveSurvivor(contacts, "+919876543210", config)
        assertEquals("Alice", survivor.name)
    }

    @Test
    fun testNameStrategyCombine() {
        val contacts = listOf(
            ContactEntity(id = "1", name = "Alice", phone = "+919876543210"),
            ContactEntity(id = "2", name = "Alice Work", phone = "+919876543210")
        )
        val config = SmartMergeConfig(nameStrategy = MergeNameStrategy.COMBINE)
        val survivor = SmartMergeConfig.resolveSurvivor(contacts, "+919876543210", config)
        assertEquals("Alice / Alice Work", survivor.name)
    }

    @Test
    fun testNameStrategyNewest() {
        val contacts = listOf(
            ContactEntity(id = "1", name = "Old Name", phone = "+919876543210", updatedAt = 1000L),
            ContactEntity(id = "2", name = "Newest Name", phone = "+919876543210", updatedAt = 5000L),
            ContactEntity(id = "3", name = "Middle Name", phone = "+919876543210", updatedAt = 3000L)
        )
        val config = SmartMergeConfig(nameStrategy = MergeNameStrategy.NEWEST)
        val survivor = SmartMergeConfig.resolveSurvivor(contacts, "+919876543210", config)
        assertEquals("Newest Name", survivor.name)
    }

    @Test
    fun testNotesStrategyCombineAll() {
        val contacts = listOf(
            ContactEntity(id = "1", name = "Ravi", phone = "+919876543210", details = "Work: Mumbai"),
            ContactEntity(id = "2", name = "Ravi Sharma", phone = "+919876543210", details = "Personal Email: ravi@test.com"),
            ContactEntity(id = "3", name = "Ravi", phone = "+919876543210", details = "Work: Mumbai") // duplicate note
        )
        val config = SmartMergeConfig(notesStrategy = MergeNotesStrategy.COMBINE_ALL)
        val survivor = SmartMergeConfig.resolveSurvivor(contacts, "+919876543210", config)
        assertTrue(survivor.details.contains("Work: Mumbai"))
        assertTrue(survivor.details.contains("Personal Email: ravi@test.com"))
        // Deduplicated note should only appear once
        assertEquals(1, survivor.details.split("Work: Mumbai").size - 1)
    }

    @Test
    fun testNotesStrategyLongestOnly() {
        val contacts = listOf(
            ContactEntity(id = "1", name = "Ravi", phone = "+919876543210", details = "Short note"),
            ContactEntity(id = "2", name = "Ravi", phone = "+919876543210", details = "This is a much longer and more comprehensive note")
        )
        val config = SmartMergeConfig(notesStrategy = MergeNotesStrategy.LONGEST_ONLY)
        val survivor = SmartMergeConfig.resolveSurvivor(contacts, "+919876543210", config)
        assertEquals("This is a much longer and more comprehensive note", survivor.details)
    }

    @Test
    fun testPreserveStarred() {
        val contacts = listOf(
            ContactEntity(id = "1", name = "Pooja", phone = "+919876543210", saved = false),
            ContactEntity(id = "2", name = "Pooja Sharma", phone = "+919876543210", saved = true),
            ContactEntity(id = "3", name = "Pooja Verma", phone = "+919876543210", saved = false)
        )
        val config = SmartMergeConfig(preserveStarred = true)
        val survivor = SmartMergeConfig.resolveSurvivor(contacts, "+919876543210", config)
        assertTrue(survivor.saved)
    }

    @Test
    fun testFilterSpamInName() {
        val contacts = listOf(
            ContactEntity(id = "1", name = "AD-HDFCBK-S", phone = "+919876543210"),
            ContactEntity(id = "2", name = "HDFC Relationship Manager", phone = "+919876543210")
        )
        val config = SmartMergeConfig(filterSpamInName = true, nameStrategy = MergeNameStrategy.LONGEST)
        val survivor = SmartMergeConfig.resolveSurvivor(contacts, "+919876543210", config)
        assertEquals("HDFC Relationship Manager", survivor.name)
    }
}
