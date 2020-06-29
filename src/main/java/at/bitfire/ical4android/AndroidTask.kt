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
import androidx.annotation.CallSuper
import at.bitfire.ical4android.MiscUtils.CursorHelper.toValues
import at.bitfire.ical4android.util.AndroidTimeUtils
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.*
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Property.*
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.io.FileNotFoundException
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.TimeZone
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

    companion object {
        const val UNKNOWN_PROPERTY_DATA = Properties.DATA0
    }

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

            try {
                val client = taskList.provider.client
                client.query(taskSyncURI(true), null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        // create new Task which will be populated
                        val newTask = Task()
                        field = newTask

                        val values = cursor.toValues(true)
                        Ical4Android.log.log(Level.FINER, "Found task", values)
                        populateTask(values)

                        if (values.containsKey(Properties.PROPERTY_ID)) {
                            // process the first property, which is combined with the task row
                            populateProperty(values)

                            while (cursor.moveToNext()) {
                                // process the other properties
                                populateProperty(cursor.toValues(true))
                            }
                        }

                        // Special case: parent_id set, but no matching parent Relation row (like given by aCalendar+)
                        val relatedToList = newTask.relatedTo
                        values.getAsLong(Tasks.PARENT_ID)?.let { parentId ->
                            val hasParentRelation = relatedToList.any { relatedTo ->
                                val relatedType = relatedTo.getParameter<RelType>(Parameter.RELTYPE)
                                relatedType == RelType.PARENT || relatedType == null /* RelType.PARENT is the default value */
                            }
                            if (!hasParentRelation) {
                                // get UID of parent task
                                val parentContentUri = ContentUris.withAppendedId(taskList.tasksSyncUri(), parentId)
                                client.query(parentContentUri, arrayOf(Tasks._UID), null, null, null)?.use { cursor ->
                                    if (cursor.moveToNext()) {
                                        // add RelatedTo for parent task
                                        relatedToList += RelatedTo(cursor.getString(0))
                                    }
                                }
                            }
                        }

                        field = newTask
                        return newTask
                    }
                }
            } catch (e: Exception) {
                /* Populating event has been interrupted by an exception, so we reset the event to
                avoid an inconsistent state. This also ensures that the exception will be thrown
                again on the next get() call. */
                field = null
                throw e
            }
            throw FileNotFoundException("Couldn't find task #$id")
        }

    @CallSuper
    protected open fun populateTask(values: ContentValues) {
        val task = requireNotNull(task)

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
                Ical4Android.log.log(Level.WARNING, "Invalid ORGANIZER email", e)
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

        values.getAsString(Tasks.DURATION)?.let { duration ->
            val fixedDuration = AndroidTimeUtils.parseDuration(duration)
            task.duration = Duration(fixedDuration)
        }

        values.getAsString(Tasks.RDATE)?.let {
            task.rDates += AndroidTimeUtils.androidStringToRecurrenceSet(it, allDay) { RDate(it) }
        }
        values.getAsString(Tasks.EXDATE)?.let {
            task.exDates += AndroidTimeUtils.androidStringToRecurrenceSet(it, allDay) { ExDate(it) }
        }

        values.getAsString(Tasks.RRULE)?.let { task.rRule = RRule(it) }
    }

    protected open fun populateProperty(row: ContentValues) {
        Ical4Android.log.log(Level.FINER, "Found property", row)

        val task = requireNotNull(task)
        when (val type = row.getAsString(Properties.MIMETYPE)) {
            Alarm.CONTENT_ITEM_TYPE ->
                populateAlarm(row)
            Category.CONTENT_ITEM_TYPE ->
                task.categories += row.getAsString(Category.CATEGORY_NAME)
            Relation.CONTENT_ITEM_TYPE ->
                populateRelatedTo(row)
            UnknownProperty.CONTENT_ITEM_TYPE ->
                task.unknownProperties += UnknownProperty.fromJsonString(row.getAsString(UNKNOWN_PROPERTY_DATA))
            else ->
                Ical4Android.log.warning("Found unknown property of type $type")
        }
    }

    protected open fun populateAlarm(row: ContentValues) {
        val task = requireNotNull(task)
        val props = PropertyList<Property>()

        val trigger = Trigger(java.time.Duration.ofMinutes(-row.getAsLong(Alarm.MINUTES_BEFORE)))
        when (row.getAsInteger(Alarm.REFERENCE)) {
            Alarm.ALARM_REFERENCE_START_DATE ->
                trigger.parameters.add(Related.START)
            Alarm.ALARM_REFERENCE_DUE_DATE ->
                trigger.parameters.add(Related.END)
        }
        props += trigger

        props += when (row.getAsInteger(Alarm.ALARM_TYPE)) {
            Alarm.ALARM_TYPE_EMAIL ->
                Action.EMAIL
            Alarm.ALARM_TYPE_SOUND ->
                Action.AUDIO
            else ->
                // show alarm by default
                Action.DISPLAY
        }

        props += Description(row.getAsString(Alarm.MESSAGE) ?: task.summary)

        task.alarms += VAlarm(props)
    }

    protected open fun populateRelatedTo(row: ContentValues) {
        val uid = row.getAsString(Relation.RELATED_UID)
        if (uid == null) {
            Ical4Android.log.warning("Task relation doesn't refer to same task list; can't be synchronized")
            return
        }

        val relatedTo = RelatedTo(uid)

        // add relation type as reltypeparam
        relatedTo.parameters.add(when (row.getAsInteger(Relation.RELATED_TYPE)) {
            Relation.RELTYPE_CHILD ->
                RelType.CHILD
            Relation.RELTYPE_SIBLING ->
                RelType.SIBLING
            else /* Relation.RELTYPE_PARENT, default value */ ->
                RelType.PARENT
        })

        requireNotNull(task).relatedTo.add(relatedTo)
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

    protected open fun insertProperties(batch: BatchOperation) {
        insertAlarms(batch)
        insertCategories(batch)
        insertRelatedTo(batch)
        insertUnknownProperties(batch)
    }

    protected open fun insertAlarms(batch: BatchOperation) {
        val task = requireNotNull(task)
        for (alarm in task.alarms) {
            val (alarmRef, minutes) = ICalendar.vAlarmToMin(alarm, task, true) ?: continue
            val ref = when (alarmRef) {
                Related.END ->
                    Alarm.ALARM_REFERENCE_DUE_DATE
                else /* Related.START is the default value */ ->
                    Alarm.ALARM_REFERENCE_START_DATE
            }

            val alarmType = when (alarm.action?.value?.toUpperCase(Locale.US)) {
                Action.AUDIO.value ->
                    Alarm.ALARM_TYPE_SOUND
                Action.DISPLAY.value ->
                    Alarm.ALARM_TYPE_MESSAGE
                Action.EMAIL.value ->
                    Alarm.ALARM_TYPE_EMAIL
                else ->
                    Alarm.ALARM_TYPE_NOTHING
            }

            val builder = ContentProviderOperation.newInsert(taskList.tasksPropertiesSyncUri())
                    .withValue(Alarm.TASK_ID, id)
                    .withValue(Alarm.MIMETYPE, Alarm.CONTENT_ITEM_TYPE)
                    .withValue(Alarm.MINUTES_BEFORE, minutes)
                    .withValue(Alarm.REFERENCE, ref)
                    .withValue(Alarm.MESSAGE, alarm.description?.value ?: alarm.summary)
                    .withValue(Alarm.ALARM_TYPE, alarmType)

            Ical4Android.log.log(Level.FINE, "Inserting alarm", builder.build())
            batch.enqueue(BatchOperation.Operation(builder))
        }
    }

    protected open fun insertCategories(batch: BatchOperation) {
        for (category in requireNotNull(task).categories) {
            val builder = ContentProviderOperation.newInsert(taskList.tasksPropertiesSyncUri())
                    .withValue(Category.TASK_ID, id)
                    .withValue(Category.MIMETYPE, Category.CONTENT_ITEM_TYPE)
                    .withValue(Category.CATEGORY_NAME, category)
            Ical4Android.log.log(Level.FINE, "Inserting category", builder.build())
            batch.enqueue(BatchOperation.Operation(builder))
        }
    }

    protected open fun insertRelatedTo(batch: BatchOperation) {
        for (relatedTo in requireNotNull(task).relatedTo) {
            val relType = when ((relatedTo.getParameter(Parameter.RELTYPE) as RelType?)) {
                RelType.CHILD ->
                    Relation.RELTYPE_CHILD
                RelType.SIBLING ->
                    Relation.RELTYPE_SIBLING
                else /* RelType.PARENT, default value */ ->
                    Relation.RELTYPE_PARENT
            }
            val builder = ContentProviderOperation.newInsert(taskList.tasksPropertiesSyncUri())
                    .withValue(Relation.TASK_ID, id)
                    .withValue(Relation.MIMETYPE, Relation.CONTENT_ITEM_TYPE)
                    .withValue(Relation.RELATED_UID, relatedTo.value)
                    .withValue(Relation.RELATED_TYPE, relType)
            Ical4Android.log.log(Level.FINE, "Inserting relation", builder.build())
            batch.enqueue(BatchOperation.Operation(builder))
        }
    }

    protected open fun insertUnknownProperties(batch: BatchOperation) {
        for (property in requireNotNull(task).unknownProperties) {
            if (property.value.length > UnknownProperty.MAX_UNKNOWN_PROPERTY_SIZE) {
                Ical4Android.log.warning("Ignoring unknown property with ${property.value.length} octets (too long)")
                return
            }

            val builder = ContentProviderOperation.newInsert(taskList.tasksPropertiesSyncUri())
                    .withValue(Properties.TASK_ID, id)
                    .withValue(Properties.MIMETYPE, UnknownProperty.CONTENT_ITEM_TYPE)
                    .withValue(UNKNOWN_PROPERTY_DATA, UnknownProperty.toJsonString(property))
            Ical4Android.log.log(Level.FINE, "Inserting unknown property", builder.build())
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

    @CallSuper
    protected open fun buildTask(builder: Builder, update: Boolean) {
        if (!update)
            builder .withValue(Tasks.LIST_ID, taskList.id)

        val task = requireNotNull(task)
        builder .withValue(Tasks._UID, task.uid)
                .withValue(Tasks._DIRTY, 0)
                .withValue(Tasks.SYNC_VERSION, task.sequence)
                .withValue(Tasks.TITLE, task.summary)
                .withValue(Tasks.LOCATION, task.location)

                .withValue(Tasks.GEO, task.geoPosition?.value)

                .withValue(Tasks.DESCRIPTION, task.description)
                .withValue(Tasks.TASK_COLOR, task.color)
                .withValue(Tasks.URL, task.url)

                // parent_id will be re-calculated when the relation row is inserted (if there is any)
                .withValue(Tasks.PARENT_ID, null)

        var organizer: String? = null
        task.organizer?.let {
            try {
                val uri = URI(it.value)
                if (uri.scheme.equals("mailto", true))
                    organizer = uri.schemeSpecificPart
                else
                    Ical4Android.log.log(Level.WARNING, "Found non-mailto ORGANIZER URI, ignoring", uri)
            } catch (e: URISyntaxException) {
                Ical4Android.log.log(Level.WARNING, "Invalid ORGANIZER URI, ignoring", e)
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
            AndroidTimeUtils.androidifyTimeZone(task.dtStart)
            AndroidTimeUtils.androidifyTimeZone(task.due)
            builder .withValue(Tasks.IS_ALLDAY, 0)
                    .withValue(Tasks.TZ, getTimeZone().id)
        }

        builder .withValue(Tasks.CREATED, task.createdAt)
                .withValue(Tasks.LAST_MODIFIED, task.lastModified)

                .withValue(Tasks.DTSTART, task.dtStart?.date?.time)
                .withValue(Tasks.DUE, task.due?.date?.time)
                .withValue(Tasks.DURATION, task.duration?.value)

                .withValue(Tasks.RDATE, if (task.rDates.isEmpty()) null else AndroidTimeUtils.recurrenceSetsToAndroidString(task.rDates, allDay))
                .withValue(Tasks.RRULE, task.rRule?.value)

                .withValue(Tasks.EXDATE, if (task.exDates.isEmpty()) null else AndroidTimeUtils.recurrenceSetsToAndroidString(task.exDates, allDay))
        Ical4Android.log.log(Level.FINE, "Built task object", builder.build())
    }


    fun getTimeZone(): TimeZone {
        val task = requireNotNull(task)

        var tz: TimeZone? = null
        task.dtStart?.timeZone?.let { tz = it }

        tz = tz ?: task.due?.timeZone

        return tz ?: TimeZone.getDefault()
    }

    protected fun taskSyncURI(loadProperties: Boolean = false): Uri {
        val id = requireNotNull(id)
        return ContentUris.withAppendedId(taskList.tasksSyncUri(loadProperties), id)
    }


    override fun toString() = MiscUtils.reflectionToString(this)

}
