/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.content.ContentValues

interface AndroidEventFactory<out T: AndroidEvent> {

    fun fromProvider(calendar: AndroidCalendar<AndroidEvent>, values: ContentValues): T

}