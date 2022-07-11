/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.util.TimeZones
import org.junit.Assert.*

import org.junit.Test
import java.io.StringReader

class EventValidatorTest {

    val tzReg = TimeZoneRegistryFactory.getInstance().createRegistry()

    @Test
    fun testShouldRemoveDtEnd() {
        val event = Event().apply {
            dtStart = DtStart(DateTime("20000105T001100"))              // DATETIME
            dtEnd = DtEnd(DateTime("20000105T000000"))                  // DATETIME
        }
        assertTrue(event.dtStart!!.date.time > event.dtEnd!!.date.time)
        EventValidator.startAndEndTime(event)
        assertNull(event.dtEnd)

        // will call validateAndRepair() when creating event
        val event1 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTSTART;VALUE=DATE:20211117\n" +                           // DATE
                "DTEND;VALUE=DATE:20211116\n" +                             // DATE
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertNull(event1.dtEnd)
    }

    @Test
    fun testDoNothingRruleTime() {
        // should remove (possibly existing) time in RRULE if DTSTART value is of type DATE (not DATETIME)

        val event = Event().apply {
            dtStart = DtStart(DateTime("20211115T001100Z"))               // DATETIME (UTC)
            rRules.add(RRule("FREQ=MONTHLY;UNTIL=20251214T001100Z"))      // DATETIME (UTC)
        }
        assertEquals(DateTime("20211115T001100Z"), event.dtStart!!.date)
        assertEquals(DateTime("20251214T001100Z"), event.rRules.first.recur.until)
        EventValidator.rRuleTime(event)
        assertEquals(DateTime("20211115T001100Z"), event.dtStart!!.date)
        assertEquals(DateTime("20251214T001100Z"), event.rRules.first.recur.until)

        // will call validateAndRepair() when creating event
        val event1 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTSTART;VALUE=DATE:20211115\n" +                               // DATE
                "RRULE:FREQ=MONTHLY;UNTIL=20231214;BYMONTHDAY=15\n" +           // DATE
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals(Date("20231214"), event1.rRules.first.recur.until)

        // will call validateAndRepair() when creating event
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
    fun testShouldRemoveRruleTime() {
        // should remove (possibly existing) time in RRULE if DTSTART value is of type DATE (not DATETIME)

        val event = Event().apply {
            dtStart = DtStart(Date("20211115"))                         // DATE
            rRules.add(RRule("FREQ=MONTHLY;UNTIL=20211214T235959Z"))    // DATETIME (UTC)
        }
        assertEquals(1639526399000, event.rRules.first.recur.until.time)
        assertEquals(DateTime("20211214T235959Z"), event.rRules.first.recur.until)
        EventValidator.rRuleTime(event)
        assertEquals(1639440000000, event.rRules.first.recur.until.time)
        assertEquals(Date("20211214"), event.rRules.first.recur.until)

        // will call validateAndRepair() when creating event
        val event1 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTSTART;VALUE=DATE:20211115\n" +                             // DATE
                "RRULE:FREQ=MONTHLY;UNTIL=20211214T235959;BYMONTHDAY=15\n"+   // DATETIME (no timezone)
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals(1639440000000, event1.rRules.first.recur.until.time)
        assertEquals(Date("20211214"), event1.rRules.first.recur.until)

        // will call validateAndRepair() when creating event
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
    fun testShouldAddRruleTime() {
        // should add (possibly missing) time in RRULE if DTSTART value is of type DATETIME (not just DATE)

        val event = Event().apply {
            dtStart = DtStart(DateTime("20110605T001100Z"))         // DATETIME (UTC)
            rRules.add(
                RRule("FREQ=MONTHLY;UNTIL=20211214")                // DATE
            )
        }
        assertEquals(Date("20211214"), event.rRules.first.recur.until)
        EventValidator.rRuleTime(event)
        assertEquals(DateTime("20211214", tzReg.getTimeZone("UTC")), event.rRules.first.recur.until)

        // will call validateAndRepair() when creating event
        val event1 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTSTART;TZID=America/New_York:20211115T000000\n" +     // DATETIME (with timezone)
                "RRULE:FREQ=MONTHLY;UNTIL=20211214;BYMONTHDAY=15\n" +   // DATE
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals(DateTime("20211214"), event1.rRules.first.recur.until)

        // will call validateAndRepair() when creating event
        val event2 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:381fb26b-2da5-4dd2-94d7-2e0874128aa7\n" +
                "DTSTART;VALUE=DATETIME:20080215T001100\n" +            // DATETIME (no timezone)
                "RRULE:FREQ=YEARLY;UNTIL=20230214;BYMONTHDAY=15\n" +    // DATE
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals(DateTime("20230214"), event2.rRules.first.recur.until)
    }

}