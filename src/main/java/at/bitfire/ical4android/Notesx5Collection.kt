package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.AndroidCalendar.Companion.syncAdapterURI
import at.bitfire.ical4android.MiscUtils.CursorHelper.toValues
import at.bitfire.notesx5.NotesX5Contract
import at.bitfire.notesx5.NotesX5Contract.X5Collection
import at.bitfire.notesx5.NotesX5Contract.asSyncAdapter
import java.util.*

open class Notesx5Collection<out T: Notesx5ICalObject>(val account: Account,
                             val client: ContentProviderClient,
                             val iCalObjectFactory: Notesx5ICalObjectFactory<Notesx5ICalObject>,
                             val id: Long) {


    companion object {

        fun create(account: Account, client: ContentProviderClient, values: ContentValues): Uri =
            client.insert(X5Collection.CONTENT_URI.asSyncAdapter(account), values)?: throw CalendarStorageException("Couldn't create NotesX5 Collection")


        fun<T: Notesx5Collection<Notesx5ICalObject>> find(account: Account, client: ContentProviderClient, factory: Notesx5CollectionFactory<T>, where: String?, whereArgs: Array<String>?): List<T> {
            val collections = LinkedList<T>()
            client.query(X5Collection.CONTENT_URI.asSyncAdapter(account), null, where, whereArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val values = cursor.toValues()
                    val collection = factory.newInstance(account, client, values.getAsLong(X5Collection.ID))
                    collection.populate(values)
                    collections += collection
                }
            }
            return collections
        }

    }


    var url: String? = null


    fun delete() {
        client.delete(ContentUris.withAppendedId(X5Collection.CONTENT_URI.asSyncAdapter(account), requireNotNull(id)), null, null)
    }

    protected fun populate(values: ContentValues) {
        url = values.getAsString(X5Collection.URL)
    }


    /**
     * Queries [Notesx5ICalObject] from this collection. Adds a WHERE clause that restricts the
     * query to [X5Collection.ID] = [id].
     * @param _where selection
     * @param _whereArgs arguments for selection
     * @return events from this calendar which match the selection
     */
    fun queryICalObjects(_where: String? = null, _whereArgs: Array<String>? = null): List<Notesx5ICalObject> {
        val where = "(${_where ?: "1"}) AND " + X5Collection.ID + "=?"
        val whereArgs = (_whereArgs ?: arrayOf()) + id.toString()

        val iCalObjects = LinkedList<Notesx5ICalObject>()
        client.query(eventsSyncURI(), null, where, whereArgs, null)?.use { cursor ->
            while (cursor.moveToNext())
                iCalObjects += iCalObjectFactory.fromProvider(this, cursor.toValues())
        }
        return iCalObjects
    }



    fun eventsSyncURI() = syncAdapterURI(NotesX5Contract.X5ICalObject.CONTENT_URI, account)



}