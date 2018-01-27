/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android.impl;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import at.bitfire.ical4android.AndroidCalendar;
import at.bitfire.ical4android.AndroidCalendarFactory;
import at.bitfire.ical4android.CalendarStorageException;

public class TestCalendar extends AndroidCalendar<TestEvent> {
    private static final String TAG = "ical4android.TestCal";

    public TestCalendar(Account account, ContentProviderClient provider, long id) {
        super(account, provider, TestEvent.Factory.INSTANCE, id);
    }

    static public TestCalendar findOrCreate(Account account, ContentProviderClient provider) throws CalendarStorageException {
        List<TestCalendar> calendars = AndroidCalendar.find(account, provider, Factory.INSTANCE, null, null);
        if (calendars.size() == 0) {
            Log.i(TAG, "Test calendar not found, creating");

            ContentValues values = new ContentValues();
            values.put(CalendarContract.Calendars.NAME, "TestCalendar");
            values.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "ical4android Test Calendar");
            values.put(CalendarContract.Calendars.ALLOWED_REMINDERS,
                    CalendarContract.Reminders.METHOD_DEFAULT);
            Uri uri = AndroidCalendar.create(account, provider, values);

            return new TestCalendar(account, provider, ContentUris.parseId(uri));
        } else
            return calendars.get(0);
    }


    public static class Factory implements AndroidCalendarFactory<TestCalendar> {

        public static final Factory INSTANCE = new Factory();

        @NotNull
        @Override
        public TestCalendar newInstance(@NotNull Account account, @NotNull ContentProviderClient provider, long id) {
            return new TestCalendar(account, provider, id);
        }

    }

}
