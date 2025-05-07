/*
 * This file is part of ical4android which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixInvalidUtcOffsetPreprocessorTest {

    @Test
    fun test_FixString_NoOccurrence() {
        assertEquals(
            "Some String",
            FixInvalidUtcOffsetPreprocessor.fixString("Some String"))
    }

    @Test
    fun test_FixString_TzOffsetFrom_Invalid() {
        assertEquals("TZOFFSETFROM:+005730",
            FixInvalidUtcOffsetPreprocessor.fixString("TZOFFSETFROM:+5730"))
    }

    @Test
    fun test_FixString_TzOffsetFrom_Valid() {
        assertEquals("TZOFFSETFROM:+005730",
            FixInvalidUtcOffsetPreprocessor.fixString("TZOFFSETFROM:+005730"))
    }

    @Test
    fun test_FixString_TzOffsetTo_Invalid() {
        assertEquals("TZOFFSETTO:+005730",
            FixInvalidUtcOffsetPreprocessor.fixString("TZOFFSETTO:+5730"))
    }

    @Test
    fun test_FixString_TzOffsetTo_Valid() {
        assertEquals("TZOFFSETTO:+005730",
            FixInvalidUtcOffsetPreprocessor.fixString("TZOFFSETTO:+005730"))
    }


    @Test
    fun test_RegexpForProblem_TzOffsetTo_Invalid() {
        val regex = FixInvalidUtcOffsetPreprocessor.regexpForProblem()
        assertTrue(regex.matches("TZOFFSETTO:+5730"))
    }

    @Test
    fun test_RegexpForProblem_TzOffsetTo_Valid() {
        val regex = FixInvalidUtcOffsetPreprocessor.regexpForProblem()
        assertFalse(regex.matches("TZOFFSETTO:+005730"))
    }

}