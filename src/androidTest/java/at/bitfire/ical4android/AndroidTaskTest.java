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
        task.setUid("sample1@testAddEvent");
        task.setSummary("Sample event");
        task.setDescription("Sample event with date/time");
        task.setLocation("Sample location");
        task.setDtStart(new DtStart("20150501T120000", tzVienna));
        task.setDue(new Due("20150501T140000", tzVienna));
        task.setOrganizer(new Organizer("mailto:organizer@example.com"));
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
        assertEquals(task.getSummary(), task2.getSummary());
        assertEquals(task.getDescription(), task2.getDescription());
        assertEquals(task.getLocation(), task2.getLocation());
        assertEquals(task.getDtStart(), task2.getDtStart());
    }

    public void testAddTaskWithInvalidDue() throws ParseException, FileNotFoundException {
        Task task = new Task();
        task.setUid("invalidDUE@ical4android.tests");
        task.setSummary("Task with invalid DUE");
        task.setDtStart(new DtStart(new Date("20150102")));
        task.setDue(new Due(new Date("20150101")));

        try {
            Uri uri = new TestTask(taskList, task).add();
            fail();
        } catch(CalendarStorageException ignored) {}
    }

    public void testUpdateTask() throws URISyntaxException, ParseException, FileNotFoundException, CalendarStorageException {
        // add test event without reminder
        Task task = new Task();
        task.setUid("sample1@testAddEvent");
        task.setSummary("Sample event");
        task.setDescription("Sample event with date/time");
        task.setLocation("Sample location");
        task.setDtStart(new DtStart("20150501T120000", tzVienna));
        assertFalse(task.isAllDay());
        Uri uri = new TestTask(taskList, task).add();

        // update test event in calendar
        @Cleanup("delete") TestTask testTask = new TestTask(taskList, ContentUris.parseId(uri));
        task = testTask.getTask();
        task.setSummary("Updated event");                     // change value
        task.setDtStart(null);                                // remove value
        task.setDue(new Due("20150501T140000", tzVienna));    // add value
        testTask.update(task);

        // read again and verify result
        testTask = new TestTask(taskList, ContentUris.parseId(uri));
        Task updatedTask = testTask.getTask();
        assertEquals(task.getSummary(), updatedTask.getSummary());
        assertEquals(task.getDtStart(), updatedTask.getDtStart());
        assertEquals(task.getDue(), updatedTask.getDue());
    }

    public void testBuildAllDayTask() throws ParseException, FileNotFoundException, CalendarStorageException {
        // add all-day event to calendar provider
        Task task = new Task();
        task.setSummary("All-day task");
        task.setDescription("All-day task for testing");
        task.setLocation("Sample location testBuildAllDayTask");
        task.setDtStart(new DtStart(new Date("20150501")));
        task.setDue(new Due(new Date("20150502")));
        assertTrue(task.isAllDay());
        Uri uri = new TestTask(taskList, task).add();
        assertNotNull("Couldn't add event", uri);

        // read again and verify result
        @Cleanup("delete") TestTask testTask = new TestTask(taskList, ContentUris.parseId(uri));
        Task task2 = testTask.getTask();
        // compare with original event
        assertEquals(task.getSummary(), task2.getSummary());
        assertEquals(task.getDescription(), task2.getDescription());
        assertEquals(task.getLocation(), task2.getLocation());
        assertEquals(task.getDtStart().getDate(), task2.getDtStart().getDate());
        assertEquals(task.getDue().getDate(), task2.getDue().getDate());
        assertTrue(task2.isAllDay());
    }

}
