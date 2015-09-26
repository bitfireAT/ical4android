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
import net.fortuna.ical4j.model.TimeZone;
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
import java.util.List;

import lombok.Cleanup;
import lombok.NonNull;

public class EventTest extends InstrumentationTestCase {
    private static final String TAG = "ical4android.EventTest";
    protected final TimeZone tzVienna = DateUtils.tzRegistry.getTimeZone("Europe/Vienna");

    AssetManager assetMgr;

    Event eOnThatDay, eAllDay1Day, eAllDay10Days, eAllDay0Sec;

    public void setUp() throws IOException, InvalidCalendarException {
        assetMgr = getInstrumentation().getContext().getResources().getAssets();

        eOnThatDay = parseCalendar("event-on-that-day.ics", null);
        eAllDay1Day = parseCalendar("all-day-1day.ics", null);
        eAllDay10Days = parseCalendar("all-day-10days.ics", null);
        eAllDay0Sec = parseCalendar("all-day-0sec.ics", null);
    }


    /* public interface tests */

    public void testCharsets() throws IOException, InvalidCalendarException {
        Event e = parseCalendar("latin1.ics", Charsets.ISO_8859_1);
        assertEquals("äöüß", e.summary);

        e = parseCalendar("utf8.ics", null);
        assertEquals("© äö — üß", e.summary);
        assertEquals("中华人民共和国", e.location);
    }

    public void testGrouping() throws IOException, InvalidCalendarException {
        @Cleanup InputStream is = assetMgr.open("events/multiple.ics", AssetManager.ACCESS_STREAMING);
        Event[] events = Event.fromStream(is, null, null);
        assertEquals(3, events.length);

        Event e = findEvent(events, "multiple-0@ical4android.EventTest");
        assertEquals("Event 0", e.summary);
        assertEquals(0, e.getExceptions().size());

        e = findEvent(events, "multiple-1@ical4android.EventTest");
        assertEquals("Event 1", e.summary);
        assertEquals(1, e.getExceptions().size());
        assertEquals("Event 1 Exception", e.getExceptions().get(0).summary);

        e = findEvent(events, "multiple-2@ical4android.EventTest");
        assertEquals("Event 2", e.summary);
        assertEquals(2, e.getExceptions().size());
        assertTrue("Event 2 Updated Exception 1".equals(e.getExceptions().get(0).summary) || "Event 2 Updated Exception 1".equals(e.getExceptions().get(1).summary));
        assertTrue("Event 2 Exception 2".equals(e.getExceptions().get(0).summary) || "Event 2 Exception 2".equals(e.getExceptions().get(1).summary));
    }

    public void testRecurringWithException() throws IOException, InvalidCalendarException {
        Event event = parseCalendar("recurring-with-exception1.ics", null);
        assertTrue(event.isAllDay());

        assertEquals(1, event.getExceptions().size());
        Event exception = event.getExceptions().get(0);
        assertEquals("20150503", exception.recurrenceId.getValue());
        assertEquals("Another summary for the third day", exception.summary);
    }

    public void testStartEndTimes() throws IOException, InvalidCalendarException {
        // event with start+end date-time
        Event eViennaEvolution = parseCalendar("vienna-evolution.ics", null);
        assertEquals(1381330800000L, eViennaEvolution.getDtStartInMillis());
        assertEquals("Europe/Vienna", eViennaEvolution.getDtStartTzID());
        assertEquals(1381334400000L, eViennaEvolution.getDtEndInMillis());
        assertEquals("Europe/Vienna", eViennaEvolution.getDtEndTzID());
    }

