/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import junit.framework.Assert.assertFalse
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RruleUntilAfterStartRuleTest {

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleNoUntil() {
        assertFalse(RruleUntilAfterStartRule.hasUntilBeforeDtStart(DtStart(DateTime("20220531T010203")), RRule()))
    }


    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_TimeBeforeDtStart() {
        assertTrue(RruleUntilAfterStartRule.hasUntilBeforeDtStart(DtStart("20200912"), RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(DateTime("20220911T235959Z"))
            .build())))
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_DateBeforeDtStart() {
        assertTrue(RruleUntilAfterStartRule.hasUntilBeforeDtStart(DtStart("20220531"), RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(DateTime("20220530"))
            .build())))
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_TimeAfterDtStart() {
        assertFalse(RruleUntilAfterStartRule.hasUntilBeforeDtStart(DtStart("20200912"), RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(DateTime("20220912T000001Z"))
            .build())))
    }


    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_DateBeforeDtStart() {
        assertTrue(RruleUntilAfterStartRule.hasUntilBeforeDtStart(DtStart(DateTime("20220531T010203")), RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(Date("20220530"))
            .build())))
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_TimeBeforeDtStart() {
        assertTrue(RruleUntilAfterStartRule.hasUntilBeforeDtStart(DtStart(DateTime("20220531T010203")), RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(DateTime("20220531T010202"))
            .build())))
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_TimeAtDtStart() {
        assertFalse(RruleUntilAfterStartRule.hasUntilBeforeDtStart(DtStart(DateTime("20220531T010203")), RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(DateTime("20220531T010203"))
            .build())))
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_TimeAfterDtStart() {
        assertFalse(RruleUntilAfterStartRule.hasUntilBeforeDtStart(DtStart(DateTime("20220531T010203")), RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(DateTime("20220531T010204"))
            .build())))
    }


    @Test
    fun testRemoveRRulesWithUntilBeforeDtStart() {
        val dtStart = DtStart(DateTime("20220531T125304"))
        val rruleBefore = RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(DateTime("20220531T125303"))
            .build())
        val rruleAfter = RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(DateTime("20220531T125305"))
            .build())

        val rrules = mutableListOf(
            rruleBefore,
            rruleAfter
        )
        RruleUntilAfterStartRule.removeRRulesWithUntilBeforeDtStart(dtStart, rrules)
        assertArrayEquals(arrayOf(
            // rRuleBefore has been removed because RRULE UNTIL is before DTSTART
            rruleAfter
        ), rrules.toTypedArray())
    }

}