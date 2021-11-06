/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android.impl

import android.content.ContentValues
import at.bitfire.ical4android.*

class TestJtxIcalObject: JtxICalObject {

    constructor(testCollection: JtxCollection<JtxICalObject>, values: ContentValues)
        : super(testCollection)


    object Factory: JtxICalObjectFactory<JtxICalObject> {

        override fun fromProvider(
            collection: JtxCollection<JtxICalObject>,
            values: ContentValues
        ): JtxICalObject = TestJtxIcalObject(collection, values)
    }

}
