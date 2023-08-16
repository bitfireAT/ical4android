/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.util

import android.accounts.Account
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.filters.SmallTest
import at.bitfire.ical4android.util.MiscUtils.CursorHelper.toValues
import at.bitfire.ical4android.util.MiscUtils.UriHelper.asSyncAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiscUtilsTest {

    private val tzVienna = DateUtils.ical4jTimeZone("Europe/Vienna")


    @Test
    @SmallTest
    fun testCursorToValues() {
        val columns = arrayOf("col1", "col2")
        val c = MatrixCursor(columns)
        c.addRow(arrayOf("row1_val1", "row1_val2"))
        c.moveToFirst()
        val values = c.toValues()
        assertEquals("row1_val1", values.getAsString("col1"))
        assertEquals("row1_val2", values.getAsString("col2"))
    }

    @Test
    @SmallTest
    fun testRemoveEmptyAndBlankStrings() {
        val values = ContentValues(2)
        values.put("key1", "value")
        values.put("key2", 1L)
        values.put("key3", "")
        values.put("key4", "\n")
        values.put("key5", " \n ")
        values.put("key6", " ")
        MiscUtils.removeEmptyAndBlankStrings(values)
        assertEquals("value", values.getAsString("key1"))
        assertEquals(1L, values.getAsLong("key2").toLong())
        assertNull(values.get("key3"))
        assertNull(values.get("key4"))
        assertNull(values.get("key5"))
        assertNull(values.get("key6"))
    }


    @Test
    fun testUriHelper_asSyncAdapter() {
        val account = Account("testName", "testType")
        val baseUri = Uri.parse("test://example.com/")
        assertEquals(
            Uri.parse("$baseUri?account_name=testName&account_type=testType&caller_is_syncadapter=true"),
            baseUri.asSyncAdapter(account)
        )
    }

}
