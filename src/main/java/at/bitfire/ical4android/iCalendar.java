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

package at.bitfire.ical4android;


import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarParserFactory;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterFactory;
import net.fortuna.ical4j.model.ParameterFactoryImpl;
import net.fortuna.ical4j.model.ParameterFactoryRegistry;
import net.fortuna.ical4j.model.PropertyFactoryRegistry;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.util.CompatibilityHints;
import net.fortuna.ical4j.util.Strings;
import net.fortuna.ical4j.util.TimeZones;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.TimeZone;
import java.util.UUID;
import java.util.logging.Level;

import lombok.Getter;
import lombok.NonNull;


public class iCalendar {
    // static ical4j initialization
    static {
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, true);
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true);
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true);
    }

    public static ProdId prodId = new ProdId("+//IDN bitfire.at//ical4android");

    public String uid;
    public Integer sequence;


    protected void generateUID() {
        uid = UUID.randomUUID().toString();
    }


    // time zone helpers

    protected static boolean isDateTime(DateProperty date) {
        return date != null && date.getDate() instanceof DateTime;
    }

    /**
     * Returns the time-zone ID for a given date-time, or TIMEZONE_UTC for dates (without time).
     * TIMEZONE_UTC is also returned for DATE-TIMEs in UTC representation.
     *
     * @param date DateProperty (DATE or DATE-TIME) whose time-zone information is used
     */
    protected static String getTzId(DateProperty date) {
        if (isDateTime(date) && !date.isUtc() && date.getTimeZone() != null)
            return date.getTimeZone().getID();
        else
            return TimeZones.UTC_ID;
    }

    /**
     * Ensures that a given DateProperty has a time zone with an ID that is available in Android.
     *
     * @param date DateProperty to validate. Values which are not DATE-TIME will be ignored.
     */
    protected static void validateTimeZone(DateProperty date) {
        if (isDateTime(date)) {
            final TimeZone tz = date.getTimeZone();
            if (tz == null)
                return;
            final String tzID = tz.getID();
            if (tzID == null)
                return;

            String deviceTzID = DateUtils.findAndroidTimezoneID(tzID);
            if (!tzID.equals(deviceTzID))
                date.setTimeZone(DateUtils.tzRegistry.getTimeZone(deviceTzID));
        }
    }

    /**
     * Takes a string with a timezone definition and returns the time zone ID.
     *
     * @param timezoneDef time zone definition (VCALENDAR with VTIMEZONE component)
     * @return time zone id (TZID)  if VTIMEZONE contains a TZID,
     * null                 otherwise
     */
    public static String TimezoneDefToTzId(@NonNull String timezoneDef) {
        try {
            CalendarBuilder builder = new CalendarBuilder();
            net.fortuna.ical4j.model.Calendar cal = builder.build(new StringReader(timezoneDef));
            VTimeZone timezone = (VTimeZone) cal.getComponent(VTimeZone.VTIMEZONE);
            if (timezone != null && timezone.getTimeZoneId() != null)
                return timezone.getTimeZoneId().getValue();
        } catch (IOException|ParserException e) {
            Constants.log.log(Level.SEVERE, "Can't understand time zone definition", e);
        }
        return null;
    }


    // misc. iCalendar helpers

    protected static int alarmMinBefore(VAlarm alarm) {
        int minutes = 0;
        if (alarm.getTrigger() != null) {
            Dur duration = alarm.getTrigger().getDuration();
            if (duration != null) {
                // negative value in TRIGGER means positive value in Reminders.MINUTES and vice versa
                minutes = -(((duration.getWeeks() * 7 + duration.getDays()) * 24 + duration.getHours()) * 60 + duration.getMinutes());
                if (duration.isNegative())
                    minutes *= -1;
            }
        }
        return minutes;
    }


    // ical4j helpers and extensions

    private static final ParameterFactoryRegistry parameterFactoryRegistry = new ParameterFactoryRegistry();

    static {
        parameterFactoryRegistry.register(Email.PARAMETER_NAME, Email.FACTORY);
    }

    protected static CalendarBuilder calendarBuilder() {
        return new CalendarBuilder(
                CalendarParserFactory.getInstance().createParser(),
                new PropertyFactoryRegistry(), parameterFactoryRegistry, DateUtils.tzRegistry);
    }


    public static class Email extends Parameter {
        /* EMAIL property for ATTENDEE properties, as used by iCloud:
           ATTENDEE;EMAIL=bla@domain.tld;/path/to/principal
        */
        public static final ParameterFactory FACTORY = new Factory();

        public static final String PARAMETER_NAME = "EMAIL";
        @Getter private String value;

        protected Email() {
            super(PARAMETER_NAME, ParameterFactoryImpl.getInstance());
        }

        public Email(String aValue) {
            super(PARAMETER_NAME, ParameterFactoryImpl.getInstance());
            value = Strings.unquote(aValue);
        }

        public static class Factory implements ParameterFactory {
            @Override
            public Parameter createParameter(String value) throws URISyntaxException {
                return new Email(value);
            }

            @Override
            public boolean supports(String name) {
                return false;
            }
        }
    }
}