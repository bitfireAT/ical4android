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

package at.bitfire.ical4android.impl;

import android.content.ContentValues;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import at.bitfire.ical4android.AndroidCalendar;
import at.bitfire.ical4android.AndroidEvent;
import at.bitfire.ical4android.AndroidEventFactory;
import at.bitfire.ical4android.Event;

public class TestEvent extends AndroidEvent {

    public TestEvent(AndroidCalendar calendar, long id) {
        super(calendar, id, null);
    }
    public TestEvent(AndroidCalendar calendar, Event event) {
        super(calendar, event);
    }


    public static class Factory implements AndroidEventFactory<TestEvent> {

        public static final Factory INSTANCE = new Factory();

        @NotNull
        @Override
        public TestEvent newInstance(@NotNull AndroidCalendar calendar, long id, @Nullable ContentValues baseInfo) {
            return new TestEvent(calendar, id);
        }

        @NotNull
        @Override
        public TestEvent newInstance(@NotNull AndroidCalendar calendar, @NotNull Event event) {
            return new TestEvent(calendar, event);
        }
    }

}
