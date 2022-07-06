/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.util

import at.bitfire.ical4android.Event
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import org.junit.Assert.*

import org.junit.Test
import java.io.StringReader

class EventRepairerTest {


    @Test
    fun testShouldRemoveDtEnd() {
        val event = Event().apply {
            dtStart = DtStart(DateTime("20000105T001100"))
            dtEnd = DtEnd(DateTime("20000105T000000"))
        }
        assertTrue(event.dtStart!!.date.time > event.dtEnd!!.date.time)
        EventRepairer.startAndEndTime(event)
        assertNull(event.dtEnd)

        // will call validateAndRepair() when creating event
        val event1 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTSTART;VALUE=DATE:20211117\n" +
                "DTEND;VALUE=DATE:20211116\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertNull(event1.dtEnd)
    }

    @Test
    fun testDoNothingRruleTime() {
        // should remove (possibly existing) time in RRULE if DTSTART value is of type DATE (not DATETIME)

        val event = Event().apply {
            dtStart = DtStart(DateTime("20211115T001100Z"))
            rRules.add(RRule("FREQ=MONTHLY;UNTIL=20251214T001100Z"))
        }
        assertEquals("20211115T001100Z", event.dtStart!!.value)
        assertEquals("20251214T001100Z", event.rRules.first.recur.until.toString())
        EventRepairer.rRuleTime(event)
        assertEquals("20211115T001100Z", event.dtStart!!.value)
        assertEquals("20251214T001100Z", event.rRules.first.recur.until.toString())

        // will call validateAndRepair() when creating event
        val event1 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTSTART;VALUE=DATE:20211115\n" + // type DATE
                "RRULE:FREQ=MONTHLY;UNTIL=20231214;BYMONTHDAY=15\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals("20211115", event1.dtStart!!.value)
        assertEquals("20231214", event1.rRules.first.recur.until.toString())

        // will call validateAndRepair() when creating event
        val event2 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:381fb26b-2da5-4dd2-94d7-2e0874128aa7\n" +
                "DTSTART;VALUE=DATE:20080215\n" + // type DATE
                "RRULE:FREQ=YEARLY;WKST=MO;UNTIL=20230214;BYMONTH=2;BYMONTHDAY=15\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals("20230214", event2.rRules.first.recur.until.toString())
    }

    @Test
    fun testShouldRemoveRruleTime() {
        // should remove (possibly existing) time in RRULE if DTSTART value is of type DATE (not DATETIME)

        val event = Event().apply {
            dtStart = DtStart(Date("20211115"))
            rRules.add(RRule("FREQ=MONTHLY;UNTIL=20211214T235959Z"))
        }
        assertEquals(1639526399000, event.rRules.first.recur.until.time)
        assertEquals("20211214T235959Z", event.rRules.first.recur.until.toString())
        EventRepairer.rRuleTime(event)
        assertEquals(1639440000000, event.rRules.first.recur.until.time)
        assertEquals("20211214", event.rRules.first.recur.until.toString())

        // will call validateAndRepair() when creating event
        val event1 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTSTART;VALUE=DATE:20211115\n" + // type DATE
                "RRULE:FREQ=MONTHLY;UNTIL=20211214T235959Z;BYMONTHDAY=15\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals(1639440000000, event1.rRules.first.recur.until.time)
        assertEquals("20211214", event1.rRules.first.recur.until.toString())

        // will call validateAndRepair() when creating event
        val event2 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:381fb26b-2da5-4dd2-94d7-2e0874128aa7\n" +
                "DTSTART;VALUE=DATE:20080215\n" + // type DATE
                "RRULE:FREQ=YEARLY;WKST=MO;UNTIL=20230214T000000Z;BYMONTH=2;BYMONTHDAY=15\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals("20230214", event2.rRules.first.recur.until.toString())
    }

    @Test
    fun testShouldAddRruleTime() {
        // should add (possibly missing) time in RRULE if DTSTART value is of type DATETIME (not just DATE)

        val event = Event().apply {
            dtStart = DtStart(DateTime("20110605T001100"))
            rRules.add(RRule("FREQ=MONTHLY;UNTIL=20211214"))
        }
        assertEquals(1639440000000, event.rRules.first.recur.until.time)
        assertEquals("20211214", event.rRules.first.recur.until.toString())
        EventRepairer.validateAndRepair(event)
        assertEquals(1639437060000, event.rRules.first.recur.until.time)
        assertEquals("20211214T001100", event.rRules.first.recur.until.toString())

        // will call validateAndRepair when creating event
        val event1 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTSTART;VALUE=DATETIME:20211115T000000Z\n" + // type DATETIME
                "RRULE:FREQ=MONTHLY;UNTIL=20211214;BYMONTHDAY=15\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals(1639440000000, event1.rRules.first.recur.until.time)
        assertEquals("20211214T000000Z", event1.rRules.first.recur.until.toString())

        // will call validateAndRepair when creating event
        val event2 = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:381fb26b-2da5-4dd2-94d7-2e0874128aa7\n" +
                "DTSTART;VALUE=DATETIME:20080215T001100Z\n" + // type DATETIME
                "RRULE:FREQ=YEARLY;WKST=MO;UNTIL=20230214;BYMONTH=2;BYMONTHDAY=15\n" + //
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals("20230214T001100Z", event2.rRules.first.recur.until.toString())
    }

}