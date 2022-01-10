/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.impl

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidCalendarFactory

class TestCalendar(
        account: Account,
        providerClient: ContentProviderClient,
        id: Long
): AndroidCalendar<TestEvent>(account, providerClient, TestEvent.Factory, id) {

    companion object {
        fun findOrCreate(account: Account, provider: ContentProviderClient): TestCalendar {
            val calendars = AndroidCalendar.find(account, provider, Factory, null, null)
            return if (calendars.isEmpty()) {
                val values = ContentValues(3)
                values.put(CalendarContract.Calendars.NAME, "TestCalendar")
                values.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "ical4android Test Calendar")
                values.put(CalendarContract.Calendars.ALLOWED_REMINDERS, CalendarContract.Reminders.METHOD_DEFAULT)
                val uri = AndroidCalendar.create(account, provider, values)

                TestCalendar(account, provider, ContentUris.parseId(uri))
            } else
                calendars.first()
        }
    }


    object Factory: AndroidCalendarFactory<TestCalendar> {
        override fun newInstance(account: Account, provider: ContentProviderClient, id: Long) =
                TestCalendar(account, provider, id)
    }

}
