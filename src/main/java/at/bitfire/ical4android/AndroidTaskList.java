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
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;

import org.apache.commons.lang3.ArrayUtils;
import org.dmfs.provider.tasks.TaskContract;
import org.dmfs.provider.tasks.TaskContract.TaskLists;
import org.dmfs.provider.tasks.TaskContract.Tasks;

import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;

import lombok.Cleanup;
import lombok.Getter;

/**
 * Represents a locally stored task list, containing AndroidTasks (whose data objects are Tasks).
 * Communicates with third-party content providers to store the tasks.
 * Currently, these task providers are supported:
 *    - Mirakel
 *    - OpenTasks
 */
public abstract class AndroidTaskList {
    final protected Account account;
    public final TaskProvider provider;
    final AndroidTaskFactory taskFactory;

    @Getter final private long id;
    @Getter private String syncId, name;
    @Getter private Integer color;
    @Getter private boolean isSynced, isVisible;

    /** Those columns will always be fetched when tasks are queried by {@link #queryTasks(String, String[])}.
     *  Must include Tasks._ID as the first element! */
    protected String[] taskBaseInfoColumns() {
        return new String[] { Tasks._ID };
    }


    protected AndroidTaskList(Account account, TaskProvider provider, AndroidTaskFactory taskFactory, long id) {
        this.account = account;
        this.provider = provider;
        this.taskFactory = taskFactory;
        this.id = id;
    }

	
	/* class methods, constructor */

    /**
     * Acquires a ContentProviderClient for a supported task provider. If multiple providers are
     * available, a pre-defined priority list is taken into account.
     * @return A TaskProvider, or null if task storage is not available/accessible.
     *         Caller is responsible for calling release()!
     */
    public static TaskProvider acquireTaskProvider(ContentResolver resolver) {
        TaskProvider.ProviderName[] byPriority = {
                //TaskProvider.ProviderName.Mirakel,
                TaskProvider.ProviderName.OpenTasks
        };
        for (TaskProvider.ProviderName name : byPriority) {
            TaskProvider provider = TaskProvider.acquire(resolver, name);
            if (provider != null)
                return provider;
        }
        return null;
    }


	@SuppressLint("InlinedApi")
	public static Uri create(Account account, TaskProvider provider, ContentValues info) throws CalendarStorageException {
        info.put(TaskContract.ACCOUNT_NAME, account.name);
        info.put(TaskContract.ACCOUNT_TYPE, account.type);
        info.put(TaskLists.ACCESS_LEVEL, 0);

		Constants.log.info("Creating local task list: " + info.toString());
		try {
			return provider.client.insert(syncAdapterURI(provider.taskListsUri(), account), info);
		} catch (RemoteException e) {
			throw new CalendarStorageException("Couldn't create local task list", e);
		}
	}

    public static AndroidTaskList findByID(Account account, TaskProvider provider, AndroidTaskListFactory factory, long id) throws FileNotFoundException, CalendarStorageException {
        try {
            @Cleanup Cursor cursor = provider.client.query(syncAdapterURI(ContentUris.withAppendedId(provider.taskListsUri(), id), account), null, null, null, null);
            if (cursor != null && cursor.moveToNext()) {
                AndroidTaskList taskList = factory.newInstance(account, provider, id);

                ContentValues values = new ContentValues(cursor.getColumnCount());
                DatabaseUtils.cursorRowToContentValues(cursor, values);
                taskList.populate(values);
                return taskList;
            } else
                throw new FileNotFoundException();
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't query task list by ID", e);
        }
    }

    public static AndroidTaskList[] find(Account account, TaskProvider provider, AndroidTaskListFactory factory, String where, String whereArgs[]) throws CalendarStorageException {
        List<AndroidTaskList> taskLists = new LinkedList<>();
        try {
            @Cleanup Cursor cursor = provider.client.query(syncAdapterURI(provider.taskListsUri(), account), null, null, null, null);
            while (cursor != null && cursor.moveToNext()) {
                ContentValues values = new ContentValues(cursor.getColumnCount());
                DatabaseUtils.cursorRowToContentValues(cursor, values);
                AndroidTaskList taskList = factory.newInstance(account, provider, values.getAsLong(TaskLists._ID));
                taskList.populate(values);
                taskLists.add(taskList);
            }
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't query task list by ID", e);
        }
        return taskLists.toArray(factory.newArray(taskLists.size()));
    }

    protected void populate(ContentValues values) {
        syncId = values.getAsString(TaskLists._SYNC_ID);
        name = values.getAsString(TaskLists.LIST_NAME);
        if (values.containsKey(TaskLists.LIST_COLOR))
            color = values.getAsInteger(TaskLists.LIST_COLOR);
        if (values.containsKey(TaskLists.SYNC_ENABLED))
            isSynced = values.getAsInteger(TaskLists.SYNC_ENABLED) != 0;
        if (values.containsKey(TaskLists.VISIBLE))
            isVisible = values.getAsInteger(TaskLists.VISIBLE) != 0;
    }


    public int update(ContentValues info) throws CalendarStorageException {
        try {
            return provider.client.update(taskListSyncUri(), info, null, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't update local task list", e);
        }
    }

    public int delete() throws CalendarStorageException {
        try {
            return provider.client.delete(taskListSyncUri(), null, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't delete local task list", e);
        }
    }


    protected AndroidTask[] queryTasks(String where, String[] whereArgs) throws CalendarStorageException {
        where = (where == null ? "" : "(" + where + ") AND ") + Tasks.LIST_ID + "=?";
        whereArgs = ArrayUtils.add(whereArgs, String.valueOf(id));

        @Cleanup Cursor cursor = null;
        try {
            cursor = provider.client.query(
                    syncAdapterURI(provider.tasksUri()),
                    taskBaseInfoColumns(),
                    where, whereArgs, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't query calendar events", e);
        }

        List<AndroidTask> tasks = new LinkedList<>();
        while (cursor != null && cursor.moveToNext()) {
            ContentValues baseInfo = new ContentValues(cursor.getColumnCount());
            DatabaseUtils.cursorRowToContentValues(cursor, baseInfo);
            tasks.add(taskFactory.newInstance(this, cursor.getLong(0), baseInfo));
        }
        return tasks.toArray(taskFactory.newArray(tasks.size()));
    }


    public static Uri syncAdapterURI(Uri uri, Account account) {
        return uri.buildUpon()
                .appendQueryParameter(TaskContract.ACCOUNT_NAME, account.name)
                .appendQueryParameter(TaskContract.ACCOUNT_TYPE, account.type)
                .appendQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
    }

    public Uri syncAdapterURI(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(TaskContract.ACCOUNT_NAME, account.name)
                .appendQueryParameter(TaskContract.ACCOUNT_TYPE, account.type)
                .appendQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
    }

    public Uri taskListSyncUri() {
        return syncAdapterURI(ContentUris.withAppendedId(provider.taskListsUri(), id));
    }

}
