/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

@file:Suppress("DEPRECATION")

package at.bitfire.ical4android.util

import android.text.format.Time
import at.bitfire.ical4android.Ical4Android
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DateListProperty
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.util.TimeZones
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAmount
import java.util.*

object AndroidTimeUtils {

    /**
     * Timezone ID to store for all-day events, according to CalendarContract.Events SDK documentation.
     */
    @Suppress("DEPRECATION")
    val TZID_ALLDAY = Time.TIMEZONE_UTC

    private const val RECURRENCE_LIST_TZID_SEPARATOR = ';'
    private const val RECURRENCE_LIST_VALUE_SEPARATOR = ","

    /**
     * Used to separate multiple RRULEs/EXRULEs in the RRULE/EXRULE storage field.
     */
    const val RECURRENCE_RULE_SEPARATOR = "\n"


    /**
     * Ensures that a given [DateProperty] either
     *
     * 1. has a time zone with an ID that is available in Android, or
     * 2. is an UTC property ([DateProperty.isUtc] = *true*).
     *
     * To get the time zone ID which shall be given to the Calendar provider,
     * use [storageTzId].
     *
     * @param date [DateProperty] to validate. Values which are not DATE-TIME will be ignored.
     */
    fun androidifyTimeZone(date: DateProperty?) {
        if (DateUtils.isDateTime(date) && date?.isUtc == false) {
            val tzID = DateUtils.findAndroidTimezoneID(date.timeZone?.id)
            date.timeZone = DateUtils.ical4jTimeZone(tzID)
        }
    }

    /**
     * Ensures that a given [DateListProperty] either
     *
     * 1. has a time zone with an ID that is available in Android, or
     * 2. is an UTC property ([DateProperty.isUtc] = *true*).
     * *
     * @param dateList [DateListProperty] to validate. Values which are not DATE-TIME will be ignored.
     */
    fun androidifyTimeZone(dateList: DateListProperty) {
        // periods (RDate only)
        val periods = (dateList as? RDate)?.periods
        if (periods != null && periods.size > 0 && !periods.isUtc) {
            val tzID = DateUtils.findAndroidTimezoneID(periods.timeZone?.id)

            // Setting the time zone won't work until resolved in ical4j (https://github.com/ical4j/ical4j/discussions/568)
            // DateListProperty.setTimeZone() does not set the timeZone property when the DateList has PERIODs
            dateList.timeZone = DateUtils.ical4jTimeZone(tzID)

            return //  RDate can only contain periods OR dates - not both, bail out fast
        }

        // date-times (RDate and ExDate)
        val dates = dateList.dates
        if (dates != null && dates.size > 0) {
            if (dates.type == Value.DATE_TIME && !dates.isUtc) {
                val tzID = DateUtils.findAndroidTimezoneID(dates.timeZone?.id)
                dateList.timeZone = DateUtils.ical4jTimeZone(tzID)
            }
        }
    }

    /**
     * Returns the time-zone ID for a given date or date-time that should be used to store it
     * in the Android calendar provider.
     *
     * Does not check whether Android actually knows the time zone ID – use [androidifyTimeZone] for that.
     *
     * @param date DateProperty (DATE or DATE-TIME) whose time-zone information is used
     *
     * @return - UTC for dates and UTC date-times
     *         - the specified time zone ID for date-times with given time zone
     *         - the currently set default time zone ID for floating date-times
     */
    fun storageTzId(date: DateProperty): String =
            if (DateUtils.isDateTime(date)) {
                // DATE-TIME
                when {
                    date.isUtc ->
                        // DATE-TIME in UTC format
                        TimeZones.UTC_ID
                    date.timeZone != null ->
                        // DATE-TIME with given time-zone
                        date.timeZone.id
                    else ->
                        // DATE-TIME in local format (floating)
                        java.util.TimeZone.getDefault().id
                }
            } else
                // DATE
                TZID_ALLDAY


    // recurrence sets

