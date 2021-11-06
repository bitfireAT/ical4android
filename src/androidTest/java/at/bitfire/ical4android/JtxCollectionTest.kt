package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentValues
import android.test.mock.MockContentProvider
import android.test.mock.MockContentResolver
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.impl.TestJtxCollection
import at.bitfire.jtx.SyncContentProviderContract
import junit.framework.TestCase.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test

class JtxCollectionTest {

    private val testAccount = Account("test", "test")
    private lateinit var mContentResolver: MockContentResolver

    @Before
    fun setUp() {
        mContentResolver = MockContentResolver()

    }

    @After
    fun tearDown() {
    }

    @Test
    fun delete() {
    }

    @Test
    fun populate() {
    }

    @Test
    fun queryICalObjects() {


        val context = InstrumentationRegistry.getInstrumentation().targetContext
        //val provider = context.contentResolver.acquireContentProviderClient(SyncContentProviderContract.AUTHORITY)



        //val mContentResolver = MockContentResolver()
        val mContentProvider = MockContentProvider(context)
        mContentResolver.addProvider(SyncContentProviderContract.AUTHORITY, mContentProvider)
        val provider = mContentResolver.acquireContentProviderClient(SyncContentProviderContract.AUTHORITY)

        //val provider = mContentResolver.acquireContentProviderClient(SyncContentProviderContract.AUTHORITY)
        assertNotNull(provider)

        val collectionValues = ContentValues().apply {
            put(SyncContentProviderContract.JtxCollection.ACCOUNT_NAME, testAccount.name)
            put(SyncContentProviderContract.JtxCollection.ACCOUNT_TYPE, testAccount.type)
            put(SyncContentProviderContract.JtxCollection.DESCRIPTION, "Test")
            put(SyncContentProviderContract.JtxCollection.DISPLAYNAME, "Testcollection")
        }

        val collection = TestJtxCollection.create(testAccount, provider!!)

        //TODO Continue here

        //collection.queryICalObjects()
    }

}
