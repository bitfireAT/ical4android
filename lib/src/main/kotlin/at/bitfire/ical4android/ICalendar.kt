/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import at.bitfire.ical4android.util.DateUtils.instantDates
import at.bitfire.ical4android.util.MiscUtils
import at.bitfire.ical4android.validation.ICalPreprocessor
import net.fortuna.ical4j.data.*
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.Daylight
import net.fortuna.ical4j.model.component.Observance
import net.fortuna.ical4j.model.component.Standard
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.TzUrl
import net.fortuna.ical4j.model.property.XProperty
import net.fortuna.ical4j.validate.ValidationException
import java.io.Reader
import java.io.StringReader
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.Period
import java.time.temporal.Temporal
import java.util.LinkedList
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Trigger
import net.fortuna.ical4j.model.property.TzId

open class ICalendar {

    var uid: String? = null
    var sequence: Int? = null

    /** list of CUAs which have edited the event since last sync */
    var userAgents = LinkedList<String>()

    companion object {

        // static ical4j initialization
        init {
            // reduce verbosity of various ical4j loggers
            org.slf4j.LoggerFactory.getLogger(net.fortuna.ical4j.data.CalendarParserImpl::class.java)
            Logger.getLogger(net.fortuna.ical4j.data.CalendarParserImpl::class.java.name).level = Level.CONFIG

            org.slf4j.LoggerFactory.getLogger(net.fortuna.ical4j.model.Recur::class.java)
            Logger.getLogger(net.fortuna.ical4j.model.Recur::class.java.name).level = Level.CONFIG

            org.slf4j.LoggerFactory.getLogger(net.fortuna.ical4j.data.FoldingWriter::class.java)
            Logger.getLogger(net.fortuna.ical4j.data.FoldingWriter::class.java.name).level = Level.CONFIG
        }

        // known iCalendar properties
        const val CALENDAR_NAME = "X-WR-CALNAME"
        const val CALENDAR_COLOR = "X-APPLE-CALENDAR-COLOR"

        /**
         * Default PRODID used when generating iCalendars. If you want another value, set it
         * statically before writing the first iCalendar.
         */
        var prodId = ProdId("+//IDN bitfire.at//ical4android")

        fun prodId(userAgents: List<String>): ProdId =
            if (userAgents.isEmpty())
                prodId
            else
                ProdId(prodId.value + " (" + userAgents.joinToString(",") + ")")

        // parser

        /**
         * Parses an iCalendar resource and applies [ICalPreprocessor] to increase compatibility.
         *
         * @param reader where the iCalendar is taken from
         * @param properties Known iCalendar properties (like [CALENDAR_NAME]) will be put into this map. Key: property name; value: property value
         *
         * @return parsed iCalendar resource
         * @throws ParserException when the iCalendar can't be parsed
         * @throws IllegalArgumentException when the iCalendar resource contains an invalid value
         */
        @UsesThreadContextClassLoader
        fun fromReader(reader: Reader, properties: MutableMap<String, String>? = null): Calendar {
            Ical4Android.log.fine("Parsing iCalendar stream")
            Ical4Android.checkThreadContextClassLoader()

            // preprocess stream to work around some problems that can't be fixed later
            val preprocessed = ICalPreprocessor.preprocessStream(reader)

            // parse stream
            val calendar: Calendar
            try {
                calendar = CalendarBuilder(
                    CalendarParserFactory.getInstance().get(),
                    ContentHandlerContext().withSupressInvalidProperties(true),
                    TimeZoneRegistryFactory.getInstance().createRegistry()      // AndroidCompatTimeZoneRegistry
                ).build(preprocessed)
            } catch(e: ParserException) {
                throw InvalidCalendarException("Couldn't parse iCalendar", e)
            } catch(e: IllegalArgumentException) {
                throw InvalidCalendarException("iCalendar contains invalid value", e)
            }

            // apply ICalPreprocessor for increased compatibility
            try {
                ICalPreprocessor.preprocessCalendar(calendar)
            } catch (e: Exception) {
                Ical4Android.log.log(Level.WARNING, "Couldn't pre-process iCalendar", e)
            }

            // fill calendar properties
            properties?.let {
                calendar.getProperty<Property>(CALENDAR_NAME)?.let { calName ->
                    properties[CALENDAR_NAME] = calName.get().value
                }

                calendar.getProperty<Property>(Color.PROPERTY_NAME)?.let { calColor ->
                    properties[Color.PROPERTY_NAME] = calColor.get().value
                }
                calendar.getProperty<Property>(CALENDAR_COLOR)?.let { calColor ->
                    properties[CALENDAR_COLOR] = calColor.get().value
                }
            }

            return calendar
        }


        // time zone helpers

        /**
         * Minifies a VTIMEZONE so that only these observances are kept:
         *
         *   - the last STANDARD observance matching [start], and
         *   - the last DAYLIGHT observance matching [start], and
         *   - observances beginning after [start]
         *
         * Additionally, TZURL properties are filtered.
         *
         * @param originalTz    time zone definition to minify
         * @param start         start date for components (usually DTSTART); *null* if unknown
         * @return              minified time zone definition
         */
        fun minifyVTimeZone(originalTz: VTimeZone, start: Instant?): VTimeZone {
            val newTz = originalTz.copy() as VTimeZone
            val keep = mutableSetOf<Observance>()

            if (start != null) {
                // find latest matching STANDARD/DAYLIGHT observances
                var latestDaylight: Pair<OffsetDateTime, Observance>? = null
                var latestStandard: Pair<OffsetDateTime, Observance>? = null
                for (observance in newTz.observances) {
                    val latest = observance.getLatestOnset(start)

                    if (latest == null)         // observance begins after "start", keep in any case
                        keep += observance
                    else
                        when (observance) {
                            is Standard ->
                                if (latestStandard == null || latest > latestStandard.first)
                                    latestStandard = Pair(latest, observance)
                            is Daylight ->
                                if (latestDaylight == null || latest > latestDaylight.first)
                                    latestDaylight = Pair(latest, observance)
                        }
                }

                // keep latest STANDARD observance
                latestStandard?.second?.let { keep += it }

                // Check latest DAYLIGHT for whether it can apply in the future. Otherwise, DST is not
                // used in this time zone anymore and the DAYLIGHT component can be dropped completely.
                latestDaylight?.second?.let { daylight ->
                    // check whether start time is in DST
                    if (latestStandard != null) {
                        val latestStandardOnset = latestStandard.second.getLatestOnset(start)
                        val latestDaylightOnset = daylight.getLatestOnset(start)
                        if (latestStandardOnset != null &&
                            latestDaylightOnset != null &&
                            latestDaylightOnset > latestStandardOnset) {
                            // we're currently in DST
                            keep += daylight
                            return@let
                        }
                    }

                    // check RRULEs
                    for (rRule in daylight.getProperties<RRule<Temporal>>(Property.RRULE)) {
                        val seed = daylight.getProperty<DtStart<out Temporal>>(Property.DTSTAMP).getOrNull()?.date
                        val nextDstOnset = rRule.recur.getNextDate(seed, start)
                        if (nextDstOnset != null) {
                            // there will be a DST onset in the future -> keep DAYLIGHT
                            keep += daylight
                            return@let
                        }
                    }
                    // no RRULE, check whether there's an RDATE in the future
                    for (rDate in daylight.getProperties<RDate<Temporal>>(Property.RDATE)) {
                        if (rDate.instantDates.any { it >= start }) {
                            // RDATE in the future
                            keep += daylight
                            return@let
                        }
                    }
                }

                // remove all observances that shall not be kept
                val iterator = newTz.observances.iterator() as MutableIterator<Observance>
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (!keep.contains(entry))
                        iterator.remove()
                }
            }

            // remove unnecessary properties
            newTz.propertyList = newTz.propertyList.apply {
                for (item in all)
                    if (item is TzUrl || item is XProperty)
                        remove(item)
            }

            // validate minified timezone
            try {
                newTz.validate()
            } catch (e: ValidationException) {
                // This should never happen!
                Ical4Android.log.log(Level.WARNING, "Minified timezone is invalid, using original one", e)
                return originalTz
            }

            return newTz
        }

