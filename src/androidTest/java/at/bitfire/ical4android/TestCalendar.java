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
import android.provider.CalendarContract;
import android.util.Log;

public class TestCalendar extends AndroidCalendar {
    private static final String TAG = "ical4android.TestCal";

    protected TestCalendar(Account account, ContentProviderClient provider, long id) {
        super(account, provider, TestEvent.Factory.FACTORY, id);
    }

    static TestCalendar findOrCreate(Account account, ContentProviderClient provider) throws CalendarStorageException {
        TestCalendar[] calendars = (TestCalendar[])AndroidCalendar.findAll(account, provider, Factory.FACTORY);
        if (calendars.length == 0) {
            Log.i(TAG, "Test calendar not found, creating");

            ContentValues values = new ContentValues();
            values.put(CalendarContract.Calendars.NAME, "TestCalendar");
            values.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "ical4android Test Calendar");
            values.put(CalendarContract.Calendars.ALLOWED_REMINDERS,
                    CalendarContract.Reminders.METHOD_DEFAULT + "," + CalendarContract.Reminders.METHOD_ALARM + "," + CalendarContract.Reminders.METHOD_ALERT);
            Uri uri = AndroidCalendar.create(account, provider, values);

            return new TestCalendar(account, provider, ContentUris.parseId(uri));
        } else
            return calendars[0];
    }


    public static class Factory implements AndroidCalendarFactory {

        public static final Factory FACTORY = new Factory();

        @Override
        public AndroidCalendar newInstance(Account account, ContentProviderClient provider, long id) {
            return new TestCalendar(account, provider, id);
        }

        @Override
        public AndroidCalendar[] newArray(int size) {
            return new TestCalendar[size];
        }

    }

}
