/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU General Public License for more details.
 */

package at.bitfire.ical4android;

import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.util.Log;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.property.Action;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Completed;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.Geo;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Status;

import org.apache.commons.lang3.StringUtils;
import org.dmfs.provider.tasks.TaskContract;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;

import lombok.Cleanup;

public abstract class AndroidTask {
    private static final String TAG = "ical4android.Task";

    final protected AndroidTaskList taskList;

    protected Long id;
    protected Task task;


    protected AndroidTask(AndroidTaskList taskList, long id) {
        this.taskList = taskList;
        this.id = id;
    }

    protected AndroidTask(AndroidTaskList taskList, Task task) {
        this.taskList = taskList;
        this.task = task;
    }


    public Task getTask() throws FileNotFoundException, CalendarStorageException {
        if (task != null)
            return task;

        try {
            task = new Task();
            populateTask();
            return task;
        } catch(RemoteException e) {
            throw new CalendarStorageException("Couldn't read locally stored event", e);
        } catch(ParseException e) {
            throw new CalendarStorageException("Couldn't parse locally stored event", e);
        }
    }

    protected void populateTask() throws FileNotFoundException, RemoteException, ParseException {
        @Cleanup final Cursor cursor = taskList.provider.client.query(tasksURI(),
                new String[] {
                        /*  0 */ TaskContract.Tasks._UID, TaskContract.Tasks.TITLE, TaskContract.Tasks.LOCATION, TaskContract.Tasks.GEO,
                        /*  4 */ TaskContract.Tasks.DESCRIPTION, TaskContract.Tasks.URL, TaskContract.Tasks.ORGANIZER,
                        /*  7 */ TaskContract.Tasks.PRIORITY, TaskContract.Tasks.CLASSIFICATION,
                        /*  9 */ TaskContract.Tasks.COMPLETED, TaskContract.Tasks.PERCENT_COMPLETE,
                        /* 11 */ TaskContract.Tasks.STATUS, TaskContract.Tasks.IS_ALLDAY, TaskContract.Tasks.TZ,
                        /* 14 */ TaskContract.Tasks.CREATED, TaskContract.Tasks.LAST_MODIFIED,
                        /* 16 */ TaskContract.Tasks.DTSTART, TaskContract.Tasks.DUE, TaskContract.Tasks.DURATION,
                        /* 19 */ TaskContract.Tasks.RDATE, TaskContract.Tasks.EXDATE, TaskContract.Tasks.RRULE
                }, TaskContract.Tasks._ID + "=?", new String[] { String.valueOf(id) }, null);

        if (cursor != null && cursor.moveToFirst()) {
            task.uid = cursor.getString(0);

            if (!StringUtils.isEmpty(cursor.getString(1)))
                task.summary = cursor.getString(1);

            if (!StringUtils.isEmpty(cursor.getString(2)))
                task.location = cursor.getString(2);

            if (!StringUtils.isEmpty(cursor.getString(3)))
                task.geoPosition = new Geo(cursor.getString(3));

            if (!StringUtils.isEmpty(cursor.getString(4)))
                task.description = cursor.getString(4);

            if (!StringUtils.isEmpty(cursor.getString(5)))
                task.url = cursor.getString(5);

            if (!StringUtils.isEmpty(cursor.getString(6)))
                try {
                    task.organizer = new Organizer("mailto:" + cursor.getString(6));
                } catch (URISyntaxException e) {
                    Log.w(TAG, "Invalid ORGANIZER email", e);
                }

            if (!cursor.isNull(7))
                task.priority = cursor.getInt(7);

            if (!cursor.isNull(8))
                switch (cursor.getInt(8)) {
                    case TaskContract.Tasks.CLASSIFICATION_PUBLIC:
                        task.classification = Clazz.PUBLIC;
                        break;
                    case TaskContract.Tasks.CLASSIFICATION_CONFIDENTIAL:
                        task.classification = Clazz.CONFIDENTIAL;
                        break;
                    default:
                        task.classification = Clazz.PRIVATE;
                }

            if (!cursor.isNull(9))
                // COMPLETED must always be a DATE-TIME
                task.completedAt = new Completed(new DateTime(cursor.getLong(9)));

            if (!cursor.isNull(10))
                task.percentComplete = cursor.getInt(10);

            if (!cursor.isNull(11))
                switch (cursor.getInt(11)) {
                    case TaskContract.Tasks.STATUS_IN_PROCESS:
                        task.status = Status.VTODO_IN_PROCESS;
                        break;
                    case TaskContract.Tasks.STATUS_COMPLETED:
                        task.status = Status.VTODO_COMPLETED;
                        break;
                    case TaskContract.Tasks.STATUS_CANCELLED:
                        task.status = Status.VTODO_CANCELLED;
                        break;
                    default:
                        task.status = Status.VTODO_NEEDS_ACTION;
                }

            boolean allDay = false;
            if (!cursor.isNull(12))
                allDay = cursor.getInt(12) != 0;

            TimeZone tz = null;
            if (!cursor.isNull(13))
                tz = DateUtils.tzRegistry.getTimeZone(cursor.getString(13));

            if (!cursor.isNull(14))
                task.createdAt = cursor.getLong(14);
            if (!cursor.isNull(15))
                task.lastModified = cursor.getLong(15);

            if (!cursor.isNull(16)) {
                long ts = cursor.getLong(16);

                Date dt;
                if (allDay)
                    dt = new Date(ts);
                else {
                    dt = new DateTime(ts);
                    if (tz != null)
                        ((DateTime)dt).setTimeZone(tz);
                }
                task.dtStart = new DtStart(dt);
            }

            if (!cursor.isNull(17)) {
                long ts = cursor.getLong(17);

                Date dt;
                if (allDay)
                    dt = new Date(ts);
                else {
                    dt = new DateTime(ts);
                    if (tz != null)
                        ((DateTime)dt).setTimeZone(tz);
                }
                task.due = new Due(dt);
            }

            if (!cursor.isNull(18))
                task.duration = new Duration(new Dur(cursor.getString(18)));

            if (!cursor.isNull(19))
                task.getRDates().add((RDate)DateUtils.androidStringToRecurrenceSet(cursor.getString(19), RDate.class, allDay));

            if (!cursor.isNull(20))
                task.getExDates().add((ExDate)DateUtils.androidStringToRecurrenceSet(cursor.getString(20), ExDate.class, allDay));

            if (!cursor.isNull(21))
                task.rRule = new RRule(cursor.getString(21));
        }
    }