        /**
         * Takes a string with a timezone definition and returns the time zone ID.
         * @param timezoneDef time zone definition (VCALENDAR with VTIMEZONE component)
         * @return time zone id (TZID) if VTIMEZONE contains a TZID, null otherwise
         */
        @UsesThreadContextClassLoader
        fun timezoneDefToTzId(timezoneDef: String): String? {
            Ical4Android.checkThreadContextClassLoader()
            try {
                val builder = CalendarBuilder()
                val cal = builder.build(StringReader(timezoneDef))
                val vTimeZone = cal.getComponent<VTimeZone>(VTimeZone.VTIMEZONE).getOrNull()
                val tzId = vTimeZone?.getProperty<TzId>(Property.TZID)?.getOrNull()
                tzId?.let { return it.value }
            } catch (e: ParserException) {
                Ical4Android.log.log(Level.SEVERE, "Can't understand time zone definition", e)
            }
            return null
        }

        /**
         * Validates an iCalendar resource.
         *
         * Debug builds only: throws [ValidationException] when the resource is invalid.
         * Release builds only: prints a warning to the log when the resource is invalid.
         *
         * @param ical iCalendar resource to be validated
         *
         * @throws ValidationException when the resource is invalid (only if [BuildConfig.DEBUG] is set)
         */
        fun softValidate(ical: Calendar) {
            try {
                ical.validate(true)
            } catch (e: ValidationException) {
                if (BuildConfig.DEBUG)
                    // debug build, re-throw ValidationException
                    throw e
                else
                    Ical4Android.log.log(Level.WARNING, "iCalendar validation failed - This is only a warning!", e)
            }
        }


