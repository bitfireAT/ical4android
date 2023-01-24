/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import org.junit.Assert.*
import org.junit.Test

class FixInvalidDayOffsetPreprocessorTest {

    @Test
    fun test_FixString_NoOccurrence() {
        assertEquals(
            "Some String",
            FixInvalidDayOffsetPreprocessor.fixString("Some String"),
        )
    }

    @Test
    fun test_FixString_DayOffsetFrom_Invalid() {
        assertEquals(
            "DURATION:-P1D",
            FixInvalidDayOffsetPreprocessor.fixString("DURATION:-PT1D"),
        )
        assertEquals(
            "TRIGGER:-P2D",
            FixInvalidDayOffsetPreprocessor.fixString("TRIGGER:-PT2D"),
        )
    }

    @Test
    fun test_FixString_DayOffsetFrom_Valid() {
        assertEquals(
            "DURATION:-PT12H",
            FixInvalidDayOffsetPreprocessor.fixString("DURATION:-PT12H"),
        )
        assertEquals(
            "TRIGGER:-PT12H",
            FixInvalidDayOffsetPreprocessor.fixString("TRIGGER:-PT12H"),
        )
    }

    @Test
    fun test_RegexpForProblem_DayOffsetTo_Invalid() {
        val regex = FixInvalidDayOffsetPreprocessor.regexpForProblem()
        assertTrue(regex.matches("DURATION:PT2D"))
        assertTrue(regex.matches("TRIGGER:PT1D"))
    }

    @Test
    fun test_RegexpForProblem_DayOffsetTo_Valid() {
        val regex = FixInvalidDayOffsetPreprocessor.regexpForProblem()
        assertFalse(regex.matches("DURATION:-PT12H"))
        assertFalse(regex.matches("TRIGGER:-PT15M"))
    }

}