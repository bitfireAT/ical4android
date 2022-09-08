/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import at.bitfire.ical4android.DateUtils
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.Ical4Android
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toZoneIdCompat
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.util.TimeZones
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.*

/**
 * Sometimes CalendarStorage or servers respond with invalid event definitions. Here we try to
 * validate, repair and assume whatever seems appropriate before denying the whole event.
 */
class EventValidator(val e: Event) {

    fun repair() {
        val dtStart = correctStartAndEndTime(e)
        sameTypeForDtStartAndRruleUntil(dtStart, e.rRules)
        removeRRulesWithUntilBeforeDtStart(dtStart, e.rRules)
    }

    companion object {
        /**
         * Ensure proper start and end time
         */
        internal fun correctStartAndEndTime(e: Event): DtStart {
            val dtStart = e.dtStart ?: throw InvalidCalendarException("Event without start time")
            e.dtEnd?.let { dtEnd ->
                if (dtStart.date > dtEnd.date) {
                    Ical4Android.log.warning("DTSTART after DTEND; removing DTEND")
                    e.dtEnd = null
                }
            }
            return dtStart
        }

        /**
         * Tries to make the value type of UNTIL and DTSTART the same (both DATE or DATETIME).
         */
        internal fun sameTypeForDtStartAndRruleUntil(dtStart: DtStart, rRules: MutableList<RRule>) {
            if (DateUtils.isDate(dtStart)) {
                for (rRule in rRules) {
                    rRule.recur.until?.let { until ->
                        if (until is DateTime) {
                            Ical4Android.log.warning("DTSTART has DATE, but UNTIL has DATETIME; making UNTIL have DATE only")
                            rRule.recur.until = until.toLocalDate().toIcal4jDate()
                        }
                    }
                }
            } else if (DateUtils.isDateTime(dtStart)) {
                val rRuleIterator = rRules.iterator()
                val newRRules = mutableListOf<RRule>()
                while (rRuleIterator.hasNext()) {
                    val rRule = rRuleIterator.next()
                    rRule.recur.until?.let { until ->
                        if (until !is DateTime) {
                            Ical4Android.log.warning("DTSTART has DATETIME, but UNTIL has DATE; copying time from DTSTART to UNTIL")
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
                            ).toIcal4jDateTime()
                            val newRRule = RRule(Recur.Builder(rRule.recur)
                                .until(newUntil)
                                .count(-1)
                                .build())

                            // remove current RRULE and remember new one to be added
                            rRuleIterator.remove()
                            newRRules += newRRule
                        }
                    }
                }
                // add repaired RRULEs
                rRules += newRRules
            } else
                throw InvalidCalendarException("Event with invalid DTSTART value")
        }

        /**
         * Will remove the RRULES of an event where UNTIL lies before DTSTART
         */
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
        internal fun hasUntilBeforeDtStart(dtStart: DtStart, rRule: RRule): Boolean {
            val until = rRule.recur.until ?: return false
            return until < dtStart.date
        }
    }
}