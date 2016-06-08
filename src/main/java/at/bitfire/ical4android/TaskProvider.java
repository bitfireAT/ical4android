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

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import org.dmfs.provider.tasks.TaskContract;

import java.io.Closeable;
import java.util.logging.Level;

public class TaskProvider implements Closeable {

    public enum ProviderName {
        //Mirakel("de.azapps.mirakel.provider"),
        OpenTasks("org.dmfs.tasks");

        public final String authority;

        ProviderName(String authority) {
            this.authority = authority;
        }
    }

    public final ProviderName name;
    public final ContentProviderClient client;

    private TaskProvider(ProviderName name, ContentProviderClient client) {
        this.name = name;
        this.client = client;
    }

    public Uri taskListsUri() {
        return TaskContract.TaskLists.getContentUri(name.authority);
    }
    public Uri tasksUri() {
        return TaskContract.Tasks.getContentUri(name.authority);
    }
    public Uri alarmsUri() { return TaskContract.Alarms.getContentUri(name.authority); }

    @Override
    public void close() {
        if (client != null)
            client.release();
    }


    /**
     * Acquires a content provider for a given task provider. The content provider will
     * be released when the TaskProvider is closed.
     * @param resolver will be used to acquire the content provider client
     * @param name task provider to acquire content provider for
     * @return content provider for the given task provider (may be {@code null})
     */
    @SuppressWarnings("Recycle")
    public static TaskProvider acquire(ContentResolver resolver, TaskProvider.ProviderName name) {
        ContentProviderClient client = null;
        try {
            client = resolver.acquireContentProviderClient(name.authority);
        } catch(SecurityException e) {
            Constants.log.log(Level.WARNING, "Not allowed to access task provider: ", e);
        }

        return client != null ? new TaskProvider(name, client) : null;
    }

}
