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

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.Sequence;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import lombok.Cleanup;
import lombok.NonNull;
import lombok.ToString;

@ToString(of={"uid","dtStart","summary"})
public class Event extends iCalendar {
    public static final String CALENDAR_NAME = "X-WR-CALNAME";

    // uid and sequence are inherited from iCalendar
    public RecurrenceId recurrenceId;

    public String summary, location, description;

    public DtStart dtStart;
    public DtEnd dtEnd;

    public Duration duration;
    public RRule rRule;
    public ExRule exRule;
    public final List<RDate> rDates = new LinkedList<>();
    public final List<ExDate> exDates = new LinkedList<>();

    public final List<Event> exceptions = new LinkedList<>();

    public Boolean forPublic;
    public Status status;

    public boolean opaque;

    public Organizer organizer;
    public final List<Attendee> attendees = new LinkedList<>();

    public final List<VAlarm> alarms = new LinkedList<>();

    // unknown properties
    protected static final String[] knownPropertyNames = {
            Uid.UID, Sequence.SEQUENCE,
            RecurrenceId.RECURRENCE_ID,
            Summary.SUMMARY, Location.LOCATION, Description.DESCRIPTION,
            DtStart.DTSTART, DtEnd.DTEND,
            Duration.DURATION, RRule.RRULE, ExRule.EXRULE, RDate.RDATE, ExDate.EXDATE,
            Clazz.CLASS,
            Status.STATUS, Transp.TRANSP,
            Organizer.ORGANIZER, Attendee.ATTENDEE,
            DtStamp.DTSTAMP, LastModified.LAST_MODIFIED     // ignore current DTSTAMP and LAST-MODIFIED
    };
    public final List<Property> unknownProperties = new LinkedList<>();


    /**
     * Parses an InputStream that contains iCalendar VEVENTs.
     *
     * @param stream        input stream containing the VEVENTs
     * @param charset       charset of the input stream or null (will assume UTF-8)
     * @param properties    map of properties, will be filled with CALENDAR_* values, if applicable (may be null)
     * @return              array of filled Event data objects (may have size 0) – doesn't return null
     * @throws InvalidCalendarException on parser exceptions
     */
    @SuppressWarnings("unchecked")
    public static Event[] fromStream(@NonNull InputStream stream, Charset charset, Map<String, String> properties) throws IOException, InvalidCalendarException {
        Constants.log.fine("Parsing iCalendar stream");
        final Calendar ical;
        try {
            if (charset != null) {
                @Cleanup InputStreamReader reader = new InputStreamReader(stream, charset);
                ical = calendarBuilder().build(reader);
            } else
                ical = calendarBuilder().build(stream);
        } catch (ParserException e) {
            throw new InvalidCalendarException("Couldn't parse calendar resource", e);
        }

        if (properties != null) {
            Property calName = ical.getProperty(CALENDAR_NAME);
            if (calName != null)
                properties.put(CALENDAR_NAME, calName.getValue());
        }

        List<VEvent> vEvents = ical.getComponents(Component.VEVENT);

        // make sure every event has an UID
        for (VEvent vEvent : vEvents)
            if (vEvent.getUid() == null) {
                Uid uid = new Uid(UUID.randomUUID().toString());
                Constants.log.warning("Found VEVENT without UID, using a random one: " + uid.getValue());
                vEvent.getProperties().add(uid);
            }

        Constants.log.fine("Assigning exceptions to master events");
        Map<String,VEvent> masterEvents = new HashMap<>(vEvents.size());
        Map<String,Map<String,VEvent>> exceptions = new HashMap<>();

        for (VEvent vEvent : vEvents) {
            final String uid = vEvent.getUid().getValue();
            int sequence = vEvent.getSequence() == null ? 0 : vEvent.getSequence().getSequenceNo();

            if (vEvent.getRecurrenceId() == null) {
                // master event (no RECURRENCE-ID)
                VEvent event = masterEvents.get(uid);
                // If there are multiple entries, compare SEQUENCE and use the one with higher SEQUENCE.
                // If the SEQUENCE is identical, use latest version.
                if (event == null || (event.getSequence() != null && sequence >= event.getSequence().getSequenceNo()))
                    masterEvents.put(uid, vEvent);

            } else {
                // exception (RECURRENCE-ID)
                Map<String,VEvent> ex = exceptions.get(uid);
                // first index level: UID
                if (ex == null) {
                    ex = new HashMap<>();
                    exceptions.put(uid, ex);
                }
                // second index level: RECURRENCE-ID
                String recurrenceID = vEvent.getRecurrenceId().getValue();
                VEvent event = ex.get(recurrenceID);
                if (event == null || (event.getSequence() != null && sequence >= event.getSequence().getSequenceNo()))
                    ex.put(recurrenceID, vEvent);
            }
        }

        List<Event> events = new ArrayList<>(masterEvents.size());
        for (Map.Entry<String, VEvent> masterEvent : masterEvents.entrySet()) {
            String uid = masterEvent.getKey();
            Event event = fromVEvent(masterEvent.getValue());

            Map<String,VEvent> eventExceptions = exceptions.get(uid);
            if (eventExceptions != null)
                for (VEvent ex : eventExceptions.values())
                    event.exceptions.add(fromVEvent(ex));

            events.add(event);
        }

        return events.toArray(new Event[events.size()]);
    }

