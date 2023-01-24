/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import at.bitfire.ical4android.validation.ICalPreprocessor
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent
import org.apache.commons.io.IOUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStreamReader
import java.io.StringReader
import java.time.Duration

class ICalPreprocessorTest {

    @Test
    fun testFixInvalidUtcOffset() {
        val invalid = "BEGIN:VEVENT" +
                "SUMMARY:Test" +
                "DTSTART;TZID=Test:19970714T133000" +
                "END:VEVENT" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Test\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:19670430T020000\n" +
                "TZOFFSETFROM:-5730\n" +
                "TZOFFSETTO:+1920\n" +
                "TZNAME:EDT\n" +
                "END:DAYLIGHT\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:19671029T020000\n" +
                "TZOFFSETFROM:-0400\n" +
                "TZOFFSETTO:-0500\n" +
                "TZNAME:EST" +
                "END:STANDARD\n" +
                "END:VTIMEZONE"
        val valid = "BEGIN:VEVENT" +
                "SUMMARY:Test" +
                "DTSTART;TZID=Test:19970714T133000" +
                "END:VEVENT" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Test\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:19670430T020000\n" +
                "TZOFFSETFROM:-005730\n" +
                "TZOFFSETTO:+001920\n" +
                "TZNAME:EDT\n" +
                "END:DAYLIGHT\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:19671029T020000\n" +
                "TZOFFSETFROM:-0400\n" +
                "TZOFFSETTO:-0500\n" +
                "TZNAME:EST" +
                "END:STANDARD\n" +
                "END:VTIMEZONE"
        ICalPreprocessor.preprocessStream(StringReader(invalid)).let { result ->
            assertEquals(valid, IOUtils.toString(result))
        }
        ICalPreprocessor.preprocessStream(StringReader(valid)).let { result ->
            assertEquals(valid, IOUtils.toString(result))
        }
    }

    @Test
    fun testFixInvalidDurationTPrefixOffset() {
        val invalid = "BEGIN:VEVENT\n" +
                "LAST-MODIFIED:20230108T011226Z\n" +
                "DTSTAMP:20230108T011226Z\n" +
                "X-ECAL-SCHEDULE:63b0e38979739f000d5c1724\n" +
                "DTSTART:20230101T015100Z\n" +
                "DTEND:20230101T020600Z\n" +
                "SUMMARY:This is a test event\n" +
                "TRANSP:TRANSPARENT\n" +
                "SEQUENCE:0\n" +
                "UID:63b0e389453c5d000e1161ae\n" +
                "PRIORITY:5\n" +
                "X-MICROSOFT-CDO-IMPORTANCE:1\n" +
                "CLASS:PUBLIC\n" +
                "DESCRIPTION:Example description\n" +
                "BEGIN:VALARM\n" +
                "TRIGGER:-PT2D\n" +
                "ACTION:DISPLAY\n" +
                "DESCRIPTION:Reminder\n" +
                "END:VALARM\n" +
                "END:VEVENT"
        val valid = "BEGIN:VEVENT\n" +
                "LAST-MODIFIED:20230108T011226Z\n" +
                "DTSTAMP:20230108T011226Z\n" +
                "X-ECAL-SCHEDULE:63b0e38979739f000d5c1724\n" +
                "DTSTART:20230101T015100Z\n" +
                "DTEND:20230101T020600Z\n" +
                "SUMMARY:This is a test event\n" +
                "TRANSP:TRANSPARENT\n" +
                "SEQUENCE:0\n" +
                "UID:63b0e389453c5d000e1161ae\n" +
                "PRIORITY:5\n" +
                "X-MICROSOFT-CDO-IMPORTANCE:1\n" +
                "CLASS:PUBLIC\n" +
                "DESCRIPTION:Example description\n" +
                "BEGIN:VALARM\n" +
                "TRIGGER:-P2D\n" +
                "ACTION:DISPLAY\n" +
                "DESCRIPTION:Reminder\n" +
                "END:VALARM\n" +
                "END:VEVENT"
        ICalPreprocessor.preprocessStream(StringReader(invalid)).let { result ->
            assertEquals(valid, IOUtils.toString(result))
        }
        ICalPreprocessor.preprocessStream(StringReader(valid)).let { result ->
            assertEquals(valid, IOUtils.toString(result))
        }
    }

    @Test
    fun testMsTimeZones() {
        javaClass.classLoader!!.getResourceAsStream("events/outlook1.ics").use { stream ->
            val reader = InputStreamReader(stream, Charsets.UTF_8)
            val calendar = CalendarBuilder().build(reader)
            val vEvent = calendar.getComponent(Component.VEVENT) as VEvent

            assertEquals("W. Europe Standard Time", vEvent.startDate.timeZone.id)
            ICalPreprocessor.preprocessCalendar(calendar)
            assertEquals("Europe/Vienna", vEvent.startDate.timeZone.id)
        }
    }

}