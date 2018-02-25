/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.net.Uri
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.TaskLists
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.io.FileNotFoundException
import java.util.*

/**
 * Represents a locally stored task list, containing AndroidTasks (whose data objects are Tasks).
 * Communicates with third-party content providers to store the tasks.
 * Currently, only the OpenTasks tasks provider (org.dmfs.provider.tasks) is supported.
 */
abstract class AndroidTaskList<out T: AndroidTask>(
        val account: Account,
        val provider: TaskProvider,
        val taskFactory: AndroidTaskFactory<T>,
        val id: Long
) {

	companion object {

        /**
         * Acquires a [ContentProviderClient] for a supported task provider. If multiple providers are
         * available, a pre-defined priority list is taken into account.
         * @return A [TaskProvider], or null if task storage is not available/accessible.
         *         Caller is responsible for calling release()!
         */
        fun acquireTaskProvider(context: Context): TaskProvider? {
            val byPriority = arrayOf(
                TaskProvider.ProviderName.OpenTasks
            )
            for (name in byPriority)
                TaskProvider.acquire(context, name)?.let { return it }
            return null
        }

        fun create(account: Account, provider: TaskProvider, info: ContentValues): Uri {
            info.put(TaskContract.ACCOUNT_NAME, account.name)
            info.put(TaskContract.ACCOUNT_TYPE, account.type)
            info.put(TaskLists.ACCESS_LEVEL, 0)

            Constants.log.info("Creating local task list: " + info.toString())
            return provider.client.insert(TaskProvider.syncAdapterUri(provider.taskListsUri(), account), info)
        }

        fun<T: AndroidTaskList<AndroidTask>> findByID(account: Account, provider: TaskProvider, factory: AndroidTaskListFactory<T>, id: Long): T {
            provider.client.query(TaskProvider.syncAdapterUri(ContentUris.withAppendedId(provider.taskListsUri(), id), account), null, null, null, null)?.use { cursor ->
                if (cursor.moveToNext()) {
                    val taskList = factory.newInstance(account, provider, id)
                    val values = ContentValues(cursor.columnCount)
                    DatabaseUtils.cursorRowToContentValues(cursor, values)
                    taskList.populate(values)
                    return taskList
                }
            }
            throw FileNotFoundException()
        }

        fun<T: AndroidTaskList<AndroidTask>> find(account: Account, provider: TaskProvider, factory: AndroidTaskListFactory<T>, where: String?, whereArgs: Array<String>?): List<T> {
            val taskLists = LinkedList<T>()
            provider.client.query(TaskProvider.syncAdapterUri(provider.taskListsUri(), account), null, where, whereArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val values = ContentValues(cursor.columnCount)
                    DatabaseUtils.cursorRowToContentValues(cursor, values)
                    val taskList = factory.newInstance(account, provider, values.getAsLong(TaskLists._ID))
                    taskList.populate(values)
                    taskLists += taskList
                }
            }
            return taskLists
        }

    }

    var syncId: String? = null
    var name: String? = null
    var color: Int? = null
    var isSynced = false
    var isVisible = false


    protected fun populate(values: ContentValues) {
        syncId = values.getAsString(TaskLists._SYNC_ID)
        name = values.getAsString(TaskLists.LIST_NAME)
        color = values.getAsInteger(TaskLists.LIST_COLOR)
        values.getAsInteger(TaskLists.SYNC_ENABLED)?.let { isSynced = it != 0 }
        values.getAsInteger(TaskLists.VISIBLE)?.let { isVisible = it != 0 }
    }

    fun update(info: ContentValues) = provider.client.update(taskListSyncUri(), info, null, null)
    fun delete() = provider.client.delete(taskListSyncUri(), null, null)


    fun queryTasks(where: String? = null, whereArgs: Array<String>? = null): List<T> {
        val where = "(${where ?: "1"}) AND ${Tasks.LIST_ID}=?"
        val whereArgs = (whereArgs ?: arrayOf()) + id.toString()

        val tasks = LinkedList<T>()
        provider.client.query(
                tasksSyncUri(),
                null,
                where, whereArgs, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val values = ContentValues(cursor.columnCount)
                DatabaseUtils.cursorRowToContentValues(cursor, values)
                tasks += taskFactory.fromProvider(this, values)
            }
        }
        return tasks
    }

    fun findById(id: Long) = queryTasks("${Tasks._ID}=?", arrayOf(id.toString())).firstOrNull()
            ?: throw FileNotFoundException()


    fun taskListSyncUri() = TaskProvider.syncAdapterUri(ContentUris.withAppendedId(provider.taskListsUri(), id), account)
    fun tasksSyncUri() = TaskProvider.syncAdapterUri(provider.tasksUri(), account)

}
