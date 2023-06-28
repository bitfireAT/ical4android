/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient

interface AndroidCalendarFactory<out T: AndroidCalendar<AndroidEvent>> {

    fun newInstance(account: Account, provider: ContentProviderClient, id: Long): T

}
