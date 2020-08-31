/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import android.content.Context
import androidx.test.runner.permission.PermissionRequester
import junit.framework.AssertionFailedError

object TestUtils {

    /**
     * Acquires a [android.content.ContentProviderClient] for a supported task provider.
     * If multiple providers are available, a pre-defined priority list is taken into account.
     *
     * @return A [TaskProvider], or null if task storage is not available/accessible.
     * Caller is responsible for calling [TaskProvider.close]!
     */
    fun acquireTaskProvider(context: Context): TaskProvider? {
        for (name in TaskProvider.TASK_PROVIDERS)
            try {
                TaskProvider.acquire(context, name)?.let { return it }
            } catch (e: Exception) {
                // couldn't acquire task provider
            }
        return null
    }

    fun requestTaskPermissions() {
        try {
            PermissionRequester().apply {
                addPermissions(
                        TaskProvider.PERMISSION_OPENTASKS_READ,
                        TaskProvider.PERMISSION_OPENTASKS_WRITE
                )
            }.requestPermissions()
        } catch (ignored: AssertionFailedError) {
            // couldn't grant OpenTasks permissions for testing
        }

        try {
            PermissionRequester().apply {
                addPermissions(
                        TaskProvider.PERMISSION_TASKS_ORG_READ,
                        TaskProvider.PERMISSION_TASKS_ORG_WRITE
                )
            }.requestPermissions()
        } catch (ignored: AssertionFailedError) {
            // couldn't grant tasks.org permissions for testing
        }
    }

}