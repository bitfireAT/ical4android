/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import at.bitfire.ical4android.Event
import at.bitfire.ical4android.InvalidCalendarException
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.util.*

class EventValidatorTest {

    val tzReg = TimeZoneRegistryFactory.getInstance().createRegistry()


    // DTSTART and DTEND

    @Test
    fun testEnsureCorrectStartAndEndTime_noDtStart() {
        assertThrows(InvalidCalendarException::class.java) {
            val event = Event().apply {
                dtEnd = DtEnd(DateTime("20000105T000000"))  // DATETIME
                // no dtStart
            }
            EventValidator.correctStartAndEndTime(event)
        }

        assertThrows(InvalidCalendarException::class.java) {
            Event.eventsFromReader(StringReader(
                "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTEND;VALUE=DATE:20211116\n" +                   // DATE
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

        val event1 = Event.eventsFromReader(StringReader(
            "BEGIN:VCALENDAR\n" +
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

        val event1 = Event.eventsFromReader(StringReader(
            "BEGIN:VCALENDAR\n" +
               "BEGIN:VEVENT\n" +
               "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
               "DTSTART;VALUE=DATE:20211115\n" +                               // DATE
               "RRULE:FREQ=MONTHLY;UNTIL=20231214;BYMONTHDAY=15\n" +           // DATE
               "END:VEVENT\n" +
               "END:VCALENDAR")).first()
        assertEquals(Date("20231214"), event1.rRules.first.recur.until)

        val event2 = Event.eventsFromReader(StringReader(
            "BEGIN:VCALENDAR\n" +
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
        assertEquals(
            DateTime("20211214T235959Z"),
            event.rRules.first.recur.until
        ) // close to flip up (T235959)
        EventValidator.sameTypeForDtStartAndRruleUntil(event.dtStart!!, event.rRules)
        assertEquals(Date("20211214"), event.rRules.first.recur.until)

        val event1 = Event.eventsFromReader(
            StringReader(
                "BEGIN:VCALENDAR\n" +
                        "BEGIN:VEVENT\n" +
                        "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                        "DTSTART;VALUE=DATE:20211115\n" +                             // DATE
                        "RRULE:FREQ=MONTHLY;UNTIL=20211214T235959;BYMONTHDAY=15\n" +  // DATETIME (no timezone)
                        "END:VEVENT\n" +
                        "END:VCALENDAR"
            )
        ).first()
        assertEquals(1639440000000, event1.rRules.first.recur.until.time)
        assertEquals(Date("20211214"), event1.rRules.first.recur.until)
    }

    @Test
    fun testSameTypeForDtStartAndRruleUntil_DtStartIsDateAndRruleUntilIsDateTime_flippingDate() {
        // Date may flip when cutting off time due to timezones and DST

        //TODO: check if test could still fail with wintertime

        val event2 = Event().apply {
            dtStart = DtStart(Date("20080215"))                                                     // DATE
            rRules.add(RRule("FREQ=YEARLY;TZID=Europe/Vienna;UNTIL=20230214T000000;BYMONTHDAY=15")) // DATETIME (with timezone) and close to flip down (T000000)
        }
        val expectedDateTime = if (TimeZone.getDefault().equals(TimeZone.getTimeZone("UTC")))
            DateTime("20230214T000000Z") // 1676332800000
        else
            DateTime("20230214T000000", tzReg.getTimeZone("Europe/Vienna")) // 1676329200000
        assertEquals(expectedDateTime,
            event2.rRules.first.recur.until
        )
        EventValidator.sameTypeForDtStartAndRruleUntil(event2.dtStart!!, event2.rRules)
        val expectedDate = if (TimeZone.getDefault().equals(TimeZone.getTimeZone("UTC")))
            Date("20230214") // 1676332800000
        else
            Date("20230213") // 1676246400000
        assertEquals(expectedDate,
            event2.rRules.first.recur.until
        )
    }

    @Test
    fun testSameTypeForDtStartAndRruleUntil_DtStartIsDateTimeAndRruleUntilIsDate() {
        // should add (possibly missing) time in UNTIL if DTSTART value is of type DATETIME (not just DATE)

        val event = Event().apply {
            dtStart = DtStart(DateTime("20110605T001100Z"))         // DATETIME (UTC)
            rRules.add(RRule("FREQ=MONTHLY;UNTIL=20211214"))        // DATE
        }
        assertEquals(Date("20211214"), event.rRules.first.recur.until)
        EventValidator.sameTypeForDtStartAndRruleUntil(event.dtStart!!, event.rRules)
        assertEquals(DateTime("20211214T001100Z"), event.rRules.first.recur.until)

        val event1 = Event.eventsFromReader(StringReader(
            "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTSTART;TZID=America/New_York:20211111T053000\n" +     // DATETIME (with timezone)
                "RRULE:FREQ=MONTHLY;UNTIL=20211214;BYMONTHDAY=15\n" +   // DATE
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals(DateTime("20211214T053000", tzReg.getTimeZone("America/New_York")), event1.rRules.first.recur.until)

        val event2 = Event.eventsFromReader(StringReader(
            "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:381fb26b-2da5-4dd2-94d7-2e0874128aa7\n" +
                "DTSTART;VALUE=DATETIME:20080214T001100\n" +            // DATETIME (no timezone)
                "RRULE:FREQ=YEARLY;UNTIL=20110214;BYMONTHDAY=15\n" +    // DATE
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals(DateTime("20110214T001100"), event2.rRules.first.recur.until)
    }


    // RRULE UNTIL time before DTSTART time

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleNoUntil() {
        assertFalse(
            EventValidator.hasUntilBeforeDtStart(
                DtStart(DateTime("20220531T010203")), RRule())
        )
    }


    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_TimeBeforeDtStart_UTC() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(DtStart("20220912", tzReg.getTimeZone("UTC")), RRule(Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(DateTime("20220911T235959Z"))
                .build())))
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_TimeBeforeDtStart_noTimezone() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(DtStart("20220912"), RRule(Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(DateTime("20220911T235959"))
                .build())))
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_TimeBeforeDtStart_withTimezone() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(DtStart("20220912", tzReg.getTimeZone("America/New_York")), RRule(Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(DateTime("20220911T235959", tzReg.getTimeZone("America/New_York")))
                .build())))
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_DateBeforeDtStart() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(DtStart("20220531"), RRule(Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(DateTime("20220530T000000"))
                .build())))
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_TimeAfterDtStart() {
        assertFalse(
            EventValidator.hasUntilBeforeDtStart(DtStart("20200912"), RRule(Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(DateTime("20220912T000001Z"))
                .build()))
        )
    }


    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_DateBeforeDtStart() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(DtStart(DateTime("20220531T010203")), RRule(Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(Date("20220530"))
                .build()))
        )
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_TimeBeforeDtStart() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(DtStart(DateTime("20220531T010203")), RRule(Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(DateTime("20220531T010202"))
                .build()))
        )
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_TimeAtDtStart() {
        assertFalse(
            EventValidator.hasUntilBeforeDtStart(DtStart(DateTime("20220531T010203")), RRule(Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(DateTime("20220531T010203"))
                .build()))
        )
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_TimeAfterDtStart() {
        assertFalse(
            EventValidator.hasUntilBeforeDtStart(DtStart(DateTime("20220531T010203")), RRule(Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(DateTime("20220531T010204"))
                .build()))
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