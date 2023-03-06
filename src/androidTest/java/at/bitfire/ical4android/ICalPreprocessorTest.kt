/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import at.bitfire.ical4android.validation.FixInvalidDayOffsetPreprocessor
import at.bitfire.ical4android.validation.ICalPreprocessor
import java.io.InputStreamReader
import java.io.StringReader
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent
import org.apache.commons.io.IOUtils
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun testFixInvalidDuration() {
        val calendar = "BEGIN:VCALENDAR\n" +
                "PRODID:-//E-DIARY//E-DIARY 1.0//EN\n" +
                "VERSION:2.0\n" +
                "METHOD:PUBLISH\n" +
                "X-WR-CALNAME:Calname\n" +
                "X-Built-On-Cache-Miss:true\n" +
                "BEGIN:VEVENT\n" +
                "LAST-MODIFIED:20230223T030355Z\n" +
                "DTSTAMP:20230223T030355Z\n" +
                "LOCATION:MCG\n" +
                "X-ECAL-SCHEDULE:508efe5dfb0615bb30000001\n" +
                "DTSTART:20230317T084000Z\n" +
                "DTEND:20230317T114000Z\n" +
                "SUMMARY:Example Event Summary\n" +
                "TRANSP:TRANSPARENT\n" +
                "SEQUENCE:0\n" +
                "UID:63945a154c410f17fb7528a7\n" +
                "PRIORITY:5\n" +
                "X-MICROSOFT-CDO-IMPORTANCE:1\n" +
                "CLASS:PUBLIC\n" +
                "DESCRIPTION:Event Description\n" +
                "BEGIN:VALARM\n" +
                "TRIGGER:-P2DT\n" +
                "ACTION:DISPLAY\n" +
                "DESCRIPTION:Reminder\n" +
                "END:VALARM\n" +
                "BEGIN:VALARM\n" +
                "TRIGGER:-PT15M\n" +
                "ACTION:DISPLAY\n" +
                "DESCRIPTION:Reminder\n" +
                "END:VALARM\n" +
                "END:VEVENT\n" +
                "BEGIN:VEVENT\n" +
                "LAST-MODIFIED:20230223T030355Z\n" +
                "DTSTAMP:20230223T030355Z\n" +
                "LOCATION:MCG\n" +
                "X-ECAL-SCHEDULE:508efe5dfb0615bb30000001\n" +
                "DTSTART:20230325T024500Z\n" +
                "DTEND:20230325T054500Z\n" +
                "SUMMARY:Example Event Summary 2\n" +
                "TRANSP:TRANSPARENT\n" +
                "SEQUENCE:0\n" +
                "UID:63945a154c410f17fb7528a8\n" +
                "PRIORITY:5\n" +
                "X-MICROSOFT-CDO-IMPORTANCE:1\n" +
                "CLASS:PUBLIC\n" +
                "DESCRIPTION:Example Event Description 2\n" +
                "BEGIN:VALARM\n" +
                "TRIGGER:-P5DT\n" +
                "ACTION:DISPLAY\n" +
                "DESCRIPTION:Reminder\n" +
                "END:VALARM\n" +
                "BEGIN:VALARM\n" +
                "TRIGGER:-P2DT\n" +
                "ACTION:DISPLAY\n" +
                "DESCRIPTION:Reminder\n" +
                "END:VALARM\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR"

        val isValid = try {
            CalendarBuilder().build(StringReader(calendar))
            true
        } catch (e: Exception) {
            false
        }
        Assert.assertFalse(isValid)

        FixInvalidDayOffsetPreprocessor.preprocess(StringReader(calendar)).let { result ->
            CalendarBuilder().build(result)
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