    /**
     * Concatenates, if necessary, multiple RDATE/EXDATE lists and converts them to
     * a formatted string which Android calendar provider can process.
     *
     * Android expects this format: "[TZID;]date1,date2,date3" where date is "yyyymmddThhmmss" (when
     * TZID is given) or "yyyymmddThhmmssZ". We don't use the TZID format here because then we're limited
     * to one time-zone, while an iCalendar may contain multiple EXDATE/RDATE lines with different time zones.
     *
     * @param dates         one more more lists of RDATE or EXDATE
     * @param allDay        whether the event is an all-day event or not
     *
     * @return formatted string for Android calendar provider
     */
    fun recurrenceSetsToAndroidString(dates: List<DateListProperty>, allDay: Boolean): String {
        /*  rdate/exdate:       DATE                                DATE_TIME
            all-day             store as ...T000000Z                cut off time and store as ...T000000Z
            event with time     (undefined)                         store as ...ThhmmssZ
        */
        val dateFormatUtcMidnight = SimpleDateFormat("yyyyMMdd'T'000000'Z'", Locale.ROOT)

        /** Provides a collection of dates for each timezone id (key) */
        val datesMap = mutableMapOf<String, MutableList<String>>()

        for (dateListProp in dates) {
            if (dateListProp is RDate && dateListProp.periods.isNotEmpty()) {
                Ical4Android.log.warning("RDATE PERIOD not supported, ignoring")
                break
            }

            // Tries to get the timezone from the property
            val tz: TimeZone? =
                (dateListProp as? RDate)?.periods?.timeZone ?:   // VALUE=PERIOD (only RDate)
                dateListProp.dates?.timeZone                     // VALUE=DATE/DATE-TIME
            // UTC is used by default if no timezone has been set
            val tzId = tz?.id ?: TimeZones.UTC_ID

            // Extract a list of dates from the property
            val datesList = when (dateListProp.dates.type) {
                Value.DATE_TIME -> {
                    if (tz == null && !dateListProp.dates.isUtc)
                        dateListProp.setUtc(true)
                    else if (tz != null && dateListProp.timeZone != tz)
                        dateListProp.timeZone = tz

                    if (allDay)
                        dateListProp.dates.map { dateFormatUtcMidnight.format(it) }
                    else
                        listOf(dateListProp.value)
                }

                Value.DATE ->
                    // DATE values have to be converted to DATE-TIME <date>T000000Z for Android
                    dateListProp.dates.map { dateFormatUtcMidnight.format(it) }

                else -> emptyList()
            }

            // Add the list of dates generated into the key of the date's timezone.
            val tzDatesList = datesMap.getOrPut(tzId) { mutableListOf() }
            tzDatesList.addAll(datesList)
        }

        // Group all the dates by timezone, but don't prefix timezones that are UTC
        return datesMap.map { (tzId, dateStrList) ->
            val s = StringBuilder()
            if (tzId != TimeZones.UTC_ID)
                s.append(tzId).append(RECURRENCE_LIST_TZID_SEPARATOR)
            s.append(dateStrList.joinToString(RECURRENCE_LIST_VALUE_SEPARATOR))
        }.joinToString("\n")
    }

    /**
     * Takes a formatted string as provided by the Android calendar provider and returns a DateListProperty
     * constructed from these values.
     *
     * @param dbStr     formatted string from Android calendar provider (RDATE/EXDATE field)
     *                  expected format: `[TZID;]date1,date2,date3` where date is `yyyymmddThhmmss[Z]`;
     *                  may consist of multiple lines, separated by `\n`
     * @param allDay    true: list will contain DATE values; false: list will contain DATE_TIME values
     * @param exclude   this time stamp won't be added to the [DateListProperty]
     * @param generator generates the [DateListProperty]; must call the constructor with the one argument of type [DateList]
     *
     * @return          instance of "type" containing the parsed dates/times from the string
     *
     * @throws ParseException when the string cannot be parsed
     */
    fun <T : DateListProperty> androidStringToRecurrenceSet(
        dbStr: String,
        allDay: Boolean,
        exclude: Long? = null,
        generator: (DateList) -> T
    ): List<T> {
        val lines = dbStr.split("\n")

        // extract time zone IDs from the lines
        val timezones =
            lines.mapNotNull { line ->
                val index = line.indexOf(RECURRENCE_LIST_TZID_SEPARATOR)
                if (index == -1) return@mapNotNull null
                val tzId = line.substring(0, index)
                DateUtils.ical4jTimeZone(tzId)
            }.toSet()           // convert to set to merge duplicates

        return timezones.map { timezone ->
            /** Has all the dates for the current timezone. */
            val dateLines = lines.filter { line ->
                val timeZoneId = line.substringBefore(RECURRENCE_LIST_TZID_SEPARATOR, TimeZones.UTC_ID)
                val lineZoneId = ZoneId.of(timeZoneId)
                lineZoneId == timezone.toZoneId()
            }
            val dateList = DateList(
                if (allDay) Value.DATE
                else Value.DATE_TIME,
                timezone
            )
            if (timezone.id == TimeZones.UTC_ID) dateList.isUtc = true

            // Store the timezone id of the current line, default to UTC
            val dates = dateLines.flatMap { line ->
                line.substringAfter(RECURRENCE_LIST_TZID_SEPARATOR)
                    .split(RECURRENCE_LIST_VALUE_SEPARATOR)
                    .map { dateStr ->
                        val pattern = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss['Z']")
                        if (allDay)
                            LocalDate
                                .parse(dateStr, pattern)
                                .atTime(0, 0)
                                .atZone(timezone.toZoneId())
                                .toIcal4jDateTime()
                        else
                            LocalDateTime
                                .parse(dateStr, pattern)
                                .atZone(timezone.toZoneId())
                                .toIcal4jDateTime()
                    }
                    .filter { it.time != exclude }
                    .map {
                        if (allDay)
                            Date(it.time)
                        else
                            DateTime(it.time)
                    }
            }
            dateList.addAll(dates)

            generator(dateList).apply {
                if (this.dates.type == Value.DATE_TIME && !this.dates.isUtc)
                    this.timeZone = timezone
            }
        }
    }

