/*
 * This file is part of ical4android which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.impl

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import at.bitfire.ical4android.DmfsTaskList
import at.bitfire.ical4android.DmfsTaskListFactory
import at.bitfire.ical4android.TaskProvider
import org.dmfs.tasks.contract.TaskContract

class TestTaskList(
        account: Account,
        provider: ContentProviderClient,
        providerName: TaskProvider.ProviderName,
        id: Long
): DmfsTaskList<TestTask>(account, provider, providerName, TestTask.Factory, id) {

    companion object {

        fun create(
            account: Account,
            provider: TaskProvider,
        ): TestTaskList {
            val values = ContentValues(4)
            values.put(TaskContract.TaskListColumns.LIST_NAME, "Test Task List")
            values.put(TaskContract.TaskListColumns.LIST_COLOR, 0xffff0000)
            values.put(TaskContract.TaskListColumns.SYNC_ENABLED, 1)
            values.put(TaskContract.TaskListColumns.VISIBLE, 1)
            val uri = DmfsTaskList.create(account, provider.client, provider.name, values)

            return TestTaskList(account, provider.client, provider.name, ContentUris.parseId(uri))
        }

    }


    object Factory: DmfsTaskListFactory<TestTaskList> {
        override fun newInstance(account: Account, provider: ContentProviderClient, providerName: TaskProvider.ProviderName, id: Long) =
                TestTaskList(account, provider, providerName, id)
    }

}
