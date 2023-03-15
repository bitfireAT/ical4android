/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import java.util.Locale
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

class LocaleNonWesternDigitsTest {

    companion object {
        val origLocale = Locale.getDefault()
        val testLocale = Locale("fa", "ir", "u-un-arabext")

        @BeforeClass
        @JvmStatic
        fun setFaIrArabLocale() {
            assertEquals("Persian (Iran) locale not available", "fa", testLocale.language)
            Locale.setDefault(testLocale)
        }

        @AfterClass
        @JvmStatic
        fun resetLocale() {
            Locale.setDefault(origLocale)
        }

    }

    @Test
    fun testLocale_StringFormat() {
        // does not fail if the Locale with Persian digits is available
        assertEquals("۲۰۲۰", String.format("%d", 2020))
    }

    @Test
    fun testLocale_StringFormat_Root() {
        assertEquals("2020", String.format(Locale.ROOT, "%d", 2020))
    }

}