    public Uri add() throws CalendarStorageException {
        BatchOperation batch = new BatchOperation(taskList.provider.client);
        Builder builder = ContentProviderOperation.newInsert(taskList.syncAdapterURI(taskList.provider.tasksUri()));
        buildTask(builder, false);
        batch.enqueue(builder.build());
        batch.commit();
        return batch.getResult(0).uri;
    }

    public void update(Task task) throws CalendarStorageException {
        this.task = task;

        BatchOperation batch = new BatchOperation(taskList.provider.client);
        Builder builder = ContentProviderOperation.newUpdate(taskList.syncAdapterURI(taskList.provider.tasksUri()));
        buildTask(builder, true);
        batch.enqueue(builder.build());
        batch.commit();
    }

    public int delete() throws CalendarStorageException {
        try {
            return taskList.provider.client.delete(taskList.syncAdapterURI(taskList.provider.tasksUri()), null, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't delete event", e);
        }
    }

    protected void buildTask(Builder builder, boolean update) {
        if (!update)
            builder .withValue(TaskContract.Tasks.LIST_ID, taskList.getId())
                    .withValue(TaskContract.Tasks._DIRTY, 0);

        builder
                .withValue(TaskContract.Tasks._UID, task.uid)
                .withValue(TaskContract.Tasks.TITLE, task.summary)
                .withValue(TaskContract.Tasks.LOCATION, task.location);

        if (task.geoPosition != null)
                builder.withValue(TaskContract.Tasks.GEO, task.geoPosition.getValue());

        builder .withValue(TaskContract.Tasks.DESCRIPTION, task.description)
                .withValue(TaskContract.Tasks.URL, task.url);

        if (task.organizer != null)
            try {
                URI organizer = new URI(task.organizer.getValue());
                if ("mailto".equals(organizer.getScheme()))
                    builder.withValue(TaskContract.Tasks.ORGANIZER, organizer);
                else
                    Log.w(TAG, "Found non-mailto ORGANIZER URI, ignoring");
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

        builder .withValue(TaskContract.Tasks.PRIORITY, task.priority);

        if (task.classification != null) {
            int classCode = TaskContract.Tasks.CLASSIFICATION_PRIVATE;
            if (task.classification == Clazz.PUBLIC)
                classCode = TaskContract.Tasks.CLASSIFICATION_PUBLIC;
            else if (task.classification == Clazz.CONFIDENTIAL)
                classCode = TaskContract.Tasks.CLASSIFICATION_CONFIDENTIAL;
            builder.withValue(TaskContract.Tasks.CLASSIFICATION, classCode);
        }

        if (task.completedAt != null) {
            // COMPLETED must always be a DATE-TIME
            builder .withValue(TaskContract.Tasks.COMPLETED, task.completedAt.getDateTime().getTime())
                    .withValue(TaskContract.Tasks.COMPLETED_IS_ALLDAY, 0);
        }
        builder.withValue(TaskContract.Tasks.PERCENT_COMPLETE, task.percentComplete);

        int statusCode = TaskContract.Tasks.STATUS_DEFAULT;
        if (task.status != null) {
            if (task.status == Status.VTODO_NEEDS_ACTION)
                statusCode = TaskContract.Tasks.STATUS_NEEDS_ACTION;
            else if (task.status == Status.VTODO_IN_PROCESS)
                statusCode = TaskContract.Tasks.STATUS_IN_PROCESS;
            else if (task.status == Status.VTODO_COMPLETED)
                statusCode = TaskContract.Tasks.STATUS_COMPLETED;
            else if (task.status == Status.VTODO_CANCELLED)
                statusCode = TaskContract.Tasks.STATUS_CANCELLED;
        }
        builder.withValue(TaskContract.Tasks.STATUS, statusCode);

        final boolean allDay = task.isAllDay();
        if (allDay)
            builder.withValue(TaskContract.Tasks.IS_ALLDAY, 1);
        else {
            builder.withValue(TaskContract.Tasks.IS_ALLDAY, 0);

            java.util.TimeZone tz = task.getTimeZone();
            builder.withValue(TaskContract.Tasks.TZ, tz.getID());
        }

        if (task.createdAt != null)
            builder.withValue(TaskContract.Tasks.CREATED, task.createdAt);
        if (task.lastModified != null)
            builder.withValue(TaskContract.Tasks.LAST_MODIFIED, task.lastModified);

        if (task.dtStart != null)
            builder.withValue(TaskContract.Tasks.DTSTART, task.dtStart.getDate().getTime());
        if (task.due != null)
            builder.withValue(TaskContract.Tasks.DUE, task.due.getDate().getTime());
        if (task.duration != null)
            builder.withValue(TaskContract.Tasks.DURATION, task.duration.getValue());

        if (!task.getRDates().isEmpty())
            try {
                builder.withValue(TaskContract.Tasks.RDATE, DateUtils.recurrenceSetsToAndroidString(task.getRDates(), allDay));
            } catch (ParseException e) {
                Log.e(TAG, "Couldn't parse RDate(s)", e);
            }
        if (!task.getExDates().isEmpty())
            try {
                builder.withValue(TaskContract.Tasks.EXDATE, DateUtils.recurrenceSetsToAndroidString(task.getExDates(), allDay));
            } catch (ParseException e) {
                Log.e(TAG, "Couldn't parse ExDate(s)", e);
            }
        if (task.rRule != null)
            builder.withValue(TaskContract.Tasks.EXDATE, task.rRule.getValue());
    }


    protected Uri tasksURI() {
        return taskList.syncAdapterURI(taskList.provider.tasksUri());
    }

    protected Uri taskURI() {
        if (id == null)
            throw new IllegalStateException("Task doesn't have an ID yet");
        return taskList.syncAdapterURI(ContentUris.withAppendedId(taskList.provider.tasksUri(), id));
    }

}
