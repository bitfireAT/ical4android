/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.impl

import android.content.ContentValues

import at.bitfire.ical4android.AndroidTask
import at.bitfire.ical4android.AndroidTaskFactory
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.Task

class TestTask: AndroidTask {

    constructor(taskList: AndroidTaskList<AndroidTask>, values: ContentValues)
        : super(taskList, values)

    constructor(taskList: TestTaskList, task: Task)
        : super(taskList, task)

    object Factory: AndroidTaskFactory<TestTask> {
        override fun fromProvider(taskList: AndroidTaskList<AndroidTask>, values: ContentValues) =
                TestTask(taskList, values)
    }

}
