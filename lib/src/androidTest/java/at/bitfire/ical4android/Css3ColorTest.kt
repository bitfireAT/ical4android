/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Css3ColorTest {

    @Test
    fun testColorFromString() {
        // color name
        assertEquals(0xffffff00.toInt(), Css3Color.colorFromString("yellow"))

        // RGB value
        assertEquals(0xffffff00.toInt(), Css3Color.colorFromString("#ffff00"))

        // ARGB value
        assertEquals(0xffffff00.toInt(), Css3Color.colorFromString("#ffffff00"))

        // empty value
        assertNull(Css3Color.colorFromString(""))

        // invalid value
        assertNull(Css3Color.colorFromString("DoesNotExist"))
    }

    @Test
    fun testFromString() {
        // lower case
        assertEquals(0xffffff00.toInt(), Css3Color.fromString("yellow")?.argb)

        // capitalized
        assertEquals(0xffffff00.toInt(), Css3Color.fromString("Yellow")?.argb)

        // not-existing color
        assertNull(Css3Color.fromString("DoesNotExist"))
    }

    @Test
    fun testNearestMatch() {
        // every color is its own nearest match
        Css3Color.values().forEach {
            assertEquals(it.argb, Css3Color.nearestMatch(it.argb).argb)
        }
    }

}