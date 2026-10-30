package com.taandava.manager

import com.taandava.manager.data.ContactEntity
import com.taandava.manager.util.VcfParser
import org.junit.Assert.*
import org.junit.Test

class VcfParserTest {

    @Test
    fun testExportAndParseRoundTrip() {
        val original = listOf(
            ContactEntity(
                id = "c-101",
                name = "Alice Smith",
                phone = "+919876543210",
                details = "Important business partner",
                saved = true
            ),
            ContactEntity(
                id = "c-102",
                name = "Bob Jones",
                phone = "+919123456780",
                details = "",
                saved = false
            )
        )

        val vcfText = VcfParser.exportToVcf(original)
        assertTrue(vcfText.contains("BEGIN:VCARD"))
        assertTrue(vcfText.contains("FN:Alice Smith"))
        assertTrue(vcfText.contains("TEL;TYPE=CELL:+919876543210"))
        assertTrue(vcfText.contains("X-STARRED:1"))

        val parsed = VcfParser.parseVcf(vcfText)
        assertEquals(2, parsed.size)

        val alice = parsed.first { it.phone == "+919876543210" }
        assertEquals("Alice Smith", alice.name)
        assertEquals("Important business partner", alice.details)
        assertTrue(alice.saved)

        val bob = parsed.first { it.phone == "+919123456780" }
        assertEquals("Bob Jones", bob.name)
        assertFalse(bob.saved)
    }

    @Test
    fun testMultilineFoldedNoteUnfolding() {
        // RFC 2426/6350 multiline folded note: continuation line starts with space or tab
        val vcfContent = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Charlie Brown
            TEL;TYPE=CELL:9876543210
            NOTE:This is a very long note that was
              folded across multiple lines
             by the exporting system.
            END:VCARD
        """.trimIndent()

        val parsed = VcfParser.parseVcf(vcfContent)
        assertEquals(1, parsed.size)
        val charlie = parsed[0]
        assertEquals("Charlie Brown", charlie.name)
        assertTrue(charlie.details.contains("folded across multiple lines"))
        assertTrue(charlie.details.contains("by the exporting system."))
    }

    @Test
    fun testFallbackToNFieldWhenFNIsMissing() {
        val vcfContent = """
            BEGIN:VCARD
            VERSION:3.0
            N:Williams;David;;;
            TEL;TYPE=CELL:9876543210
            END:VCARD
        """.trimIndent()

        val parsed = VcfParser.parseVcf(vcfContent)
        assertEquals(1, parsed.size)
        assertEquals("David Williams", parsed[0].name)
    }
}

