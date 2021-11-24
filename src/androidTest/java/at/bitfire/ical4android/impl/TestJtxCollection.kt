/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android.impl

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import at.bitfire.ical4android.*
import at.bitfire.ical4android.MiscUtils.CursorHelper.toValues
import at.bitfire.jtx.SyncContentProviderContract
import at.bitfire.jtx.SyncContentProviderContract.asSyncAdapter
import java.util.*

class TestJtxCollection(
        account: Account,
        provider: ContentProviderClient,
        id: Long
): JtxCollection<JtxICalObject>(account, provider, TestJtxIcalObject.Factory, id) {

    companion object {

        fun createMock(account: Account, provider: ContentProviderClient): TestJtxCollection {

            var uri = SyncContentProviderContract.JtxCollection.CONTENT_URI.asSyncAdapter(account)
            uri = uri.buildUpon().appendPath("1").build()

            return TestJtxCollection(account, provider, ContentUris.parseId(uri))
        }

    }


    /**
     * Queries [SyncContentProviderContract.JtxICalObject] from this collection. Adds a WHERE clause that restricts the
     * query to [SyncContentProviderContract.JtxCollection.ID] = [id].
     * @param _where selection
     * @param _whereArgs arguments for selection
     * @return events from this calendar which match the selection
     */
    fun queryICalObjects(_where: String? = null, _whereArgs: Array<String>? = null): List<JtxICalObject> {
        val where = "(${_where ?: "1"}) AND " + SyncContentProviderContract.JtxICalObject.ICALOBJECT_COLLECTIONID + "= ?"
        val whereArgs = (_whereArgs ?: arrayOf()) + id.toString()

        val iCalObjects = LinkedList<JtxICalObject>()
        client.query(jtxSyncURI(), null, where, whereArgs, null)?.use { cursor ->
            while (cursor.moveToNext())
                iCalObjects += TestJtxIcalObject.Factory.fromProvider(this, cursor.toValues())
        }
        return iCalObjects
    }


    object Factory: JtxCollectionFactory<TestJtxCollection> {

        override fun newInstance(
            account: Account,
            client: ContentProviderClient,
            id: Long
        ): TestJtxCollection = TestJtxCollection(account, client, id)
    }

}
