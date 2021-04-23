package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import at.bitfire.notesx5.NotesX5Contract.X5ICalObject
import at.bitfire.notesx5.NotesX5Contract.asSyncAdapter
import java.util.*

open class Notesx5ICalObject(val account: Account, val client: ContentProviderClient, collectionId: Long, component: X5ICalObject.Component) {

            var id: Long = 0L
            var summary: String? = null
            var description: String? = null
            var dtstart: Long? = null
            var dtstartTimezone: String? = null
            var dtend: Long? = null
            var dtendTimezone: String? = null

            var classification: X5ICalObject.Classification = X5ICalObject.Classification.PUBLIC    // 0 = PUBLIC, 1 = PRIVATE, 2 = CONFIDENTIAL, -1 = NOT SUPPORTED (value in classificationX)

            var url: String? = null
            var contact: String? = null
            var geoLat: Float? = null
            var geoLong: Float? = null
            var location: String? = null

            var uid: String = "${System.currentTimeMillis()}-${UUID.randomUUID()}@at.bitfire.notesx5"                              //unique identifier, see https://tools.ietf.org/html/rfc5545#section-3.8.4.7

            var created: Long = System.currentTimeMillis()   // see https://tools.ietf.org/html/rfc5545#section-3.8.7.1
            var dtstamp: Long = System.currentTimeMillis()   // see https://tools.ietf.org/html/rfc5545#section-3.8.7.2
            var lastModified: Long = System.currentTimeMillis() // see https://tools.ietf.org/html/rfc5545#section-3.8.7.3
            var sequence: Long = 0                             // increase on every change (+1), see https://tools.ietf.org/html/rfc5545#section-3.8.7.4

            var color: Int? = null
            var other: String? = null

            //var collectionId: Long = 1L,

            var dirty: Boolean = true
            var deleted: Boolean = false



    fun delete() {
        client.delete(ContentUris.withAppendedId(X5ICalObject.CONTENT_URI.asSyncAdapter(account), requireNotNull(id)), null, null)
    }

    protected fun populate(values: ContentValues) {
        id = values.getAsLong(X5ICalObject.ID)
        //url = values.getAsString(X5Collection.URL)
    }

}

class Notesx5Todo(account: Account, client: ContentProviderClient, collectionId: Long)
    : Notesx5ICalObject(account, client, collectionId, X5ICalObject.Component.TODO)
{

    var status: X5ICalObject.StatusTodo? = null

    var percent: Int? = null    // VTODO only!
    var priority: Int? = null   // VTODO and VEVENT
    var due: Long? = null      // VTODO only!
    var dueTimezone: String? = null //VTODO only!
    var completed: Long? = null // VTODO only!
    var completedTimezone: String? = null //VTODO only!
    var duration: String? = null //VTODO only!

}

class Notesx5Journal(account: Account, client: ContentProviderClient, collectionId: Long)
    : Notesx5ICalObject(account, client, collectionId, X5ICalObject.Component.JOURNAL)
{

    var status: X5ICalObject.StatusJournal? = null
}