    /**
     * Concatenates, if necessary, multiple RDATE/EXDATE lists and converts them to
     * a formatted string which OpenTasks can process.
     * OpenTasks expect a list of RFC 5545 DATE ("yyyymmdd") or DATE-TIME ("yyyymmdd[Z]") values,
     * where the time zone is stored in a separate field.
     *
     * @param dates         one more more lists of RDATE or EXDATE
     * @param tz            output time zone (*null* for all-day event)
     *
     * @return formatted string for Android calendar provider
     */
    fun recurrenceSetsToOpenTasksString(dates: List<DateListProperty>, tz: TimeZone?): String {
        val allDay = tz == null
        val strDates = LinkedList<String>()
        for (dateListProp in dates) {
            if (dateListProp is RDate)
                if (dateListProp.periods.isNotEmpty())
                    Ical4Android.log.warning("RDATE PERIOD not supported, ignoring")
                else if (dateListProp is ExDate)
                    if (dateListProp.periods.isNotEmpty())
                        Ical4Android.log.warning("EXDATE PERIOD not supported, ignoring")

            for (date in dateListProp.dates) {
                val dateToUse =
                        if (date is DateTime && allDay)             // VALUE=DATE-TIME, but allDay=1
                            Date(date)
                        else if (date !is DateTime && !allDay)      // VALUE=DATE, but allDay=0
                            DateTime(date.toString(), tz)
                        else
                            date
                if (dateToUse is DateTime && !dateToUse.isUtc)
                    dateToUse.timeZone = tz!!
                strDates += dateToUse.toString()
            }
        }
        return strDates.joinToString(RECURRENCE_LIST_VALUE_SEPARATOR)
    }


    // duration

    /**
     * Checks and fixes [Event.duration] values with incorrect format which can't be
     * parsed by ical4j. Searches for values like "1H" and "3M" and
     * groups them together in a standards-compliant way.
     *
     * @param durationStr value from the content provider (like "PT3600S" or "P3600S")
     * @return duration value in RFC 2445 format ("PT3600S" when the argument was "P3600S")
     */
    fun parseDuration(durationStr: String): TemporalAmount {
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
        val possibleFormats = Regex("([+-]?)P?(T|((\\d+)W)|((\\d+)D)|((\\d+)H)|((\\d+)M)|((\\d+)S))*")
                                         //  1            4         6         8         10        12
        possibleFormats.matchEntire(durationStr)?.let { result ->
            fun fromMatch(s: String) = if (s.isEmpty()) 0 else s.toInt()

            val intSign = if (result.groupValues[1] == "-") -1 else 1
            val intDays = fromMatch(result.groupValues[4]) * TimeApiExtensions.DAYS_PER_WEEK + fromMatch(result.groupValues[6])
            val intHours = fromMatch(result.groupValues[8])
            val intMinutes = fromMatch(result.groupValues[10])
            val intSeconds = fromMatch(result.groupValues[12])

            return if (intDays != 0 && intHours == 0 && intMinutes == 0 && intSeconds == 0)
                Period.ofDays(intSign * intDays)
            else
                Duration.ofSeconds(intSign * (
                        intDays * TimeApiExtensions.SECONDS_PER_DAY.toLong() +
                        intHours * TimeApiExtensions.SECONDS_PER_HOUR +
                        intMinutes * TimeApiExtensions.SECONDS_PER_MINUTE +
                        intSeconds
                ))
        }
        // no match, try TemporalAmountAdapter
        return TemporalAmountAdapter.parse(durationStr).duration
    }

}