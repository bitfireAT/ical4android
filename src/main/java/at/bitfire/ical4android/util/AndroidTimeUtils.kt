package at.bitfire.ical4android.util

import android.text.format.Time
import at.bitfire.ical4android.DateUtils
import at.bitfire.ical4android.Ical4Android
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DateListProperty
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.util.TimeZones
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object AndroidTimeUtils {

    /**
     * Timezone ID to store for all-day events, according to CalendarContract.Events SDK documentation.
     */
    @Suppress("DEPRECATION")
    val TZID_ALLDAY = Time.TIMEZONE_UTC

    val RECURRENCE_LIST_TZID_SEPARATOR = ';'
    val RECURRENCE_LIST_VALUE_SEPARATOR = ","

    /**
     * Used to separate multiple RRULEs/EXRULEs in the RRULE/EXRULE storage field.
     */
    val RECURRENCE_RULE_SEPARATOR = "\n"


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
            val tzID = date.timeZone?.id
            val bestMatchingTzId = DateUtils.findAndroidTimezoneID(tzID)
            if (tzID != bestMatchingTzId) {
                Ical4Android.log.warning("Android doesn't know time zone ${tzID ?: "(floating)"}, setting default time zone $bestMatchingTzId")
                date.timeZone = DateUtils.ical4jTimeZone(bestMatchingTzId)
            }
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
        val dates = dateList.dates
        if (dates.type == Value.DATE_TIME && dates.isUtc == false) {
            val tzID = dateList.dates.timeZone?.id
            val bestMatchingTzId = DateUtils.findAndroidTimezoneID(tzID)
            if (tzID != bestMatchingTzId) {
                Ical4Android.log.warning("Android doesn't know time zone ${tzID ?: "(floating)"}, setting default time zone $bestMatchingTzId")
                dateList.timeZone = DateUtils.ical4jTimeZone(bestMatchingTzId)
            }

            // keep the time zone of dateList in sync with the actual dates
            if (dateList.timeZone != dates.timeZone)
                dateList.timeZone = dates.timeZone
        }
    }

    /**
     * Returns the time-zone ID for a given date or date-time that should be used to store it
     * in the Android calendar provider.
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
     * Android expects this format: "[TZID;]date1,date2,date3" where date is "yyyymmddThhmmss" (when
     * TZID is given) or "yyyymmddThhmmssZ". We don't use the TZID format here because then we're limited
     * to one time-zone, while an iCalendar may contain multiple EXDATE/RDATE lines with different time zones.
     *
     * @param dates     one more more lists of RDATE or EXDATE
     * @param allDay    whether the event is an all-day event or not
     *
     * @return formatted string for Android calendar provider:
     *
     *   - in case of all-day events, all dates/times are returned as yyyymmddT000000Z
     *   - in case of timed events, all dates/times are returned as UTC time: yyyymmddThhmmssZ
     */
    fun recurrenceSetsToAndroidString(dates: List<DateListProperty>, allDay: Boolean): String {
        /*  rdate/exdate:       DATE                                DATE_TIME
            all-day             store as ...T000000Z                cut off time and store as ...T000000Z
            event with time     (undefined)                         store as ...ThhmmssZ
        */
        val dateFormatUtcMidnight = SimpleDateFormat("yyyyMMdd'T'000000'Z'", Locale.US)
        val strDates = LinkedList<String>()

        // use time zone of first entry for the whole set; null for UTC
        val tz = dates.firstOrNull()?.dates?.timeZone

        for (dateListProp in dates) {
            if (dateListProp is RDate)
                if (dateListProp.periods.isNotEmpty())
                    Ical4Android.log.warning("RDATE PERIOD not supported, ignoring")
            else if (dateListProp is ExDate)
                    if (dateListProp.periods.isNotEmpty())
                        Ical4Android.log.warning("EXDATE PERIOD not supported, ignoring")

            when (dateListProp.dates.type) {
                Value.DATE_TIME -> {
                    if (tz == null && !dateListProp.dates.isUtc)
                        dateListProp.setUtc(true)
                    else if (tz != null && dateListProp.timeZone != tz)
                        dateListProp.timeZone = tz

                    if (allDay)
                        dateListProp.dates.mapTo(strDates) { dateFormatUtcMidnight.format(it) }
                    else
                        strDates.add(dateListProp.value)
                }
                Value.DATE ->
                    // DATE values have to be converted to DATE-TIME <date>T000000Z for Android
                    dateListProp.dates.mapTo(strDates) {
                        dateFormatUtcMidnight.format(it)
                    }
            }
        }

        // format: [tzid;]value1,value2,...
        val result = StringBuilder()
        if (tz != null) {
            result.append(tz.id).append(RECURRENCE_LIST_TZID_SEPARATOR)
        }
        result.append(strDates.joinToString(RECURRENCE_LIST_VALUE_SEPARATOR))
        return result.toString()
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

        val limiter = dbStr.indexOf(RECURRENCE_LIST_TZID_SEPARATOR)
        if (limiter != -1) {    // TZID given
            val tzId = dbStr.substring(0, limiter)
            timeZone = DateUtils.ical4jTimeZone(tzId)
            datesStr = dbStr.substring(limiter + 1)
        } else {
            timeZone = null
            datesStr = dbStr
        }

        // 2. process date string and generate list of DATEs or DATE-TIMEs
        val dateList: DateList
        if (allDay)
            dateList = DateList(datesStr, Value.DATE)
        else {
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


    // duration

    /**
     * Checks and fixes [Event.duration] values with incorrect format which can't be
     * parsed by ical4j. Searches for values like "1H" and "3M" and
     * groups them together in a standards-compliant way.
     *
     * @param duration value from the content provider (like "PT3600S" or "P3600S")
     * @return duration value in RFC 2445 format ("PT3600S" when the argument was "P3600S")
     */
    fun fixDuration(duration: String?): String? {
        if (duration == null)
            return null

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
        val possibleFormats = Regex("([+-]?)P?(T|(\\d+W)|(\\d+D)|(\\d+H)|(\\d+M)|(\\d+S))*")
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

}