/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.util.TimeZones
import java.lang.reflect.Modifier
import java.util.*

object MiscUtils {

    /**
     * Ensures that a given DateProperty has a time zone with an ID that is available in Android.
     *
     * @param date DateProperty to validate. Values which are not DATE-TIME will be ignored.
     */
    fun androidifyTimeZone(date: DateProperty?) {
        if (ICalendar.isDateTime(date)) {
            val tz = date!!.timeZone ?: return
            val tzID = tz.id ?: return
            val deviceTzID = DateUtils.findAndroidTimezoneID(tzID)
            if (tzID != deviceTzID) {
                Constants.log.warning("Android doesn't know time zone \"$tzID\", assuming device time zone \"$deviceTzID\"")
                date.timeZone = DateUtils.tzRegistry.getTimeZone(deviceTzID)
            }
        }
    }

    /**
     * Returns the time-zone ID for a given date or date-time that should be used to store it
     * in the Android calendar storage.
     *
     * @param date DateProperty (DATE or DATE-TIME) whose time-zone information is used
     *
     * @return - UTC for dates and UTC date-times
     *         - the specified time zone ID for date-times with given time zone
     *         - the currently set default time zone ID for floating date-times
     */
    fun getTzId(date: DateProperty): String =
            if (ICalendar.isDateTime(date)) {
                when {
                    date.isUtc ->
                        // DATE-TIME in UTC format
                        TimeZones.UTC_ID
                    date.timeZone != null ->
                        // DATE-TIME with given time-zone
                        date.timeZone.id
                    else /* date.timeZone == null */ ->
                        // DATE-TIME in local format (floating)
                        TimeZone.getDefault().id
                }
            } else
                // DATE
                TimeZones.UTC_ID

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
                s += "${prop.name}=" + prop.get(obj)?.toString()?.trim()
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


    object TextListHelper {

        fun TextList.toList(): List<String> {
            val list = LinkedList<String>()
            val it = iterator()
            while (it.hasNext())
                list += it.next()
            return list
        }

    }

}