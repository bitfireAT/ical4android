/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.content.ContentValues

interface DmfsTaskFactory<out T: DmfsTask> {

    fun fromProvider(taskList: DmfsTaskList<DmfsTask>, values: ContentValues): T

}
