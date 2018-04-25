/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarParserFactory
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.component.*
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.TzUrl
import net.fortuna.ical4j.util.Strings
import java.io.StringReader
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

open class ICalendar {

    var uid: String? = null
    var sequence: Int? = null

    companion object {
        // static ical4j initialization
        init {
            // reduce verbosity of those two loggers
            org.slf4j.LoggerFactory.getLogger(net.fortuna.ical4j.data.CalendarParserImpl::class.java)
            Logger.getLogger(net.fortuna.ical4j.data.CalendarParserImpl::class.java.name).level = Level.CONFIG
            org.slf4j.LoggerFactory.getLogger(net.fortuna.ical4j.model.Recur::class.java)
            Logger.getLogger(net.fortuna.ical4j.model.Recur::class.java.name).level = Level.CONFIG
        }

        var prodId = ProdId("+//IDN bitfire.at//ical4android")

        private val parameterFactoryRegistry = ParameterFactoryRegistry()
        init {
            parameterFactoryRegistry.register(Email.PARAMETER_NAME, Email.Factory)
        }

        private val propertyFactoryRegistry = PropertyFactoryRegistry()
        init {
            propertyFactoryRegistry.register(Color.PROPERTY_NAME, Color.Factory)
        }

        @JvmStatic
        protected fun calendarBuilder() = CalendarBuilder(
                CalendarParserFactory.getInstance().createParser(),
                propertyFactoryRegistry, parameterFactoryRegistry,
                TimeZoneRegistryFactory.getInstance().createRegistry())


        // time zone helpers

        fun isDateTime(date: DateProperty?) = date != null && date.date is DateTime

        /**
         * Minifies a VTIMEZONE so that only components after [start] are kept.
         * Doesn't return the smallest possible VTIMEZONE at the moment, but
         * reduces its size significantly.
         *
         * @param tz      Time zone definition to minify. Attention: the observances of this object
         *                will be modified!
         * @param start   Start date for components
         */
        fun minifyVTimeZone(tz: VTimeZone, start: Date) {
            // find latest matching STANDARD/DAYLIGHT component,
            // keep components at/after "start"
            val iter = tz.observances.iterator()
            var latestDaylight: Pair<Date, Observance>? = null
            var latestStandard: Pair<Date, Observance>? = null
            val keep = mutableSetOf<Observance>()
            while (iter.hasNext()) {
                val entry = iter.next() as Observance
                val latest = entry.getLatestOnset(start)

                if (latest == null  /* observance begins after "start" */ ||
                    latest >= start /* observance has onsets at/after "start" */ ) {
                    keep += entry
                    continue
                }

                when (entry) {
                    is Standard -> {
                        if (latestStandard == null || latest.after(latestStandard.first))
                            latestStandard = Pair(latest, entry)
                    }
                    is Daylight -> {
                        if (latestDaylight == null || latest.after(latestDaylight.first))
                            latestDaylight = Pair(latest, entry)
                    }
                }
            }
            latestStandard?.second?.let { keep += it }
            latestDaylight?.second?.let { keep += it }

            // actually remove all observances that shall not be kept
            val iter2 = tz.observances.iterator()
            while (iter2.hasNext()) {
                val entry = iter2.next() as Observance
                if (!keep.contains(entry))
                    iter2.remove()
            }

            // remove TZURL
            tz.properties.filter { it is TzUrl }.forEach {
                tz.properties.remove(it)
            }
        }

        /**
         * Takes a string with a timezone definition and returns the time zone ID.
         * @param timezoneDef time zone definition (VCALENDAR with VTIMEZONE component)
         * @return time zone id (TZID) if VTIMEZONE contains a TZID, null otherwise
         */
        fun timezoneDefToTzId(timezoneDef: String): String? {
            try {
                val builder = CalendarBuilder()
                val cal = builder.build(StringReader(timezoneDef))
                val timezone = cal.getComponent(VTimeZone.VTIMEZONE) as VTimeZone?
                timezone?.timeZoneId?.let { return it.value }
            } catch (e: ParserException) {
                Constants.log.log(Level.SEVERE, "Can't understand time zone definition", e)
            }
            return null
        }


        // misc. iCalendar helpers

        internal fun alarmMinBefore(alarm: VAlarm): Int {
            var minutes = 0
            alarm.trigger?.duration?.let { duration ->
                // negative value in TRIGGER means positive value in Reminders.MINUTES and vice versa
                minutes = -(((duration.weeks * 7 + duration.days) * 24 + duration.hours) * 60 + duration.minutes + duration.seconds/60)
                if (duration.isNegative)
                    minutes *= -1
            }
            return minutes
        }

    }


    protected fun generateUID() {
        uid = UUID.randomUUID().toString()
    }

    override fun toString() = MiscUtils.reflectionToString(this)


    // ical4j helpers and extensions

    /** COLOR property for VEVENT components [RFC 7986 5.9 COLOR] */
    class Color(
            var value: EventColor? = null
    ): Property(PROPERTY_NAME, Factory) {
        companion object {
            const val PROPERTY_NAME = "COLOR"
        }

        override fun getValue() = value?.name

        override fun setValue(name: String?) {
            name?.let {
                try {
                    value = EventColor.valueOf(name.toLowerCase())
                } catch(e: IllegalArgumentException) {
                    Constants.log.warning("Ignoring unknown COLOR $name")
                }
            }
        }

        override fun validate() {
        }

        object Factory: PropertyFactory<Color> {
            override fun createProperty() = Color()

            override fun createProperty(params: ParameterList?, value: String?): Color {
                val c = Color()
                c.setValue(value)
                return c
            }

            override fun supports(property: String?) = property == PROPERTY_NAME
        }

    }

    /** EMAIL parameter for ATTENDEE properties, as used by iCloud:
        ATTENDEE;EMAIL=bla@domain.tld;/path/to/principal
    */
    class Email(): Parameter(PARAMETER_NAME, Factory) {
        companion object {
            const val PARAMETER_NAME = "EMAIL"
        }

        var email: String? = null
        override fun getValue() = email

        constructor(aValue: String): this()
        {
            email = Strings.unquote(aValue)
        }

        object Factory: ParameterFactory<Email> {
            override fun createParameter(value: String) = Email(value)
            override fun supports(name: String) = name == PARAMETER_NAME
        }
    }

}