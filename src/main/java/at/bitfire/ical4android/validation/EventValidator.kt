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
import java.text.SimpleDateFormat
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
        internal fun sameTypeForDtStartAndRruleUntil(dtStart: DtStart, rRules: MutableList<RRule>) {
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
                            Ical4Android.log.warning("DTSTART has DATETIME, but UNTIL has DATE; copying time from DTSTART to UNTIL")
                            val sdf = SimpleDateFormat("HHmmss", Locale.US).apply {
                                if (dtStart.isUtc) timeZone = TimeZone.getTimeZone("UTC")
                            }
                            val untilDate = SimpleDateFormat("yyyyMMdd", Locale.US).format(until)
                            val dtStartTime = sdf.format(dtStart.date)
                            rRule.recur.until = DateTime(untilDate +"T"+ dtStartTime + "Z".takeIf { dtStart.isUtc })
                        }
                    }
                }
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