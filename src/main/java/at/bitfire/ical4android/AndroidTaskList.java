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
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import org.dmfs.provider.tasks.TaskContract;

import java.io.FileNotFoundException;

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
    private static final String TAG = "ical4android.TaskList";

    final protected Account account;
    final protected TaskProvider provider;
    final AndroidTaskFactory taskFactory;

    @Getter final private long id;
    @Getter private String name, displayName;
    @Getter private Integer color;
    @Getter private boolean isSynced, isVisible;

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
        info.put(TaskContract.TaskListColumns.ACCESS_LEVEL, 0);

		Log.i(TAG, "Creating local task list: " + info.toString());
		try {
			return provider.client.insert(syncAdapterURI(provider.taskListsUri(), account), info);
		} catch (RemoteException e) {
			throw new CalendarStorageException("Couldn't create calendar", e);
		}
	}

    public static AndroidTaskList findByID(Account account, TaskProvider provider, AndroidTaskListFactory factory, long id) throws FileNotFoundException, CalendarStorageException {
        try {
            @Cleanup Cursor cursor = provider.client.query(syncAdapterURI(ContentUris.withAppendedId(provider.taskListsUri(), id), account), new String[] {
                    TaskContract.TaskLists._ID
            }, null, null, null);
            if (cursor != null && cursor.moveToNext()) {
                AndroidTaskList taskList = factory.newInstance(account, provider, id);
                return taskList;
            } else
                throw new FileNotFoundException();
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't query task list by ID", e);
        }
    }

    public static AndroidTaskList[] findAll(Account account, ContentProviderClient provider, AndroidTaskListFactory factory) throws CalendarStorageException {
        throw new UnsupportedOperationException();
    }

    public void update(ContentValues info) throws CalendarStorageException {
        /*try {
            provider.update(syncAdapterURI(ContentUris.withAppendedId(Calendars.CONTENT_URI, id)), info, null, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't update calendar", e);
        }*/
        throw new UnsupportedOperationException();
    }

    public int delete() throws CalendarStorageException {
        try {
            return provider.client.delete(syncAdapterURI(ContentUris.withAppendedId(provider.taskListsUri(), id)), null, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't delete task list", e);
        }
    }


    protected void populate(ContentValues info) {
        /*name = info.getAsString(Calendars.NAME);
        displayName = info.getAsString(Calendars.CALENDAR_DISPLAY_NAME);

        if (info.containsKey(Calendars.CALENDAR_COLOR))
            color = info.getAsInteger(Calendars.CALENDAR_COLOR);

        isSynced = info.getAsInteger(Calendars.SYNC_EVENTS) != 0;
        isVisible = info.getAsInteger(Calendars.VISIBLE) != 0;*/
    }

    protected AndroidEvent[] query(String where, String[] whereArgs) throws CalendarStorageException {
        /*where = (where == null ? "" : "(" + where + ") AND ") + Events.CALENDAR_ID + "=?";
        whereArgs = ArrayUtils.add(whereArgs, String.valueOf(id));

        @Cleanup Cursor cursor = null;
        try {
            cursor = provider.query(
                    syncAdapterURI(Events.CONTENT_URI),
                    new String[] { Events._ID },
                    where, whereArgs, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't query calendar events", e);
        }

        List<AndroidEvent> events = new LinkedList<>();
        while (cursor != null && cursor.moveToNext()) {
            AndroidEvent event = eventFactory.newInstance(this, cursor.getLong(0));
            events.add(event);
        }
        return events.toArray(eventFactory.newArray(events.size()));*/
        throw new UnsupportedOperationException();
    }

    protected int delete(String where, String[] whereArgs) throws CalendarStorageException {
        /*where = (where == null ? "" : "(" + where + ") AND ") + Events.CALENDAR_ID + "=?";
        whereArgs = ArrayUtils.add(whereArgs, String.valueOf(id));

        try {
            return provider.delete(syncAdapterURI(Events.CONTENT_URI), where, whereArgs);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't delete calendar events", e);
        }*/
        throw new UnsupportedOperationException();
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

}
