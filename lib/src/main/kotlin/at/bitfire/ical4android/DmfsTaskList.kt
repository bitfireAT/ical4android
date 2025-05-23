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
import androidx.annotation.CallSuper
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.ical4android.util.MiscUtils.toValues
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.dmfs.tasks.contract.TaskContract.TaskLists
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.io.FileNotFoundException
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Represents a locally stored task list, containing [DmfsTask]s (tasks).
 * Communicates with tasks.org-compatible content providers (currently tasks.org and OpenTasks) to store the tasks.
 */
abstract class DmfsTaskList<out T : DmfsTask>(
    val account: Account,
    val provider: ContentProviderClient,
    val providerName: TaskProvider.ProviderName,
    val taskFactory: DmfsTaskFactory<T>,
    val id: Long
) {

    private val logger
        get() = Logger.getLogger(DmfsTaskList::class.java.name)

    var syncId: String? = null
    var name: String? = null
    var color: Int? = null
    var isSynced = false
    var isVisible = false

    /**
     * Sets the task list properties ([syncId], [name] etc.) from the passed argument,
     * which is usually directly taken from the tasks provider.
     *
     * Called when an instance is created from a tasks provider data row, for example
     * using [find].
     *
     * @param values  values from tasks provider
     */
    @CallSuper
    internal open fun populate(values: ContentValues) {
        syncId = values.getAsString(TaskLists._SYNC_ID)
        name = values.getAsString(TaskLists.LIST_NAME)
        color = values.getAsInteger(TaskLists.LIST_COLOR)
        values.getAsInteger(TaskLists.SYNC_ENABLED)?.let { isSynced = it != 0 }
        values.getAsInteger(TaskLists.VISIBLE)?.let { isVisible = it != 0 }
    }

    fun update(info: ContentValues): Int {
        logger.log(Level.FINE, "Updating ${providerName.authority} task list (#$id)", info)
        return provider.update(taskListSyncUri(), info, null, null)
    }

    /**
     * Deletes this calendar from the local calendar provider.
     *
     * @return `true` if the calendar was deleted, `false` otherwise (like it was not there before the call)
     */
    fun delete(): Boolean {
        logger.log(Level.FINE, "Deleting ${providerName.authority} task list (#$id)")
        return provider.delete(taskListSyncUri(), null, null) > 0
    }

    /**
     * When tasks are added or updated, they may refer to related tasks by UID ([Relation.RELATED_UID]).
     * However, those related tasks may not be available (for instance, because they have not been
     * synchronized yet), so that the tasks provider can't establish the actual relation (= set
     * [Relation.TASK_ID]) in the database.
     *
     * As soon as such a related task is added, OpenTasks updates the [Relation.RELATED_ID],
     * but it does *not* update [Tasks.PARENT_ID] of the parent task:
     * https://github.com/dmfs/opentasks/issues/877
     *
     * This method shall be called after all tasks have been synchronized. It touches
     *
     *   - all [Relation] rows
     *   - with [Relation.RELATED_ID] (→ related task is already synchronized)
     *   - of tasks without [Tasks.PARENT_ID] (→ only touch relevant rows)
     *
     * so that missing [Tasks.PARENT_ID] fields are updated.
     *
     * @return number of touched [Relation] rows
     */
    fun touchRelations(): Int {
        logger.fine("Touching relations to set parent_id")
        val batchOperation = BatchOperation(provider, BatchOperation.TASKS_OPERATIONS_PER_YIELD_POINT)
        provider.query(
            tasksSyncUri(true), null,
            "${Tasks.LIST_ID}=? AND ${Tasks.PARENT_ID} IS NULL AND ${Relation.MIMETYPE}=? AND ${Relation.RELATED_ID} IS NOT NULL",
            arrayOf(id.toString(), Relation.CONTENT_ITEM_TYPE),
            null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val values = cursor.toValues()
                val id = values.getAsLong(Relation.PROPERTY_ID)
                val propertyContentUri = ContentUris.withAppendedId(tasksPropertiesSyncUri(), id)
                batchOperation.enqueue(
                    BatchOperation.CpoBuilder
                        .newUpdate(propertyContentUri)
                        .withValue(Relation.RELATED_ID, values.getAsLong(Relation.RELATED_ID))
                )
            }
        }
        return batchOperation.commit()
    }


    /**
     * Queries tasks from this task list. Adds a WHERE clause that restricts the
     * query to [Tasks.LIST_ID] = [id].
     *
     * @param _where selection
     * @param _whereArgs arguments for selection
     *
     * @return events from this task list which match the selection
     */
    fun queryTasks(_where: String? = null, _whereArgs: Array<String>? = null): List<T> {
        val where = "(${_where ?: "1"}) AND ${Tasks.LIST_ID}=?"
        val whereArgs = (_whereArgs ?: arrayOf()) + id.toString()

        val tasks = LinkedList<T>()
        provider.query(
            tasksSyncUri(),
            null,
            where, whereArgs, null
        )?.use { cursor ->
            while (cursor.moveToNext())
                tasks += taskFactory.fromProvider(this, cursor.toValues())
        }
        return tasks
    }

    fun findById(id: Long) = queryTasks("${Tasks._ID}=?", arrayOf(id.toString())).firstOrNull()
        ?: throw FileNotFoundException()


    fun taskListSyncUri() =
        ContentUris.withAppendedId(TaskLists.getContentUri(providerName.authority), id).asSyncAdapter(account)

    fun tasksSyncUri(loadProperties: Boolean = false): Uri {
        val uri = Tasks.getContentUri(providerName.authority).asSyncAdapter(account)
        return if (loadProperties)
            uri.buildUpon()
                .appendQueryParameter(TaskContract.LOAD_PROPERTIES, "1")
                .build()
        else
            uri
    }

    fun tasksPropertiesSyncUri() = TaskContract.Properties.getContentUri(providerName.authority).asSyncAdapter(account)

}