    /**
     * Same as #{@link #fromStream(InputStream, Charset, Map)}, but without properties map.
     */
    public static Event[] fromStream(@NonNull InputStream stream, Charset charset) throws IOException, InvalidCalendarException {
        return fromStream(stream, charset, null);
    }


    protected static Event fromVEvent(VEvent event) throws InvalidCalendarException {
        final Event e = new Event();

        // default values
        e.sequence = 0;
        e.opaque = true;
        e.forPublic = true;

        // process properties
        for (Property prop : event.getProperties()) {
            if (prop instanceof Uid)
                e.uid = prop.getValue();

            else if (prop instanceof RecurrenceId)
                e.recurrenceId = (RecurrenceId)prop;

            else if (prop instanceof Sequence)
                e.sequence = ((Sequence)prop).getSequenceNo();

            else if (prop instanceof DtStart)
                e.dtStart = (DtStart)prop;
            else if (prop instanceof DtEnd)
                e.dtEnd = (DtEnd)prop;
            else if (prop instanceof Duration)
                e.duration = (Duration)prop;

            else if (prop instanceof RRule)
                e.rRule = (RRule)prop;
            else if (prop instanceof RDate)
                e.rDates.add((RDate)prop);
            else if (prop instanceof ExRule)
                e.exRule = (ExRule)prop;
            else if (prop instanceof ExDate)
                e.exDates.add((ExDate)prop);

            else if (prop instanceof Summary)
                e.summary = prop.getValue();
            else if (prop instanceof Location)
                e.location = prop.getValue();
            else if (prop instanceof Description)
                e.description = prop.getValue();

            else if (prop instanceof Status)
                e.status = (Status)prop;
            else if (prop instanceof Transp)
                e.opaque = prop == Transp.OPAQUE;

            else if (prop instanceof Clazz)
                e.forPublic = prop == Clazz.PUBLIC;

            else if (prop instanceof Organizer)
                e.organizer = (Organizer)prop;
            else if (prop instanceof Attendee)
                e.attendees.add((Attendee)prop);

            else if (prop instanceof ProdId || prop instanceof DtStamp || prop instanceof LastModified)
                /* ignore */;

            else    // retain unknown properties
                e.unknownProperties.add(prop);
        }

        // calculate DtEnd from Duration
        if (e.dtEnd == null && e.duration != null)
            e.dtEnd = event.getEndDate(true);

        e.alarms.addAll(event.getAlarms());

        // validation
        if (e.dtStart == null)
            throw new InvalidCalendarException("Event without start time");

        validateTimeZone(e.dtStart);
        validateTimeZone(e.dtEnd);

        return e;
    }

