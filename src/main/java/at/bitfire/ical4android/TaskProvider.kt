/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
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
