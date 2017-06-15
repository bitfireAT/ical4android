/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU General Public License for more details.
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
import net.fortuna.ical4j.util.TimeZones
import java.io.StringReader
import java.net.URISyntaxException
import java.util.*
import java.util.logging.Level

open class iCalendar {
    // static ical4j initialization
    init {
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, true)
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true)
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true)
    }

    var uid: String? = null
    var sequence: Int? = null


    companion object {
        @JvmStatic
        var prodId = ProdId("+//IDN bitfire.at//ical4android")

        @JvmStatic
        private val parameterFactoryRegistry = ParameterFactoryRegistry()
        init {
            parameterFactoryRegistry.register(Email.PARAMETER_NAME, Email.FACTORY)
        }

        @JvmStatic
        protected val calendarBuilder = CalendarBuilder(
                CalendarParserFactory.getInstance().createParser(),
                PropertyFactoryRegistry(), parameterFactoryRegistry, DateUtils.tzRegistry)


        // time zone helpers

        @JvmStatic
        protected fun isDateTime(date: DateProperty?) = date != null && date.date is DateTime

        /**
         * Ensures that a given DateProperty has a time zone with an ID that is available in Android.
         * @param date DateProperty to validate. Values which are not DATE-TIME will be ignored.
         */
        @JvmStatic
        protected fun validateTimeZone(date: DateProperty?) {
            if (isDateTime(date)) {
                val tz = date!!.timeZone ?: return
                val tzID = tz.id ?: return
                val deviceTzID = DateUtils.findAndroidTimezoneID(tzID)
                if (tzID != deviceTzID)
                    date.timeZone = DateUtils.tzRegistry.getTimeZone(deviceTzID)
            }
        }

        /**
         * Returns the time-zone ID for a given date-time, or TIMEZONE_UTC for dates (without time).
         * TIMEZONE_UTC is also returned for DATE-TIMEs in UTC representation.
         * @param date DateProperty (DATE or DATE-TIME) whose time-zone information is used
         */
        @JvmStatic
        protected fun getTzId(date: DateProperty?) =
                if (isDateTime(date!!) && !date.isUtc && date.timeZone != null)
                    date.timeZone.id!!
                else
                    TimeZones.UTC_ID

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
                Constants.log.log(Level.SEVERE, "Can't understand time zone definition", e);
            }
            return null
        }


        // misc. iCalendar helpers

        @JvmStatic
        internal fun alarmMinBefore(alarm: VAlarm): Int {
            var minutes = 0
            alarm.trigger?.duration?.let { duration ->
                // negative value in TRIGGER means positive value in Reminders.MINUTES and vice versa
                minutes = -(((duration.weeks * 7 + duration.days) * 24 + duration.hours) * 60 + duration.minutes)
                if (duration.isNegative)
                    minutes *= -1
            }
            return minutes
        }

    }


    protected fun generateUID() {
        uid = UUID.randomUUID().toString()
    }


    // ical4j helpers and extensions

    /** EMAIL property for ATTENDEE properties, as used by iCloud:
        ATTENDEE;EMAIL=bla@domain.tld;/path/to/principal
    */
    class Email(): Parameter(PARAMETER_NAME, ParameterFactoryImpl.getInstance()) {

        companion object {
            val FACTORY = Factory()

            @JvmField
            val PARAMETER_NAME = "EMAIL"
        }

        var email: String? = null
        override fun getValue() = email

        constructor(aValue: String)
            : this()
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