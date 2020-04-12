/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

class ICalendarTest {

    @Test
    fun testTimezoneDefToTzId() {
		// test valid definition
		assertEquals("US-Eastern", ICalendar.timezoneDefToTzId("BEGIN:VCALENDAR\n" +
				"PRODID:-//Example Corp.//CalDAV Client//EN\n" +
				"VERSION:2.0\n" +
				"BEGIN:VTIMEZONE\n" +
				"TZID:US-Eastern\n" +
				"LAST-MODIFIED:19870101T000000Z\n" +
				"BEGIN:STANDARD\n" +
				"DTSTART:19671029T020000\n" +
				"RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10\n" +
				"TZOFFSETFROM:-0400\n" +
				"TZOFFSETTO:-0500\n" +
				"TZNAME:Eastern Standard Time (US &amp; Canada)\n" +
				"END:STANDARD\n" +
				"BEGIN:DAYLIGHT\n" +
				"DTSTART:19870405T020000\n" +
				"RRULE:FREQ=YEARLY;BYDAY=1SU;BYMONTH=4\n" +
				"TZOFFSETFROM:-0500\n" +
				"TZOFFSETTO:-0400\n" +
				"TZNAME:Eastern Daylight Time (US &amp; Canada)\n" +
				"END:DAYLIGHT\n" +
				"END:VTIMEZONE\n" +
				"END:VCALENDAR"))

        // test invalid time zone
		assertNull(ICalendar.timezoneDefToTzId("/* invalid content */"))

        // test time zone without TZID
		assertNull(ICalendar.timezoneDefToTzId("BEGIN:VCALENDAR\n" +
				"PRODID:-//Inverse inc./SOGo 2.2.10//EN\n" +
				"VERSION:2.0\n" +
				"END:VCALENDAR"))
    }

	@Test
	fun testVAlarmToMin() {
		run {
			// TRIGGER;REL=START:-P1DT1H1M29S (round up the 29s so that the alarm appears earlier)
			val (ref, min) = ICalendar.vAlarmToMin(
					VAlarm(Duration.parse("-P1DT1H1M29S") /*Dur(1, 1, 1, 29).negate()*/),
					ICalendar(), false)!!
			assertEquals(Related.START, ref)
			assertEquals(60*24 + 60 + 1 + 1, min)
		}

		run {
			// TRIGGER;REL=START:P1DT1H1M30S (alarm *after* start; round down the 30s so that alarm appears earlier)
			val (ref, min) = ICalendar.vAlarmToMin(
					VAlarm(Duration.parse("P1DT1H1M30S") /*Dur(1, 1, 1, 30)*/),
					ICalendar(), false)!!
			assertEquals(Related.START, ref)
			assertEquals(-(60*24 + 60 + 1), min)
		}

		run {
			// TRIGGER;REL=END:-P1DT1H1M30S (caller accepts Related.END)
			val alarm = VAlarm(Duration.parse("-P1DT1H1M30S") /*Dur(1, 1, 1, 30).negate()*/)
			alarm.trigger.parameters.add(Related.END)
			val (ref, min) = ICalendar.vAlarmToMin(alarm, ICalendar(), true)!!
			assertEquals(Related.END, ref)
			assertEquals(60 * 24 + 60 + 1 + 1, min)
		}

		run {
			// event with TRIGGER;REL=END:-P1DT1H1M30S (caller doesn't accept Related.END)
			val alarm = VAlarm(Duration.parse("-P1DT1H1M30S") /*Dur(1, 1, 1, 30).negate()*/)
			alarm.trigger.parameters.add(Related.END)
			val event = Event()

			val currentTime = java.util.Date().time
			event.dtStart = DtStart(DateTime(currentTime))
			event.dtEnd = DtEnd(DateTime(currentTime + 90*1000))	// 90 sec later
			val (ref, min) = ICalendar.vAlarmToMin(alarm, event, false)!!
			assertEquals(Related.START, ref)
			assertEquals(60 * 24 + 60 + 1 + 1 /* duration of event: */ - 2, min)
		}

		run {
			// task with TRIGGER;REL=END:-P1DT1H1M30S (caller doesn't accept Related.END; alarm *after* end)
			val alarm = VAlarm(Duration.parse("P1DT1H1M30S") /*Dur(1, 1, 1, 30)*/)
			alarm.trigger.parameters.add(Related.END)
			val task = Task()
			val currentTime = java.util.Date().time
			task.dtStart = DtStart(DateTime(currentTime))
			task.due = Due(DateTime(currentTime + 90*1000))	// 90 sec (should be rounded down to 1 min) later
			val (ref, min) = ICalendar.vAlarmToMin(alarm, task, false)!!
			assertEquals(Related.START, ref)
			assertEquals(-(60 * 24 + 60 + 1 + 1) /* duration of event: */ - 1, min)
		}

		run {
			// TRIGGER;VALUE=DATE-TIME:<xxxx>
			val event = Event()
			val currentTime = java.util.Date().time
			event.dtStart = DtStart(DateTime(currentTime))
			val alarm = VAlarm(DateTime(currentTime - 89*1000))	// 89 sec (should be rounded up to 2 min) before event
			alarm.trigger.parameters.add(Related.END)	// not useful for DATE-TIME values, should be ignored
			val (ref, min) = ICalendar.vAlarmToMin(alarm, event, false)!!
			assertEquals(Related.START, ref)
			assertEquals(2, min)
		}
	}

}