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
import android.content.ContentValues
import android.support.test.InstrumentationRegistry.getInstrumentation
import android.support.test.filters.MediumTest
import at.bitfire.ical4android.impl.TestTaskList
import org.dmfs.tasks.contract.TaskContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Test

class AndroidTaskListTest {

    private var provider: TaskProvider? = null
    private val testAccount = Account("AndroidTaskListTest", TaskContract.LOCAL_ACCOUNT_TYPE)

    init {
        TestUtils.requestTaskPermissions()
    }

    @Before
    fun prepare() {
        val providerOrNull = AndroidTaskList.acquireTaskProvider(getInstrumentation().targetContext)
        assumeNotNull(providerOrNull)
        provider = providerOrNull!!
    }

    @After
    fun shutdown() {
        provider?.close()
    }


    @MediumTest
    @Test
    fun testManageTaskLists() {
        // create task list
        val info = ContentValues()
        info.put(TaskContract.TaskLists.LIST_NAME, "Test Task List")
        info.put(TaskContract.TaskLists.LIST_COLOR, 0xffff0000)
        info.put(TaskContract.TaskLists.OWNER, "test@example.com")
        info.put(TaskContract.TaskLists.SYNC_ENABLED, 1)
        info.put(TaskContract.TaskLists.VISIBLE, 1)
        val uri = AndroidTaskList.create(testAccount, provider!!, info)
        assertNotNull(uri)

        // query task list
        val taskList = AndroidTaskList.findByID(testAccount, provider!!, TestTaskList.Factory.FACTORY, ContentUris.parseId(uri))
        assertNotNull(taskList)

        // delete task list
        assertEquals(1, taskList.delete())
    }

}
