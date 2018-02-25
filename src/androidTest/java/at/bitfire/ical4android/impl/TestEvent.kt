/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android.impl

import android.content.ContentValues
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.AndroidEventFactory
import at.bitfire.ical4android.Event

class TestEvent: AndroidEvent {

    constructor(calendar: AndroidCalendar<AndroidEvent>, values: ContentValues)
        : super(calendar, values)

    constructor(calendar: TestCalendar, event: Event)
        : super(calendar, event)


    object Factory: AndroidEventFactory<TestEvent> {
        override fun fromProvider(calendar: AndroidCalendar<AndroidEvent>, values: ContentValues) =
                TestEvent(calendar, values)
    }

}
