/*
 * This file is part of ical4android which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.ical4android.util.MiscUtils.toValues
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.TaskLists
import java.io.FileNotFoundException
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

class DmfsTaskListStore<TaskList: DmfsTaskList<Task>, Task: DmfsTask>(
    private val account: Account,
    private val providerName: TaskProvider.ProviderName,
    private val provider: ContentProviderClient,
    private val taskListFactory: DmfsTaskListFactory<TaskList>
) {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    fun create(info: ContentValues): Uri {
        info.put(TaskContract.ACCOUNT_NAME, account.name)
        info.put(TaskContract.ACCOUNT_TYPE, account.type)

        logger.log(Level.FINE, "Creating ${providerName.authority} task list", info)
        val uri = TaskLists.getContentUri(providerName.authority).asSyncAdapter(account)
        return provider.insert(uri, info)
            ?: throw CalendarStorageException("Couldn't create task list (empty result from provider)")
    }

    fun getById(id: Long): TaskList {
        provider.query(
            /* url = */ ContentUris.withAppendedId(TaskLists.getContentUri(providerName.authority), id).asSyncAdapter(account),
            /* projection = */ null,
            /* selection = */ null,
            /* selectionArgs = */ null,
            /* sortOrder = */ null
        )?.use { cursor ->
            if (cursor.moveToNext()) {
                val taskList = taskListFactory.newInstance(account, provider, providerName, id)
                taskList.populate(cursor.toValues())
                return taskList
            }
        }

        // no task list found
        throw FileNotFoundException()
    }

    fun find(where: String?, whereArgs: Array<String>?): List<TaskList> {
        val taskLists = LinkedList<TaskList>()
        provider.query(
            /* url = */ TaskLists.getContentUri(providerName.authority).asSyncAdapter(account),
            /* projection = */ null,
            /* selection = */ where,
            /* selectionArgs = */ whereArgs,
            /* sortOrder = */ null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val values = cursor.toValues()
                val taskList = taskListFactory.newInstance(account, provider, providerName, values.getAsLong(TaskLists._ID))
                taskList.populate(values)
                taskLists += taskList
            }
        }
        return taskLists
    }

}