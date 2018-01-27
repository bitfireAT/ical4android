/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
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
