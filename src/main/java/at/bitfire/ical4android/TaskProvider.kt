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
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.dmfs.tasks.contract.TaskContract
import java.io.Closeable
import java.util.logging.Level

class TaskProvider private constructor(
        val name: ProviderName,
        val client: ContentProviderClient
): Closeable {

    enum class ProviderName(
            val authority: String,
            val packageName: String,
            val minVersionCode: Int,
            val minVersionName: String
    ) {
        //Mirakel("de.azapps.mirakel.provider"),
        OpenTasks("org.dmfs.tasks", "org.dmfs.tasks", 103, "1.1.8.2")
    }

    companion object {

        @JvmField val PERMISSION_READ_TASKS = "org.dmfs.permission.READ_TASKS"
        @JvmField val PERMISSION_WRITE_TASKS = "org.dmfs.permission.WRITE_TASKS"

        /**
         * Acquires a content provider for a given task provider. The content provider will
         * be released when the TaskProvider is closed.
         * @param resolver will be used to acquire the content provider client
         * @param name task provider to acquire content provider for
         * @return content provider for the given task provider (may be {@code null})
         */
        @SuppressLint("Recycle")
        @JvmStatic
        fun acquire(context: Context, name: TaskProvider.ProviderName): TaskProvider? {
            return try {
                checkVersion(context, name)

                val client = context.contentResolver.acquireContentProviderClient(name.authority)
                if (client != null)
                    TaskProvider(name, client)
                else
                    null
            } catch(e: SecurityException) {
                Constants.log.log(Level.WARNING, "Not allowed to access task provider", e)
                null
            } catch(e: PackageManager.NameNotFoundException) {
                Constants.log.warning("Package ${name.packageName} not installed")
                null
            }
        }

        @JvmStatic
        fun fromProviderClient(context: Context, client: ContentProviderClient): TaskProvider {
            // at the moment, only OpenTasks is supported
            checkVersion(context, ProviderName.OpenTasks)
            return TaskProvider(ProviderName.OpenTasks, client)
        }

        /**
         * Checks the version code of an installed tasks provider.
         * @throws PackageManager.NameNotFoundException if the tasks provider is not installed
         * @throws IllegalArgumentException if the tasks provider is installed, but doesn't meet the minimum version requirement
         * */
        private fun checkVersion(context: Context, name: ProviderName) {
            // check whether package is available with required minimum version
            val info = context.packageManager.getPackageInfo(name.packageName, 0)
            if (info.versionCode < name.minVersionCode) {
                val exception = ProviderTooOldException(name, info.versionCode, info.versionName)
                Constants.log.log(Level.WARNING, "Task provider too old", exception)
                throw exception
            }
        }

    }


    fun taskListsUri() = TaskContract.TaskLists.getContentUri(name.authority)!!
    fun tasksUri() = TaskContract.Tasks.getContentUri(name.authority)!!
    //fun alarmsUri() = TaskContract.Alarms.getContentUri(name.authority)!!

    override fun close() {
        if (Build.VERSION.SDK_INT >= 24)
            client.close()
        else
            @Suppress("DEPRECATION")
            client.release()
    }


    class ProviderTooOldException(
            val provider: ProviderName,
            val installedVersionCode: Int,
            val installedVersionName: String
    ): Exception("Package ${provider.packageName} has version $installedVersionName ($installedVersionCode), " +
            "required: ${provider.minVersionName} (${provider.minVersionCode})")

}
