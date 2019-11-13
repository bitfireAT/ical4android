/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentUris
import android.net.Uri
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import at.bitfire.ical4android.impl.TestTask
import at.bitfire.ical4android.impl.TestTaskList
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.*
import org.dmfs.tasks.contract.TaskContract
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Test

class AndroidTaskTest {

    private val tzVienna = DateUtils.tzRegistry.getTimeZone("Europe/Vienna")

    private var provider: TaskProvider? = null
    private val testAccount = Account("AndroidTaskTest", TaskContract.LOCAL_ACCOUNT_TYPE)

    private lateinit var taskListUri: Uri
    private var taskList: TestTaskList? = null

    init {
        TestUtils.requestTaskPermissions()
    }

    @Before
    fun prepare() {
        // connect to OpenTasks
        val providerOrNull = AndroidTaskList.acquireTaskProvider(getInstrumentation().targetContext)
        assumeNotNull(providerOrNull)
        provider = providerOrNull!!

        taskList = TestTaskList.create(testAccount, providerOrNull)
        assertNotNull("Couldn't find/create test task list", taskList)

        taskListUri = ContentUris.withAppendedId(provider!!.taskListsUri(), taskList!!.id)
    }

    @After
    fun shutdown() {
        taskList?.delete()
        provider?.close()
    }


    // tests

    @MediumTest
    @Test
    fun testAddTask() {
        // build and write event to calendar provider
        val task = Task()
        task.uid = "sample1@testAddEvent"
        task.summary = "Sample event"
        task.description = "Sample event with date/time"
        task.location = "Sample location"
        task.dtStart = DtStart("20150501T120000", tzVienna)
        task.due = Due("20150501T140000", tzVienna)
        task.organizer = Organizer("mailto:organizer@example.com")
        assertFalse(task.isAllDay())

        // extended properties
        task.categories.addAll(arrayOf("Cat1", "Cat2"))

        val sibling = RelatedTo("most-fields2@example.com")
        sibling.parameters.add(RelType.SIBLING)
        task.relatedTo.add(sibling)

        task.unknownProperties += XProperty("X-UNKNOWN-PROP", "Unknown Value")

        // add to task list
        val uri = TestTask(taskList!!, task).add()
        assertNotNull("Couldn't add task", uri)

        // read and parse event from calendar provider
        val testTask = taskList!!.findById(ContentUris.parseId(uri))
        try {
            assertNotNull("Inserted task is not here", testTask)
            val task2 = testTask.task
            assertNotNull("Inserted task is empty", task2)

            // compare with original event
            assertEquals(task.summary, task2!!.summary)
            assertEquals(task.description, task2.description)
            assertEquals(task.location, task2.location)
            assertEquals(task.dtStart, task2.dtStart)

            assertEquals(task.categories, task2.categories)
            assertEquals(task.relatedTo, task2.relatedTo)
            assertEquals(task.unknownProperties, task2.unknownProperties)
        } finally {
            testTask.delete()
        }
    }

    @MediumTest
    @Test(expected = CalendarStorageException::class)
    fun testAddTaskWithInvalidDue() {
        val task = Task()
        task.uid = "invalidDUE@ical4android.tests"
        task.summary = "Task with invalid DUE"
        task.dtStart = DtStart(Date("20150102"))

        task.due = Due(Date("20150101"))
        TestTask(taskList!!, task).add()
    }

    @MediumTest
    @Test
    fun testUpdateTask() {
        // add test event without reminder
        val task = Task()
        task.uid = "sample1@testAddEvent"
        task.summary = "Sample event"
        task.description = "Sample event with date/time"
        task.location = "Sample location"
        task.dtStart = DtStart("20150501T120000", tzVienna)
        assertFalse(task.isAllDay())
        val uri = TestTask(taskList!!, task).add()
        assertNotNull(uri)

        val testTask = taskList!!.findById(ContentUris.parseId(uri))
        try {
            // update test event in calendar
            val task2 = testTask.task!!
            task2.summary = "Updated event"                     // change value
            task2.dtStart = null                                // remove value
            task2.due = Due("20150501T140000", tzVienna)    // add value
            testTask.update(task)

            // read again and verify result
            val updatedTask = taskList!!.findById(ContentUris.parseId(uri)).task!!
            assertEquals(task.summary, updatedTask.summary)
            assertEquals(task.dtStart, updatedTask.dtStart)
            assertEquals(task.due, updatedTask.due)
        } finally {
            testTask.delete()
        }
    }

    @MediumTest
    @Test
    fun testBuildAllDayTask() {
        // add all-day event to calendar provider
        val task = Task()
        task.summary = "All-day task"
        task.description = "All-day task for testing"
        task.location = "Sample location testBuildAllDayTask"
        task.dtStart = DtStart(Date("20150501"))
        task.due = Due(Date("20150502"))
        assertTrue(task.isAllDay())
        val uri = TestTask(taskList!!, task).add()
        assertNotNull(uri)

        val testTask = taskList!!.findById(ContentUris.parseId(uri))
        try {
            // read again and verify result
            val task2 = testTask.task!!
            assertEquals(task.summary, task2.summary)
            assertEquals(task.description, task2.description)
            assertEquals(task.location, task2.location)
            assertEquals(task.dtStart!!.date, task2.dtStart!!.date)
            assertEquals(task.due!!.date, task2.due!!.date)
            assertTrue(task2.isAllDay())
        } finally {
            testTask.delete()
        }
    }

    @MediumTest
    @Test
    fun testGetTimeZone() {
        // no date/time
        var t = TestTask(taskList!!, Task())
        assertEquals(TimeZone.getDefault(), t.getTimeZone())

        // dtstart with date (no time)
        t = TestTask(taskList!!, Task())
        t.task!!.dtStart = DtStart("20150101")
        assertEquals(TimeZone.getDefault(), t.getTimeZone())

        // dtstart with time
        t = TestTask(taskList!!, Task())
        t.task!!.dtStart = (DtStart("20150101", tzVienna))
        assertEquals(tzVienna, t.getTimeZone())
    }

}