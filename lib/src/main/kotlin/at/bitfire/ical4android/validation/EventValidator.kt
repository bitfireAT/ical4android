/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import at.bitfire.ical4android.Event
import at.bitfire.ical4android.Ical4Android
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toZoneIdCompat
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.util.TimeZones
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.util.Calendar
import java.util.Date
import java.util.LinkedList
import java.util.TimeZone

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
        internal fun correctStartAndEndTime(e: Event): DtStart<Temporal> {
            val dtStart = e.dtStart ?: throw InvalidCalendarException("Event without start time")
            val startInstant = Instant.from(dtStart.date)
            e.dtEnd?.let { dtEnd ->
                val endInstant = Instant.from(dtEnd.date)
                if (startInstant > endInstant) {
                    Ical4Android.log.warning("DTSTART after DTEND; removing DTEND")
                    e.dtEnd = null
                }
            }
            return dtStart
        }

        /**
         * Tries to make the value type of UNTIL and DTSTART the same (both DATE or DATETIME).
         */
        internal fun sameTypeForDtStartAndRruleUntil(
            dtStart: DtStart<Temporal>,
            rRules: LinkedList<RRule<Temporal>>
        ) {
            if (DateUtils.isDate(dtStart)) {
                // DTSTART is a DATE
                val newRRules = mutableListOf<RRule<Temporal>>()
                for (rRule in rRules) {
                    rRule.recur.until?.let { until ->
                        if (until.isSupported(ChronoUnit.HOURS)) {
                            Ical4Android.log.warning("DTSTART has DATE, but UNTIL has DATETIME; making UNTIL have DATE only")

                            val newUntil = LocalDate.from(until)

                            // remove current RRULE and remember new one to be added
                            val newRRule = RRule(
                                Recur.Builder(rRule.recur)
                                    .until(newUntil)
                                    .build()
                            )
                            Ical4Android.log.info("New $newRRule (was ${rRule.toString().trim()})")
                            newRRules += newRRule
                        }
                    }
                }
                // add repaired RRULEs
                rRules += newRRules
            } else if (DateUtils.isDateTime(dtStart)) {
                // DTSTART is a DATE-TIME
                val newRRules = mutableListOf<RRule<Temporal>>()
                val rRuleIterator = rRules.iterator()
                while (rRuleIterator.hasNext()) {
                    val rRule = rRuleIterator.next()
                    rRule.recur.until?.let { until ->
                        if (!until.isSupported(ChronoUnit.HOURS)) {
                            Ical4Android.log.warning("DTSTART has DATETIME, but UNTIL has DATE; copying time from DTSTART to UNTIL")
                            val dtStartTimeZone =
                                try {
                                    ZonedDateTime.from(dtStart.date).zone
                                } catch (_: DateTimeException) {
                                    if (dtStart.isUtc)
                                        ZoneOffset.UTC
                                    else /* floating time */
                                        ZoneId.systemDefault()
                                }

                            val dtStartCal = Calendar.getInstance(
                                net.fortuna.ical4j.model.TimeZone.getTimeZone(dtStartTimeZone)
                            ).apply {
                                time = Date(Instant.from(dtStart.date).toEpochMilli())
                            }
                            val dtStartTime = LocalTime.of(
                                dtStartCal.get(Calendar.HOUR_OF_DAY),
                                dtStartCal.get(Calendar.MINUTE),
                                dtStartCal.get(Calendar.SECOND)
                            )

                            val newUntil = LocalDate.from(until)
                                .atTime(dtStartTime)
                                .atZone(dtStartTimeZone)

                            // Android requires UNTIL in UTC as defined in RFC 2445.
                            // https://android.googlesource.com/platform/frameworks/opt/calendar/+/refs/tags/android-12.1.0_r27/src/com/android/calendarcommon2/RecurrenceProcessor.java#93
                            val newUntilUTC = newUntil.withZoneSameInstant(ZoneOffset.UTC)

                            // remove current RRULE and remember new one to be added
                            val newRRule = RRule(Recur.Builder(rRule.recur)
                                .until(newUntilUTC)
                                .build())
                            Ical4Android.log.info("New $newRRule (was ${rRule.toString().trim()})")
                            newRRules += newRRule
                            rRuleIterator.remove()
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
        internal fun removeRRulesWithUntilBeforeDtStart(
            dtStart: DtStart<Temporal>,
            rRules: LinkedList<RRule<Temporal>>
        ) {
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
        internal fun hasUntilBeforeDtStart(dtStart: DtStart<Temporal>, rRule: RRule<Temporal>): Boolean {
            val until = Instant.from(rRule.recur.until) ?: return false
            val start = Instant.from(dtStart.date)
            return until < start
        }
    }
}
