/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.MiscUtils.ContentProviderClientHelper.closeCompat
import at.bitfire.ical4android.impl.TestJtxCollection
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.asSyncAdapter
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test

class JtxCollectionTest {

    private val testAccount = Account("TEST", JtxContract.JtxCollection.TEST_ACCOUNT_TYPE)
    private lateinit var contentResolver: ContentResolver
    private lateinit var client: ContentProviderClient
    var collection: JtxCollection<JtxICalObject>? = null
    lateinit var context: Context

    private val url = "https://jtx.techbee.at"
    private val displayname = "jtx"
    private val syncversion = JtxContract.VERSION

    private val cv = ContentValues().apply {
        put(JtxContract.JtxCollection.ACCOUNT_TYPE, testAccount.type)
        put(JtxContract.JtxCollection.ACCOUNT_NAME, testAccount.name)
        put(JtxContract.JtxCollection.URL, url)
        put(JtxContract.JtxCollection.DISPLAYNAME, displayname)
        put(JtxContract.JtxCollection.SYNC_VERSION, syncversion)
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        contentResolver = context.contentResolver
        TestUtils.requestPermissions(TaskProvider.ProviderName.JtxBoard.permissions)
        client = contentResolver.acquireContentProviderClient(JtxContract.AUTHORITY)!!
    }

