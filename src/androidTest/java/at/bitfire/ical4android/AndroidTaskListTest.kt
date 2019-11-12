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
import android.database.DatabaseUtils
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import at.bitfire.ical4android.impl.TestTask
import at.bitfire.ical4android.impl.TestTaskList
import net.fortuna.ical4j.model.property.RelatedTo
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.junit.After
import org.junit.Assert.*
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

    private fun createTaskList(): TestTaskList {
        val info = ContentValues()
        info.put(TaskContract.TaskLists.LIST_NAME, "Test Task List")
        info.put(TaskContract.TaskLists.LIST_COLOR, 0xffff0000)
        info.put(TaskContract.TaskLists.OWNER, "test@example.com")
        info.put(TaskContract.TaskLists.SYNC_ENABLED, 1)
        info.put(TaskContract.TaskLists.VISIBLE, 1)

        val uri = AndroidTaskList.create(testAccount, provider!!, info)
        assertNotNull(uri)

        return AndroidTaskList.findByID(testAccount, provider!!, TestTaskList.Factory, ContentUris.parseId(uri))
    }


    @Test
    fun testManageTaskLists() {
        val taskList = createTaskList()

        try {
            // sync URIs
            assertEquals("true", taskList.taskListSyncUri().getQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER))
            assertEquals(testAccount.type, taskList.taskListSyncUri().getQueryParameter(TaskContract.ACCOUNT_TYPE))
            assertEquals(testAccount.name, taskList.taskListSyncUri().getQueryParameter(TaskContract.ACCOUNT_NAME))

            assertEquals("true", taskList.tasksSyncUri().getQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER))
            assertEquals(testAccount.type, taskList.tasksSyncUri().getQueryParameter(TaskContract.ACCOUNT_TYPE))
            assertEquals(testAccount.name, taskList.tasksSyncUri().getQueryParameter(TaskContract.ACCOUNT_NAME))
        } finally {
            // delete task list
            assertEquals(1, taskList.delete())
        }
    }

    @Test
    fun testCommitRelations() {
        val taskList = createTaskList()
        assertTrue(taskList.useDelayedRelations)
        try {
            val parent = Task()
            parent.uid = "parent"
            parent.summary = "Parent task"
            val parentContentUri = TestTask(taskList, parent).add()

            val child = Task()
            child.uid = "child"
            child.summary = "Child task"
            child.relatedTo.add(RelatedTo(parent.uid))
            val childContentUri = TestTask(taskList, child).add()

            // there should be one DelayedRelation row
            taskList.provider.client.query(taskList.tasksPropertiesSyncUri(), null,
                    "${Properties.TASK_ID}=?", arrayOf(ContentUris.parseId(childContentUri).toString()),
                    null, null)!!.use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToNext()

                val row = ContentValues()
                DatabaseUtils.cursorRowToContentValues(cursor, row)

                assertEquals(AndroidTask.DelayedRelation.CONTENT_ITEM_TYPE, row.getAsString(Properties.MIMETYPE))
                assertNull(row.getAsLong(Relation.RELATED_ID))
                assertEquals(parent.uid, row.getAsString(Relation.RELATED_UID))
                assertEquals(Relation.RELTYPE_PARENT, row.getAsInteger(Relation.RELATED_TYPE))
            }

            taskList.commitRelations()

            // now there must be a real Relation row
            taskList.provider.client.query(taskList.tasksPropertiesSyncUri(), null,
                    "${Properties.TASK_ID}=?", arrayOf(ContentUris.parseId(childContentUri).toString()),
                    null, null)!!.use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToNext()

                val row = ContentValues()
                DatabaseUtils.cursorRowToContentValues(cursor, row)

                assertEquals(Relation.CONTENT_ITEM_TYPE, row.getAsString(Properties.MIMETYPE))
                assertEquals(ContentUris.parseId(parentContentUri), row.getAsLong(Relation.RELATED_ID))
                assertEquals(parent.uid, row.getAsString(Relation.RELATED_UID))
                assertEquals(Relation.RELTYPE_PARENT, row.getAsInteger(Relation.RELATED_TYPE))
            }
        } finally {
            taskList.delete()
        }
    }

}
