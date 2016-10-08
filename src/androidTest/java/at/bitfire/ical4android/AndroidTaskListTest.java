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
import android.support.annotation.RequiresPermission;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.dmfs.provider.tasks.TaskContract;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

import at.bitfire.ical4android.impl.TestTaskList;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class AndroidTaskListTest {
    private static final String TAG = "ical4android.TaskList";

    final Account testAccount = new Account("ical4android.AndroidTaskListTest", TaskContract.LOCAL_ACCOUNT_TYPE);
    TaskProvider provider;

    @Before
    @RequiresPermission(allOf = { "org.dmfs.permission.READ_TASKS", "org.dmfs.permission.WRITE_TASKS" })
    public void connect() throws Exception {
        provider = AndroidTaskList.acquireTaskProvider(getContext().getContentResolver());
        assertNotNull(provider);
        Log.i(TAG, "Acquired context for " + provider.name);
    }

    @Before
    public void disconnect() throws Exception {
        provider.close();
    }

    @Test
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
        TestTaskList taskList = (TestTaskList)TestTaskList.findByID(testAccount, provider, TestTaskList.Factory.FACTORY, ContentUris.parseId(uri));
        assertNotNull(taskList);

        // delete task list
        assertEquals(1, taskList.delete());
    }

}
