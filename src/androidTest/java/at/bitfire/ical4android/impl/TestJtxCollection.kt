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
import at.bitfire.jtx.SyncContentProviderContract
import at.bitfire.jtx.SyncContentProviderContract.asSyncAdapter

class TestJtxCollection(
        account: Account,
        provider: ContentProviderClient,
        id: Long
): JtxCollection<JtxICalObject>(account, provider, TestJtxIcalObject.Factory, id) {

    companion object {

        fun create(account: Account, provider: ContentProviderClient): TestJtxCollection {

            var uri = SyncContentProviderContract.JtxCollection.CONTENT_URI.asSyncAdapter(account)
            uri = uri.buildUpon().appendPath("1").build()

            return TestJtxCollection(account, provider, ContentUris.parseId(uri))
        }

    }


    object Factory: JtxCollectionFactory<TestJtxCollection> {

        override fun newInstance(
            account: Account,
            client: ContentProviderClient,
            id: Long
        ): TestJtxCollection = TestJtxCollection(account, client, id)
    }

}
