/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.format.DateTimeParseException

class FixMissingTPrefixPreprocessorTest {

    private fun fixAndAssert(expected: String, testValue: String) {

        // Fix the duration string
        val fixed = FixMissingTPrefixPreprocessor.fixString(testValue)

        // Test the duration can now be parsed
        for (line in fixed.split('\n')) {
            val duration = line.substring(line.indexOf(':') + 1)
            try {
                Duration.parse(duration)
            } catch (e: DateTimeParseException) {
                throw AssertionError("<$duration> is not a valid duration.", e)
            }
        }

        // Assert
        assertEquals(expected, fixed)
    }

    @Test
    fun test_FixString_NoOccurrence() {
        assertEquals(
            "Some String",
            FixMissingTPrefixPreprocessor.fixString("Some String"),
        )
    }

    @Test
    fun test_FixString_MissingTFrom_Invalid() {
        fixAndAssert("TRIGGER:-PT5S", "TRIGGER:-P5S")
        fixAndAssert("DURATION:-PT5S", "DURATION:-P5S")
    }

    @Test
    fun test_FixString_MissingTFrom_Valid() {
        fixAndAssert("TRIGGER:-PT5S", "TRIGGER:-PT5S")
        fixAndAssert("DURATION:-PT5S", "DURATION:-PT5S")
    }

    @Test
    fun test_FixString_MissingTFromMultiple_Invalid() {
        fixAndAssert("DURATION:-PT5H\nTRIGGER:-PT5S", "DURATION:-P5H\nTRIGGER:-P5S")
    }

    @Test
    fun test_FixString_MissingTFromMultiple_Valid() {
        fixAndAssert("DURATION:-PT5S\nTRIGGER:-PT5M", "DURATION:-PT5S\nTRIGGER:-PT5M")
    }

    @Test
    fun test_FixString_MissingTFromMultiple_Mixed() {
        fixAndAssert(
            "DURATION:-PT5S\nTRIGGER:-PT7H\nTRIGGER:-PT2M",
            "DURATION:-PT5S\nTRIGGER:-P7H\nTRIGGER:-P2M"
        )
    }

    @Test
    fun test_RegexpForProblem_MissingTTo_Invalid() {
        val regex = FixMissingTPrefixPreprocessor.regexpForProblem()
        assertTrue(regex.matches("DURATION:P2S"))
        assertTrue(regex.matches("TRIGGER:-P1H"))
        assertTrue(regex.matches("TRIGGER:-P5S"))
        assertTrue(regex.matches("TRIGGER:P5M"))
    }

    @Test
    fun test_RegexpForProblem_MissingTTo_Valid() {
        val regex = FixMissingTPrefixPreprocessor.regexpForProblem()
        assertFalse(regex.matches("DURATION:-PT12H"))
        assertFalse(regex.matches("TRIGGER:-PT15M"))
        assertFalse(regex.matches("TRIGGER:-PT5S"))
    }

}