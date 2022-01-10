/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.impl

import android.content.ContentValues
import at.bitfire.ical4android.*

class TestJtxIcalObject(testCollection: JtxCollection<JtxICalObject>): JtxICalObject(testCollection) {

    object Factory: JtxICalObjectFactory<JtxICalObject> {

        override fun fromProvider(
            collection: JtxCollection<JtxICalObject>,
            values: ContentValues
        ): JtxICalObject = TestJtxIcalObject(collection)
    }
}
