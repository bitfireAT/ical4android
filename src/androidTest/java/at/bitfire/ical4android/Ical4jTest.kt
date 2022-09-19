/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.parameter.Email
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.StringReader
import java.time.Period

class Ical4jTest {

    val tzReg = TimeZoneRegistryFactory.getInstance().createRegistry()

    @Test
    fun testEmailParameter() {
        // https://github.com/ical4j/ical4j/issues/418
        val e = Event.eventsFromReader(
            StringReader(
                "BEGIN:VCALENDAR\n" +
                        "VERSION:2.0\n" +
                        "BEGIN:VEVENT\n" +
                        "SUMMARY:Test\n" +
                        "DTSTART;VALUE=DATE:20200702\n" +
                        "ATTENDEE;EMAIL=attendee1@example.virtual:sample:attendee1\n" +
                        "END:VEVENT\n" +
                        "END:VCALENDAR"
            )
        ).first()
        assertEquals("attendee1@example.virtual", e.attendees.first.getParameter<Email>(Parameter.EMAIL).value)
    }

    @Test
    fun testTemporalAmountAdapter_durationToString_DropsMinutes() {
        // https://github.com/ical4j/ical4j/issues/420
        assertEquals("P1DT1H4M", TemporalAmountAdapter.parse("P1DT1H4M").toString())
    }

    @Test(expected = AssertionError::class)
    fun testTemporalAmountAdapter_Months() {
        // https://github.com/ical4j/ical4j/issues/419
        // A month usually doesn't have 4 weeks = 4*7 days = 28 days (except February in non-leap years).
        assertNotEquals("P4W", TemporalAmountAdapter(Period.ofMonths(1)).toString())
    }

    @Test(expected = AssertionError::class)
    fun testTemporalAmountAdapter_Year() {
        // https://github.com/ical4j/ical4j/issues/419
        // A year has 365 or 366 days, but never 52 weeks = 52*7 days = 364 days.
        assertNotEquals("P52W", TemporalAmountAdapter(Period.ofYears(1)).toString())
    }

    @Test(expected = AssertionError::class)
    fun testTzDarwin() {
        val darwin = tzReg.getTimeZone("Australia/Darwin")

        val ts1 = 1616720400000
        val dt1 = DateTime(ts1).apply { isUtc = true }
        assertEquals(9.5, darwin.getOffset(ts1)/3600000.0, .01)

        val dt2 = DateTime("20210326T103000", darwin)
        assertEquals(1616720400000, dt2.time)
    }

    @Test(expected = AssertionError::class)
    fun testTzDublin_external() {
        // https://github.com/ical4j/ical4j/issues/493
        val vtzFromGoogle = "BEGIN:VCALENDAR\n" +
                "CALSCALE:GREGORIAN\n" +
                "VERSION:2.0\n" +
                "PRODID:-//Google Inc//Google Calendar 70.9054//EN\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Europe/Dublin\n" +
                "BEGIN:STANDARD\n" +
                "TZOFFSETFROM:+0000\n" +
                "TZOFFSETTO:+0100\n" +
                "TZNAME:IST\n" +
                "DTSTART:19700329T010000\n" +
                "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZOFFSETFROM:+0100\n" +
                "TZOFFSETTO:+0000\n" +
                "TZNAME:GMT\n" +
                "DTSTART:19701025T020000\n" +
                "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "END:VCALENDAR"
        val iCalFromGoogle = CalendarBuilder().build(StringReader(vtzFromGoogle))
        val dublinFromGoogle = iCalFromGoogle.getComponent(Component.VTIMEZONE) as VTimeZone
        val dt = DateTime("20210108T151500", TimeZone(dublinFromGoogle))
        assertEquals("20210108T151500", dt.toString())
    }

    @Test
    fun testTzKarachi() {
        // https://github.com/ical4j/ical4j/issues/491
        val karachi = tzReg.getTimeZone("Asia/Karachi")

        val ts1 = 1609945200000
        val dt1 = DateTime(ts1).apply { isUtc = true }
        assertEquals(5, karachi.getOffset(ts1)/3600000)

        val dt2 = DateTime("20210106T200000", karachi)
        assertEquals(1609945200000, dt2.time)
    }

}