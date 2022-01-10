/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import androidx.test.runner.permission.PermissionRequester

object TestUtils {

    fun requestPermissions(permissions: Array<String>) {
        PermissionRequester().apply {
            addPermissions(*permissions)
        }.requestPermissions()
    }

}