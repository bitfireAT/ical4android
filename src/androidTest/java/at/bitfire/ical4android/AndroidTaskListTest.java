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
import android.content.ContentValues;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.util.Log;

import org.dmfs.provider.tasks.TaskContract;

import java.io.FileNotFoundException;

import at.bitfire.ical4android.impl.TestTaskList;

public class AndroidTaskListTest extends InstrumentationTestCase {
    private static final String TAG = "ical4android.TaskList";

    final Account testAccount = new Account("ical4android.AndroidTaskListTest", TaskContract.LOCAL_ACCOUNT_TYPE);

    TaskProvider provider;

    @Override
    public void setUp() throws Exception {
        provider = AndroidTaskList.acquireTaskProvider(getInstrumentation().getContext().getContentResolver());
        assertNotNull(provider);
        Log.i(TAG, "Acquired context for " + provider.getName());
    }

    @Override
    public void tearDown() throws Exception {
        provider.close();
    }

    public void testManageTaskLists() throws CalendarStorageException, FileNotFoundException {
        // create task list
        ContentValues info = new ContentValues();
        info.put(TaskContract.TaskLists.LIST_NAME, "Test Task List");
        info.put(TaskContract.TaskLists.LIST_COLOR, 0xffff0000);
        info.put(TaskContract.TaskLists.OWNER, "test@example.com");
        info.put(TaskContract.TaskLists.SYNC_ENABLED, 1);
        info.put(TaskContract.TaskLists.VISIBLE, 1);
        Uri uri = TestTaskList.create(testAccount, provider, info);
        assertNotNull(uri);

        // query task list
        TestTaskList taskList = TestTaskList.findByID(testAccount, provider, TestTaskList.Factory.FACTORY, ContentUris.parseId(uri));
        assertNotNull(taskList);

        // delete task list
        assertEquals(1, taskList.delete());
    }

}
