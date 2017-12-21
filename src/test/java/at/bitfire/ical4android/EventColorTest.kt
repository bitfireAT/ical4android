/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import org.junit.Assert.assertEquals
import org.junit.Test

class EventColorTest {

    @Test
    fun testNearestMatch() {
        // every color is its own nearest match
        EventColor.values().forEach {
            assertEquals(it.rgba, EventColor.nearestMatch(it.rgba).rgba)
        }
    }

}