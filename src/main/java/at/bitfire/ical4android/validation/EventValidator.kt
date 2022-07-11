/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import at.bitfire.ical4android.DateUtils
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.Ical4Android
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule

/**
 * Sometimes calendar apps or servers provide invalid event definitions, which are then
 * saved in the calendar provider. This class tries to validate, repair and assume
 * whatever seems appropriate to avoid sending invalid events (that may be rejected by servers).
 */
object EventValidator {

    /**
     * Repair an event so that it's more conforming to iCalendar standard. This will
     * be used before an event is uploaded to a server to reduce the cases where the
     * servers reject uploaded events.
     */
    fun repair(e: Event) {
        val dtStart = correctStartAndEndTime(e)
        sameTypeForDtStartAndRruleUntil(dtStart, e.rRules)
        removeRRulesWithUntilBeforeDtStart(dtStart, e.rRules)
    }


    /**
     * Ensure proper start and end time
     */
    fun correctStartAndEndTime(e: Event): DtStart {
        if (e.dtStart == null)
            throw InvalidCalendarException("Event without start time")
        else if (e.dtEnd != null && e.dtStart!!.date > e.dtEnd!!.date) {
            Ical4Android.log.warning("DTSTART after DTEND; removing DTEND")
            e.dtEnd = null
        }
        return e.dtStart!!
    }

    /**
     * Tries to make the value type of UNTIL and DTSTART the same (both DATE or DATETIME).
     */
    fun sameTypeForDtStartAndRruleUntil(dtStart: DtStart, rRules: MutableList<RRule>) {
        if (DateUtils.isDate(dtStart)) {
            for (rRule in rRules) {
                rRule.recur.until?.let { until ->
                    if (until is DateTime) {
                        Ical4Android.log.warning("DTSTART has DATE, but UNTIL has DATETIME; making UNTIL have DATE only")
                        rRule.recur.until = Date(until.toLocalDate().toIcal4jDate())
                    }
                }
            }
        } else if (DateUtils.isDateTime(dtStart)) {
            for (rRule in rRules) {
                rRule.recur.until?.let { until ->
                    if (until !is DateTime) {
                        Ical4Android.log.warning("DTSTART has DATETIME, but UNTIL has DATE; making UNTIL have DATETIME")
                        rRule.recur.until = DateTime(until)
                    }
                }
            }
        } else
            throw InvalidCalendarException("Event with invalid DTSTART value")
    }

    /**
     * Will remove the RRULES of an event where UNTIL lies before DTSTART
     */
    fun removeRRulesWithUntilBeforeDtStart(dtStart: DtStart, rRules: MutableList<RRule>) {
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
    fun hasUntilBeforeDtStart(dtStart: DtStart, rRule: RRule): Boolean {
        val until = rRule.recur.until ?: return false
        return until < dtStart.date
    }

}