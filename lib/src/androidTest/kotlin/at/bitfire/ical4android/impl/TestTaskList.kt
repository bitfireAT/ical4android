/*
 * This file is part of ical4android which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.impl

import android.accounts.Account
import android.content.ContentProviderClient
import at.bitfire.ical4android.DmfsTaskList
import at.bitfire.ical4android.DmfsTaskListFactory
import at.bitfire.ical4android.TaskProvider

class TestTaskList(
    account: Account,
    provider: ContentProviderClient,
    providerName: TaskProvider.ProviderName,
    id: Long
) : DmfsTaskList<TestTask>(account, provider, providerName, TestTask.Factory, id) {

    object Factory : DmfsTaskListFactory<TestTaskList> {
        override fun newInstance(account: Account, provider: ContentProviderClient, providerName: TaskProvider.ProviderName, id: Long) =
            TestTaskList(account, provider, providerName, id)
    }

}