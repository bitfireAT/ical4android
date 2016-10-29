/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android;

import android.annotation.TargetApi;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Status;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;

import lombok.Cleanup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaskTest {
    private static final String TAG = "ical4android.TaskTest";


    /* public interface tests */

    @Test
    public void testCharsets() throws IOException, InvalidCalendarException {
        Task t = parseCalendar("latin1.ics", Charset.forName("ISO-8859-1"));
        assertEquals("äöüß", t.summary);

        t = parseCalendar("utf8.ics", null);
        assertEquals("© äö — üß", t.summary);
        assertEquals("中华人民共和国", t.location);
    }

    @Test
    public void testDueBeforeDtStart() throws IOException, InvalidCalendarException {
        Task t = parseCalendar("due-before-dtstart.ics", null);
        assertEquals(t.summary, "DUE before DTSTART");
        assertTrue(t.due.getDate().before(t.dtStart.getDate()));
        // no error handling is expected here, the tasks provider will throw an exception
        // and ical4android will pass it to the caller
    }

    @Test
    public void testSamples() throws ParseException, IOException, InvalidCalendarException {
        Task t = regenerate(parseCalendar("rfc5545-sample1.ics", null));
        assertEquals(2, (int)t.sequence);
        assertEquals("uid4@example.com", t.uid);
        assertEquals("mailto:unclesam@example.com", t.organizer.getValue());
        assertEquals(new Due("19980415T000000"), t.due);
        assertEquals(TimeZone.getDefault(), t.getTimeZone());
        assertFalse(t.isAllDay());
        assertEquals(Status.VTODO_NEEDS_ACTION, t.status);
        assertEquals("Submit Income Taxes", t.summary);
    }

    @Test
    public void testAllFields() throws ParseException, IOException, InvalidCalendarException {
        // 1. parse the VTODO file
        // 2. generate a new VTODO file from the parsed code
        // 3. parse it again – so we can test parsing and generating at once
        Task t = regenerate(parseCalendar("most-fields1.ics", null));
        assertEquals(1, (int)t.sequence);
        assertEquals("most-fields1@example.com", t.uid);
        assertEquals("Conference Room - F123, Bldg. 002", t.location);
        assertEquals("37.386013", t.geoPosition.getLatitude().toPlainString());
        assertEquals("-122.082932", t.geoPosition.getLongitude().toPlainString());
        assertEquals("Meeting to provide technical review for \"Phoenix\" design.\nHappy Face Conference Room. Phoenix design team MUST attend this meeting.\nRSVP to team leader.", t.description);
        assertEquals("http://example.com/principals/jsmith", t.organizer.getValue());
        assertEquals("http://example.com/pub/calendars/jsmith/mytime.ics", t.url);
        assertEquals(1, t.priority);
        assertEquals(Clazz.CONFIDENTIAL, t.classification);
        assertEquals(Status.VTODO_IN_PROCESS, t.status);
        assertEquals(25, t.percentComplete.longValue());
        assertEquals(new DtStart(new Date("20100101")), t.dtStart);
        assertEquals(new Due(new Date("20101001")), t.due);
        assertTrue(t.isAllDay());

        assertEquals(new RRule("FREQ=YEARLY;INTERVAL=2"), t.rRule);
        assertEquals(2, t.exDates.size());
        assertTrue(t.exDates.contains(new ExDate(new DateList("20120101", Value.DATE))));
        assertTrue(t.exDates.contains(new ExDate(new DateList("20140101,20180101", Value.DATE))));
        assertEquals(2, t.rDates.size());
        assertTrue(t.rDates.contains(new RDate(new DateList("20100310,20100315", Value.DATE))));
        assertTrue(t.rDates.contains(new RDate(new DateList("20100810", Value.DATE))));

        assertEquals(828106200000L, t.createdAt.longValue());
        assertEquals(840288600000L, t.lastModified.longValue());

        t = regenerate(parseCalendar("most-fields2.ics", null));
        assertEquals("most-fields2@example.com", t.uid);
        assertEquals(new DtStart(new DateTime("20100101T101010Z")), t.dtStart);
        assertEquals(new Duration(new Dur(4, 3, 2, 1)), t.duration);
    }


    /* helpers */

    private Task parseCalendar(String fname, Charset charset) throws IOException, InvalidCalendarException {
        fname = "tasks/" + fname;
        @Cleanup InputStream is = getClass().getClassLoader().getResourceAsStream(fname);
        return Task.fromStream(is, charset)[0];
    }

    private Task regenerate(Task t) throws IOException, InvalidCalendarException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        t.write(os);
        return Task.fromStream(new ByteArrayInputStream(os.toByteArray()), null)[0];
    }
    
}