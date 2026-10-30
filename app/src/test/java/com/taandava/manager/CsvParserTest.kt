package com.taandava.manager

import com.taandava.manager.util.CsvParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {

    @Test
    fun testSimpleCsvRow() {
        val input = "id,name,phone\n1,Alice,9876543210"
        val rows = CsvParser.parse(input)
        assertEquals(2, rows.size)
        assertArrayEquals(arrayOf("id", "name", "phone"), rows[0])
        assertArrayEquals(arrayOf("1", "Alice", "9876543210"), rows[1])
    }

    @Test
    fun testEscapedQuotes() {
        val input = "1,\"Alice \"\"Boss\"\" Smith\",9876543210"
        val rows = CsvParser.parse(input)
        assertEquals(1, rows.size)
        assertEquals("Alice \"Boss\" Smith", rows[0][1])
    }

    @Test
    fun testCommasWithinQuotes() {
        val input = "1,\"Smith, Alice\",9876543210"
        val rows = CsvParser.parse(input)
        assertEquals(1, rows.size)
        assertEquals("Smith, Alice", rows[0][1])
    }

    @Test
    fun testNewlinesWithinCell() {
        val input = "1,Alice,\"Line 1\nLine 2\""
        val rows = CsvParser.parse(input)
        assertEquals(1, rows.size)
        assertEquals("Line 1\nLine 2", rows[0][2])
    }

    @Test
    fun testEscapeRoundTrip() {
        val valueWithComma = "Smith, Alice"
        val escaped = CsvParser.escape(valueWithComma)
        assertEquals("\"Smith, Alice\"", escaped)

        val parsed = CsvParser.parse(escaped)
        assertEquals(1, parsed.size)
        assertEquals(valueWithComma, parsed[0][0])
    }
}

