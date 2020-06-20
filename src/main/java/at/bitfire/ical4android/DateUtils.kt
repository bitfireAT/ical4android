/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.DateProperty
import java.io.StringReader
import java.time.ZoneId

/**
 * Date/time utilities
 *
 * Before this object is accessed the first time, the accessing thread's contextClassLoader
 * must be set to an Android Context.classLoader!
 */
object DateUtils {

    /**
     * Global ical4j time zone registry used for event/task processing. Do not
     * modify this registry or its entries!
     */
    @Deprecated("Use ical4jTimeZone() directly (registry must not be modified)")
    @UsesThreadContextClassLoader
    val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()

    init {
        Ical4Android.checkThreadContextClassLoader()
    }


    // time zones

    /**
     * For a given time zone ID taken from an iCalendar resource, find the matching
     * Android time zone ID (if possible):
     *
     * 1. Use a case-insensitive match ("EUROPE/VIENNA" will return "Europe/Vienna",
     *    assuming "Europe/Vienna") is available in Android
     * 2. Find partial matches (case-sensitive) in both directions, so both "Vienna"
     *    and "MyClient: Europe/Vienna" will return "Europe/Vienna". This shouldn't be
     *    case-sensitive, because that would (for instance) return "EST" for "Westeuropäische Sommerzeit"
     * 3. If nothing can be found, use the system default time zone
     *
     * @param tzID time zone ID to be converted into Android time zone ID
     *
     * @return best matching Android time zone ID
     */
    fun findAndroidTimezoneID(tzID: String?): String {
        val availableTZs = ZoneId.getAvailableZoneIds()
        var result: String? = null

        if (tzID != null) {
            // first, try to find an exact match (case insensitive)
            result = availableTZs.firstOrNull { it.equals(tzID, true) }

            // if that doesn't work, try to find something else that matches
            if (result == null)
                for (availableTZ in availableTZs)
                    if (availableTZ.contains(tzID) || tzID.contains(availableTZ)) {
                        result = availableTZ
                        Ical4Android.log.warning("Couldn't find system time zone \"$tzID\", assuming $result")
                        break
                    }
        }

        // if that doesn't work, use device default as fallback
        return if (result == null)
            TimeZone.getDefault().id
        else
            result
    }

    /**
     * Checks and fixes [Event.duration] values with incorrect format which can't be
     * parsed by ical4j. Searches for values like "1H" and "3M" and
     * groups them together in a standards-compliant way.
     *
     * @param duration value from the database (like "PT3600S" or "P3600S")
     * @return duration value in RFC 2445 format ("PT3600S" when the argument was "P3600S")
     */
    fun fixDuration(duration: String): String {
        /** [RFC 2445/5445]
         * dur-value  = (["+"] / "-") "P" (dur-date / dur-time / dur-week)
         * dur-date   = dur-day [dur-time]
         * dur-day    = 1*DIGIT "D"
         * dur-time   = "T" (dur-hour / dur-minute / dur-second)
         * dur-week   = 1*DIGIT "W"
         * dur-hour   = 1*DIGIT "H" [dur-minute]
         * dur-minute = 1*DIGIT "M" [dur-second]
         * dur-second = 1*DIGIT "S"
         */
        val possibleFormats = Regex("([+-]?)P?((\\d+W)|(\\d+D)|(\\d+H)|(\\d+M)|(\\d+S))*")
        possibleFormats.matchEntire(duration)?.destructured?.let { (sign, _, weeks, days, hours, minutes, seconds) ->
            val newValue = StringBuilder()
            if (sign.isNotEmpty())
                newValue.append(sign)
            newValue.append("P")

            // It's not possible to mix weeks with everything else, so convert
            // one week to seven days if there's anything else than weeks.
            var addDays = 0
            if (weeks.isNotEmpty()) {
                if ((days.isEmpty() && hours.isEmpty() && minutes.isEmpty() && seconds.isEmpty())) {
                    // only weeks
                    newValue.append(weeks)
                    return newValue.toString()
                } else
                    addDays = weeks.dropLast(1).toInt() * 7
            }
            if (days.isNotEmpty() || addDays != 0) {
                val daysInt = (if (days.isEmpty()) 0 else days.dropLast(1).toInt()) + addDays
                newValue.append("${daysInt}D")
            }

            val durTime = StringBuilder()
            if (hours.isNotEmpty())
                durTime.append(hours)

            if (minutes.isEmpty()) {
                if (hours.isNotEmpty() && seconds.isNotEmpty())
                    durTime.append("0M")
            } else
                durTime.append(minutes)

            if (seconds.isNotEmpty())
                durTime.append(seconds)

            if (durTime.isNotEmpty())
                newValue.append("T").append(durTime)

            return newValue.toString()
        }
        // no match, return unchanged input value
        return duration
    }

    @Suppress("DEPRECATION")
    @UsesThreadContextClassLoader
    fun ical4jTimeZone(id: String) = tzRegistry.getTimeZone(id)

    /**
     * Determines whether a given date represents a DATE-TIME value.
     * @param date date property to check
     * @return *true* if the date is a DATE value; *false* otherwise (for instance, when the argument is a DATE-TIME value or null)
     */
    fun isDate(date: DateProperty?) = date != null && date.date !is DateTime

    /**
     * Determines whether a given date represents a DATE-TIME value.
     * @param date date property to check
     * @return *true* if the date is a DATE-TIME value; *false* otherwise (for instance, when the argument is a DATE value or null)
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

}
