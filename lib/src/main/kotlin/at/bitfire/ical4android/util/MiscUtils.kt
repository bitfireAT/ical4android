/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.util

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import java.lang.reflect.Modifier
import java.util.*

object MiscUtils {

    private const val TOSTRING_MAXCHARS = 10000

    /**
     * Generates useful toString info (fields and values) from [obj] by reflection.
     *
     * @param obj   object to inspect
     * @return      string containing properties and non-static declared fields
     */
    fun reflectionToString(obj: Any): String {
        val s = LinkedList<String>()
        var clazz: Class<in Any>? = obj.javaClass
        while (clazz != null) {
            for (prop in clazz.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }) {
                prop.isAccessible = true
                val valueStr = try {
                    prop.get(obj)?.toString()?.abbreviate(TOSTRING_MAXCHARS)
                } catch(e: OutOfMemoryError) {
                    "![$e]"
                }
                s += "${prop.name}=" + valueStr
            }
            clazz = clazz.superclass
        }
        return "${obj.javaClass.simpleName}=[${s.joinToString(", ")}]"
    }


    // various extension methods

    fun ContentProviderClient.closeCompat() {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            close()
        else
            release()
    }

    /**
     * Removes blank (empty or only white-space) [String] values from [ContentValues].
     *
     * @return the modified object (which is the same object as passed in; for chaining)
     */
    fun ContentValues.removeBlankStrings(): ContentValues {
        val iter = keySet().iterator()
        while (iter.hasNext()) {
            val obj = this[iter.next()]
            if (obj is CharSequence && obj.isBlank())
                iter.remove()
        }
        return this
    }

    /**
     * Returns the entire contents of the current row as a [ContentValues] object.
     *
     * @param  removeBlankRows  whether rows with blank values should be removed
     * @return entire contents of the current row
     */
    fun Cursor.toValues(removeBlankRows: Boolean = false): ContentValues {
        val values = ContentValues(columnCount)
        DatabaseUtils.cursorRowToContentValues(this, values)

        if (removeBlankRows)
            values.removeBlankStrings()

        return values
    }

    fun Uri.asSyncAdapter(account: Account): Uri = buildUpon()
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .build()

    /**
     * Abbreviates a String using ellipses. This will turn "Now is the time for all good men" into
     * "Now is the time for..."
     *
     * Specifically:
     *
     * If the number of characters in str is less than or equal to maxWidth, return str.
     * Else abbreviate it to (`substring(str, 0, max-3) + "..."`).
     * If [maxWidth] is less than `4`, throw an [IllegalArgumentException].
     * In no case will it return a [String] of length greater than [maxWidth].
     * ```kotlin
     * "".abbreviate(4)        = ""
     * "abcdefg".abbreviate(6) = "abc..."
     * "abcdefg".abbreviate(7) = "abcdefg"
     * "abcdefg".abbreviate(8) = "abcdefg"
     * "abcdefg".abbreviate(4) = "a..."
     * "abcdefg".abbreviate(3) = IllegalArgumentException
     * ```
     * @see <a href="https://commons.apache.org/proper/commons-lang/apidocs/org/apache/commons/lang3/StringUtils.html#abbreviate-java.lang.String-int-">org.apache.commons.lang3.StringUtils#abbreviate</a>
     */
    fun String.abbreviate(maxWidth: Int): String {
        require(maxWidth >= 4) { "Minimum maxWidth is 4" }

        return if (this.length <= maxWidth) {
            this
        } else {
            this.substring(0, maxWidth - 3) + "..."
        }
    }

}