package com.taandava.manager

import com.taandava.manager.util.NameDisambiguator
import org.junit.Assert.*
import org.junit.Test

class NameDisambiguatorTest {

    @Test
    fun testStandardNameDisambiguation() {
        val original = "Ramesh Kumar"
        assertEquals("Ramesh Kumar", NameDisambiguator.disambiguateName(original, 0))
        assertEquals("Rameshh Kumar", NameDisambiguator.disambiguateName(original, 1))
        assertEquals("Rameshhh Kumar", NameDisambiguator.disambiguateName(original, 2))
        assertEquals("Rameshhhh Kumar", NameDisambiguator.disambiguateName(original, 3))
    }

    @Test
    fun testPrefixPreservation() {
        // "Dr." must not have its letter repeated; "Ramesh" must have its letter repeated.
        val nameWithPrefix = "Dr. Ramesh Kumar"
        assertEquals("Dr. Ramesh Kumar", NameDisambiguator.disambiguateName(nameWithPrefix, 0))
        assertEquals("Dr. Rameshh Kumar", NameDisambiguator.disambiguateName(nameWithPrefix, 1))
        assertEquals("Dr. Rameshhh Kumar", NameDisambiguator.disambiguateName(nameWithPrefix, 2))

        val mrPrefix = "mr ramesh kumar"
        assertEquals("Mr. Ramesh Kumar", NameDisambiguator.disambiguateName(mrPrefix, 0))
        assertEquals("Mr. Rameshh Kumar", NameDisambiguator.disambiguateName(mrPrefix, 1))

        val shriPrefix = "Shri Ramesh Sharma"
        assertEquals("Shri Ramesh Sharma", NameDisambiguator.disambiguateName(shriPrefix, 0))
        assertEquals("Shri Rameshh Sharma", NameDisambiguator.disambiguateName(shriPrefix, 1))
    }

    @Test
    fun testSuffixPreservation() {
        val nameWithSuffix = "Ramesh Kumar Jr."
        assertEquals("Ramesh Kumar Jr.", NameDisambiguator.disambiguateName(nameWithSuffix, 0))
        assertEquals("Rameshh Kumar Jr.", NameDisambiguator.disambiguateName(nameWithSuffix, 1))
        assertEquals("Rameshhh Kumar Jr.", NameDisambiguator.disambiguateName(nameWithSuffix, 2))

        val romanSuffix = "john doe ii"
        assertEquals("John Doe II", NameDisambiguator.disambiguateName(romanSuffix, 0))
        assertEquals("Johnn Doe II", NameDisambiguator.disambiguateName(romanSuffix, 1))
    }

    @Test
    fun testPrefixAndSuffixCombined() {
        val full = "dr. john doe jr."
        assertEquals("Dr. John Doe Jr.", NameDisambiguator.disambiguateName(full, 0))
        assertEquals("Dr. Johnn Doe Jr.", NameDisambiguator.disambiguateName(full, 1))
        assertEquals("Dr. Johnnn Doe Jr.", NameDisambiguator.disambiguateName(full, 2))
    }

    @Test
    fun testSingleWordName() {
        val single = "suresh"
        assertEquals("Suresh", NameDisambiguator.disambiguateName(single, 0))
        assertEquals("Sureshh", NameDisambiguator.disambiguateName(single, 1))
        assertEquals("Sureshhh", NameDisambiguator.disambiguateName(single, 2))
    }

    @Test
    fun testSentenceCaseConversion() {
        val allCaps = "RAMESH KUMAR"
        assertEquals("Ramesh Kumar", NameDisambiguator.disambiguateName(allCaps, 0))
        assertEquals("Rameshh Kumar", NameDisambiguator.disambiguateName(allCaps, 1))

        val allLower = "ramesh kumar"
        assertEquals("Ramesh Kumar", NameDisambiguator.disambiguateName(allLower, 0))
        assertEquals("Rameshh Kumar", NameDisambiguator.disambiguateName(allLower, 1))
    }

    @Test
    fun testExtractNameKey() {
        val key1 = NameDisambiguator.extractNameKey("Dr. Ramesh Kumar")
        val key2 = NameDisambiguator.extractNameKey("ramesh kumar")
        val key3 = NameDisambiguator.extractNameKey("Ramesh Kumar Jr.")
        val key4 = NameDisambiguator.extractNameKey("Suresh Kumar")

        assertEquals(key1, key2)
        assertEquals(key1, key3)
        assertNotEquals(key1, key4)
    }

    @Test
    fun testAppendLastAlphabet() {
        assertEquals("Rameshh", NameDisambiguator.appendLastAlphabet("Ramesh", 1))
        assertEquals("Rameshhh", NameDisambiguator.appendLastAlphabet("Ramesh", 2))
        assertEquals("Rameshh.", NameDisambiguator.appendLastAlphabet("Ramesh.", 1))
        assertEquals("Aa", NameDisambiguator.appendLastAlphabet("A", 1))
    }
}

