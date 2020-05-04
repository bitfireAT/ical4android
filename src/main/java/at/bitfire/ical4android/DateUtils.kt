/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DateListProperty
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import java.io.StringReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Date utilities
 *
 * Before this object is accessed the first time, the accessing thread's contextClassLoader
 * must be set to an Android Context.classLoader!
 */
object DateUtils {

    init {
        Ical4Android.checkThreadContextClassLoader()
    }

    /**
     * global ical4j time zone registry used for event/task processing
     */
    @UsesThreadContextClassLoader
    val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()!!


    // time zones

    /**
     * For a given time zone ID taken from an iCalendar resource, find the matching
     * Android time zone ID (if possible):
     * 1. Use a case-insensitive match ("EUROPE/VIENNA" will return "Europe/Vienna",
     *    assuming "Europe/Vienna") is available in Android
     * 2. Find partial matches (case-sensitive) in both directions, so both "Vienna"
     *    and "MyClient: Europe/Vienna" will return "Europe/Vienna". This shouldn't be
     *    case-sensitive, because that would (for instance) return "EST" for "Westeuropäische Sommerzeit"
     * 3. If nothing can be found, use the system default time zone
     * @param tzID time zone ID to be converted into Android time zone ID
     * @return best matching Android time zone ID
     */
    fun findAndroidTimezoneID(tzID: String): String {
        val availableTZs = SimpleTimeZone.getAvailableIDs()

        // first, try to find an exact match (case insensitive)
        var deviceTZ = availableTZs.firstOrNull { it.equals(tzID, true) }

        // if that doesn't work, try to find something else that matches
        if (deviceTZ == null)
            for (availableTZ in availableTZs)
                if (availableTZ.contains(tzID) || tzID.contains(availableTZ)) {
                    deviceTZ = availableTZ
                    Ical4Android.log.warning("Couldn't find system time zone \"$tzID\", assuming $deviceTZ")
                    break
                }

        // if that doesn't work, use device default as fallback
        if (deviceTZ == null) {
            val defaultTZ = TimeZone.getDefault().id!!
            deviceTZ = defaultTZ
        }

        return deviceTZ
    }

    /**
     * Determines whether a given date represents a DATE-TIME value.
     * @param date date property to check
     * @return *true* if the date is a DATE-TIME value; *false* otherwise (for instance, when the
     * date is a DATE value or null)
     */
    fun isDateTime(date: DateProperty?) = date != null && date.date is DateTime

    /**
     * Parses a VTIMEZONE definition to a VTimeZone object.
     * @param timezoneDef VTIMEZONE definition
     * @return parsed VTimeZone
     * @throws IllegalArgumentException when the timezone definition can't be parsed
     */
    @UsesThreadContextClassLoader
    fun parseVTimeZone(timezoneDef: String): VTimeZone {
        Ical4Android.checkThreadContextClassLoader()
        val builder = CalendarBuilder(tzRegistry)
        try {
            val cal = builder.build(StringReader(timezoneDef))
            return cal.getComponent(VTimeZone.VTIMEZONE) as VTimeZone
        } catch (e: Exception) {
            throw IllegalArgumentException("Couldn't parse timezone definition")
        }
    }


    // recurrence sets

    /**
     * Concatenates, if necessary, multiple RDATE/EXDATE lists and converts them to
     * a formatted string which Android calendar provider can process.
     * Android expects this format: "[TZID;]date1,date2,date3" where date is "yyyymmddThhmmss" (when
     * TZID is given) or "yyyymmddThhmmssZ". We don't use the TZID format here because then we're limited
     * to one time-zone, while an iCalendar may contain multiple EXDATE/RDATE lines with different time zones.
     * @param dates        one more more lists of RDATE or EXDATE
     * @param allDay    indicates whether the event is an all-day event or not
     * @return            formatted string for Android calendar provider:
     *                  - in case of all-day events, all dates/times are returned as yyyymmddT000000Z
     *                  - in case of timed events, all dates/times are returned as UTC time: yyyymmddThhmmssZ
     */
    fun recurrenceSetsToAndroidString(dates: List<DateListProperty>, allDay: Boolean): String
    {
        /*  rdate/exdate:       DATE                                DATE_TIME
            all-day             store as ...T000000Z                cut off time and store as ...T000000Z
            event with time     (ignored)                           store as ...ThhmmssZ
        */
        val dateFormatUtcMidnight = SimpleDateFormat("yyyyMMdd'T'000000'Z'", Locale.US)

        val strDates = LinkedList<String>()
        for (dateListProp in dates) {
            when (dateListProp.dates.type) {
                Value.DATE_TIME -> {
                    // DATE-TIME values will be stored in UTC format for Android
                    if (allDay)
                        dateListProp.dates.mapTo(strDates) { dateFormatUtcMidnight.format(it) }
                    else {
                        dateListProp.setUtc(true)
                        strDates.add(dateListProp.value)
                    }
                }
                Value.DATE ->
                    // DATE values have to be converted to DATE-TIME <date>T000000Z for Android
                    dateListProp.dates.mapTo(strDates) { dateFormatUtcMidnight.format(it) }
            }
        }
        return strDates.joinToString(",")
    }

    /**
     * Takes a formatted string as provided by the Android calendar provider and returns a DateListProperty
     * constructed from these values.
     * @param dbStr     formatted string from Android calendar provider (RDATE/EXDATE field)
     *                  expected format: "[TZID;]date1,date2,date3" where date is "yyyymmddThhmmss[Z]"
     * @param type      subclass of DateListProperty, e.g. [RDate] or [ExDate]
     * @param allDay    true: list will contain DATE values; false: list will contain DATE_TIME values
     * @return          instance of "type" containing the parsed dates/times from the string
     * @throws ParseException when the string cannot be parsed
     */
    fun<T: DateListProperty> androidStringToRecurrenceSet(dbStr: String, type: Class<T>, allDay: Boolean): T
    {
        // 1. split string into time zone and actual dates
        val timeZone: TimeZone?
        val datesStr: String

        val limiter = dbStr.indexOf(';')
        if (limiter != -1) {    // TZID given
            timeZone = tzRegistry.getTimeZone(dbStr.substring(0, limiter))
            datesStr = dbStr.substring(limiter + 1)
        } else {
            timeZone = null
            datesStr = dbStr
        }

        // 2. process date string and generate list of DATEs or DATE-TIMEs
        val dateList: DateList
        if (allDay) {
            dateList = DateList(Value.DATE)
            datesStr.split(',').mapTo(dateList) { Date(DateTime(it)) }
        } else {
            dateList = DateList(datesStr, Value.DATE_TIME, timeZone)
            if (timeZone == null)
                dateList.isUtc = true
        }

        // 3. generate requested DateListProperty (RDate/ExDate) from list of DATEs or DATE-TIMEs
        val list: DateListProperty
        try {
            list = type.getDeclaredConstructor(DateList::class.java).newInstance(dateList)
            dateList.timeZone?.let { list.timeZone = it }
        } catch (e: Exception) {
            throw ParseException("Couldn't create date/time list by reflection", -1)
        }

        return list
    }

}
