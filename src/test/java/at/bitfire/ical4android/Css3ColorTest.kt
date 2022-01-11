/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import org.junit.Assert.assertEquals
import org.junit.Test

class Css3ColorTest {

    @Test
    fun testNearestMatch() {
        // every color is its own nearest match
        Css3Color.values().forEach {
            assertEquals(it.argb, Css3Color.nearestMatch(it.argb).argb)
        }
    }

}