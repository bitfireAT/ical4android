/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU General Public License for more details.
 */

package at.bitfire.ical4android;

import junit.framework.TestCase;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.util.TimeZones;

import java.io.StringReader;

public class iCalendarTest extends TestCase {
	protected final TimeZone tzVienna = DateUtils.tzRegistry.getTimeZone("Europe/Vienna");

    public void testGetTzID() throws Exception {
        // DATE (without time)
        assertEquals(TimeZones.UTC_ID, Event.getTzId(new DtStart(new Date("20150101"))));

        // DATE-TIME without time zone (floating time): should be UTC (because net.fortuna.ical4j.timezone.date.floating=false)
        assertEquals(TimeZones.UTC_ID, Event.getTzId(new DtStart(new DateTime("20150101T000000"))));

        // DATE-TIME without time zone (UTC)
        assertEquals(TimeZones.UTC_ID, Event.getTzId(new DtStart(new DateTime(1438607288000L))));

        // DATE-TIME with time zone
        assertEquals(tzVienna.getID(), Event.getTzId(new DtStart(new DateTime("20150101T000000", tzVienna))));
    }

    public void testTimezoneDefToTzId() {
		// test valid definition
		assertEquals("US-Eastern", Event.TimezoneDefToTzId("BEGIN:VCALENDAR\n" +
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
				"END:VCALENDAR"));

		// test invalid time zone
		assertNull(iCalendar.TimezoneDefToTzId("/* invalid content */"));

		// test time zone without TZID
		assertNull(iCalendar.TimezoneDefToTzId("BEGIN:VCALENDAR\n" +
				"PRODID:-//Inverse inc./SOGo 2.2.10//EN\n" +
				"VERSION:2.0\n" +
				"END:VCALENDAR"));
	}

	public void testValidateTimeZone() throws Exception {
		assertNotNull(tzVienna);

		// date (no time zone) should be ignored
		DtStart date = new DtStart(new Date("20150101"));
		iCalendar.validateTimeZone(date);
		assertNull(date.getTimeZone());

		// date-time (Europe/Vienna) should be unchanged
		DtStart dtStart = new DtStart("20150101", tzVienna);
		iCalendar.validateTimeZone(dtStart);
		assertEquals(tzVienna, dtStart.getTimeZone());

		// time zone that is not available on Android systems should be changed to system default
		CalendarBuilder builder = new CalendarBuilder();
		net.fortuna.ical4j.model.Calendar cal = builder.build(new StringReader("BEGIN:VCALENDAR\n" +
				"BEGIN:VTIMEZONE\n" +
				"TZID:CustomTime\n" +
				"BEGIN:STANDARD\n" +
				"TZOFFSETFROM:-0400\n" +
				"TZOFFSETTO:-0500\n" +
				"DTSTART:19600101T000000\n" +
				"END:STANDARD\n" +
				"END:VTIMEZONE\n" +
				"END:VCALENDAR"));
		final TimeZone tzCustom = new TimeZone((VTimeZone)cal.getComponent(VTimeZone.VTIMEZONE));
		dtStart = new DtStart("20150101T000000", tzCustom);
		iCalendar.validateTimeZone(dtStart);

		final TimeZone tzDefault = DateUtils.tzRegistry.getTimeZone(java.util.TimeZone.getDefault().getID());
		assertNotNull(tzDefault);
		assertEquals(tzDefault.getID(), dtStart.getTimeZone().getID());
	}

}