    public void testStartEndTimesAllDay() throws IOException {
        // event with start date only
        assertEquals(868838400000L, eOnThatDay.getDtStartInMillis());
        assertEquals(TimeZones.UTC_ID, eOnThatDay.getDtStartTzID());
        // DTEND missing in VEVENT, must have been set to DTSTART+1 day
        assertEquals(868838400000L + 86400000, eOnThatDay.getDtEndInMillis());
        assertEquals(TimeZones.UTC_ID, eOnThatDay.getDtEndTzID());

        // event with start+end date for all-day event (one day)
        assertEquals(868838400000L, eAllDay1Day.getDtStartInMillis());
        assertEquals(TimeZones.UTC_ID, eAllDay1Day.getDtStartTzID());
        assertEquals(868838400000L + 86400000, eAllDay1Day.getDtEndInMillis());
        assertEquals(TimeZones.UTC_ID, eAllDay1Day.getDtEndTzID());

        // event with start+end date for all-day event (ten days)
        assertEquals(868838400000L, eAllDay10Days.getDtStartInMillis());
        assertEquals(TimeZones.UTC_ID, eAllDay10Days.getDtStartTzID());
        assertEquals(868838400000L + 10 * 86400000, eAllDay10Days.getDtEndInMillis());
        assertEquals(TimeZones.UTC_ID, eAllDay10Days.getDtEndTzID());

        // event with start+end date on some day (0 sec-event)
        assertEquals(868838400000L, eAllDay0Sec.getDtStartInMillis());
        assertEquals(TimeZones.UTC_ID, eAllDay0Sec.getDtStartTzID());
        // DTEND is same as DTSTART which is not valid for Android – but this will be handled by AndroidEvent, not Event
        assertEquals(868838400000L, eAllDay0Sec.getDtEndInMillis());
        assertEquals(TimeZones.UTC_ID, eAllDay0Sec.getDtEndTzID());
    }

    public void testUnfolding() throws IOException, InvalidCalendarException {
        Event e = parseCalendar("two-line-description-without-crlf.ics", null);
        assertEquals("http://www.tgbornheim.de/index.php?sessionid=&page=&id=&sportcentergroup=&day=6", e.description);
    }


    /* internal tests */

    public void testFindMasterEventsAndExceptions() throws ParseException {
        List<VEvent> events;
        Collection<VEvent> masterEvents;

        // two single events
        masterEvents = Event.findMasterEvents(events = Arrays.asList(new VEvent[]{
                newVEvent("event1", null, 0),
                newVEvent("event2", null, 0)
        }));
        assertEquals(2, masterEvents.size());
        for (VEvent event : masterEvents) {
            Collection<VEvent> exceptions = Event.findExceptions(event.getUid().getValue(), events);
            assertEquals(0, exceptions.size());
        }

        // one event with exception, another single event
        masterEvents = Event.findMasterEvents(events = Arrays.asList(new VEvent[]{
                newVEvent("event1", null, 0),
                newVEvent("event2", null, 0),
                newVEvent("event1", "20150101", 0)
        }));
        assertEquals(2, masterEvents.size());
        for (VEvent event : masterEvents) {
            String uid = event.getUid().getValue();
            Collection<VEvent> exceptions = Event.findExceptions(uid, events);
            if ("event1".equals(uid))
                assertEquals(1, exceptions.size());
            else
                assertEquals(0, exceptions.size());
        }

        // one event two exceptions (thereof one updated two times) and updated exception, another single event
        masterEvents = Event.findMasterEvents(events = Arrays.asList(new VEvent[]{
                newVEvent("event1", null, 0),
                newVEvent("event2", null, 0),
                newVEvent("event1", "20150101", 1),
                newVEvent("event1", "20150101", 2),
                newVEvent("event1", "20150101", 0),
                newVEvent("event1", "20150102", 0)
        }));
        assertEquals(2, masterEvents.size());
        for (VEvent event : masterEvents) {
            String uid = event.getUid().getValue();
            Collection<VEvent> exceptions = Event.findExceptions(uid, events);
            if ("event1".equals(uid)) {
                assertEquals(2, exceptions.size());
                for (VEvent exception : exceptions)
                    if ("20150101".equals(exception.getRecurrenceId().getValue()))
                        assertEquals(2, exception.getSequence().getSequenceNo());
            } else
                assertEquals(0, exceptions.size());
        }
    }


    // helpers

    private Event findEvent(@NonNull Event[] events, @NonNull String uid) throws FileNotFoundException {
        for (Event event : events)
            if (uid.equals(event.uid))
                return event;
        throw new FileNotFoundException();
    }

    private VEvent newVEvent(String uid, String recurrenceId, int sequence) throws ParseException {
        VEvent event = new VEvent();
        PropertyList props = event.getProperties();
        props.add(new Uid(uid));
        if (recurrenceId != null)
            props.add(new RecurrenceId(recurrenceId));
        if (sequence != 0)
            props.add(new Sequence(sequence));
        return event;
    }

    private Event parseCalendar(String fname, Charset charset) throws IOException, InvalidCalendarException {
        fname = "events/" + fname;
        Log.d(TAG, "Loading event file " + fname);
        @Cleanup InputStream is = assetMgr.open(fname, AssetManager.ACCESS_STREAMING);
        return Event.fromStream(is, charset, null)[0];
    }

}
