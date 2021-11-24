package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import at.bitfire.ical4android.MiscUtils.CursorHelper.toValues
import at.bitfire.jtx.SyncContentProviderContract
import at.bitfire.jtx.SyncContentProviderContract.asSyncAdapter
import java.util.*

open class JtxCollection<out T: JtxICalObject>(val account: Account,
                                               val client: ContentProviderClient,
                                               private val iCalObjectFactory: JtxICalObjectFactory<JtxICalObject>,
                                               val id: Long) {

    companion object {

        fun create(account: Account, client: ContentProviderClient, values: ContentValues): Uri =
            client.insert(SyncContentProviderContract.JtxCollection.CONTENT_URI.asSyncAdapter(account), values)?: throw CalendarStorageException("Couldn't create JTX Collection")


        fun<T: JtxCollection<JtxICalObject>> find(account: Account, client: ContentProviderClient, factory: JtxCollectionFactory<T>, where: String?, whereArgs: Array<String>?): List<T> {
            val collections = LinkedList<T>()
            client.query(SyncContentProviderContract.JtxCollection.CONTENT_URI.asSyncAdapter(account), null, where, whereArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val values = cursor.toValues()
                    val collection = factory.newInstance(account, client, values.getAsLong(SyncContentProviderContract.JtxCollection.ID))
                    collection.populate(values)
                    collections += collection
                }
            }
            return collections
        }
    }


    var url: String? = null
    var displayname: String? = null
    var syncstate: String? = null


    fun delete() {
        client.delete(ContentUris.withAppendedId(SyncContentProviderContract.JtxCollection.CONTENT_URI.asSyncAdapter(account), id), null, null)
    }

    protected fun populate(values: ContentValues) {
        url = values.getAsString(SyncContentProviderContract.JtxCollection.URL)
        displayname = values.getAsString(SyncContentProviderContract.JtxCollection.DISPLAYNAME)
        syncstate = values.getAsString(SyncContentProviderContract.JtxCollection.SYNC_VERSION)
    }


    /**
     * Builds the JtxICalObject content uri with appended parameters for account and syncadapter
     * @return the Uri for the JtxICalObject Sync in the content provider of jtx Board
     */
    fun jtxSyncURI(): Uri =
        SyncContentProviderContract.JtxICalObject.CONTENT_URI.buildUpon()
            .appendQueryParameter(SyncContentProviderContract.ACCOUNT_NAME, account.name)
            .appendQueryParameter(SyncContentProviderContract.ACCOUNT_TYPE, account.type)
            .appendQueryParameter(SyncContentProviderContract.CALLER_IS_SYNCADAPTER, "true")
            .build()
}