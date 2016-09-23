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

import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;
import android.util.Log;

import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.Sequence;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.util.TimeZones;

import org.apache.commons.codec.Charsets;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Cleanup;
import lombok.NonNull;

public class EventTest extends InstrumentationTestCase {
    private static final String TAG = "ical4android.EventTest";

    AssetManager assetMgr;

    public void setUp() throws IOException, InvalidCalendarException {
        assetMgr = getInstrumentation().getContext().getResources().getAssets();
    }


    /* public interface tests */

    public void testCalendarProperties() throws IOException, InvalidCalendarException {
        @Cleanup InputStream is = assetMgr.open("events/multiple.ics", AssetManager.ACCESS_STREAMING);
        Map<String, String> properties = new HashMap<>();
        Event.fromStream(is, null, properties);
        assertEquals(1, properties.size());
        assertEquals("Test-Kalender", properties.get(Event.CALENDAR_NAME));
    }

    public void testCharsets() throws IOException, InvalidCalendarException {
        Event e = parseCalendar("latin1.ics", Charsets.ISO_8859_1)[0];
        assertEquals("äöüß", e.summary);

        e = parseCalendar("utf8.ics", null)[0];
        assertEquals("© äö — üß", e.summary);
        assertEquals("中华人民共和国", e.location);
    }

    public void testGrouping() throws IOException, InvalidCalendarException {
        @Cleanup InputStream is = assetMgr.open("events/multiple.ics", AssetManager.ACCESS_STREAMING);
        Event[] events = Event.fromStream(is, null);
        assertEquals(3, events.length);

        Event e = findEvent(events, "multiple-0@ical4android.EventTest");
        assertEquals("Event 0", e.summary);
        assertEquals(0, e.exceptions.size());

        e = findEvent(events, "multiple-1@ical4android.EventTest");
        assertEquals("Event 1", e.summary);
        assertEquals(1, e.exceptions.size());
        assertEquals("Event 1 Exception", e.exceptions.get(0).summary);

        e = findEvent(events, "multiple-2@ical4android.EventTest");
        assertEquals("Event 2", e.summary);
        assertEquals(2, e.exceptions.size());
        assertTrue("Event 2 Updated Exception 1".equals(e.exceptions.get(0).summary) || "Event 2 Updated Exception 1".equals(e.exceptions.get(1).summary));
        assertTrue("Event 2 Exception 2".equals(e.exceptions.get(0).summary) || "Event 2 Exception 2".equals(e.exceptions.get(1).summary));
    }

    public void testRecurringWithException() throws IOException, InvalidCalendarException {
        Event event = parseCalendar("recurring-with-exception1.ics", null)[0];
        assertTrue(event.isAllDay());

        assertEquals(1, event.exceptions.size());
        Event exception = event.exceptions.get(0);
        assertEquals("20150503", exception.recurrenceId.getValue());
        assertEquals("Another summary for the third day", exception.summary);
    }

    public void testStartEndTimes() throws IOException, InvalidCalendarException {
        // event with start+end date-time
        Event eViennaEvolution = parseCalendar("vienna-evolution.ics", null)[0];
        assertEquals(1381330800000L, eViennaEvolution.getDtStartInMillis());
        assertEquals("Europe/Vienna", eViennaEvolution.getDtStartTzID());
        assertEquals(1381334400000L, eViennaEvolution.getDtEndInMillis());
        assertEquals("Europe/Vienna", eViennaEvolution.getDtEndTzID());
    }

    public void testStartEndTimesAllDay() throws IOException, InvalidCalendarException {
        // event with start date only
        Event eOnThatDay = parseCalendar("event-on-that-day.ics", null)[0];
        assertEquals(868838400000L, eOnThatDay.getDtStartInMillis());
        assertEquals(TimeZones.UTC_ID, eOnThatDay.getDtStartTzID());

        // event with start+end date for all-day event (one day)
        Event eAllDay1Day = parseCalendar("all-day-1day.ics", null)[0];
        assertEquals(868838400000L, eAllDay1Day.getDtStartInMillis());
        assertEquals(TimeZones.UTC_ID, eAllDay1Day.getDtStartTzID());
        assertEquals(868838400000L + 86400000, eAllDay1Day.getDtEndInMillis());
        assertEquals(TimeZones.UTC_ID, eAllDay1Day.getDtEndTzID());

        // event with start+end date for all-day event (ten days)
        Event eAllDay10Days = parseCalendar("all-day-10days.ics", null)[0];
        assertEquals(868838400000L, eAllDay10Days.getDtStartInMillis());
        assertEquals(TimeZones.UTC_ID, eAllDay10Days.getDtStartTzID());
        assertEquals(868838400000L + 10 * 86400000, eAllDay10Days.getDtEndInMillis());
        assertEquals(TimeZones.UTC_ID, eAllDay10Days.getDtEndTzID());

        // event with start+end date on some day (0 sec-event)
        Event eAllDay0Sec = parseCalendar("all-day-0sec.ics", null)[0];
        assertEquals(868838400000L, eAllDay0Sec.getDtStartInMillis());
        assertEquals(TimeZones.UTC_ID, eAllDay0Sec.getDtStartTzID());
        // DTEND is same as DTSTART which is not valid for Android – but this will be handled by AndroidEvent, not Event
        assertEquals(868838400000L, eAllDay0Sec.getDtEndInMillis());
        assertEquals(TimeZones.UTC_ID, eAllDay0Sec.getDtEndTzID());
    }

    public void testUnfolding() throws IOException, InvalidCalendarException {
        Event e = parseCalendar("two-line-description-without-crlf.ics", null)[0];
        assertEquals("http://www.tgbornheim.de/index.php?sessionid=&page=&id=&sportcentergroup=&day=6", e.description);
    }


    /* internal tests */

    public void testFindMasterEventsAndExceptions() throws ParseException, IOException, InvalidCalendarException {
        Event[] events;

        // two single events
        events = parseCalendar("two-events-without-exceptions.ics", null);
        assertEquals(2, events.length);
        for (Event event : events)
            assertTrue(event.exceptions.isEmpty());

        // one event with exception, another single event
        events = parseCalendar("one-event-with-exception-one-without.ics", null);
        assertEquals(2, events.length);
        for (Event event : events) {
            String uid = event.uid;
            if ("event1".equals(uid))
                assertEquals(1, event.exceptions.size());
            else
                assertTrue(event.exceptions.isEmpty());
        }

        // one event two exceptions (thereof one updated two times) and updated exception, another single event
        events = parseCalendar("one-event-with-multiple-exceptions-one-without.ics", null);
        assertEquals(2, events.length);
        for (Event event : events) {
            String uid = event.uid;
            if ("event1".equals(uid)) {
                assertEquals(2, event.exceptions.size());
                for (Event exception : event.exceptions)
                    if ("20150503".equals(exception.recurrenceId.getValue())) {
                        assertEquals(2, (int)exception.sequence);
                        assertEquals("Final summary", exception.summary);
                    }
            } else
                assertTrue(event.exceptions.isEmpty());
        }
    }


    // helpers

    private Event findEvent(@NonNull Event[] events, @NonNull String uid) throws FileNotFoundException {
        for (Event event : events)
            if (uid.equals(event.uid))
                return event;
        throw new FileNotFoundException();
    }

    private Event[] parseCalendar(String fname, Charset charset) throws IOException, InvalidCalendarException {
        fname = "events/" + fname;
        Log.d(TAG, "Loading event file " + fname);
        @Cleanup InputStream is = assetMgr.open(fname, AssetManager.ACCESS_STREAMING);
        return Event.fromStream(is, charset);
    }

}
