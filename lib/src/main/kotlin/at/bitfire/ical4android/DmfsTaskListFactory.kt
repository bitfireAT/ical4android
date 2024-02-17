/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.accounts.Account

interface DmfsTaskListFactory<out T: DmfsTaskList<DmfsTask>> {

    fun newInstance(account: Account, provider: TaskProvider, id: Long): T

}