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

import android.Manifest;
import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.CalendarContract;
import android.support.annotation.RequiresPermission;
import android.test.InstrumentationTestCase;
import android.util.Log;

import org.dmfs.provider.tasks.TaskContract;

import java.io.FileNotFoundException;

import at.bitfire.ical4android.impl.TestCalendar;

public class AndroidCalendarTest extends InstrumentationTestCase {
    private static final String TAG = "ical4android.Calendar";

    final Account testAccount = new Account("ical4android.AndroidCalendarTest", TaskContract.LOCAL_ACCOUNT_TYPE);
    ContentProviderClient provider;

    @Override
    @RequiresPermission(allOf = { Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR })
    public void setUp() throws Exception {
        provider = getInstrumentation().getContext().getContentResolver().acquireContentProviderClient(CalendarContract.AUTHORITY);
        assertNotNull(provider);
    }

    @Override
    public void tearDown() throws Exception {
        provider.release();
    }

    public void testManageCalendars() throws CalendarStorageException, FileNotFoundException {
        // create calendar
        ContentValues info = new ContentValues();
        info.put(CalendarContract.Calendars.NAME, "TestCalendar");
        info.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "ical4android Test Calendar");
        info.put(CalendarContract.Calendars.VISIBLE, 0);
        info.put(CalendarContract.Calendars.SYNC_EVENTS, 0);
        Uri uri = TestCalendar.create(testAccount, provider, info);
        assertNotNull(uri);
        Log.i(TAG, "Created calendar: " + uri);

        // query task list
        TestCalendar calendar = (TestCalendar)TestCalendar.findByID(testAccount, provider, TestCalendar.Factory.FACTORY, ContentUris.parseId(uri));
        assertNotNull(calendar);

        // delete task list
        assertEquals(1, calendar.delete());
    }

}
