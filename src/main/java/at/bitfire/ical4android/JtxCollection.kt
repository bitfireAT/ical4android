package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import at.bitfire.ical4android.AndroidCalendar.Companion.syncAdapterURI
import at.bitfire.ical4android.MiscUtils.CursorHelper.toValues
import at.bitfire.jtx.SyncContentProviderContract
import at.bitfire.jtx.SyncContentProviderContract.asSyncAdapter
import java.util.*

open class JtxCollection<out T: JtxICalObject>(val account: Account,
                                               val client: ContentProviderClient,
                                               val iCalObjectFactory: JtxICalObjectFactory<JtxICalObject>,
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
        client.delete(ContentUris.withAppendedId(SyncContentProviderContract.JtxCollection.CONTENT_URI.asSyncAdapter(account), requireNotNull(id)), null, null)
    }

    protected fun populate(values: ContentValues) {
        url = values.getAsString(SyncContentProviderContract.JtxCollection.URL)
        displayname = values.getAsString(SyncContentProviderContract.JtxCollection.DISPLAYNAME)
        syncstate = values.getAsString(SyncContentProviderContract.JtxCollection.SYNC_VERSION)
    }


    /**
     * Queries [SyncContentProviderContract.JtxICalObject] from this collection. Adds a WHERE clause that restricts the
     * query to [SyncContentProviderContract.JtxCollection.ID] = [id].
     * @param _where selection
     * @param _whereArgs arguments for selection
     * @return events from this calendar which match the selection
     */
    fun queryICalObjects(_where: String? = null, _whereArgs: Array<String>? = null): List<JtxICalObject> {
        val where = "(${_where ?: "1"}) AND " + SyncContentProviderContract.JtxCollection.ID + "=?"
        val whereArgs = (_whereArgs ?: arrayOf()) + id.toString()

        val iCalObjects = LinkedList<JtxICalObject>()
        client.query(eventsSyncURI(), null, where, whereArgs, null)?.use { cursor ->
            while (cursor.moveToNext())
                iCalObjects += iCalObjectFactory.fromProvider(this, cursor.toValues())
        }
        return iCalObjects
    }



    fun eventsSyncURI() = syncAdapterURI(SyncContentProviderContract.JtxICalObject.CONTENT_URI, account)



}