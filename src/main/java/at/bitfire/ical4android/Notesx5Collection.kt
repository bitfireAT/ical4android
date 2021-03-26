package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import at.bitfire.ical4android.MiscUtils.CursorHelper.toValues
import at.bitfire.notesx5.NotesX5Contract
import at.bitfire.notesx5.NotesX5Contract.X5Collection
import at.bitfire.notesx5.NotesX5Contract.asSyncAdapter
import org.dmfs.tasks.contract.TaskContract
import java.util.*

open class Notesx5Collection(val account: Account, val client: ContentProviderClient) {

    companion object {

        fun<T: Notesx5Collection> find(account: Account, client: ContentProviderClient, factory: Notesx5CollectionFactory<T>, where: String?, whereArgs: Array<String>?): List<T> {
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


    var id: Long? = null
    var url: String? = null


    fun delete() {
        client.delete(ContentUris.withAppendedId(X5Collection.CONTENT_URI.asSyncAdapter(account), requireNotNull(id)), null, null)
    }

    protected fun populate(values: ContentValues) {
        id = values.getAsLong(X5Collection.ID)
        url = values.getAsString(X5Collection.URL)
    }

}