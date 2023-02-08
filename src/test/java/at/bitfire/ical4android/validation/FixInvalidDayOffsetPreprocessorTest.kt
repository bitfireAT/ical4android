/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration

class FixInvalidDayOffsetPreprocessorTest {

    private fun fixStringAndAssert(expected: String, string: String) {
        val fixed = FixInvalidDayOffsetPreprocessor.fixString(string)
        Duration.parse(fixed.substring(fixed.indexOf(':') + 1))
        assertEquals(expected, fixed)
    }

    @Test
    fun test_FixString_NoOccurrence() {
        assertEquals(
            "Some String",
            FixInvalidDayOffsetPreprocessor.fixString("Some String"),
        )
    }

    @Test
    fun test_FixString_DayOffsetFrom_Invalid() {
        fixStringAndAssert("DURATION:-P1D", "DURATION:-PT1D")
        fixStringAndAssert("TRIGGER:-P2D", "TRIGGER:-PT2D")

        fixStringAndAssert("DURATION:-P1D", "DURATION:-P1DT")
        fixStringAndAssert("TRIGGER:-P2D", "TRIGGER:-P2DT")
    }

    @Test
    fun test_FixString_DayOffsetFrom_Valid() {
        fixStringAndAssert("DURATION:-PT12H", "DURATION:-PT12H")
        fixStringAndAssert("TRIGGER:-PT12H", "TRIGGER:-PT12H")
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