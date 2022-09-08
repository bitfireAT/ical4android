/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import at.bitfire.ical4android.util.MiscUtils
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscUtilsTest {

    @Test
    fun testReflectionToString() {
        val s = MiscUtils.reflectionToString(MiscUtilsTest.TestClass())
        assertTrue(s.startsWith("TestClass=["))
        assertTrue(s.contains("i=2"))
        assertTrue(s.contains("large=null"))
        assertTrue(s.contains("s=test"))
    }

    @Test
    fun testReflectionToString_OOM() {
        val t = MiscUtilsTest.TestClass()
        t.large = object: Any() {
            override fun toString(): String {
                throw OutOfMemoryError("toString() causes OOM")
            }
        }
        val s = MiscUtils.reflectionToString(t)
        assertTrue(s.startsWith("TestClass=["))
        assertTrue(s.contains("large=![java.lang.OutOfMemoryError"))
    }


    @Suppress("unused")
    private class TestClass {
        val i = 2
        var large: Any? = null
        private val s = "test"
    }

}