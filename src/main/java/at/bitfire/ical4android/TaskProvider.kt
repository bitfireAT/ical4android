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

package at.bitfire.ical4android

import android.annotation.SuppressLint
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.os.Build
import org.dmfs.provider.tasks.TaskContract
import java.io.Closeable
import java.util.logging.Level

class TaskProvider private constructor(
        val name: ProviderName,
        val client: ContentProviderClient
): Closeable {

    enum class ProviderName(
            val authority: String
    ) {
        //Mirakel("de.azapps.mirakel.provider"),
        OpenTasks("org.dmfs.tasks");
    }

    companion object {

        /**
         * Acquires a content provider for a given task provider. The content provider will
         * be released when the TaskProvider is closed.
         * @param resolver will be used to acquire the content provider client
         * @param name task provider to acquire content provider for
         * @return content provider for the given task provider (may be {@code null})
         */
        @SuppressLint("Recycle")
        @JvmStatic
        fun acquire(resolver: ContentResolver, name: TaskProvider.ProviderName): TaskProvider? {
            try {
                val client = resolver.acquireContentProviderClient(name.authority)
                return if (client != null)
                    TaskProvider(name, client)
                else
                    null
            } catch(e: SecurityException) {
                Constants.log.log(Level.WARNING, "Not allowed to access task provider", e);
                return null
            }
        }

    }


    fun taskListsUri() = TaskContract.TaskLists.getContentUri(name.authority)
    fun tasksUri() = TaskContract.Tasks.getContentUri(name.authority)
    fun alarmsUri() = TaskContract.Alarms.getContentUri(name.authority)

    override fun close() {
        if (Build.VERSION.SDK_INT >= 24)
            client.close()
        else
            client.release()
    }

}
