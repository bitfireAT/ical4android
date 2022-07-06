/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.util

import at.bitfire.ical4android.Event
import at.bitfire.ical4android.Ical4Android
import at.bitfire.ical4android.InvalidCalendarException
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime

class EventRepairer {

    companion object {
        fun validateAndRepair(e: Event) {
            startAndEndTime(e)
            rRuleTime(e)
        }

        /**
         * Ensure proper start and end time
         */
        internal  fun startAndEndTime(e: Event) {
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
            if (e.dtStart == null) return
            val dtStart = e.dtStart!!.value
            if (dtStart.length == 8) {
                // no time in DTSTART (value is DATE)
                for (rRule in e.rRules) {
                    if (rRule.recur.until != null) {
                        val until = rRule.recur.until.toString()
                        if (until.length > 8) {
                            // but time present in UNTIL, removing
                            val newUntil = until.substringBefore("T")
                            Ical4Android.log.warning("DTSTART has no time ($dtStart), but UNTIL does ($until); removing time of UNTIL (UNTIL=$newUntil)")
                            rRule.recur.until = Date(newUntil)
                        }
                    }
                }
            } else if (dtStart.length >= 15) {
                // time present in DTSTART (value is DATETIME)
                for (rRule in e.rRules) {
                    if (rRule.recur.until != null) {
                        val until = rRule.recur.until.toString()
                        if (until.length == 8) {
                            // but no time in UNTIL, adding
                            val dtStartTime = dtStart.substringAfterLast("T")
                            val newUntil = until + "T" + dtStartTime
                            Ical4Android.log.warning("DTSTART includes time ($dtStart), but UNTIL does not ($until); adding time to until (UNTIL=$newUntil)")
                            rRule.recur.until = DateTime(newUntil)
                        }
                    }
                }
            } else
                throw InvalidCalendarException("Event with invalid DTSTART value")
        }

    }
}
