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
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.TimeZone;
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
import org.dmfs.provider.tasks.TaskContract.Tasks;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;

import lombok.Cleanup;
import lombok.Getter;

public abstract class AndroidTask {
    private static final String TAG = "ical4android.Task";

    final protected AndroidTaskList taskList;

    @Getter
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
            @Cleanup final Cursor cursor = taskList.provider.client.query(taskSyncURI(), null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                ContentValues values = new ContentValues(cursor.getColumnCount());
                DatabaseUtils.cursorRowToContentValues(cursor, values);
                populateTask(values);
            } else
                throw new FileNotFoundException("Couldn't load details of task #" + id);
            return task;
        } catch(RemoteException e) {
            throw new CalendarStorageException("Couldn't read locally stored event", e);
        } catch(ParseException e) {
            throw new CalendarStorageException("Couldn't parse locally stored event", e);
        }
    }

    protected void populateTask(ContentValues values) throws FileNotFoundException, RemoteException, ParseException {
        task.uid = values.getAsString(Tasks._UID);
        task.summary = values.getAsString(Tasks.TITLE);
        task.location = values.getAsString(Tasks.LOCATION);

        if (values.containsKey(Tasks.GEO)) {
            String geo = values.getAsString(Tasks.GEO);
            if (geo != null)
                task.geoPosition = new Geo(geo);
        };

        task.description = StringUtils.stripToNull(values.getAsString(Tasks.DESCRIPTION));
        task.url = StringUtils.stripToNull(values.getAsString(Tasks.URL));

        String organizer = values.getAsString(Tasks.ORGANIZER);
        if (!TextUtils.isEmpty(organizer))
            try {
                task.organizer = new Organizer("mailto:" + values.getAsString(Tasks.ORGANIZER));
            } catch (URISyntaxException e) {
                Log.w(TAG, "Invalid ORGANIZER email", e);
            }

        Integer priority = values.getAsInteger(Tasks.PRIORITY);
        if (priority != null)
            task.priority = priority;

        Integer classification = values.getAsInteger(Tasks.CLASSIFICATION);
        if (classification != null)
            switch (classification) {
                case Tasks.CLASSIFICATION_PUBLIC:
                    task.classification = Clazz.PUBLIC;
                    break;
                case Tasks.CLASSIFICATION_CONFIDENTIAL:
                    task.classification = Clazz.CONFIDENTIAL;
                    break;
                default:
                    task.classification = Clazz.PRIVATE;
            }

        Long completed = values.getAsLong(Tasks.COMPLETED);
        if (completed != null)
            // COMPLETED must always be a DATE-TIME
            task.completedAt = new Completed(new DateTime(completed));

        Integer percentComplete = values.getAsInteger(Tasks.PERCENT_COMPLETE);
        if (percentComplete != null)
            task.percentComplete = percentComplete;

        Integer status = values.getAsInteger(Tasks.STATUS);
        if (status != null)
            switch (status) {
                case Tasks.STATUS_IN_PROCESS:
                    task.status = Status.VTODO_IN_PROCESS;
                    break;
                case Tasks.STATUS_COMPLETED:
                    task.status = Status.VTODO_COMPLETED;
                    break;
                case Tasks.STATUS_CANCELLED:
                    task.status = Status.VTODO_CANCELLED;
                    break;
                default:
                    task.status = Status.VTODO_NEEDS_ACTION;
            }

        boolean allDay = false;
        if (values.getAsInteger(Tasks.IS_ALLDAY) != null)
            allDay = values.getAsInteger(Tasks.IS_ALLDAY) != 0;

        String tzID = values.getAsString(Tasks.TZ);
        TimeZone tz = (tzID != null) ? DateUtils.tzRegistry.getTimeZone(tzID) : null;

        Long createdAt = values.getAsLong(Tasks.CREATED);
        if (createdAt != null)
            task.createdAt = createdAt;

        Long lastModified = values.getAsLong(Tasks.LAST_MODIFIED);
        if (lastModified != null)
            task.lastModified = lastModified;

        Long dtStart = values.getAsLong(Tasks.DTSTART);
        if (dtStart != null) {
            long ts = dtStart;

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

        Long due = values.getAsLong(Tasks.DUE);
        if (due != null) {
            long ts = due;

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

        String duration = values.getAsString(Tasks.DURATION);
        if (duration != null)
            task.duration = new Duration(new Dur(duration));

        String rDate = values.getAsString(Tasks.RDATE);
        if (rDate != null)
            task.getRDates().add((RDate)DateUtils.androidStringToRecurrenceSet(rDate, RDate.class, allDay));

        String exDate = values.getAsString(Tasks.EXDATE);
        if (exDate != null)
            task.getExDates().add((ExDate)DateUtils.androidStringToRecurrenceSet(exDate, ExDate.class, allDay));

        String rRule = values.getAsString(Tasks.RRULE);
        if (rRule != null)
            task.rRule = new RRule(rRule);
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
        Builder builder = ContentProviderOperation.newUpdate(taskSyncURI());
        buildTask(builder, true);
        batch.enqueue(builder.build());
        batch.commit();
    }

    public int delete() throws CalendarStorageException {
        try {
            return taskList.provider.client.delete(taskSyncURI(), null, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't delete event", e);
        }
    }

    protected void buildTask(Builder builder, boolean update) {
        if (!update)
            builder .withValue(Tasks.LIST_ID, taskList.getId())
                    .withValue(Tasks._DIRTY, 0);

        builder
                //.withValue(Tasks._UID, task.uid)          // not available in F-Droid OpenTasks version yet (15 Oct 2015)
                .withValue(Tasks.TITLE, task.summary)
                .withValue(Tasks.LOCATION, task.location);

        if (task.geoPosition != null)
                builder.withValue(Tasks.GEO, task.geoPosition.getValue());

        builder .withValue(Tasks.DESCRIPTION, task.description)
                .withValue(Tasks.URL, task.url);

        if (task.organizer != null)
            try {
                URI organizer = new URI(task.organizer.getValue());
                if ("mailto".equals(organizer.getScheme()))
                    builder.withValue(Tasks.ORGANIZER, organizer.getSchemeSpecificPart());
                else
                    Log.w(TAG, "Found non-mailto ORGANIZER URI, ignoring");
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

        builder.withValue(Tasks.PRIORITY, task.priority);

        if (task.classification != null) {
            int classCode = Tasks.CLASSIFICATION_PRIVATE;
            if (task.classification == Clazz.PUBLIC)
                classCode = Tasks.CLASSIFICATION_PUBLIC;
            else if (task.classification == Clazz.CONFIDENTIAL)
                classCode = Tasks.CLASSIFICATION_CONFIDENTIAL;
            builder.withValue(Tasks.CLASSIFICATION, classCode);
        }

        if (task.completedAt != null) {
            // COMPLETED must always be a DATE-TIME
            builder .withValue(Tasks.COMPLETED, task.completedAt.getDateTime().getTime())
                    .withValue(Tasks.COMPLETED_IS_ALLDAY, 0);
        }
        builder.withValue(Tasks.PERCENT_COMPLETE, task.percentComplete);

        int statusCode = Tasks.STATUS_DEFAULT;
        if (task.status != null) {
            if (task.status == Status.VTODO_NEEDS_ACTION)
                statusCode = Tasks.STATUS_NEEDS_ACTION;
            else if (task.status == Status.VTODO_IN_PROCESS)
                statusCode = Tasks.STATUS_IN_PROCESS;
            else if (task.status == Status.VTODO_COMPLETED)
                statusCode = Tasks.STATUS_COMPLETED;
            else if (task.status == Status.VTODO_CANCELLED)
                statusCode = Tasks.STATUS_CANCELLED;
        }
        builder.withValue(Tasks.STATUS, statusCode);

        final boolean allDay = task.isAllDay();
        if (allDay)
            builder.withValue(Tasks.IS_ALLDAY, 1);
        else {
            builder.withValue(Tasks.IS_ALLDAY, 0);

            java.util.TimeZone tz = task.getTimeZone();
            builder.withValue(Tasks.TZ, tz.getID());
        }

        if (task.createdAt != null)
            builder.withValue(Tasks.CREATED, task.createdAt);
        if (task.lastModified != null)
            builder.withValue(Tasks.LAST_MODIFIED, task.lastModified);

        if (task.dtStart != null)
            builder.withValue(Tasks.DTSTART, task.dtStart.getDate().getTime());
        if (task.due != null)
            builder.withValue(Tasks.DUE, task.due.getDate().getTime());
        if (task.duration != null)
            builder.withValue(Tasks.DURATION, task.duration.getValue());

        if (!task.getRDates().isEmpty())
            try {
                builder.withValue(Tasks.RDATE, DateUtils.recurrenceSetsToAndroidString(task.getRDates(), allDay));
            } catch (ParseException e) {
                Log.e(TAG, "Couldn't parse RDate(s)", e);
            }
        if (!task.getExDates().isEmpty())
            try {
                builder.withValue(Tasks.EXDATE, DateUtils.recurrenceSetsToAndroidString(task.getExDates(), allDay));
            } catch (ParseException e) {
                Log.e(TAG, "Couldn't parse ExDate(s)", e);
            }
        if (task.rRule != null)
            builder.withValue(Tasks.EXDATE, task.rRule.getValue());
    }


    protected Uri tasksSyncURI() {
        return taskList.syncAdapterURI(taskList.provider.tasksUri());
    }

    protected Uri taskSyncURI() {
        if (id == null)
            throw new IllegalStateException("Task doesn't have an ID yet");
        return taskList.syncAdapterURI(ContentUris.withAppendedId(taskList.provider.tasksUri(), id));
    }

}