    @SuppressWarnings("unchecked")
    public void write(OutputStream os) throws IOException {
        net.fortuna.ical4j.model.Calendar ical = new net.fortuna.ical4j.model.Calendar();
        ical.getProperties().add(Version.VERSION_2_0);
        ical.getProperties().add(prodId);

        // "master event" (without exceptions)
        ComponentList components = ical.getComponents();
        VEvent master = toVEvent(new Uid(uid));
        components.add(master);

        // remember used time zones
        Set<TimeZone> usedTimeZones = new HashSet<>();
        if (dtStart != null && dtStart.getTimeZone() != null)
            usedTimeZones.add(dtStart.getTimeZone());
        if (dtEnd != null && dtEnd.getTimeZone() != null)
            usedTimeZones.add(dtEnd.getTimeZone());

        // recurrence exceptions
        for (Event exception : exceptions) {
            // create VEVENT for exception
            VEvent vException = exception.toVEvent(master.getUid());

            components.add(vException);

            // remember used time zones
            if (exception.dtStart != null && exception.dtStart.getTimeZone() != null)
                usedTimeZones.add(exception.dtStart.getTimeZone());
            if (exception.dtEnd != null && exception.dtEnd.getTimeZone() != null)
                usedTimeZones.add(exception.dtEnd.getTimeZone());
        }

        // add VTIMEZONE components
        for (net.fortuna.ical4j.model.TimeZone timeZone : usedTimeZones)
            ical.getComponents().add(timeZone.getVTimeZone());

        CalendarOutputter output = new CalendarOutputter(false);
        try {
            output.output(ical, os);
        } catch (ValidationException e) {
            Constants.log.log(Level.SEVERE, "Couldn't generate valid VEVENT", e);
        }
    }

    protected VEvent toVEvent(Uid uid) {
        VEvent event = new VEvent();
        PropertyList props = event.getProperties();

        if (uid != null)
            props.add(uid);
        if (recurrenceId != null)
            props.add(recurrenceId);
        if (sequence != null && sequence != 0)
            props.add(new Sequence(sequence));

        props.add(dtStart);
        if (dtEnd != null)
            props.add(dtEnd);
        if (duration != null)
            props.add(duration);

        if (rRule != null)
            props.add(rRule);
        for (RDate rdate : rDates)
            props.add(rdate);
        if (exRule != null)
            props.add(exRule);
        for (ExDate exdate : exDates)
            props.add(exdate);

        if (summary != null && !summary.isEmpty())
            props.add(new Summary(summary));
        if (location != null && !location.isEmpty())
            props.add(new Location(location));
        if (description != null && !description.isEmpty())
            props.add(new Description(description));

        if (status != null)
            props.add(status);
        if (!opaque)
            props.add(Transp.TRANSPARENT);

        if (organizer != null)
            props.add(organizer);
        props.addAll(attendees);

        if (forPublic != null)
            event.getProperties().add(forPublic ? Clazz.PUBLIC : Clazz.PRIVATE);

        props.addAll(unknownProperties);

        event.getAlarms().addAll(alarms);
        return event;
    }


    // time helpers

    public boolean isAllDay() {
        return !isDateTime(dtStart);
    }

    public long getDtStartInMillis() {
        return dtStart.getDate().getTime();
    }

    public String getDtStartTzID() {
        return getTzId(dtStart);
    }

    public void setDtStart(long tsStart, String tzID) {
        if (tzID == null) {    // all-day
            dtStart = new DtStart(new Date(tsStart));
        } else {
            DateTime start = new DateTime(tsStart);
            start.setTimeZone(DateUtils.tzRegistry.getTimeZone(tzID));
            dtStart = new DtStart(start);
        }
    }


    public long getDtEndInMillis() {
        return dtEnd.getDate().getTime();
    }

    public String getDtEndTzID() {
        return getTzId(dtEnd);
    }

    public void setDtEnd(long tsEnd, String tzID) {
        if (tzID == null) {    // all-day
            dtEnd = new DtEnd(new Date(tsEnd));
        } else {
            DateTime end = new DateTime(tsEnd);
            end.setTimeZone(DateUtils.tzRegistry.getTimeZone(tzID));
            dtEnd = new DtEnd(end);
        }
    }

}