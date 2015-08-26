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

public class TestEvent extends AndroidEvent {

    protected TestEvent(AndroidCalendar calendar, long id) {
        super(calendar, id);
    }

    protected TestEvent(AndroidCalendar calendar, Event event) {
        super(calendar, event);
    }


    public static class Factory implements AndroidEventFactory {

        public static final Factory FACTORY = new Factory();

        @Override
        public AndroidEvent newInstance(AndroidCalendar calendar, long id) {
            return new TestEvent(calendar, id);
        }

        @Override
        public AndroidEvent newInstance(AndroidCalendar calendar, Event event) {
            return new TestEvent(calendar, event);
        }

        @Override
        public AndroidEvent[] newArray(int size) {
            return new TestEvent[size];
        }
    }

}
