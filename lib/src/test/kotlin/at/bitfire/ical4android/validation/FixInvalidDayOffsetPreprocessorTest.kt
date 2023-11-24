/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class FixInvalidDayOffsetPreprocessorTest {

    private fun fixAndAssert(expected: String, testValue: String) {

        // Fix the duration string
        val fixed = FixInvalidDayOffsetPreprocessor.fixString(testValue)

        // Test the duration can now be parsed
        for (line in fixed.split('\n')) {
            val duration = line.substring(line.indexOf(':') + 1)
            Duration.parse(duration)
        }

        // Assert
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
        fixAndAssert("DURATION:-P1D", "DURATION:-PT1D")
        fixAndAssert("TRIGGER:-P2D", "TRIGGER:-PT2D")

        fixAndAssert("DURATION:-P1D", "DURATION:-P1DT")
        fixAndAssert("TRIGGER:-P2D", "TRIGGER:-P2DT")
    }

    @Test
    fun test_FixString_DayOffsetFrom_Valid() {
        fixAndAssert("DURATION:-PT12H", "DURATION:-PT12H")
        fixAndAssert("TRIGGER:-PT12H", "TRIGGER:-PT12H")
    }

    @Test
    fun test_FixString_DayOffsetFromMultiple_Invalid() {
        fixAndAssert("DURATION:-P1D\nTRIGGER:-P2D", "DURATION:-PT1D\nTRIGGER:-PT2D")
        fixAndAssert("DURATION:-P1D\nTRIGGER:-P2D", "DURATION:-P1DT\nTRIGGER:-P2DT")
    }

    @Test
    fun test_FixString_DayOffsetFromMultiple_Valid() {
        fixAndAssert("DURATION:-PT12H\nTRIGGER:-PT12H", "DURATION:-PT12H\nTRIGGER:-PT12H")
    }

    @Test
    fun test_FixString_DayOffsetFromMultiple_Mixed() {
        fixAndAssert("DURATION:-P1D\nDURATION:-PT12H\nTRIGGER:-P2D", "DURATION:-PT1D\nDURATION:-PT12H\nTRIGGER:-PT2D")
        fixAndAssert("DURATION:-P1D\nDURATION:-PT12H\nTRIGGER:-P2D", "DURATION:-P1DT\nDURATION:-PT12H\nTRIGGER:-P2DT")
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