/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.accounts.Account

interface AndroidTaskListFactory<out T: AndroidTaskList<AndroidTask>> {

    fun newInstance(account: Account, provider: TaskProvider, id: Long): T

}