        // misc. iCalendar helpers

        /**
         * Calculates the minutes before/after an event/task a given alarm occurs.
         *
         * @param alarm the alarm to calculate the minutes from
         * @param reference reference [VEvent] or [VToDo] to take start/end time from (required for calculations)
         * @param allowRelEnd *true*: caller accepts minutes related to the end;
         * *false*: caller only accepts minutes related to the start
         *
         * Android's alarm granularity is minutes. This methods calculates with milliseconds, but the result
         * is rounded down to minutes (seconds cut off).
         *
         * @return Pair of values:
         *
         * 1. whether the minutes are related to the start or end (always [Related.START] if [allowRelEnd] is *false*)
         * 2. number of minutes before start/end (negative value means number of minutes *after* start/end)
         *
         * May be *null* if there's not enough information to calculate the number of minutes.
         */
        fun vAlarmToMin(alarm: VAlarm, reference: ICalendar, allowRelEnd: Boolean): Pair<Related, Int>? {
            val trigger = alarm.getProperty<Trigger>(Property.TRIGGER).getOrNull() ?: return null

            val minutes: Int    // minutes before/after the event
            var related = trigger.getParameter<Related>(Parameter.RELATED).getOrNull() ?: Related.START

            // event/task start time
            val start: Temporal?
            var end: Temporal?
            when (reference) {
                is Event -> {
                    start = reference.dtStart?.date
                    end = reference.dtEnd?.date
                }
                is Task -> {
                    start = reference.dtStart?.date
                    end = reference.due?.date
                }
                else -> throw IllegalArgumentException("reference must be Event or Task")
            }

            // event/task end time
            if (end == null && start != null) {
                val duration = when (reference) {
                    is Event -> reference.duration?.duration
                    is Task -> reference.duration?.duration
                    else -> throw IllegalArgumentException("reference must be Event or Task")
                }
                if (duration != null)
                    end = start + duration
            }

            // event/task duration
            val duration: Duration? =
                    if (start != null && end != null)
                        Duration.between(start, end)
                    else
                        null

            val triggerDur = trigger.duration
            val triggerTime = trigger.date

            if (triggerDur != null) {
                // TRIGGER value is a DURATION. Important:
                // 1) Negative values in TRIGGER mean positive values in Reminders.MINUTES and vice versa.
                // 2) Android doesn't know alarm seconds, but only minutes. Cut off seconds from the final result.
                // 3) DURATION can be a Duration (time-based) or a Period (date-based), which have to be treated differently.
                var millisBefore =
                    when (triggerDur) {
                        is Duration -> -triggerDur.toMillis()
                        is Period -> // TODO: Take time zones into account (will probably be possible with ical4j 4.x).
                            // For instance, an alarm one day before the DST change should be 23/25 hours before the event.
                            -triggerDur.days.toLong()*24*3600000     // months and years are not used in DURATION values; weeks are calculated to days
                        else -> throw AssertionError("triggerDur must be Duration or Period")
                    }

                if (related == Related.END && !allowRelEnd) {
                    if (duration == null) {
                        Ical4Android.log.warning("Event/task without duration; can't calculate END-related alarm")
                        return null
                    }
                    // move alarm towards end
                    related = Related.START
                    millisBefore -= duration.toMillis()
                }
                minutes = (millisBefore / 60000).toInt()

            } else if (triggerTime != null && start != null) {
                // TRIGGER value is a DATE-TIME, calculate minutes from start time
                related = Related.START
                minutes = Duration.between(triggerTime, start).toMinutes().toInt()

            } else {
                Ical4Android.log.log(Level.WARNING, "VALARM TRIGGER type is not DURATION or DATE-TIME (requires event DTSTART for Android), ignoring alarm", alarm)
                return null
            }

            return Pair(related, minutes)
        }

    }


    protected fun generateUID() {
        uid = UUID.randomUUID().toString()
    }

    fun prodId(): ProdId = prodId(userAgents)

    override fun toString() = MiscUtils.reflectionToString(this)

}