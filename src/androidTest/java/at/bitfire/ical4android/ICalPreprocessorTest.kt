/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import at.bitfire.ical4android.validation.FixInvalidDayOffsetPreprocessor
import at.bitfire.ical4android.validation.FixInvalidUtcOffsetPreprocessor
import at.bitfire.ical4android.validation.ICalPreprocessor
import io.mockk.mockkObject
import io.mockk.verify
import java.io.InputStreamReader
import java.io.StringReader
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VTimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ICalPreprocessorTest {

    @Test
    fun testPreprocessStream_appliesStreamProcessors() {
        mockkObject(FixInvalidDayOffsetPreprocessor, FixInvalidUtcOffsetPreprocessor) {
            ICalPreprocessor.preprocessStream(StringReader(""))

            // verify that the required stream processors have been called
            verify {
                FixInvalidDayOffsetPreprocessor.preprocess(any())
                FixInvalidUtcOffsetPreprocessor.preprocess(any())
            }
        }
    }


    @Test
    fun testPreprocessCalendar_MsTimeZones() {
        javaClass.classLoader!!.getResourceAsStream("events/outlook1.ics").use { stream ->
            val reader = InputStreamReader(stream, Charsets.UTF_8)
            val calendar = CalendarBuilder().build(reader)
            val vEvent = calendar.getComponent(Component.VEVENT) as VEvent

            assertEquals("W. Europe Standard Time", vEvent.startDate.timeZone.id)
            ICalPreprocessor.preprocessCalendar(calendar)
            assertEquals("Europe/Vienna", vEvent.startDate.timeZone.id)
        }
    }

    @Test
    fun testPreprocessCalendar_InvalidTimeZones() {
        javaClass.classLoader!!.getResourceAsStream("events/dublin.ics").use { stream ->
            val reader = InputStreamReader(stream, Charsets.UTF_8)
            val calendar = CalendarBuilder().build(reader)

            val vTimeZone = calendar.getComponent<VTimeZone>(Component.VTIMEZONE)
            val vEvent = calendar.getComponent<VEvent>(Component.VEVENT)

            assertEquals("Europe/Dublin", vTimeZone.timeZoneId.value)
            assertEquals("Europe/Dublin", vEvent.startDate.timeZone.id)
            assertEquals("Europe/Dublin", vEvent.endDate.timeZone.id)

            ICalPreprocessor.preprocessCalendar(calendar)
            assertEquals("Europe/London", vTimeZone.timeZoneId.value)
            assertEquals("Europe/London", vEvent.startDate.timeZone.id)
            assertEquals("Europe/London", vEvent.endDate.timeZone.id)
        }
    }

    @Test
    fun testTzDublin_external_FixedByPreprocessor() {
        // https://github.com/ical4j/ical4j/issues/493
        // Also present in Ical4jTest. Make sure the preprocessor fixes the issue
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
        val reader = ICalPreprocessor.preprocessStream(StringReader(vtzFromGoogle))
        val iCalFromGoogle = CalendarBuilder().build(reader)
        val notDublinFromGoogle = iCalFromGoogle.getComponent(Component.VTIMEZONE) as VTimeZone

        // Check that TZ has been replaced by London
        assertEquals("Europe/London", notDublinFromGoogle.timeZoneId.value)

        val dt = DateTime("20210108T151500", TimeZone(notDublinFromGoogle))
        assertEquals("20210108T151500", dt.toString())
    }

}