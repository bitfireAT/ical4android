/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import org.junit.Assert.*

import org.junit.Test
import java.io.StringReader

class EventValidatorTest {

    val tzReg = TimeZoneRegistryFactory.getInstance().createRegistry()


    // DTSTART and DTEND

    @Test
    fun testEnsureCorrectStartAndEndTime_noDtStart() {
        val event = Event().apply {
            dtEnd = DtEnd(DateTime("20000105T000000"))                  // DATETIME
        }
        assertThrows(InvalidCalendarException("Event without start time").javaClass) {
            EventValidator.correctStartAndEndTime(event)
        }

        assertThrows(InvalidCalendarException("Event without start time").javaClass) {
            Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                    "BEGIN:VEVENT\n" +
                    "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                    "DTEND;VALUE=DATE:20211116\n" +                     // DATE
                    "END:VEVENT\n" +
                    "END:VCALENDAR")).first()
        }
    }

    @Test
    fun testEnsureCorrectStartAndEndTime_dtEndBeforeDtStart() {
        val event = Event().apply {
            dtStart = DtStart(DateTime("20000105T001100"))              // DATETIME
            dtEnd = DtEnd(DateTime("20000105T000000"))                  // DATETIME
        }
        assertTrue(event.dtStart!!.date.time > event.dtEnd!!.date.time)
        EventValidator.correctStartAndEndTime(event)
        assertNull(event.dtEnd)

        val event1 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTSTART;VALUE=DATE:20211117\n" +                           // DATE
                "DTEND;VALUE=DATE:20211116\n" +                             // DATE
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertNull(event1.dtEnd)
    }


    // RRULE UNTIL and DTSTART of same type (DATETIME/DATE)

    @Test
    fun testSameTypeForDtStartAndRruleUntil_DtStartAndRruleUntilAreBothDateTimeOrDate() {
        // should do nothing when types are the same

        val event = Event().apply {
            dtStart = DtStart(DateTime("20211115T001100Z"))               // DATETIME (UTC)
            rRules.add(RRule("FREQ=MONTHLY;UNTIL=20251214T001100Z"))      // DATETIME (UTC)
        }
        assertEquals(DateTime("20211115T001100Z"), event.dtStart!!.date)
        assertEquals(DateTime("20251214T001100Z"), event.rRules.first.recur.until)
        EventValidator.sameTypeForDtStartAndRruleUntil(event.dtStart!!, event.rRules)
        assertEquals(DateTime("20211115T001100Z"), event.dtStart!!.date)
        assertEquals(DateTime("20251214T001100Z"), event.rRules.first.recur.until)

        val event1 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTSTART;VALUE=DATE:20211115\n" +                               // DATE
                "RRULE:FREQ=MONTHLY;UNTIL=20231214;BYMONTHDAY=15\n" +           // DATE
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals(Date("20231214"), event1.rRules.first.recur.until)

        val event2 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:381fb26b-2da5-4dd2-94d7-2e0874128aa7\n" +
                "DTSTART;VALUE=DATE:20080215\n" +                               // DATE
                "RRULE:FREQ=YEARLY;UNTIL=20230216;BYMONTHDAY=15\n" +            // DATE
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals(Date("20230216"), event2.rRules.first.recur.until)
    }

    @Test
    fun testSameTypeForDtStartAndRruleUntil_DtStartIsDateAndRruleUntilIsDateTime() {
        // should remove (possibly existing) time in RRULE if DTSTART value is of type DATE (not DATETIME)

        val event = Event().apply {
            dtStart = DtStart(Date("20211115"))                         // DATE
            rRules.add(RRule("FREQ=MONTHLY;UNTIL=20211214T235959Z"))    // DATETIME (UTC)
        }
        assertEquals(1639526399000, event.rRules.first.recur.until.time)
        assertEquals(DateTime("20211214T235959Z"), event.rRules.first.recur.until)
        EventValidator.sameTypeForDtStartAndRruleUntil(event.dtStart!!, event.rRules)
        assertEquals(1639440000000, event.rRules.first.recur.until.time)
        assertEquals(Date("20211214"), event.rRules.first.recur.until)

        val event1 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTSTART;VALUE=DATE:20211115\n" +                             // DATE
                "RRULE:FREQ=MONTHLY;UNTIL=20211214T235959;BYMONTHDAY=15\n"+   // DATETIME (no timezone)
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals(1639440000000, event1.rRules.first.recur.until.time)
        assertEquals(Date("20211214"), event1.rRules.first.recur.until)

        val event2 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:381fb26b-2da5-4dd2-94d7-2e0874128aa7\n" +
                "DTSTART;VALUE=DATE:20080215\n" +                           // DATE
                "RRULE:FREQ=YEARLY;UNTIL=20230214T000000Z;BYMONTHDAY=15\n"+ // DATETIME (with timezone)
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals(Date("20230214"), event2.rRules.first.recur.until)
    }

    @Test
    fun testSameTypeForDtStartAndRruleUntil_DtStartIsDateTimeAndRruleUntilIsDate() {
        // should add (possibly missing) time in RRULE if DTSTART value is of type DATETIME (not just DATE)

        val event = Event().apply {
            dtStart = DtStart(DateTime("20110605T001100Z"))         // DATETIME (UTC)
            rRules.add(
                RRule("FREQ=MONTHLY;UNTIL=20211214")                // DATE
            )
        }
        assertEquals(Date("20211214"), event.rRules.first.recur.until)
        EventValidator.sameTypeForDtStartAndRruleUntil(event.dtStart!!, event.rRules)
        assertEquals(DateTime("20211214", tzReg.getTimeZone("UTC")), event.rRules.first.recur.until)

        val event1 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTSTART;TZID=America/New_York:20211115T000000\n" +     // DATETIME (with timezone)
                "RRULE:FREQ=MONTHLY;UNTIL=20211214;BYMONTHDAY=15\n" +   // DATE
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals(DateTime("20211214"), event1.rRules.first.recur.until)

        val event2 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:381fb26b-2da5-4dd2-94d7-2e0874128aa7\n" +
                "DTSTART;VALUE=DATETIME:20080215T001100\n" +            // DATETIME (no timezone)
                "RRULE:FREQ=YEARLY;UNTIL=20230214;BYMONTHDAY=15\n" +    // DATE
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals(DateTime("20230214"), event2.rRules.first.recur.until)
    }


    // RRULE UNTIL time before DTSTART time

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleNoUntil() {
        assertFalse(
            EventValidator.hasUntilBeforeDtStart(
                DtStart(DateTime("20220531T010203")),
                RRule()
            )
        )
    }


    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_TimeBeforeDtStart() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(DtStart("20220912"), RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(DateTime("20220911T235959Z"))
            .build())))
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_DateBeforeDtStart() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(DtStart("20220531"), RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(DateTime("20220530"))
            .build())))
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_TimeAfterDtStart() {
        assertFalse(
            EventValidator.hasUntilBeforeDtStart(
                DtStart("20200912"), RRule(
                    Recur.Builder()
                        .frequency(Recur.Frequency.DAILY)
                        .until(DateTime("20220912T000001Z"))
                        .build()
                )
            )
        )
    }


    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_DateBeforeDtStart() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(DtStart(DateTime("20220531T010203")), RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(Date("20220530"))
            .build())))
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_TimeBeforeDtStart() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(DtStart(DateTime("20220531T010203")), RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(DateTime("20220531T010202"))
            .build())))
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_TimeAtDtStart() {
        assertFalse(
            EventValidator.hasUntilBeforeDtStart(
                DtStart(DateTime("20220531T010203")), RRule(
                    Recur.Builder()
                        .frequency(Recur.Frequency.DAILY)
                        .until(DateTime("20220531T010203"))
                        .build()
                )
            )
        )
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_TimeAfterDtStart() {
        assertFalse(
            EventValidator.hasUntilBeforeDtStart(
                DtStart(DateTime("20220531T010203")), RRule(
                    Recur.Builder()
                        .frequency(Recur.Frequency.DAILY)
                        .until(DateTime("20220531T010204"))
                        .build()
                )
            )
        )
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
        EventValidator.removeRRulesWithUntilBeforeDtStart(dtStart, rrules)
        assertArrayEquals(arrayOf(
            // rRuleBefore has been removed because RRULE UNTIL is before DTSTART
            rruleAfter
        ), rrules.toTypedArray())
    }

}