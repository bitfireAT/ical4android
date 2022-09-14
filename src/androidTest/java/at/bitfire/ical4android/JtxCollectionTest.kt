/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.impl.TestJtxCollection
import at.bitfire.ical4android.util.MiscUtils.ContentProviderClientHelper.closeCompat
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.asSyncAdapter
import junit.framework.TestCase.*
import org.junit.*

class JtxCollectionTest {

    companion object {

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val contentResolver = context.contentResolver

        private lateinit var client: ContentProviderClient

        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(*TaskProvider.PERMISSIONS_JTX)

        @BeforeClass
        @JvmStatic
        fun openProvider() {
            val clientOrNull = contentResolver.acquireContentProviderClient(JtxContract.AUTHORITY)
            Assume.assumeNotNull(clientOrNull)

            client = clientOrNull!!
        }

        @AfterClass
        @JvmStatic
        fun closeProvider() {
            client.closeCompat()
        }

    }

    private val testAccount = Account("TEST", JtxContract.JtxCollection.TEST_ACCOUNT_TYPE)

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

    @After
    fun tearDown() {
        var collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)
        collections.forEach { collection ->
            collection.delete()
        }
        collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)
        assertEquals(0, collections.size)
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
    fun getICSForCollection_test() {
        val collectionUri = JtxCollection.create(testAccount, client, cv)
        assertNotNull(collectionUri)

        val collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)
        val items = collections[0].queryICalObjects(null, null)
        assertEquals(0, items.size)

        val cv1 = ContentValues().apply {
            put(JtxContract.JtxICalObject.SUMMARY, "summary")
            put(JtxContract.JtxICalObject.COMPONENT, JtxContract.JtxICalObject.Component.VJOURNAL.name)
            put(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collections[0].id)
        }
        val cv2 = ContentValues().apply {
            put(JtxContract.JtxICalObject.SUMMARY, "entry2")
            put(JtxContract.JtxICalObject.COMPONENT, JtxContract.JtxICalObject.Component.VTODO.name)
            put(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collections[0].id)
        }
        client.insert(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv1)
        client.insert(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv2)

        val ics = collections[0].getICSForCollection()
        assertTrue(ics.contains(Regex("BEGIN:VCALENDAR(\\n*|\\r*|\\t*|.*)*END:VCALENDAR")))
        assertTrue(ics.contains("PRODID:+//IDN bitfire.at//ical4android"))
        assertTrue(ics.contains("SUMMARY:summary"))
        assertTrue(ics.contains("SUMMARY:entry2"))
        assertTrue(ics.contains(Regex("BEGIN:VJOURNAL(\\n*|\\r*|\\t*|.*)*END:VJOURNAL")))
        assertTrue(ics.contains(Regex("BEGIN:VTODO(\\n*|\\r*|\\t*|.*)*END:VTODO")))
    }
}
