package at.bitfire.ical4android

import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.TemporalAmountAdapter
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.parameter.Email
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.StringReader
import java.time.Period

class Ical4jTest {

    @Test
    fun testEmailParameter() {
        // https://github.com/ical4j/ical4j/issues/418
        val e = Event.eventsFromReader(StringReader("BEGIN:VCALENDAR\n" +
                "VERSION:2.0\n" +
                "BEGIN:VEVENT\n" +
                "SUMMARY:Test\n" +
                "DTSTART;VALUE=DATE:20200702\n" +
                "ATTENDEE;EMAIL=attendee1@example.com:sample:attendee1\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals("attendee1@example.com", e.attendees.first.getParameter<Email>(Parameter.EMAIL).value)
    }

    @Test
    fun testKarachiTz() {
        // https://github.com/ical4j/ical4j/issues/475
        val tzReg = TimeZoneRegistryFactory.getInstance().createRegistry()
        val karachi = tzReg.getTimeZone("Asia/Karachi")

        val ts1 = 1609945200000
        val dt1 = DateTime(ts1).apply { isUtc = true }
        assertEquals(5, karachi.getOffset(ts1)/3600000)

        val dt2 = DateTime("20210106T200000", karachi)
        assertEquals(1609945200000, dt2.time)
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

}