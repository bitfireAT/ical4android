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
import org.apache.commons.lang3.StringUtils
import java.lang.reflect.Modifier
import java.util.*

object MiscUtils {

    const val TOSTRING_MAXCHARS = 10000

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
                    StringUtils.abbreviate(prop.get(obj)?.toString(), TOSTRING_MAXCHARS)
                } catch(e: OutOfMemoryError) {
                    "![$e]"
                }
                s += "${prop.name}=" + valueStr
            }
            clazz = clazz.superclass
        }
        return "${obj.javaClass.simpleName}=[${s.joinToString(", ")}]"
    }

    /**
     * Removes empty [String] values from [values].
     *
     * @param values set of values to be modified
     * @return the modified object (which is the same object as passed in; for chaining)
     */
    fun removeEmptyStrings(values: ContentValues): ContentValues {
        val it = values.keySet().iterator()
        while (it.hasNext()) {
            val obj = values[it.next()]
            if (obj is String && obj.isEmpty())
                it.remove()
        }
        return values
    }


    object ContentProviderClientHelper {

        fun ContentProviderClient.closeCompat() {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                close()
            else
                release()
        }

    }


    object CursorHelper {

        /**
         * Returns the entire contents of the current row as a [ContentValues] object.
         *
         * @param  removeEmptyRows  whether rows with empty values should be removed
         * @return entire contents of the current row
         */
        fun Cursor.toValues(removeEmptyRows: Boolean = false): ContentValues {
            val values = ContentValues(columnCount)
            DatabaseUtils.cursorRowToContentValues(this, values)

            if (removeEmptyRows)
                removeEmptyStrings(values)

            return values
        }

    }


    object UriHelper {

        fun Uri.asSyncAdapter(account: Account): Uri = buildUpon()
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .build()

    }

}