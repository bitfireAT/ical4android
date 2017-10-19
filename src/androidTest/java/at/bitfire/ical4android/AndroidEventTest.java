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

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.test.InstrumentationTestCase;
import android.util.Log;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.Status;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Arrays;

import at.bitfire.ical4android.impl.TestCalendar;
import at.bitfire.ical4android.impl.TestEvent;
import lombok.Cleanup;

public class AndroidEventTest extends InstrumentationTestCase {
    private static final String TAG = "ical4android.CalTest";

    private static final TimeZone tzVienna = DateUtils.tzRegistry.getTimeZone("Europe/Vienna");

    ContentProviderClient provider;
    Uri calendarUri;
    AndroidCalendar calendar;

    final Account testAccount = new Account("ical4android.AndroidEventTest", CalendarContract.ACCOUNT_TYPE_LOCAL);


    // helpers

    private Uri syncAdapterURI(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, testAccount.type)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, testAccount.name)
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
    }


    // initialization

    @Override
    public void setUp() throws RemoteException, FileNotFoundException, CalendarStorageException {
        provider = getInstrumentation().getTargetContext().getContentResolver().acquireContentProviderClient(CalendarContract.AUTHORITY);
        assertNotNull("Couldn't access calendar provider", provider);

        AndroidCalendar.insertColors(provider, testAccount);

        calendar = TestCalendar.findOrCreate(testAccount, provider);
        assertNotNull("Couldn't find/create test calendar", calendar);

        calendarUri = ContentUris.withAppendedId(Calendars.CONTENT_URI, calendar.getId());
        Log.i(TAG, "Prepared test calendar " + calendarUri);
    }

    @Override
    public void tearDown() throws CalendarStorageException {
        Log.i(TAG, "Deleting test calendar");
        calendar.delete();
    }


    // tests

    public void testAddEvent() throws URISyntaxException, ParseException, FileNotFoundException, CalendarStorageException {
        // build and write recurring event to calendar provider
        Event event = new Event();
        event.setUid("sample1@testAddEvent");
        event.setSummary("Sample event");
        event.setDescription("Sample event with date/time");
        event.setLocation("Sample location");
        event.setDtStart(new DtStart("20150501T120000", tzVienna));
        event.setDtEnd(new DtEnd("20150501T130000", tzVienna));
        event.setOrganizer(new Organizer(new URI("mailto:organizer@example.com")));
        event.setRRule(new RRule("FREQ=DAILY;COUNT=10"));
        event.setClassification(Clazz.PRIVATE);
        event.setStatus(Status.VEVENT_CONFIRMED);
        event.setColor(EventColor.aliceblue);
        assertFalse(event.isAllDay());

        // TODO test rDates, exDate, duration

        // set an alarm one day, two hours, three minutes and four seconds before begin of event
        event.getAlarms().add(new VAlarm(new Dur(-1, -2, -3, -4)));

        // add two attendees
        event.getAttendees().add(new Attendee(new URI("mailto:user1@example.com")));
        event.getAttendees().add(new Attendee(new URI("mailto:user2@example.com")));

        // add exception with alarm and attendee
        Event exception = new Event();
        exception.setRecurrenceId(new RecurrenceId("20150502T120000", tzVienna));
        exception.setSummary("Exception for sample event");
        exception.setDtStart(new DtStart("20150502T140000", tzVienna));
        exception.setDtEnd(new DtEnd("20150502T150000", tzVienna));
        exception.getAlarms().add(new VAlarm(new Dur(-2, -3, -4, -5)));
        exception.getAttendees().add(new Attendee(new URI("mailto:only.here@today")));
        event.getExceptions().add(exception);

        // add EXDATE
        event.getExDates().add(new ExDate(new DateList("20150502T120000", Value.DATE_TIME, tzVienna)));

        // add to calendar
        Uri uri = new TestEvent(calendar, event).add();
        assertNotNull("Couldn't add event", uri);

        // read and parse event from calendar provider
        @Cleanup("delete") TestEvent testEvent = new TestEvent(calendar, ContentUris.parseId(uri));
        assertNotNull("Inserted event is not here", testEvent);
        Event event2 = testEvent.getEvent();
        assertNotNull("Inserted event is empty", event2);

        // compare with original event
        assertEquals(event.getSummary(), event2.getSummary());
        assertEquals(event.getDescription(), event2.getDescription());
        assertEquals(event.getLocation(), event2.getLocation());
        assertEquals(event.getDtStart(), event2.getDtStart());
        assertFalse(event2.isAllDay());
        assertEquals(event.getOrganizer(), event2.getOrganizer());
        assertEquals(event.getRRule(), event2.getRRule());
        assertEquals(event.getClassification(), event2.getClassification());
        assertEquals(event.getStatus(), event2.getStatus());

        if (Build.VERSION.SDK_INT >= 23)
            // doesn't work on Android 4.1
            assertEquals(event.getColor(), event2.getColor());

        // compare alarm
        assertEquals(1, event2.getAlarms().size());
        VAlarm alarm2 = event2.getAlarms().get(0);
        assertEquals(event.getSummary(), alarm2.getDescription().getValue());  // should be built from event title
        assertEquals(new Dur(0, 0, -(24 * 60 + 60 * 2 + 3), 0), alarm2.getTrigger().getDuration());   // calendar provider stores trigger in minutes

        // compare attendees
        assertEquals(2, event2.getAttendees().size());
        assertEquals(event.getAttendees().get(0).getCalAddress(), event2.getAttendees().get(0).getCalAddress());
        assertEquals(event.getAttendees().get(1).getCalAddress(), event2.getAttendees().get(1).getCalAddress());

        // compare exception
        assertEquals(1, event2.getExceptions().size());
        Event exception2 = event2.getExceptions().get(0);
        assertEquals(exception.getRecurrenceId().getDate(), exception2.getRecurrenceId().getDate());
        assertEquals(exception.getSummary(), exception2.getSummary());
        assertEquals(exception.getDtStart(), exception2.getDtStart());
        assertEquals(exception.getDtEnd(), exception2.getDtEnd());

        // compare exception alarm
        assertEquals(1, exception2.getAlarms().size());
        alarm2 = exception2.getAlarms().get(0);
        assertEquals(exception.getSummary(), alarm2.getDescription().getValue());
        assertEquals(new Dur(0, 0, -(2 * 24 * 60 + 60 * 3 + 4), 0), alarm2.getTrigger().getDuration());   // calendar provider stores trigger in minutes

        // compare exception attendee
        assertEquals(1, exception2.getAttendees().size());
        assertEquals(exception.getAttendees().get(0).getCalAddress(), exception2.getAttendees().get(0).getCalAddress());

        // compare EXDATE
        assertEquals(1, event2.getExDates().size());
        assertEquals(event.getExDates().get(0), event2.getExDates().get(0));
    }

    public void testUpdateEvent() throws URISyntaxException, ParseException, FileNotFoundException, CalendarStorageException {
        // add test event without reminder
        Event event = new Event();
        event.setUid("sample1@testAddEvent");
        event.setSummary("Sample event");
        event.setDtStart(new DtStart("20150502T120000Z"));
        event.setDtEnd(new DtEnd("20150502T130000Z"));
        event.setOrganizer(new Organizer(new URI("mailto:organizer@example.com")));
        Uri uri = new TestEvent(calendar, event).add();

        // update test event in calendar
        @Cleanup("delete") TestEvent testEvent = new TestEvent(calendar, ContentUris.parseId(uri));
        event = testEvent.getEvent();
        event.setSummary("Updated event");
        // add data rows
        event.getAlarms().add(new VAlarm(new Dur(-1, -2, -3, -4)));
        event.getAttendees().add(new Attendee(new URI("mailto:user@example.com")));
        uri = testEvent.update(event);

        // read again and verify result
        testEvent = new TestEvent(calendar, ContentUris.parseId(uri));
        Event updatedEvent = testEvent.getEvent();
        assertEquals(event.getSummary(), updatedEvent.getSummary());
        assertEquals(1, updatedEvent.getAlarms().size());
        assertEquals(1, updatedEvent.getAttendees().size());
    }

    public void testLargeTransactionManyRows() throws ParseException, CalendarStorageException, URISyntaxException, FileNotFoundException {
        Event event = new Event();
        event.setUid("sample1@testLargeTransaction");
        event.setSummary("Large event");
        event.setDtStart(new DtStart("20150502T120000Z"));
        event.setDtEnd(new DtEnd("20150502T130000Z"));
        for (int i = 0; i < 4000; i++)
            event.getAttendees().add(new Attendee(new URI("mailto:att" + i + "@example.com")));
        Uri uri = new TestEvent(calendar, event).add();

        @Cleanup("delete") TestEvent testEvent = new TestEvent(calendar, ContentUris.parseId(uri));
        assertEquals(4000, testEvent.getEvent().getAttendees().size());
    }

    public void testLargeTransactionSingleRow() throws ParseException, CalendarStorageException, URISyntaxException, FileNotFoundException {
        Event event = new Event();
        event.setUid("sample1@testLargeTransaction");
        event.setDtStart(new DtStart("20150502T120000Z"));
        event.setDtEnd(new DtEnd("20150502T130000Z"));

        // 1 MB SUMMARY ... have fun
        char data[] = new char[1024*1024];
        Arrays.fill(data, 'x');
        event.setSummary(new String(data));

        try {
            Uri uri = new TestEvent(calendar, event).add();
            fail();
        } catch(CalendarStorageException e) {
            assertTrue(e.getCause() instanceof RemoteException);
        }
    }

    public void testAllDay() throws ParseException, FileNotFoundException, CalendarStorageException {
        // add all-day event to calendar provider
        Event event = new Event();
        event.setSummary("All-day event");
        event.setDescription("All-day event for testing");
        event.setLocation("Sample location testBuildAllDayEntry");
        event.setDtStart(new DtStart(new Date("20150501")));
        event.setDtEnd(new DtEnd(new Date("20150501")));  // "events on same day" are not understood by Android, so it should be changed to next day
        assertTrue(event.isAllDay());
        Uri uri = new TestEvent(calendar, event).add();
        assertNotNull("Couldn't add event", uri);

        // read again and verify result
        @Cleanup("delete") TestEvent testEvent = new TestEvent(calendar, ContentUris.parseId(uri));
        Event event2 = testEvent.getEvent();
        // compare with original event
        assertEquals(event.getSummary(), event2.getSummary());
        assertEquals(event.getDescription(), event2.getDescription());
        assertEquals(event.getLocation(), event2.getLocation());
        assertEquals(event.getDtStart(), event2.getDtStart());
        assertEquals(event.getDtEnd().getDate(), new Date("20150502"));
        assertTrue(event2.isAllDay());
    }

    public void testAllDayWithoutDtEndOrDuration() throws ParseException, FileNotFoundException, CalendarStorageException {
        // add event without dtEnd/duration to calendar provider
        Event event = new Event();
        event.setSummary("Event without duration");
        event.setDtStart(new DtStart(new Date("20150501")));
        Uri uri = new TestEvent(calendar, event).add();
        assertNotNull("Couldn't add event", uri);

        // read again and verify result
        @Cleanup("delete") TestEvent testEvent = new TestEvent(calendar, ContentUris.parseId(uri));
        Event event2 = testEvent.getEvent();
        // should now be an all-day event (converted by ical4android because events without duration don't show up in Android calendar)
        assertEquals(event.getDtStart(), event2.getDtStart());
        assertEquals(event.getDtStart().getDate().getTime() + 86400000, event2.getDtEnd().getDate().getTime());
        assertTrue(event2.isAllDay());
    }

    public void testAllDayWithZeroDuration() throws ParseException, FileNotFoundException, CalendarStorageException {
        // add all-day event with 0 sec duration to calendar provider
        Event event = new Event();
        event.setSummary("Event with zero duration");
        event.setDtStart(new DtStart(new Date("20150501")));
        event.setDuration(new Duration(new Dur("PT0S")));
        Uri uri = new TestEvent(calendar, event).add();
        assertNotNull("Couldn't add event", uri);

        // read again and verify result
        @Cleanup("delete") TestEvent testEvent = new TestEvent(calendar, ContentUris.parseId(uri));
        Event event2 = testEvent.getEvent();
        // should now be an all-day event (converted by ical4android because events without duration don't show up in Android calendar)
        assertEquals(event.getDtStart(), event2.getDtStart());
        assertEquals(event.getDtStart().getDate().getTime() + 86400000, event2.getDtEnd().getDate().getTime());
        assertTrue(event2.isAllDay());
    }

    public void testClassificationConfidential() throws Exception {
        Event event = new Event();
        event.setSummary("Confidential event");
        event.setDtStart(new DtStart(new Date("20150501")));
        event.setDtEnd(new DtEnd(new Date("20150502")));
        event.setClassification(Clazz.CONFIDENTIAL);
        Uri uri = new TestEvent(calendar, event).add();
        assertNotNull("Couldn't add event", uri);
        long id = ContentUris.parseId(uri);

        // now, the calendar app changes to ACCESS_DEFAULT
        ContentValues values = new ContentValues(1);
        values.put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT);
        calendar.getProvider().update(calendar.syncAdapterURI(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)),
                values, null, null);

        // read again and verify result
        @Cleanup("delete") TestEvent testEvent = new TestEvent(calendar, id);
        Event event2 = testEvent.getEvent();
        // CONFIDENTIAL has been retained
        assertTrue(event.getUnknownProperties().contains(Clazz.CONFIDENTIAL));
        // should still be CONFIDENTIAL
        assertEquals(event.getClassification(), event2.getClassification());

        // now, the calendar app changes to ACCESS_PRIVATE
        values.put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE);
        calendar.getProvider().update(calendar.syncAdapterURI(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)),
                values, null, null);

        // read again and verify result
        TestEvent testEventPrivate = new TestEvent(calendar, id);
        Event eventPrivate = testEventPrivate.getEvent();
        // should be PRIVATE
        assertEquals(Clazz.PRIVATE, eventPrivate.getClassification());
        // the retained value is not used in this case
        assertFalse(eventPrivate.getUnknownProperties().contains(Clazz.CONFIDENTIAL));
    }

    public void testClassificationPrivate() throws Exception {
        Event event = new Event();
        event.setSummary("Private event");
        event.setDtStart(new DtStart(new Date("20150501")));
        event.setDtEnd(new DtEnd(new Date("20150502")));
        event.setClassification(Clazz.PRIVATE);
        Uri uri = new TestEvent(calendar, event).add();
        assertNotNull("Couldn't add event", uri);
        long id = ContentUris.parseId(uri);

        // read again and verify result
        @Cleanup("delete") TestEvent testEvent = new TestEvent(calendar, id);
        Event event2 = testEvent.getEvent();
        // PRIVATE has not been retained
        assertFalse(event.getUnknownProperties().contains(Clazz.PRIVATE));
        // should still be PRIVATE
        assertEquals(Clazz.PRIVATE, event2.getClassification());
    }

    public void testNoOrganizerWithoutAttendees() throws ParseException, URISyntaxException, CalendarStorageException, FileNotFoundException {
        Event event = new Event();
        event.setSummary("Not a group-scheduled event");
        event.setDtStart(new DtStart(new Date("20150501")));
        event.setDtEnd(new DtEnd(new Date("20150502")));
        event.setRRule(new RRule("FREQ=DAILY;COUNT=10;INTERVAL=1"));
        event.setOrganizer(new Organizer("mailto:test@test.at"));

        Event exception = new Event();
        exception.setRecurrenceId(new RecurrenceId(new Date("20150502")));
        exception.setDtStart(new DtStart(new Date("20150502")));
        exception.setDtEnd(new DtEnd(new Date("20150503")));
        exception.setStatus(Status.VEVENT_CANCELLED);
        exception.setOrganizer(event.getOrganizer());
        event.getExceptions().add(exception);

        Uri uri = new TestEvent(calendar, event).add();
        assertNotNull("Couldn't add event", uri);

        // read again and verify result
        @Cleanup("delete") TestEvent testEvent = new TestEvent(calendar, ContentUris.parseId(uri));
        event = testEvent.getEvent();
        assertNull(event.getOrganizer());
        exception = event.getExceptions().get(0);
        assertNull(exception.getOrganizer());
    }

    public void testPopulateEventWithoutDuration() throws RemoteException, FileNotFoundException, CalendarStorageException {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.CALENDAR_ID, calendar.getId());
        values.put(CalendarContract.Events.DTSTART, 1381330800000L);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, "Europe/Vienna");
        values.put(CalendarContract.Events.TITLE, "Without dtend/duration");
        Uri uri = provider.insert(syncAdapterURI(CalendarContract.Events.CONTENT_URI), values);

        @Cleanup("delete") TestEvent testEvent = new TestEvent(calendar, ContentUris.parseId(uri));
        assertNull(testEvent.getEvent().getDtEnd());
    }

    public void testWithZeroDuration() throws ParseException, FileNotFoundException, CalendarStorageException {
        // add event with 0 sec duration to calendar provider
        Event event = new Event();
        event.setSummary("Event with zero duration");
        event.setDtStart(new DtStart(new Date("20150501T152010Z")));
        event.setDuration(new Duration(new Dur("PT0S")));
        Uri uri = new TestEvent(calendar, event).add();
        assertNotNull("Couldn't add event", uri);

        // read again and verify result
        @Cleanup("delete") TestEvent testEvent = new TestEvent(calendar, ContentUris.parseId(uri));
        Event event2 = testEvent.getEvent();
        // should now be an event with one day duration (converted by ical4android because events without duration don't show up in Android calendar)
        assertEquals(event.getDtStart(), event2.getDtStart());
        assertEquals(event.getDtStart().getDate().getTime() + 86400000, event2.getDtEnd().getDate().getTime());
        assertTrue(event2.isAllDay());
    }


}
