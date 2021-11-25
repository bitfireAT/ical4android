package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.impl.TestJtxCollection
import at.bitfire.jtx.JtxContract
import at.bitfire.jtx.JtxContract.asSyncAdapter
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test

class JtxCollectionTest {

    private val testAccount = Account("TEST", JtxContract.JtxCollection.TEST_ACCOUNT_TYPE)
    private lateinit var contentResolver: ContentResolver
    private lateinit var client: ContentProviderClient
    var collection: TestJtxCollection? = null
    lateinit var context: Context

    val url = "https://jtx.techbee.at"
    val displayname = "jtx"
    val description = "jtx Collection Test"
    val syncversion = JtxContract.CONTRACT_VERSION

    val cv = ContentValues().apply {
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

        var collections = JtxCollection.find(testAccount, client, TestJtxCollection.Factory, null, null)
        collections.forEach { collection ->
            collection.delete()
        }
        collections = JtxCollection.find(testAccount, client, TestJtxCollection.Factory, null, null)
        assertEquals(0, collections.size)
    }

    @Test
    fun create_populate_find() {

        val collectionUri = JtxCollection.create(testAccount, client, cv)
        assertNotNull(collectionUri)

        val collections = JtxCollection.find(testAccount, client, TestJtxCollection.Factory, null, null)
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

        val collections = JtxCollection.find(testAccount, client, TestJtxCollection.Factory, null, null)
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
}
