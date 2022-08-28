/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.impl

import android.content.ContentValues
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.*
import java.util.*

class TestEvent: AndroidEvent {

    constructor(calendar: AndroidCalendar<AndroidEvent>, values: ContentValues)
        : super(calendar, values)

    constructor(calendar: TestCalendar, event: Event)
        : super(calendar, event)

    val syncId by lazy { UUID.randomUUID().toString() }


    override fun buildEvent(recurrence: Event?, builder: BatchOperation.CpoBuilder) {
        if (recurrence != null)
            builder.withValue(Events.ORIGINAL_SYNC_ID, syncId)
        else
            builder.withValue(Events._SYNC_ID, syncId)

        super.buildEvent(recurrence, builder)
    }


    object Factory: AndroidEventFactory<TestEvent> {
        override fun fromProvider(calendar: AndroidCalendar<AndroidEvent>, values: ContentValues) =
                TestEvent(calendar, values)
    }

}
