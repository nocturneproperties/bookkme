package com.bookk.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class UtilTest {

    @Test
    fun `should return true when range is fully contained in other range`() {
        assertTrue((2..5).containedIn(0..10))
    }

    @Test
    fun `should return true when range exactly matches other range`() {
        assertTrue((0..10).containedIn(0..10))
    }

    @Test
    fun `should return false when range starts before other range`() {
        assertFalse((-1..5).containedIn(0..10))
    }

    @Test
    fun `should return false when range ends after other range`() {
        assertFalse((5..11).containedIn(0..10))
    }

    @Test
    fun `should return false when range is fully outside other range`() {
        assertFalse((20..30).containedIn(0..10))
    }
}
