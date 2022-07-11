/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime

class EventValidator {

    companion object {

        fun repair(e: Event) {
            startAndEndTime(e)
            rRuleTime(e)
        }

        /**
         * Ensure proper start and end time
         */
        internal fun startAndEndTime(e: Event) {
            if (e.dtStart == null)
                throw InvalidCalendarException("Event without start time")
            else if (e.dtEnd != null && e.dtStart!!.date > e.dtEnd!!.date) {
                Ical4Android.log.warning("DTSTART after DTEND; removing DTEND")
                e.dtEnd = null
            }
        }

        /**
         * Recurrence Rule
         * "The value of the UNTIL rule part MUST have the same value type as the "DTSTART" property."
         * https://datatracker.ietf.org/doc/html/rfc5545#section-3.3.10
         */
        internal fun rRuleTime(e: Event) {
            val dtStart = e.dtStart ?: return
            if (DateUtils.isDate(dtStart)) {
                for (rRule in e.rRules) {
                    rRule.recur.until?.let { until ->
                        if (until is DateTime) {
                            Ical4Android.log.warning("DTSTART has DATE, but UNTIL has DATETIME; removing time from UNTIL")
                            rRule.recur.until = Date(until.toLocalDate().toIcal4jDate())
                        }
                    }
                }
            } else if (DateUtils.isDateTime(dtStart)) {
                for (rRule in e.rRules) {
                    rRule.recur.until?.let { until ->
                        if (until !is DateTime) {
                            Ical4Android.log.warning("DTSTART has DATETIME, but UNTIL has DATE; adding time to UNTIL")
                            rRule.recur.until = DateTime(until)
                        }
                    }
                }
            } else
                throw InvalidCalendarException("Event with invalid DTSTART value")
        }

    }
}