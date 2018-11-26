/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import androidx.test.runner.permission.PermissionRequester
import junit.framework.AssertionFailedError

object TestUtils {

    fun requestTaskPermissions() {
        val perm = PermissionRequester()
        perm.addPermissions(
                TaskProvider.PERMISSION_READ_TASKS,
                TaskProvider.PERMISSION_WRITE_TASKS
        )
        try {
            perm.requestPermissions()
        } catch (ignored: AssertionFailedError) {
        }
    }

}