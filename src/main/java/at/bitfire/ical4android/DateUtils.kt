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
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import org.apache.commons.lang3.StringUtils
import java.io.StringReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    init {
        // disable automatic time-zone updates (causes unwanted network traffic)
        System.setProperty("net.fortuna.ical4j.timezone.update.enabled", "false")
    }

    @JvmField
    val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()!!


    // time zones

    @JvmStatic
    fun findAndroidTimezoneID(tzID: String): String {
        val availableTZs = SimpleTimeZone.getAvailableIDs()

        // first, try to find an exact match (case insensitive)
        var deviceTZ = availableTZs.firstOrNull { it.equals(tzID, true) }

        // if that doesn't work, try to find something else that matches
        if (deviceTZ == null)
            for (availableTZ in availableTZs)
                if (StringUtils.indexOfIgnoreCase(tzID, availableTZ) != -1) {
                    deviceTZ = availableTZ
                    Constants.log.warning("Couldn't find system time zone \"$tzID\", assuming $deviceTZ")
                    break
                }

        // if that doesn't work, use UTC as fallback
        if (deviceTZ == null) {
            val defaultTZ = TimeZone.getDefault().id!!
            Constants.log.warning("Couldn't find system time zone \"$tzID\", using system default ($defaultTZ) as fallback")
            deviceTZ = defaultTZ
        }

        return deviceTZ
    }

    /**
     * @param timezoneDef VTIMEZONE definition
     * @return parsed VTimeZone
     * @throws IllegalArgumentException when the timezone definition can't be parsed
     */
    @JvmStatic
    fun parseVTimeZone(timezoneDef: String): VTimeZone {
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
    @JvmStatic
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
     * @throws ParseException
     */
    @JvmStatic
    @Throws(ParseException::class)
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
