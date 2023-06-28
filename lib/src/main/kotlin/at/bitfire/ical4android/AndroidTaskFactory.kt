/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.content.ContentValues

interface AndroidTaskFactory<out T: AndroidTask> {

    fun fromProvider(taskList: AndroidTaskList<AndroidTask>, values: ContentValues): T

}
