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

import android.accounts.Account;
import android.content.ContentUris;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.support.annotation.RequiresPermission;
import android.test.InstrumentationTestCase;
import android.util.Log;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;
import net.fortuna.ical4j.model.property.Organizer;

import org.dmfs.provider.tasks.TaskContract;

import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.text.ParseException;

import at.bitfire.ical4android.impl.TestTask;
import at.bitfire.ical4android.impl.TestTaskList;
import lombok.Cleanup;

public class AndroidTaskTest extends InstrumentationTestCase {
    private static final String TAG = "ical4android.TaskTest";

    private static final TimeZone tzVienna = DateUtils.tzRegistry.getTimeZone("Europe/Vienna");

    TaskProvider provider;
    final Account testAccount = new Account("ical4android.AndroidTaskTest", CalendarContract.ACCOUNT_TYPE_LOCAL);

    Uri taskListUri;
    AndroidTaskList taskList;


    // helpers

    private Uri syncAdapterURI(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(TaskContract.ACCOUNT_TYPE, testAccount.type)
                .appendQueryParameter(TaskContract.ACCOUNT_NAME, testAccount.name)
                .appendQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
    }


    // initialization

    @Override
    @RequiresPermission(allOf = { "org.dmfs.permission.READ_TASKS", "org.dmfs.permission.WRITE_TASKS" })
    public void setUp() throws RemoteException, FileNotFoundException, CalendarStorageException {
        provider = AndroidTaskList.acquireTaskProvider(getInstrumentation().getTargetContext().getContentResolver());
        assertNotNull("Couldn't access task provider", provider);

        taskList = TestTaskList.findOrCreate(testAccount, provider);
        assertNotNull("Couldn't find/create test task list", taskList);

        taskListUri = ContentUris.withAppendedId(provider.taskListsUri(), taskList.getId());
        Log.i(TAG, "Prepared test task list " + taskListUri);
    }

    @Override
    public void tearDown() throws CalendarStorageException {
        Log.i(TAG, "Deleting test task list");
        taskList.delete();
    }


    // tests

    public void testAddTask() throws URISyntaxException, ParseException, FileNotFoundException, CalendarStorageException {
        // build and write event to calendar provider
        Task task = new Task();
        task.uid = "sample1@testAddEvent";
        task.summary = "Sample event";
        task.description = "Sample event with date/time";
        task.location = "Sample location";
        task.dtStart = new DtStart("20150501T120000", tzVienna);
        task.due = new Due("20150501T140000", tzVienna);
        task.organizer = new Organizer("mailto:organizer@example.com");
        assertFalse(task.isAllDay());

        // add to task list
        Uri uri = new TestTask(taskList, task).add();
        assertNotNull("Couldn't add task", uri);

        // read and parse event from calendar provider
        @Cleanup("delete") TestTask testTask = new TestTask(taskList, ContentUris.parseId(uri));
        assertNotNull("Inserted task is not here", testTask);
        Task task2 = testTask.getTask();
        assertNotNull("Inserted task is empty", task2);

        // compare with original event
        assertEquals(task.summary, task2.summary);
        assertEquals(task.description, task2.description);
        assertEquals(task.location, task2.location);
        assertEquals(task.dtStart, task2.dtStart);
    }

    public void testAddTaskWithInvalidDue() throws ParseException, CalendarStorageException, FileNotFoundException {
        Task task = new Task();
        task.uid = "invalidDUE@ical4android.tests";
        task.summary = "Task with invalid DUE";
        task.dtStart = new DtStart(new Date("20150102"));
        task.due = new Due(new Date("20150101"));

        Uri uri = new TestTask(taskList, task).add();
        assertNotNull("Couldn't add task", uri);

        @Cleanup("delete") TestTask testTask = new TestTask(taskList, ContentUris.parseId(uri));
        assertNotNull("Inserted task is not here", testTask);
        Task task2 = testTask.getTask();
        assertNull(task.dtStart);
    }

    public void testUpdateTask() throws URISyntaxException, ParseException, FileNotFoundException, CalendarStorageException {
        // add test event without reminder
        Task task = new Task();
        task.uid = "sample1@testAddEvent";
        task.summary = "Sample event";
        task.description = "Sample event with date/time";
        task.location = "Sample location";
        task.dtStart = new DtStart("20150501T120000", tzVienna);
        assertFalse(task.isAllDay());
        Uri uri = new TestTask(taskList, task).add();

        // update test event in calendar
        @Cleanup("delete") TestTask testTask = new TestTask(taskList, ContentUris.parseId(uri));
        task = testTask.getTask();
        task.summary = "Updated event";                     // change value
        task.dtStart = null;                                // remove value
        task.due = new Due("20150501T140000", tzVienna);    // add value
        testTask.update(task);

        // read again and verify result
        testTask = new TestTask(taskList, ContentUris.parseId(uri));
        Task updatedTask = testTask.getTask();
        assertEquals(task.summary, updatedTask.summary);
        assertEquals(task.dtStart, updatedTask.dtStart);
        assertEquals(task.due, updatedTask.due);
    }

    public void testBuildAllDayTask() throws ParseException, FileNotFoundException, CalendarStorageException {
        // add all-day event to calendar provider
        Task task = new Task();
        task.summary = "All-day task";
        task.description = "All-day task for testing";
        task.location = "Sample location testBuildAllDayTask";
        task.dtStart = new DtStart(new Date("20150501"));
        task.due = new Due(new Date("20150502"));
        assertTrue(task.isAllDay());
        Uri uri = new TestTask(taskList, task).add();
        assertNotNull("Couldn't add event", uri);

        // read again and verify result
        @Cleanup("delete") TestTask testTask = new TestTask(taskList, ContentUris.parseId(uri));
        Task task2 = testTask.getTask();
        // compare with original event
        assertEquals(task.summary, task2.summary);
        assertEquals(task.description, task2.description);
        assertEquals(task.location, task2.location);
        assertEquals(task.dtStart.getDate(), task2.dtStart.getDate());
        assertEquals(task.due.getDate(), task2.due.getDate());
        assertTrue(task2.isAllDay());
    }

}
