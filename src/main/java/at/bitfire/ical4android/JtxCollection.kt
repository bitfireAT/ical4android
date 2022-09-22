/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import at.bitfire.ical4android.util.MiscUtils.CursorHelper.toValues
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.asSyncAdapter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Version
import java.util.*

open class JtxCollection<out T: JtxICalObject>(val account: Account,
                                               val client: ContentProviderClient,
                                               private val iCalObjectFactory: JtxICalObjectFactory<JtxICalObject>,
                                               val id: Long) {

    companion object {

        fun create(account: Account, client: ContentProviderClient, values: ContentValues): Uri =
            client.insert(JtxContract.JtxCollection.CONTENT_URI.asSyncAdapter(account), values)?: throw CalendarStorageException("Couldn't create JTX Collection")

        fun<T: JtxCollection<JtxICalObject>> find(account: Account, client: ContentProviderClient, context: Context, factory: JtxCollectionFactory<T>, where: String?, whereArgs: Array<String>?): List<T> {
            val collections = LinkedList<T>()
            client.query(JtxContract.JtxCollection.CONTENT_URI.asSyncAdapter(account), null, where, whereArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val values = cursor.toValues()
                    val collection = factory.newInstance(account, client, values.getAsLong(JtxContract.JtxCollection.ID))
                    collection.populate(values, context)
                    collections += collection
                }
            }
            return collections
        }
    }


    var url: String? = null
    var displayname: String? = null
    var syncstate: String? = null

    var supportsVEVENT = true
    var supportsVTODO = true
    var supportsVJOURNAL = true

    var context: Context? = null


    fun delete() {
        client.delete(ContentUris.withAppendedId(JtxContract.JtxCollection.CONTENT_URI.asSyncAdapter(account), id), null, null)
    }

    fun update(values: ContentValues) {
        client.update(ContentUris.withAppendedId(JtxContract.JtxCollection.CONTENT_URI.asSyncAdapter(account), id), values, null, null)
    }

    protected fun populate(values: ContentValues, context: Context) {
        url = values.getAsString(JtxContract.JtxCollection.URL)
        displayname = values.getAsString(JtxContract.JtxCollection.DISPLAYNAME)
        syncstate = values.getAsString(JtxContract.JtxCollection.SYNC_VERSION)

        supportsVEVENT = values.getAsString(JtxContract.JtxCollection.SUPPORTSVEVENT) == "1"
                || values.getAsString(JtxContract.JtxCollection.SUPPORTSVEVENT) == "true"
        supportsVTODO = values.getAsString(JtxContract.JtxCollection.SUPPORTSVTODO) == "1"
                || values.getAsString(JtxContract.JtxCollection.SUPPORTSVTODO) == "true"
        supportsVJOURNAL = values.getAsString(JtxContract.JtxCollection.SUPPORTSVJOURNAL) == "1"
                || values.getAsString(JtxContract.JtxCollection.SUPPORTSVJOURNAL) == "true"

        this.context = context
    }


    /**
     * Builds the JtxICalObject content uri with appended parameters for account and syncadapter
     * @return the Uri for the JtxICalObject Sync in the content provider of jtx Board
     */
    fun jtxSyncURI(): Uri =
        JtxContract.JtxICalObject.CONTENT_URI.buildUpon()
            .appendQueryParameter(JtxContract.ACCOUNT_NAME, account.name)
            .appendQueryParameter(JtxContract.ACCOUNT_TYPE, account.type)
            .appendQueryParameter(JtxContract.CALLER_IS_SYNCADAPTER, "true")
            .build()


    /**
     * @return a list of content values of the deleted jtxICalObjects
     */
    fun queryDeletedICalObjects(): List<ContentValues> {
        val values = mutableListOf<ContentValues>()
        client.query(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account), null, "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID} = ? AND ${JtxContract.JtxICalObject.DELETED} = ?", arrayOf(id.toString(), "1"), null).use { cursor ->
            Ical4Android.log.fine("findDeleted: found ${cursor?.count} deleted records in ${account.name}")
            while (cursor?.moveToNext() == true) {
                values.add(cursor.toValues())
            }
        }
        return values
    }


    /**
     * @return a list of content values of the dirty jtxICalObjects
     */
    fun queryDirtyICalObjects(): List<ContentValues> {
        val values = mutableListOf<ContentValues>()
        client.query(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account), null, "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID} = ? AND ${JtxContract.JtxICalObject.DIRTY} = ?", arrayOf(id.toString(), "1"), null).use { cursor ->
            Ical4Android.log.fine("findDirty: found ${cursor?.count} dirty records in ${account.name}")
            while (cursor?.moveToNext() == true) {
                values.add(cursor.toValues())
            }
        }
        return values
    }

    /**
     * @param [filename] of the entry that should be retrieved as content values
     * @return Content Values of the found item with the given filename or null if the result was empty or more than 1
     */
    fun queryByFilename(filename: String): ContentValues? {
        client.query(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account), null, "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID} = ? AND ${JtxContract.JtxICalObject.FILENAME} = ?", arrayOf(id.toString(), filename), null).use { cursor ->
            Ical4Android.log.fine("queryByFilename: found ${cursor?.count} records in ${account.name}")
            if (cursor?.count != 1)
                return null
            cursor.moveToFirst()
            return cursor.toValues()
        }
    }

    /**
     * @param [uid] of the entry that should be retrieved as content values
     * @return Content Values of the found item with the given UID or null if the result was empty or more than 1
     * The query checks for the [uid] within all collections of this account, not only the current collection.
     */
    fun queryByUID(uid: String): ContentValues? {
        client.query(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account), null, "${JtxContract.JtxICalObject.UID} = ?", arrayOf(uid), null).use { cursor ->
            Ical4Android.log.fine("queryByUID: found ${cursor?.count} records in ${account.name}")
            if (cursor?.count != 1)
                return null
            cursor.moveToFirst()
            return cursor.toValues()
        }
    }

    /**
     * updates the flags of all entries in the collection with the given flag
     * @param [flags] to be set
     * @return the number of records that were updated
     */
    fun updateSetFlags(flags: Int): Int {
        val values = ContentValues(1)
        values.put(JtxContract.JtxICalObject.FLAGS, flags)
        return client.update(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account), values, "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID} = ? AND ${JtxContract.JtxICalObject.DIRTY} = ?", arrayOf(id.toString(), "0"))
    }

    /**
     * deletes all entries with the given flags
     * @param [flags] of the entries that should be deleted
     * @return the number of deleted records
     */
    fun deleteByFlags(flags: Int) =
        client.delete(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account), "${JtxContract.JtxICalObject.DIRTY} = ? AND ${JtxContract.JtxICalObject.FLAGS} = ? ", arrayOf("0", flags.toString()))

    /**
     * Updates the eTag value of all entries within a collection to the given eTag
     * @param [eTag] to be set (or null)
     */
    fun updateSetETag(eTag: String?) {
        val values = ContentValues(1)
        if(eTag == null)
            values.putNull(JtxContract.JtxICalObject.ETAG)
        else
            values.put(JtxContract.JtxICalObject.ETAG, eTag)
        client.update(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account), values, "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID} = ?", arrayOf(id.toString()))
    }


    /**
     * This function updates the Related-To relations in jtx Board.
     * STEP 1: find entries to update (all entries with 0 in related-to). When inserting the relation, we only know the parent iCalObjectId and the related UID (but not the related iCalObjectId).
     *         In this step we search for all Related-To relations where the LINKEDICALOBJEC_ID is not set, resolve it through the UID and set it.
     * STEP 2/3: jtx Board saves the relations in both directions, the Parent has an entry for his Child, the Child has an entry for his Parent. Step 2 and Step 3 make sure, that the Child-Parent pair is
     *         present in both directions.
     */
    @Deprecated("Moved to jtx Board content provider (function updateRelatedTo()). This function here will be deleted in one of the next versions.")
    fun updateRelatedTo() {
        // STEP 1: first find entries to update (all entries with 0 in related-to)
        client.query(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(account), arrayOf(JtxContract.JtxRelatedto.TEXT), "${JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID} = ?", arrayOf("0"), null).use {
            while(it?.moveToNext() == true) {
                val uid2upddate = it.getString(0)

                client.query(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account), arrayOf(JtxContract.JtxICalObject.ID), "${JtxContract.JtxICalObject.UID} = ?", arrayOf(uid2upddate), null).use { idOfthisUidCursor ->
                    if (idOfthisUidCursor?.moveToFirst() == true) {
                        val idOfthisUid = idOfthisUidCursor.getLong(0)

                        val updateContentValues = ContentValues()
                        updateContentValues.put(JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID, idOfthisUid)
                        client.update(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(account), updateContentValues,"${JtxContract.JtxRelatedto.TEXT} = ? AND ${JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID} = ?", arrayOf(uid2upddate, "0")
                        )
                    }
                }
            }
        }


        // STEP 2: query all related to that are linking their PARENTS and check if they also have the opposite relationship entered, if not, then add it
        client.query(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(account), arrayOf(JtxContract.JtxRelatedto.ICALOBJECT_ID, JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID, JtxContract.JtxRelatedto.RELTYPE), "${JtxContract.JtxRelatedto.RELTYPE} = ?", arrayOf(JtxContract.JtxRelatedto.Reltype.PARENT.name), null).use {
                cursorAllLinkedParents ->
            while (cursorAllLinkedParents?.moveToNext() == true) {
                val childId = cursorAllLinkedParents.getString(0)
                val parentId = cursorAllLinkedParents.getString(1)

                client.query(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(account), arrayOf(JtxContract.JtxRelatedto.ICALOBJECT_ID, JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID, JtxContract.JtxRelatedto.RELTYPE), "${JtxContract.JtxRelatedto.ICALOBJECT_ID} = ? AND ${JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID} = ? AND ${JtxContract.JtxRelatedto.RELTYPE} = ?", arrayOf(parentId.toString(), childId.toString(), JtxContract.JtxRelatedto.Reltype.CHILD.name), null).use {  cursor ->
                    // if the query does not bring any result, then we insert the opposite relationship
                    if (cursor?.moveToFirst() == false) {
                        //get the UID of the linked entry
                        client.query(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account), arrayOf(JtxContract.JtxICalObject.UID), "${JtxContract.JtxICalObject.ID} = ?", arrayOf(childId.toString()), null).use {
                                foundIcalObjectCursor ->

                            if (foundIcalObjectCursor?.moveToFirst() == true) {
                                val childUID = foundIcalObjectCursor.getString(0)
                                val cv = ContentValues().apply {
                                    put(JtxContract.JtxRelatedto.ICALOBJECT_ID, parentId)
                                    put(JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID, childId)
                                    put(JtxContract.JtxRelatedto.RELTYPE, JtxContract.JtxRelatedto.Reltype.CHILD.name)
                                    put(JtxContract.JtxRelatedto.TEXT, childUID)
                                }
                                client.insert(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(account), cv)
                            }
                        }
                    }
                }
            }
        }


        // STEP 3: query all related to that are linking their CHILD and check if they also have the opposite relationship entered, if not, then add it
        client.query(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(account), arrayOf(JtxContract.JtxRelatedto.ICALOBJECT_ID, JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID, JtxContract.JtxRelatedto.RELTYPE), "${JtxContract.JtxRelatedto.RELTYPE} = ?", arrayOf(JtxContract.JtxRelatedto.Reltype.CHILD.name), null).use {
                cursorAllLinkedParents ->
            while (cursorAllLinkedParents?.moveToNext() == true) {

                val parentId = cursorAllLinkedParents.getLong(0)
                val childId = cursorAllLinkedParents.getLong(1)

                client.query(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(account), arrayOf(JtxContract.JtxRelatedto.ICALOBJECT_ID, JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID, JtxContract.JtxRelatedto.RELTYPE), "${JtxContract.JtxRelatedto.ICALOBJECT_ID} = ? AND ${JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID} = ? AND ${JtxContract.JtxRelatedto.RELTYPE} = ?", arrayOf(childId.toString(), parentId.toString(), JtxContract.JtxRelatedto.Reltype.PARENT.name), null).use {
                        cursor ->

                    // if the query does not bring any result, then we insert the opposite relationship
                    if (cursor?.moveToFirst() == false) {

                        //get the UID of the linked entry
                        client.query(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account), arrayOf(JtxContract.JtxICalObject.UID), "${JtxContract.JtxICalObject.ID} = ?", arrayOf(parentId.toString()), null).use {
                                foundIcalObjectCursor ->

                            if(foundIcalObjectCursor?.moveToFirst() == true) {
                                val parentUID = foundIcalObjectCursor.getString(0)
                                val cv = ContentValues().apply {
                                    put(JtxContract.JtxRelatedto.ICALOBJECT_ID, childId)
                                    put(JtxContract.JtxRelatedto.LINKEDICALOBJECT_ID, parentId)
                                    put(JtxContract.JtxRelatedto.RELTYPE, JtxContract.JtxRelatedto.Reltype.PARENT.name)
                                    put(JtxContract.JtxRelatedto.TEXT, parentUID)
                                }
                                client.insert(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(account), cv)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * @return a string with all JtxICalObjects within the collection as iCalendar
     */
    fun getICSForCollection(): String {
        client.query(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account), null, "${JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID} = ? AND ${JtxContract.JtxICalObject.DELETED} = ?", arrayOf(id.toString(), "0"), null).use { cursor ->
            Ical4Android.log.fine("getICSForCollection: found ${cursor?.count} records in ${account.name}")

            val ical = Calendar()
            ical.properties += Version.VERSION_2_0
            ical.properties += ICalendar.prodId

            while (cursor?.moveToNext() == true) {
                val jtxIcalObject = JtxICalObject(this)
                jtxIcalObject.populateFromContentValues(cursor.toValues())
                val singleICS = jtxIcalObject.getICalendarFormat()
                singleICS?.components?.forEach { component ->
                    if(component is VToDo || component is VJournal)
                        ical.components += component
                }
            }
            return ical.toString()
        }
    }
}