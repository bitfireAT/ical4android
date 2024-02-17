/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.impl

import android.content.ContentValues

import at.bitfire.ical4android.DmfsTask
import at.bitfire.ical4android.DmfsTaskFactory
import at.bitfire.ical4android.DmfsTaskList
import at.bitfire.ical4android.Task

class TestTask: DmfsTask {

    constructor(taskList: DmfsTaskList<DmfsTask>, values: ContentValues)
        : super(taskList, values)

    constructor(taskList: TestTaskList, task: Task)
        : super(taskList, task)

    object Factory: DmfsTaskFactory<TestTask> {
        override fun fromProvider(taskList: DmfsTaskList<DmfsTask>, values: ContentValues) =
                TestTask(taskList, values)
    }

}
