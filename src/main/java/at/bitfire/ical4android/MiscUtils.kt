/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import android.content.ContentValues
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
                Constants.log.warning("Android doesn't know time zone \"$tzID\", storing event in time zone \"$deviceTzID\"")
                date.timeZone = DateUtils.tzRegistry.getTimeZone(deviceTzID)
            }
        }
    }

    /**
     * Returns the time-zone ID for a given date-time, or TIMEZONE_UTC for dates (without time).
     * TIMEZONE_UTC is also returned for DATE-TIMEs in UTC representation.
     *
     * @param date DateProperty (DATE or DATE-TIME) whose time-zone information is used
     */
    fun getTzId(date: DateProperty?) =
            if (ICalendar.isDateTime(date!!) && !date.isUtc && date.timeZone != null)
                date.timeZone.id!!
            else
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
     * @param values set of values to be processed
     */
    fun removeEmptyStrings(values: ContentValues) {
        val it = values.keySet().iterator()
        while (it.hasNext()) {
            val obj = values[it.next()]
            if (obj is String && obj.isEmpty())
                it.remove()
        }
    }

}