/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;
import at.bitfire.ical4android.AndroidTaskList.Companion.syncAdapterURI

import org.apache.commons.lang3.ArrayUtils;
import org.dmfs.provider.tasks.TaskContract;
import org.dmfs.provider.tasks.TaskContract.TaskLists;
import org.dmfs.provider.tasks.TaskContract.Tasks;

import java.io.FileNotFoundException;
import java.util.LinkedList;

import lombok.Cleanup;
import lombok.Getter;

/**
 * Represents a locally stored task list, containing AndroidTasks (whose data objects are Tasks).
 * Communicates with third-party content providers to store the tasks.
 * Currently, only the OpenTasks tasks provider (org.dmfs.provider.tasks) is supported.
 */
abstract class AndroidTaskList(
        val account: Account,
        val provider: TaskProvider,
        val taskFactory: AndroidTaskFactory<AndroidTask>,
        val id: Long
) {

    var syncId: String? = null
    var name: String? = null
    var color: Int? = null
    var isSynced = true
    var isVisible = true

    /** Those columns will always be fetched when tasks are queried by {@link #queryTasks(String, String[])}.
     *  Must include Tasks._ID as the first element! */
    protected fun taskBaseInfoColumns() = arrayOf(Tasks._ID)

	
	companion object {

        /**
         * Acquires a ContentProviderClient for a supported task provider. If multiple providers are
         * available, a pre-defined priority list is taken into account.
         * @return A TaskProvider, or null if task storage is not available/accessible.
         *         Caller is responsible for calling release()!
         */
        @JvmStatic
        fun acquireTaskProvider(resolver: ContentResolver): TaskProvider? {
            val byPriority = arrayOf(
                //TaskProvider.ProviderName.Mirakel,
                TaskProvider.ProviderName.OpenTasks
            )
            for (name in byPriority)
                TaskProvider.acquire(resolver, name)?.let { return it }
            return null
        }

        @JvmStatic
        @Throws(CalendarStorageException::class)
        fun create(account: Account, provider: TaskProvider, info: ContentValues): Uri {
            info.put(TaskContract.ACCOUNT_NAME, account.name)
            info.put(TaskContract.ACCOUNT_TYPE, account.type)
            info.put(TaskLists.ACCESS_LEVEL, 0)

            Constants.log.info("Creating local task list: " + info.toString())
            try {
                return provider.client.insert(syncAdapterURI(provider.taskListsUri(), account), info)
            } catch(e: RemoteException) {
                throw CalendarStorageException("Couldn't create local task list", e)
            }
        }

        @JvmStatic
        @Throws(FileNotFoundException::class, CalendarStorageException::class)
        fun<T: AndroidTaskList> findByID(account: Account, provider: TaskProvider, factory: AndroidTaskListFactory<T>, id: Long): T {
            try {
                provider.client.query(syncAdapterURI(ContentUris.withAppendedId(provider.taskListsUri(), id), account), null, null, null, null)?.use { cursor ->
                    if (cursor.moveToNext()) {
                        val taskList = factory.newInstance(account, provider, id)

                        val values = ContentValues(cursor.columnCount)
                        DatabaseUtils.cursorRowToContentValues(cursor, values)
                        taskList.populate(values)
                        return taskList
                    }
                }
            } catch(e: RemoteException) {
                throw CalendarStorageException("Couldn't query task list by ID", e)
            }
            throw FileNotFoundException()
        }

        @Throws(CalendarStorageException::class)
        fun<T: AndroidTaskList> find(account: Account, provider: TaskProvider, factory: AndroidTaskListFactory<T>, where: String, whereArgs: Array<String>): List<T> {
            val taskLists = LinkedList<T>()
            try {
                provider.client.query(syncAdapterURI(provider.taskListsUri(), account), null, where, whereArgs, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val values = ContentValues(cursor.columnCount)
                        DatabaseUtils.cursorRowToContentValues(cursor, values)
                        val taskList = factory.newInstance(account, provider, values.getAsLong(TaskLists._ID))
                        taskList.populate(values)
                        taskLists.add(taskList)
                    }
                }
            } catch(e: RemoteException) {
                throw CalendarStorageException("Couldn't query task list by ID", e)
            }
            return taskLists
        }

        @JvmStatic
        fun syncAdapterURI(uri: Uri, account: Account) = uri.buildUpon()
                .appendQueryParameter(TaskContract.ACCOUNT_NAME, account.name)
                .appendQueryParameter(TaskContract.ACCOUNT_TYPE, account.type)
                .appendQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER, "true")
                .build()!!

    }

    protected fun populate(values: ContentValues) {
        syncId = values.getAsString(TaskLists._SYNC_ID)
        name = values.getAsString(TaskLists.LIST_NAME)
        color = values.getAsInteger(TaskLists.LIST_COLOR)
        if (values.containsKey(TaskLists.SYNC_ENABLED))
            isSynced = values.getAsInteger(TaskLists.SYNC_ENABLED) != 0
        if (values.containsKey(TaskLists.VISIBLE))
            isVisible = values.getAsInteger(TaskLists.VISIBLE) != 0
    }

    @Throws(CalendarStorageException::class)
    fun update(info: ContentValues): Int {
        try {
            return provider.client.update(taskListSyncUri(), info, null, null)
        } catch (e: RemoteException) {
            throw CalendarStorageException("Couldn't update local task list", e)
        }
    }

    @Throws(CalendarStorageException::class)
    fun delete(): Int {
        try {
            return provider.client.delete(taskListSyncUri(), null, null)
        } catch (e: RemoteException) {
            throw CalendarStorageException("Couldn't delete local task list", e)
        }
    }


    @Throws(CalendarStorageException::class)
    protected fun queryTasks(where: String, whereArgs: Array<String> = arrayOf()): List<AndroidTask> {
        val where = "($where) AND ${Tasks.LIST_ID}=?"
        val whereArgs = whereArgs + id.toString()

        val tasks = LinkedList<AndroidTask>()
        try {
            provider.client.query(
                    syncAdapterURI(provider.tasksUri()),
                    taskBaseInfoColumns(),
                    where, whereArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val baseInfo = ContentValues(cursor.getColumnCount())
                    DatabaseUtils.cursorRowToContentValues(cursor, baseInfo)
                    tasks.add(taskFactory.newInstance(this, cursor.getLong(0), baseInfo))
                }
            }
        } catch (e: RemoteException) {
            throw CalendarStorageException("Couldn't query calendar events", e)
        }
        return tasks
    }


    fun syncAdapterURI(uri: Uri) = uri.buildUpon()
                .appendQueryParameter(TaskContract.ACCOUNT_NAME, account.name)
                .appendQueryParameter(TaskContract.ACCOUNT_TYPE, account.type)
                .appendQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER, "true")
                .build()!!

    fun taskListSyncUri() =
        syncAdapterURI(ContentUris.withAppendedId(provider.taskListsUri(), id))

}
