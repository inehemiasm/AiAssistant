package com.neo.chevere.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberUtilsTest {

    @Test
    fun testZero() {
        assertEquals("zero", NumberUtils.toWords(0))
    }

    @Test
    fun testBasicNumbers() {
        assertEquals("one", NumberUtils.toWords(1))
        assertEquals("nine", NumberUtils.toWords(9))
        assertEquals("ten", NumberUtils.toWords(10))
        assertEquals("eleven", NumberUtils.toWords(11))
        assertEquals("nineteen", NumberUtils.toWords(19))
    }

    @Test
    fun testTens() {
        assertEquals("twenty", NumberUtils.toWords(20))
        assertEquals("twenty-five", NumberUtils.toWords(25))
        assertEquals("fifty-five", NumberUtils.toWords(55))
        assertEquals("ninety-nine", NumberUtils.toWords(99))
    }

    @Test
    fun testHundreds() {
        assertEquals("one hundred", NumberUtils.toWords(100))
        assertEquals("one hundred five", NumberUtils.toWords(105))
        assertEquals("three hundred fifty-four", NumberUtils.toWords(354))
        assertEquals("nine hundred ninety-nine", NumberUtils.toWords(999))
    }

    @Test
    fun testThousands() {
        assertEquals("one thousand", NumberUtils.toWords(1000))
        assertEquals("five thousand five hundred fifty-five", NumberUtils.toWords(5555))
        assertEquals("twelve thousand three hundred forty-five", NumberUtils.toWords(12345))
    }

    @Test
    fun testNegativeNumbers() {
        assertEquals("minus five", NumberUtils.toWords(-5))
        assertEquals("minus fifty-five", NumberUtils.toWords(-55))
    }
}
