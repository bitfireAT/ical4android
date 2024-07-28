/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import androidx.annotation.VisibleForTesting
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.Ical4Android
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toZoneIdCompat
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.util.TimeZones
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.TimeZone
import java.util.logging.Logger

/**
 * Validates events and tries to repair broken events, since sometimes CalendarStorage or servers
 * respond with invalid event definitions.
 *
 * This class should not throw exceptions, but try to repair as much as possible instead.
 *
 * This class is applied
 *
 * - once to every event after completely reading an iCalendar, and
 * - to every event when writing an iCalendar.
 */
object EventValidator {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Searches for some invalid conditions and fixes them.
     *
     * @param event  event to repair (including its exceptions) – may be modified!
     */
    fun repair(event: Event) {
        val dtStart = correctStartAndEndTime(event)
        sameTypeForDtStartAndRruleUntil(dtStart, event.rRules)
        removeRRulesWithUntilBeforeDtStart(dtStart, event.rRules)
        removeRRulesOfExceptions(event.exceptions)
    }


    /**
     * Makes sure that event has a start time and that it's before the end time.
     * If the event doesn't have start time,
     *
     * 1. the end time is used as start time, if available,
     * 2. otherwise the current time is used as start time.
     *
     * If the event has an end time and it's before the start time, the end time is removed.
     *
     * @return the (potentially corrected) start time
     */
    @VisibleForTesting
    internal fun correctStartAndEndTime(e: Event): DtStart {
        // make sure that event has a start time
        var dtStart: DtStart? = e.dtStart
        if (dtStart == null) {
            dtStart =
                e.dtEnd?.let {
                    DtStart(it.date)
                } ?: DtStart(DateTime(/* current time */))
            e.dtStart = dtStart
        }

        // validate end time
        e.dtEnd?.let { dtEnd ->
            if (dtStart.date > dtEnd.date) {
                logger.warning("DTSTART after DTEND; removing DTEND")
                e.dtEnd = null
            }
        }

        return dtStart
    }

    /**
     * Tries to make the value type of UNTIL and DTSTART the same (both DATE or DATETIME).
     */
    @VisibleForTesting
    internal fun sameTypeForDtStartAndRruleUntil(dtStart: DtStart, rRules: MutableList<RRule>) {
        if (DateUtils.isDate(dtStart)) {
            // DTSTART is a DATE
            val newRRules = mutableListOf<RRule>()
            val rRuleIterator = rRules.iterator()
            while (rRuleIterator.hasNext()) {
                val rRule = rRuleIterator.next()
                rRule.recur.until?.let { until ->
                    if (until is DateTime) {
                        logger.warning("DTSTART has DATE, but UNTIL has DATETIME; making UNTIL have DATE only")

                        val newUntil = until.toLocalDate().toIcal4jDate()

                        // remove current RRULE and remember new one to be added
                        val newRRule = RRule(Recur.Builder(rRule.recur)
                            .until(newUntil)
                            .build())
                        logger.info("New $newRRule (was ${rRule.toString().trim()})")
                        newRRules += newRRule
                        rRuleIterator.remove()
                    }
                }
            }
            // add repaired RRULEs
            rRules += newRRules

        } else if (DateUtils.isDateTime(dtStart)) {
            // DTSTART is a DATE-TIME
            val newRRules = mutableListOf<RRule>()
            val rRuleIterator = rRules.iterator()
            while (rRuleIterator.hasNext()) {
                val rRule = rRuleIterator.next()
                rRule.recur.until?.let { until ->
                    if (until !is DateTime) {
                        logger.warning("DTSTART has DATETIME, but UNTIL has DATE; copying time from DTSTART to UNTIL")
                        val dtStartTimeZone = if (dtStart.timeZone != null)
                            dtStart.timeZone
                        else if (dtStart.isUtc)
                            TimeZones.getUtcTimeZone()
                        else /* floating time */
                            TimeZone.getDefault()

                        val dtStartCal = Calendar.getInstance(dtStartTimeZone).apply {
                            time = dtStart.date
                        }
                        val dtStartTime = LocalTime.of(
                            dtStartCal.get(Calendar.HOUR_OF_DAY),
                            dtStartCal.get(Calendar.MINUTE),
                            dtStartCal.get(Calendar.SECOND)
                        )

                        val newUntil = ZonedDateTime.of(
                            until.toLocalDate(),    // date from until
                            dtStartTime,       // time from dtStart
                            dtStartTimeZone.toZoneIdCompat()
                        )

                        // Android requires UNTIL in UTC as defined in RFC 2445.
                        // https://android.googlesource.com/platform/frameworks/opt/calendar/+/refs/tags/android-12.1.0_r27/src/com/android/calendarcommon2/RecurrenceProcessor.java#93
                        val newUntilUTC = DateTime(true).apply {
                            time = newUntil.toInstant().toEpochMilli()
                        }

                        // remove current RRULE and remember new one to be added
                        val newRRule = RRule(Recur.Builder(rRule.recur)
                            .until(newUntilUTC)
                            .build())
                        logger.info("New $newRRule (was ${rRule.toString().trim()})")
                        newRRules += newRRule
                        rRuleIterator.remove()
                    }
                }
            }
            // add repaired RRULEs
            rRules += newRRules
        }
    }


    /**
     * Removes RRULEs of exceptions of (potentially recurring) events
     * Note: This repair step needs to be applied after all exceptions have been found
     *
     * @param exceptions exceptions of an event
     */
    @VisibleForTesting
    internal fun removeRRulesOfExceptions(exceptions: List<Event>) {
        for (exception in exceptions)
            exception.rRules.clear()     // Drop all RRULEs for the exception
    }


    /**
     * Will remove the RRULES of an event where UNTIL lies before DTSTART
     */
    @VisibleForTesting
    internal fun removeRRulesWithUntilBeforeDtStart(dtStart: DtStart, rRules: MutableList<RRule>) {
        val iter = rRules.iterator()
        while (iter.hasNext()) {
            val rRule = iter.next()

            // drop invalid RRULEs
            if (hasUntilBeforeDtStart(dtStart, rRule))
                iter.remove()
        }
    }

    /**
     * Checks whether UNTIL of an RRULE lies before DTSTART
     */
    @VisibleForTesting
    internal fun hasUntilBeforeDtStart(dtStart: DtStart, rRule: RRule): Boolean {
        val until = rRule.recur.until ?: return false
        return until < dtStart.date
    }

}