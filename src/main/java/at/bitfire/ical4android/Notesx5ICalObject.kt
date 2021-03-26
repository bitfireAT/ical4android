package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import at.bitfire.ical4android.MiscUtils.CursorHelper.toValues
import at.bitfire.notesx5.NotesX5Contract.X5ICalObject
import at.bitfire.notesx5.NotesX5Contract.asSyncAdapter
import java.util.*

// TODO Ricki please check :-)
open class Notesx5ICalObject(val account: Account, val client: ContentProviderClient) {

    companion object {

        fun<T: Notesx5ICalObject> find(account: Account, client: ContentProviderClient, factory: Notesx5ICalObjectFactory<T>, where: String?, whereArgs: Array<String>?): List<T> {
            val iCalObjects = LinkedList<T>()
            client.query(X5ICalObject.CONTENT_URI.asSyncAdapter(account), null, where, whereArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val values = cursor.toValues()
                    val iCalObject = factory.newInstance(account, client, values.getAsLong(X5ICalObject.ID))
                    iCalObject.populate(values)
                    iCalObjects += iCalObject
                }
            }
            return iCalObjects
        }

    }


    var id: Long? = null
    //var url: String? = null


    fun delete() {
        client.delete(ContentUris.withAppendedId(X5ICalObject.CONTENT_URI.asSyncAdapter(account), requireNotNull(id)), null, null)
    }

    protected fun populate(values: ContentValues) {
        id = values.getAsLong(X5ICalObject.ID)
        //url = values.getAsString(X5Collection.URL)
    }

}