    @After
    fun tearDown() {
        var collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)
        collections.forEach { collection ->
            collection.delete()
        }
        collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)
        assertEquals(0, collections.size)
        client.closeCompat()
    }


    @Test
    fun create_populate_find() {
        val collectionUri = JtxCollection.create(testAccount, client, cv)
        assertNotNull(collectionUri)
        val collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)

        assertEquals(1, collections.size)
        assertEquals(testAccount.type, collections[0].account.type)
        assertEquals(testAccount.name, collections[0].account.name)
        assertEquals(url, collections[0].url)
        assertEquals(displayname, collections[0].displayname)
        assertEquals(syncversion.toString(), collections[0].syncstate)
    }

    @Test
    fun queryICalObjects() {
        val collectionUri = JtxCollection.create(testAccount, client, cv)
        assertNotNull(collectionUri)

        val collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)
        val items = collections[0].queryICalObjects(null, null)
        assertEquals(0, items.size)

        val cv = ContentValues().apply {
            put(JtxContract.JtxICalObject.SUMMARY, "summary")
            put(JtxContract.JtxICalObject.COMPONENT, JtxContract.JtxICalObject.Component.VJOURNAL.name)
            put(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collections[0].id)
        }
        client.insert(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)
        val icalobjects = collections[0].queryICalObjects(null, null)

        assertEquals(1, icalobjects.size)
    }

    @Test
    fun updateRelatedTo_check_update_of_linkedId_CHILD_to_PARENT_is_present() {
        JtxCollection.create(testAccount, client, cv)
        val collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)

        // insert 2 icalobjects
        val parentCV = ContentValues().apply {
            put(JtxContract.JtxICalObject.SUMMARY, "summary")
            put(JtxContract.JtxICalObject.COMPONENT, JtxContract.JtxICalObject.Component.VJOURNAL.name)
            put(JtxContract.JtxICalObject.UID, "AAA")
            put(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collections[0].id)
        }
        val parentUri = client.insert(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), parentCV)
        val childCV = ContentValues().apply {
            put(JtxContract.JtxICalObject.SUMMARY, "summary")
            put(JtxContract.JtxICalObject.COMPONENT, JtxContract.JtxICalObject.Component.VJOURNAL.name)
            put(JtxContract.JtxICalObject.UID, "BBB")
            put(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collections[0].id)
        }
        val childUri = client.insert(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), childCV)

        val icalobjects = collections[0].queryICalObjects(null, null)
        assertEquals(2, icalobjects.size)

        // link one of them to the other with PARENT reltype
        val parentRelCV = ContentValues().apply {
            put(JtxContract.JtxRelatedto.ICALOBJECT_ID, childUri?.lastPathSegment)
            put(JtxContract.JtxRelatedto.TEXT, "AAA")
            put(JtxContract.JtxRelatedto.RELTYPE, JtxContract.JtxRelatedto.Reltype.PARENT.name)
        }
        client.insert(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(testAccount), parentRelCV)

        // update related to and check
        collections[0].updateRelatedTo()

        // check child to parent
        client.query(
            JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(testAccount),
            arrayOf(JtxContract.JtxRelatedto.ICALOBJECT_ID, JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID, JtxContract.JtxRelatedto.TEXT, JtxContract.JtxRelatedto.RELTYPE),
            "${JtxContract.JtxRelatedto.ICALOBJECT_ID} = ?",
            arrayOf(childUri?.lastPathSegment),
            null
        ).use {
            assertNotNull(it)
            assertEquals(1, it?.count)
            it?.moveToFirst()
            assertEquals(childUri?.lastPathSegment?.toLong(), it?.getLong(0))   // ICALOBJECT_ID
            assertEquals(parentUri?.lastPathSegment?.toLong(), it?.getLong(1))   // LINKEDICALOBJECT_ID
            assertEquals("AAA", it?.getString(2))   // TEXT (UID)
            assertEquals(JtxContract.JtxRelatedto.Reltype.PARENT.name, it?.getString(3))
        }

        // check parent to child
        client.query(
            JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(testAccount),
            arrayOf(JtxContract.JtxRelatedto.ICALOBJECT_ID, JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID, JtxContract.JtxRelatedto.TEXT, JtxContract.JtxRelatedto.RELTYPE),
            "${JtxContract.JtxRelatedto.ICALOBJECT_ID} = ?",
            arrayOf(parentUri?.lastPathSegment),
            null
        ).use {
            assertNotNull(it)
            assertEquals(1, it?.count)
            it?.moveToFirst()
            assertEquals(parentUri?.lastPathSegment?.toLong(), it?.getLong(0))   // ICALOBJECT_ID
            assertEquals(childUri?.lastPathSegment?.toLong(), it?.getLong(1))   // LINKEDICALOBJECT_ID
            assertEquals("BBB", it?.getString(2))   // TEXT (UID)
            assertEquals(JtxContract.JtxRelatedto.Reltype.CHILD.name, it?.getString(3))
        }
    }


    @Test
    fun updateRelatedTo_check_update_of_linkedId_PARENT_TO_CHILD_is_present() {
        JtxCollection.create(testAccount, client, cv)
        val collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)

        // insert 2 icalobjects
        val parentCV = ContentValues().apply {
            put(JtxContract.JtxICalObject.SUMMARY, "summary")
            put(JtxContract.JtxICalObject.COMPONENT, JtxContract.JtxICalObject.Component.VJOURNAL.name)
            put(JtxContract.JtxICalObject.UID, "AAA")
            put(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collections[0].id)
        }
        val parentUri = client.insert(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), parentCV)
        val childCV = ContentValues().apply {
            put(JtxContract.JtxICalObject.SUMMARY, "summary")
            put(JtxContract.JtxICalObject.COMPONENT, JtxContract.JtxICalObject.Component.VJOURNAL.name)
            put(JtxContract.JtxICalObject.UID, "BBB")
            put(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collections[0].id)
        }
        val childUri = client.insert(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), childCV)

        val icalobjects = collections[0].queryICalObjects(null, null)
        assertEquals(2, icalobjects.size)

        // link one of them to the other with PARENT reltype
        val parent2childRelCV = ContentValues().apply {
            put(JtxContract.JtxRelatedto.ICALOBJECT_ID, parentUri?.lastPathSegment)
            put(JtxContract.JtxRelatedto.TEXT, "BBB")
            put(JtxContract.JtxRelatedto.RELTYPE, JtxContract.JtxRelatedto.Reltype.CHILD.name)
        }
        client.insert(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(testAccount), parent2childRelCV)

        // update related to and check
        collections[0].updateRelatedTo()

        // check child to parent
        client.query(
            JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(testAccount),
            arrayOf(JtxContract.JtxRelatedto.ICALOBJECT_ID, JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID, JtxContract.JtxRelatedto.TEXT, JtxContract.JtxRelatedto.RELTYPE),
            "${JtxContract.JtxRelatedto.ICALOBJECT_ID} = ?",
            arrayOf(parentUri?.lastPathSegment),
            null
        ).use {
            assertNotNull(it)
            assertEquals(1, it?.count)
            it?.moveToFirst()
            assertEquals(parentUri?.lastPathSegment?.toLong(), it?.getLong(0))   // ICALOBJECT_ID
            assertEquals(childUri?.lastPathSegment?.toLong(), it?.getLong(1))   // LINKEDICALOBJECT_ID
            assertEquals("BBB", it?.getString(2))   // TEXT (UID)
            assertEquals(JtxContract.JtxRelatedto.Reltype.CHILD.name, it?.getString(3))
        }

        // check parent to child
        client.query(
            JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(testAccount),
            arrayOf(JtxContract.JtxRelatedto.ICALOBJECT_ID, JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID, JtxContract.JtxRelatedto.TEXT, JtxContract.JtxRelatedto.RELTYPE),
            "${JtxContract.JtxRelatedto.ICALOBJECT_ID} = ?",
            arrayOf(childUri?.lastPathSegment),
            null
        ).use {
            assertNotNull(it)
            assertEquals(1, it?.count)
            it?.moveToFirst()
            assertEquals(childUri?.lastPathSegment?.toLong(), it?.getLong(0))   // ICALOBJECT_ID
            assertEquals(parentUri?.lastPathSegment?.toLong(), it?.getLong(1))   // LINKEDICALOBJECT_ID
            assertEquals("AAA", it?.getString(2))   // TEXT (UID)
            assertEquals(JtxContract.JtxRelatedto.Reltype.PARENT.name, it?.getString(3))
        }
    }
}
