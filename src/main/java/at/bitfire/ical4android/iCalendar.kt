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
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.util.CompatibilityHints
import net.fortuna.ical4j.util.Strings
import java.io.StringReader
import java.net.URISyntaxException
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

open class iCalendar {

    var uid: String? = null
    var sequence: Int? = null

    companion object {
        // static ical4j initialization
        init {
            Constants.log.info("Enabling ical4j releaxed unfolding, relaxed parsing, Outlook compatibility")
            CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, true)
            CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true)
            CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true)

            // reduce verbosity of those two loggers
            org.slf4j.LoggerFactory.getLogger(net.fortuna.ical4j.data.CalendarParserImpl::class.java)
            Logger.getLogger(net.fortuna.ical4j.data.CalendarParserImpl::class.java.name).level = Level.CONFIG
            org.slf4j.LoggerFactory.getLogger(net.fortuna.ical4j.model.Recur::class.java)
            Logger.getLogger(net.fortuna.ical4j.model.Recur::class.java.name).level = Level.CONFIG
        }

        var prodId = ProdId("+//IDN bitfire.at//ical4android")

        private val parameterFactoryRegistry = ParameterFactoryRegistry()
        init {
            parameterFactoryRegistry.register(Email.PARAMETER_NAME, Email.FACTORY)
        }

        private val propertyFactoryRegistry = PropertyFactoryRegistry()
        init {
            propertyFactoryRegistry.register(Color.PROPERTY_NAME, Color.FACTORY)
        }

        @JvmStatic
        protected fun calendarBuilder() = CalendarBuilder(
                CalendarParserFactory.getInstance().createParser(),
                propertyFactoryRegistry, parameterFactoryRegistry, DateUtils.tzRegistry)


        // time zone helpers

        @JvmStatic
        fun isDateTime(date: DateProperty?) = date != null && date.date is DateTime

        /**
         * Takes a string with a timezone definition and returns the time zone ID.
         * @param timezoneDef time zone definition (VCALENDAR with VTIMEZONE component)
         * @return time zone id (TZID) if VTIMEZONE contains a TZID, null otherwise
         */
        @JvmStatic
        fun TimezoneDefToTzId(timezoneDef: String): String? {
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

        @JvmStatic
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
    ): Property(PROPERTY_NAME, PropertyFactoryImpl.getInstance()) {
        companion object {
            val FACTORY = Factory()
            val PROPERTY_NAME = "COLOR"
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

        class Factory: PropertyFactory<Color> {
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
    class Email(): Parameter(PARAMETER_NAME, ParameterFactoryImpl.getInstance()) {
        companion object {
            val FACTORY = Factory()
            val PARAMETER_NAME = "EMAIL"
        }

        var email: String? = null
        override fun getValue() = email

        constructor(aValue: String): this()
        {
            email = Strings.unquote(aValue)
        }

        class Factory: ParameterFactory<Email> {
            @Throws(URISyntaxException::class)
            override fun createParameter(value: String) = Email(value)

            override fun supports(name: String) = name == PARAMETER_NAME
        }
    }

}