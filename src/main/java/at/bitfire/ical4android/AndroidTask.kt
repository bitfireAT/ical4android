/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import android.content.ContentProviderOperation
import android.content.ContentProviderOperation.Builder
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.RemoteException
import at.bitfire.ical4android.MiscUtils.CursorHelper.toValues
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Dur
import net.fortuna.ical4j.model.property.*
import org.dmfs.tasks.contract.TaskContract.*
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Property.Category
import java.io.FileNotFoundException
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.logging.Level

/**
 * Stores and retrieves VTODO iCalendar objects (represented as [Task]s) to/from the
 * OpenTasks provider.
 *
 * Extend this class to process specific fields of the task.
 *
 * The SEQUENCE field is stored in [Tasks.SYNC_VERSION], so don't use [Tasks.SYNC_VERSION]
 * for anything else.
 *
 */
abstract class AndroidTask(
        val taskList: AndroidTaskList<AndroidTask>
) {

    var id: Long? = null


    constructor(taskList: AndroidTaskList<AndroidTask>, values: ContentValues): this(taskList) {
        id = values.getAsLong(Tasks._ID)
    }

    constructor(taskList: AndroidTaskList<AndroidTask>, task: Task): this(taskList) {
        this.task = task
    }


    var task: Task? = null
        /**
         * This getter returns the full task data, either from [task] or, if [task] is null, by reading task
         * number [id] from the task provider
         * @throws IllegalArgumentException if task has not been saved yet
         * @throws FileNotFoundException if there's no task with [id] in the task provider
         * @throws RemoteException on task provider errors
         */
        get() {
            if (field != null)
                return field
            val id = requireNotNull(id)

            task = Task()
            val client = taskList.provider.client
            client.query(taskSyncURI(), null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val values = cursor.toValues()
                    Constants.log.log(Level.FINER, "Found task", values)
                    populateTask(values)

                    if (values.getAsInteger(Tasks.HAS_PROPERTIES) != 0)
                        // fetch properties
                        client.query(taskList.tasksPropertiesSyncUri(), null,
                                "${Properties.TASK_ID}=?", arrayOf(id.toString()),
                                null)?.use { propCursor ->
                            while (propCursor.moveToNext()) {
                                val propValues = propCursor.toValues()
                                Constants.log.log(Level.FINER, "Found property", propValues)
                                populateProperty(propValues)
                            }
                        }

                    return task
                }
            }
            throw FileNotFoundException("Couldn't find task #$id")
        }

    protected open fun populateTask(values: ContentValues) {
        val task = requireNotNull(task)

        MiscUtils.removeEmptyStrings(values)

        task.uid = values.getAsString(Tasks._UID)
        task.sequence = values.getAsInteger(Tasks.SYNC_VERSION)
        task.summary = values.getAsString(Tasks.TITLE)
        task.location = values.getAsString(Tasks.LOCATION)

        values.getAsString(Tasks.GEO)?.let { task.geoPosition = Geo(it) }

        task.description = values.getAsString(Tasks.DESCRIPTION)
        task.color = values.getAsInteger(Tasks.TASK_COLOR)
        task.url = values.getAsString(Tasks.URL)

        values.getAsString(Tasks.ORGANIZER)?.let {
            try {
                task.organizer = Organizer("mailto:$it")
            } catch(e: URISyntaxException) {
                Constants.log.log(Level.WARNING, "Invalid ORGANIZER email", e)
            }
        }

        values.getAsInteger(Tasks.PRIORITY)?.let { task.priority = it }

        task.classification = when (values.getAsInteger(Tasks.CLASSIFICATION)) {
            Tasks.CLASSIFICATION_PUBLIC ->       Clazz.PUBLIC
            Tasks.CLASSIFICATION_PRIVATE ->      Clazz.PRIVATE
            Tasks.CLASSIFICATION_CONFIDENTIAL -> Clazz.CONFIDENTIAL
            else ->                              null
        }

        values.getAsLong(Tasks.COMPLETED)?.let { task.completedAt = Completed(DateTime(it)) }
        values.getAsInteger(Tasks.PERCENT_COMPLETE)?.let { task.percentComplete = it }

        task.status = when (values.getAsInteger(Tasks.STATUS)) {
            Tasks.STATUS_IN_PROCESS -> Status.VTODO_IN_PROCESS
            Tasks.STATUS_COMPLETED ->  Status.VTODO_COMPLETED
            Tasks.STATUS_CANCELLED ->  Status.VTODO_CANCELLED
            else ->                    Status.VTODO_NEEDS_ACTION
        }

        var allDay = false
        values.getAsInteger(Tasks.IS_ALLDAY)?.let { allDay = it != 0 }

        val tzID = values.getAsString(Tasks.TZ)
        val tz = if (tzID != null) DateUtils.tzRegistry.getTimeZone(tzID) else null

        values.getAsLong(Tasks.CREATED)?.let { task.createdAt = it }
        values.getAsLong(Tasks.LAST_MODIFIED)?.let { task.lastModified = it }

        values.getAsLong(Tasks.DTSTART)?.let { dtStart ->
            val dt: Date
            if (allDay)
                dt = Date(dtStart)
            else {
                dt = DateTime(dtStart)
                if (tz != null)
                    dt.timeZone = tz
            }
            task.dtStart = DtStart(dt)
        }

        values.getAsLong(Tasks.DUE)?.let { due ->
            val dt: Date
            if (allDay)
                dt = Date(due)
            else {
                dt = DateTime(due)
                if (tz != null)
                    dt.timeZone = tz
            }
            task.due = Due(dt)
        }

        values.getAsString(Tasks.DURATION)?.let { task.duration = Duration(Dur(it)) }

        values.getAsString(Tasks.RDATE)?.let { task.rDates += DateUtils.androidStringToRecurrenceSet(it, RDate::class.java, allDay) }
        values.getAsString(Tasks.EXDATE)?.let { task.exDates += DateUtils.androidStringToRecurrenceSet(it, ExDate::class.java, allDay) }

        values.getAsString(Tasks.RRULE)?.let { task.rRule = RRule(it) }
    }

    protected open fun populateProperty(values: ContentValues) {
        val task = requireNotNull(task)
        val type = values.getAsString(Properties.MIMETYPE)
        when (type) {
            Category.CONTENT_ITEM_TYPE ->
                task.categories += values.getAsString(Category.CATEGORY_NAME)
            else ->
                Constants.log.warning("Found unknown property of type $type")
        }
    }


    fun add(): Uri {
        val batch = BatchOperation(taskList.provider.client)
        val builder = ContentProviderOperation.newInsert(taskList.tasksSyncUri())
        buildTask(builder, false)
        batch.enqueue(BatchOperation.Operation(builder))
        batch.commit()

        // TODO use backref mechanism so that only one commit is required for the whole task
        val result = batch.getResult(0) ?: throw CalendarStorageException("Empty result from provider when adding a task")
        id = ContentUris.parseId(result.uri)

        insertProperties(batch)
        batch.commit()

        return result.uri
    }

    fun update(task: Task): Uri {
        this.task = task

        val batch = BatchOperation(taskList.provider.client)
        val uri = taskSyncURI()
        val builder = ContentProviderOperation.newUpdate(uri)
        buildTask(builder, true)
        batch.enqueue(BatchOperation.Operation(builder))

        val deleteProperties = ContentProviderOperation.newDelete(taskList.tasksPropertiesSyncUri())
                .withSelection("${Properties.TASK_ID}=?", arrayOf(id.toString()))
        batch.enqueue(BatchOperation.Operation(deleteProperties))
        insertProperties(batch)

        batch.commit()
        return uri
    }

    private fun insertProperties(batch: BatchOperation) {
        val task = requireNotNull(task)

        // insert categories
        for (category in task.categories) {
            val builder = ContentProviderOperation.newInsert(taskList.tasksPropertiesSyncUri())
            builder .withValue(Category.TASK_ID, id)
                    .withValue(Category.MIMETYPE, Category.CONTENT_ITEM_TYPE)
                    .withValue(Category.CATEGORY_NAME, category)
            Constants.log.log(Level.FINE, "Inserting category", builder.build())
            batch.enqueue(BatchOperation.Operation(builder))
        }
    }

    fun delete(): Int {
        try {
            return taskList.provider.client.delete(taskSyncURI(), null, null)
        } catch(e: RemoteException) {
            throw CalendarStorageException("Couldn't delete event", e)
        }
    }

    protected open fun buildTask(builder: Builder, update: Boolean) {
        if (!update)
            builder .withValue(Tasks.LIST_ID, taskList.id)

        val task = requireNotNull(task)
        builder
                .withValue(Tasks._UID, task.uid)
                .withValue(Tasks._DIRTY, 0)
                .withValue(Tasks.SYNC_VERSION, task.sequence)
                .withValue(Tasks.TITLE, task.summary)
                .withValue(Tasks.LOCATION, task.location)

        builder .withValue(Tasks.GEO, task.geoPosition?.value)

        builder .withValue(Tasks.DESCRIPTION, task.description)
                .withValue(Tasks.TASK_COLOR, task.color)
                .withValue(Tasks.URL, task.url)

        var organizer: String? = null
        task.organizer?.let {
            try {
                val uri = URI(it.value)
                if (uri.scheme.equals("mailto", true))
                    organizer = uri.schemeSpecificPart
                else
                    Constants.log.log(Level.WARNING, "Found non-mailto ORGANIZER URI, ignoring", uri)
            } catch (e: URISyntaxException) {
                Constants.log.log(Level.WARNING, "Invalid ORGANIZER URI, ignoring", e)
            }
        }
        builder .withValue(Tasks.ORGANIZER, organizer)

        builder .withValue(Tasks.PRIORITY, task.priority)
                .withValue(Tasks.CLASSIFICATION, when (task.classification) {
                    Clazz.PUBLIC -> Tasks.CLASSIFICATION_PUBLIC
                    Clazz.CONFIDENTIAL -> Tasks.CLASSIFICATION_CONFIDENTIAL
                    null -> Tasks.CLASSIFICATION_DEFAULT
                    else -> Tasks.CLASSIFICATION_PRIVATE    // all unknown classifications MUST be treated as PRIVATE
                })

        // COMPLETED must always be a DATE-TIME
        builder .withValue(Tasks.COMPLETED, task.completedAt?.date?.time)
                .withValue(Tasks.COMPLETED_IS_ALLDAY, 0)
                .withValue(Tasks.PERCENT_COMPLETE, task.percentComplete)

        builder .withValue(Tasks.STATUS, when (task.status) {
            Status.VTODO_IN_PROCESS -> Tasks.STATUS_IN_PROCESS
            Status.VTODO_COMPLETED  -> Tasks.STATUS_COMPLETED
            Status.VTODO_CANCELLED  -> Tasks.STATUS_CANCELLED
            else                    -> Tasks.STATUS_DEFAULT    // == Tasks.STATUS_NEEDS_ACTION
        })

        val allDay = task.isAllDay()
        if (allDay)
            builder .withValue(Tasks.IS_ALLDAY, 1)
                    .withValue(Tasks.TZ, null)
        else {
            MiscUtils.androidifyTimeZone(task.dtStart)
            MiscUtils.androidifyTimeZone(task.due)
            builder .withValue(Tasks.IS_ALLDAY, 0)
                    .withValue(Tasks.TZ, getTimeZone().id)
        }

        builder .withValue(Tasks.CREATED, task.createdAt)
                .withValue(Tasks.LAST_MODIFIED, task.lastModified)

        builder .withValue(Tasks.DTSTART, task.dtStart?.date?.time)
                .withValue(Tasks.DUE, task.due?.date?.time)
                .withValue(Tasks.DURATION, task.duration?.value)

        builder .withValue(Tasks.RDATE, if (task.rDates.isEmpty()) null else DateUtils.recurrenceSetsToAndroidString(task.rDates, allDay))
                .withValue(Tasks.RRULE, task.rRule?.value)

        builder .withValue(Tasks.EXDATE, if (task.exDates.isEmpty()) null else DateUtils.recurrenceSetsToAndroidString(task.exDates, allDay))
        Constants.log.log(Level.FINE, "Built task object", builder.build())
    }


    fun getTimeZone(): TimeZone {
        val task = requireNotNull(task)

        var tz: java.util.TimeZone? = null
        task.dtStart?.timeZone?.let { tz = it }

        tz = tz ?: task.due?.timeZone

        return tz ?: TimeZone.getDefault()
    }

    protected fun taskSyncURI(): Uri {
        val id = requireNotNull(id)
        val builder = taskList.tasksSyncUri().buildUpon()
        return ContentUris.appendId(builder, id)
                .appendQueryParameter(LOAD_PROPERTIES, "1")
                .build()
    }


    override fun toString() = MiscUtils.reflectionToString(this)

}
