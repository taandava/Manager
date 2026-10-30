package com.taandava.manager

import com.taandava.manager.util.PhoneUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneUtilsTest {

    @Test
    fun testNormalizePhone_tenDigitIndian() {
        assertEquals("+919876543210", PhoneUtils.normalizePhone("9876543210"))
    }

    @Test
    fun testNormalizePhone_withLeadingZero() {
        assertEquals("+919876543210", PhoneUtils.normalizePhone("09876543210"))
    }

    @Test
    fun testNormalizePhone_withPrefix91() {
        assertEquals("+919876543210", PhoneUtils.normalizePhone("919876543210"))
    }

    @Test
    fun testNormalizePhone_withPlus91() {
        assertEquals("+919876543210", PhoneUtils.normalizePhone("+91 98765 43210"))
    }

    @Test
    fun testNormalizePhone_withSymbolsAndFormatting() {
        assertEquals("+919876543210", PhoneUtils.normalizePhone("+91 (98765)-43210"))
    }

    @Test
    fun testNormalizePhone_internationalZeroZero() {
        assertEquals("+14155552671", PhoneUtils.normalizePhone("0014155552671"))
    }

    @Test
    fun testNormalizePhone_internationalPlus() {
        assertEquals("+447911123456", PhoneUtils.normalizePhone("+44 7911 123456"))
    }

    @Test
    fun testNormalizePhone_emptyAndBlank() {
        assertEquals("", PhoneUtils.normalizePhone(null))
        assertEquals("", PhoneUtils.normalizePhone(""))
        assertEquals("", PhoneUtils.normalizePhone("   "))
    }

    @Test
    fun testFormatIndianPhone() {
        assertEquals("+91 98765 43210", PhoneUtils.formatIndianPhone("+919876543210"))
        assertEquals("+91 98765 43210", PhoneUtils.formatIndianPhone("9876543210"))
        assertEquals("+14155552671", PhoneUtils.formatIndianPhone("+14155552671"))
    }

    @Test
    fun testNormalizeSearch() {
        assertEquals("alicebob123", PhoneUtils.normalizeSearch("Alice & Bob - 123!"))
        assertEquals("", PhoneUtils.normalizeSearch(""))
        assertEquals("", PhoneUtils.normalizeSearch(null))
    }
}
