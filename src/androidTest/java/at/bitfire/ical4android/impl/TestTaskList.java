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

package at.bitfire.ical4android.impl;

import android.accounts.Account;
import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.util.Log;

import org.dmfs.provider.tasks.TaskContract;

import at.bitfire.ical4android.AndroidTaskList;
import at.bitfire.ical4android.AndroidTaskListFactory;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.TaskProvider;

public class TestTaskList extends AndroidTaskList<TestTask> {

    private static final String TAG = "ical4android.TestCal";

    protected TestTaskList(Account account, TaskProvider provider, long id) {
        super(account, provider, TestTask.Factory.FACTORY, id);
    }

    public static TestTaskList findOrCreate(Account account, TaskProvider provider) throws CalendarStorageException {
        TestTaskList[] taskLists = new TestTaskList[0]; /*findAll()*/
        if (taskLists.length == 0) {
            Log.i(TAG, "Test calendar not found, creating");

            ContentValues values = new ContentValues();
            values.put(TaskContract.TaskListColumns.LIST_NAME, "Test Task List");
            values.put(TaskContract.TaskListColumns.LIST_COLOR, 0xffff0000);
            values.put(TaskContract.TaskListColumns.SYNC_ENABLED, 1);
            values.put(TaskContract.TaskListColumns.VISIBLE, 1);
            Uri uri = AndroidTaskList.create(account, provider, values);

            return new TestTaskList(account, provider, ContentUris.parseId(uri));
        } else
            return taskLists[0];
    }


    public static class Factory implements AndroidTaskListFactory<TestTaskList> {

        public static final Factory FACTORY = new Factory();

        @Override
        public TestTaskList newInstance(Account account, TaskProvider provider, long id) {
            return new TestTaskList(account, provider, id);
        }

    }

}
