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

import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Entity;
import android.content.EntityIterator;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.ExtendedProperties;
import android.provider.CalendarContract.Reminders;
import android.util.Base64;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.CuType;
import net.fortuna.ical4j.model.parameter.PartStat;
import net.fortuna.ical4j.model.parameter.Role;
import net.fortuna.ical4j.model.parameter.Rsvp;
import net.fortuna.ical4j.model.property.Action;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.util.TimeZones;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

import lombok.Cleanup;
import lombok.Getter;
import lombok.ToString;

/**
 * Extend this class for your local implementation of the
 * event that's stored in the Android Calendar Provider.
 *
 * Important: To use recurrence exceptions, you MUST set _SYNC_ID and ORIGINAL_SYNC_ID
 * in populateEvent() / buildEvent. Setting _ID and ORIGINAL_ID is not sufficient.
 */
@ToString(of={ "id", "event" }, doNotUseGetters=true)
public abstract class AndroidEvent {

    /** {@link ExtendedProperties#NAME} for unknown iCal properties */
    public static final String EXT_UNKNOWN_PROPERTY = "unknown-property";
    protected static final int MAX_UNKNOWN_PROPERTY_SIZE = 25000;

    final protected AndroidCalendar calendar;

    @Getter
    protected Long id;

    protected Event event;


    protected AndroidEvent(AndroidCalendar calendar, long id, ContentValues baseInfo) {
        this.calendar = calendar;
        this.id = id;

        // baseInfo is used by derived classes which process SYNC1 etc.
    }

    protected AndroidEvent(AndroidCalendar calendar, Event event) {
        this.calendar = calendar;
        this.event = event;
    }


    public Event getEvent() throws FileNotFoundException, CalendarStorageException {
        if (event != null)
            return event;

        try {
            @Cleanup EntityIterator iterEvents = CalendarContract.EventsEntity.newEntityIterator(
                    calendar.provider.query(
                            calendar.syncAdapterURI(ContentUris.withAppendedId(CalendarContract.EventsEntity.CONTENT_URI, id)),
                            null, null, null, null),
                    calendar.provider
            );
            if (iterEvents.hasNext()) {
	            event = new Event();
                Entity e = iterEvents.next();
                populateEvent(e.getEntityValues());

                List<Entity.NamedContentValues> subValues = e.getSubValues();
                for (Entity.NamedContentValues subValue : subValues) {
                    if (Attendees.CONTENT_URI.equals(subValue.uri))
                        populateAttendee(subValue.values);
                    else if (Reminders.CONTENT_URI.equals(subValue.uri))
                        populateReminder(subValue.values);
                    else if (CalendarContract.ExtendedProperties.CONTENT_URI.equals(subValue.uri))
                        populateExtended(subValue.values);
                }
                populateExceptions();

                /* remove ORGANIZER from all components if there are no attendees
                   (i.e. this is not a group-scheduled calendar entity) */
                if (event.attendees.isEmpty()) {
                    event.organizer = null;
                    for (Event exception : event.exceptions)
                        exception.organizer = null;
                }

	            return event;
            } else
                throw new FileNotFoundException("Locally stored event couldn't be found");
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't read locally stored event", e);
        }
    }

    protected void populateEvent(ContentValues values) {
        event.summary = values.getAsString(Events.TITLE);
        event.location = values.getAsString(Events.EVENT_LOCATION);
        event.description = values.getAsString(Events.DESCRIPTION);

        final boolean allDay = values.getAsInteger(Events.ALL_DAY) != 0;
        final long tsStart = values.getAsLong(Events.DTSTART);
        final Long tsEnd = values.getAsLong(Events.DTEND);
        final String duration = values.getAsString(Events.DURATION);

        if (allDay) {
            // use DATE values
            event.dtStart = new DtStart(new Date(tsStart));
            if (tsEnd != null)
                event.dtEnd = new DtEnd(new Date(tsEnd));
            else if (duration != null)
                event.duration = new Duration(new Dur(duration));

        } else {
            // use DATE-TIME values
            TimeZone tz = null;
            String tzId = values.getAsString(Events.EVENT_TIMEZONE);
            if (tzId != null)
                tz = DateUtils.tzRegistry.getTimeZone(tzId);

            DateTime start = new DateTime(tsStart);
            start.setTimeZone(tz);
            event.dtStart = new DtStart(start);

            if (tsEnd != null) {
                DateTime end = new DateTime(tsEnd);
                end.setTimeZone(tz);
                event.dtEnd = new DtEnd(end);
            } else if (duration != null)
                event.duration = new Duration(new Dur(duration));
        }

        // recurrence
        try {
            String strRRule = values.getAsString(Events.RRULE);
            if (!StringUtils.isEmpty(strRRule))
                event.rRule = new RRule(strRRule);

            String strRDate = values.getAsString(Events.RDATE);
            if (!StringUtils.isEmpty(strRDate)) {
                RDate rDate = (RDate)DateUtils.androidStringToRecurrenceSet(strRDate, RDate.class, allDay);
                event.rDates.add(rDate);
            }

            String strExRule = values.getAsString(Events.EXRULE);
            if (!StringUtils.isEmpty(strExRule)) {
                ExRule exRule = new ExRule();
                exRule.setValue(strExRule);
                event.exRule = exRule;
            }

            String strExDate = values.getAsString(Events.EXDATE);
            if (!StringUtils.isEmpty(strExDate)) {
                ExDate exDate = (ExDate)DateUtils.androidStringToRecurrenceSet(strExDate, ExDate.class, allDay);
                event.exDates.add(exDate);
            }
        } catch (ParseException ex) {
            Constants.log.log(Level.WARNING, "Couldn't parse recurrence rules, ignoring", ex);
        } catch (IllegalArgumentException ex) {
            Constants.log.log(Level.WARNING, "Invalid recurrence rules, ignoring", ex);
        }

        // status
        if (values.containsKey(Events.STATUS))
            switch (values.getAsInteger(Events.STATUS)) {
                case Events.STATUS_CONFIRMED:
                    event.status = Status.VEVENT_CONFIRMED;
                    break;
                case Events.STATUS_TENTATIVE:
                    event.status = Status.VEVENT_TENTATIVE;
                    break;
                case Events.STATUS_CANCELED:
                    event.status = Status.VEVENT_CANCELLED;
            }

        // availability
        event.opaque = values.getAsInteger(Events.AVAILABILITY) != Events.AVAILABILITY_FREE;

        // set ORGANIZER if there's attendee data
        if (values.getAsInteger(Events.HAS_ATTENDEE_DATA) != 0 && values.containsKey(Events.ORGANIZER))
            try {
                event.organizer = new Organizer(new URI("mailto", values.getAsString(Events.ORGANIZER), null));
            } catch (URISyntaxException ex) {
                Constants.log.log(Level.WARNING, "Error when creating ORGANIZER mailto URI, ignoring", ex);
            }

        // classification
        switch (values.getAsInteger(Events.ACCESS_LEVEL)) {
            case Events.ACCESS_PUBLIC:
                event.forPublic = true;
                break;
            case Events.ACCESS_PRIVATE:
                event.forPublic = false;
                break;
            /*default:
                event.forPublic = null;*/
        }

        // exceptions from recurring events
        if (values.containsKey(Events.ORIGINAL_INSTANCE_TIME)) {
            // this event is an exception of a recurring event
            long originalInstanceTime = values.getAsLong(Events.ORIGINAL_INSTANCE_TIME);

            boolean originalAllDay = false;
            if (values.containsKey(Events.ORIGINAL_ALL_DAY))
                originalAllDay = values.getAsInteger(Events.ORIGINAL_ALL_DAY) != 0;

            Date originalDate = originalAllDay ?
                    new Date(originalInstanceTime) :
                    new DateTime(originalInstanceTime);
            if (originalDate instanceof DateTime)
                ((DateTime)originalDate).setUtc(true);
            event.recurrenceId = new RecurrenceId(originalDate);
        }
    }

    protected void populateAttendee(ContentValues values) {
        try {
            final Attendee attendee;
            final String email = values.getAsString(Attendees.ATTENDEE_EMAIL),
                    idNS, id;
            if (Build.VERSION.SDK_INT >= 16) {
                idNS = values.getAsString(Attendees.ATTENDEE_ID_NAMESPACE);
                id = values.getAsString(Attendees.ATTENDEE_IDENTITY);
            } else
                idNS = id = null;

            if (idNS != null || id != null) {
                // attendee identified by namespace and ID
                attendee = new Attendee(new URI(idNS, id, null));
                if (email != null)
                    attendee.getParameters().add(new iCalendar.Email(email));
            } else
                // attendee identified by email address
                attendee = new Attendee(new URI("mailto", email, null));
            final ParameterList params = attendee.getParameters();

            String cn = values.getAsString(Attendees.ATTENDEE_NAME);
            if (cn != null)
                params.add(new Cn(cn));

            // type
            int type = values.getAsInteger(Attendees.ATTENDEE_TYPE);
            params.add((type == Attendees.TYPE_RESOURCE) ? CuType.RESOURCE : CuType.INDIVIDUAL);

            // role
            int relationship = values.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP);
            switch (relationship) {
                case Attendees.RELATIONSHIP_ORGANIZER:
                case Attendees.RELATIONSHIP_ATTENDEE:
                case Attendees.RELATIONSHIP_PERFORMER:
                case Attendees.RELATIONSHIP_SPEAKER:
                    params.add((type == Attendees.TYPE_REQUIRED) ? Role.REQ_PARTICIPANT : Role.OPT_PARTICIPANT);
                    params.add(new Rsvp(true));
                    break;
                case Attendees.RELATIONSHIP_NONE:
                    params.add(Role.NON_PARTICIPANT);
            }

            // status
            switch (values.getAsInteger(Attendees.ATTENDEE_STATUS)) {
                case Attendees.ATTENDEE_STATUS_INVITED:
                    params.add(PartStat.NEEDS_ACTION);
                    break;
                case Attendees.ATTENDEE_STATUS_ACCEPTED:
                    params.add(PartStat.ACCEPTED);
                    break;
                case Attendees.ATTENDEE_STATUS_DECLINED:
                    params.add(PartStat.DECLINED);
                    break;
                case Attendees.ATTENDEE_STATUS_TENTATIVE:
                    params.add(PartStat.TENTATIVE);
                    break;
            }

            event.attendees.add(attendee);
        } catch (URISyntaxException ex) {
            Constants.log.log(Level.WARNING, "Couldn't parse attendee information, ignoring", ex);
        }
    }

    protected void populateReminder(ContentValues row) {
        VAlarm alarm = new VAlarm(new Dur(0, 0, -row.getAsInteger(Reminders.MINUTES), 0));

        PropertyList props = alarm.getProperties();
        switch (row.getAsInteger(Reminders.METHOD)) {
            case Reminders.METHOD_ALARM:
            case Reminders.METHOD_ALERT:
                props.add(Action.DISPLAY);
                break;
            case Reminders.METHOD_EMAIL:
            case Reminders.METHOD_SMS:
                props.add(Action.EMAIL);
                break;
            default:
                // show alarm by default
                props.add(Action.DISPLAY);
        }
        props.add(new Description(event.summary));
        event.alarms.add(alarm);
    }

    protected void populateExtended(ContentValues row) {
        if (EXT_UNKNOWN_PROPERTY.equals(row.getAsString(ExtendedProperties.NAME))) {
            // de-serialize unknown property
            ByteArrayInputStream bais = new ByteArrayInputStream(Base64.decode(row.getAsString(ExtendedProperties.VALUE), Base64.NO_WRAP));
            try {
                @Cleanup ObjectInputStream ois = new ObjectInputStream(bais);
                Property property = (Property)ois.readObject();
                event.unknownProperties.add(property);
            } catch(IOException|ClassNotFoundException e) {
                Constants.log.log(Level.WARNING, "Couldn't de-serialize unknown property", e);
            }
        }
    }

    @SuppressWarnings("Recycle")
    protected void populateExceptions() throws FileNotFoundException, RemoteException {
        @Cleanup Cursor c = calendar.provider.query(calendar.syncAdapterURI(Events.CONTENT_URI),
                new String[] { Events._ID },
                Events.ORIGINAL_ID + "=?", new String[] { String.valueOf(id) }, null);
        while (c != null && c.moveToNext()) {
            long exceptionId = c.getLong(0);
            try {
                AndroidEvent exception = calendar.eventFactory.newInstance(calendar, exceptionId, null);

                // make sure that all components have the same ORGANIZER [RFC 6638 3.1]
                Event exceptionEvent = exception.getEvent();
                exceptionEvent.organizer = getEvent().organizer;

                event.exceptions.add(exceptionEvent);
            } catch (CalendarStorageException e) {
                Constants.log.log(Level.WARNING, "Couldn't find exception details, ignoring", e);
            }
        }
    }


    public Uri add() throws CalendarStorageException {
        BatchOperation batch = new BatchOperation(calendar.provider);
        final int idxEvent = add(batch);
        batch.commit();

        Uri uri = batch.getResult(idxEvent).uri;
        id = ContentUris.parseId(uri);

        return uri;
    }

    protected int add(BatchOperation batch) {
        Builder builder = ContentProviderOperation.newInsert(calendar.syncAdapterURI(eventsSyncURI()));

        final int idxEvent = batch.nextBackrefIdx();
        buildEvent(null, builder);
        batch.enqueue(new BatchOperation.Operation(builder));

        // add reminders
        for (VAlarm alarm : event.alarms)
            insertReminder(batch, idxEvent, alarm);

        // add attendees
        for (Attendee attendee : event.attendees)
            insertAttendee(batch, idxEvent, attendee);

        // add unknown properties
        for (Property unknown : event.unknownProperties)
            insertUnknownProperty(batch, idxEvent, unknown);

        // add exceptions
        for (Event exception : event.exceptions) {
            /* I guess exceptions should be inserted using Events.CONTENT_EXCEPTION_URI so that we could
               benefit from some provider logic (for recurring exceptions e.g.). However, this method
               has some caveats:
               - For instance, only Events.SYNC_DATA1, SYNC_DATA3 and SYNC_DATA7 can be used
               in exception events (that's hardcoded in the CalendarProvider, don't ask me why).
               - Also, CONTENT_EXCEPTIONS_URI doesn't deal with exceptions for recurring events defined by RDATE
               (it checks for RRULE and aborts if no RRULE is found).
               So I have chosen the method of inserting the exception event manually.

               It's also noteworthy that the link between the "master event" and the exception is not
               between ID and ORIGINAL_ID (as one could assume), but between _SYNC_ID and ORIGINAL_SYNC_ID.
               So, if you don't set _SYNC_ID in the master event and ORIGINAL_SYNC_ID in the exception,
               the exception will appear additionally (and not *instead* of the instance).
             */

            builder = ContentProviderOperation.newInsert(calendar.syncAdapterURI(eventsSyncURI()));
            buildEvent(exception, builder);

            Date date = exception.recurrenceId.getDate();
            if (event.isAllDay() && date instanceof DateTime) {       // correct VALUE=DATE-TIME RECURRENCE-IDs to VALUE=DATE for all-day events
                final DateFormat dateFormatDate = new SimpleDateFormat("yyyyMMdd", Locale.US);
                final String dateString = dateFormatDate.format(exception.recurrenceId.getDate());
                try {
                    date = new Date(dateString);
                } catch (ParseException e) {
                    Constants.log.log(Level.WARNING, "Couldn't parse DATE part of DATE-TIME RECURRENCE-ID", e);
                }
            }
            builder .withValue(Events.ORIGINAL_ALL_DAY, event.isAllDay() ? 1 : 0)
                    .withValue(Events.ORIGINAL_INSTANCE_TIME, date.getTime());

            int idxException = batch.nextBackrefIdx();
            batch.enqueue(new BatchOperation.Operation(builder, Events.ORIGINAL_ID, idxEvent));

            // add exception reminders
            for (VAlarm alarm : exception.alarms)
                insertReminder(batch, idxException, alarm);

            // add exception attendees
            for (Attendee attendee : exception.attendees)
                insertAttendee(batch, idxException, attendee);
        }

        return idxEvent;
    }

    public Uri update(Event event) throws CalendarStorageException {
        this.event = event;

        BatchOperation batch = new BatchOperation(calendar.provider);
        delete(batch);

        final int idxEvent = batch.nextBackrefIdx();

        add(batch);
        batch.commit();

        Uri uri = batch.getResult(idxEvent).uri;
        id = ContentUris.parseId(uri);
        return uri;
    }

    public int delete() throws CalendarStorageException {
        BatchOperation batch = new BatchOperation(calendar.provider);
        delete(batch);
        return batch.commit();
    }

    protected void delete(BatchOperation batch) {
        // remove event
        batch.enqueue(new BatchOperation.Operation(ContentProviderOperation.newDelete(eventSyncURI())));

        // remove exceptions of that event, too (CalendarProvider doesn't do this)
        batch.enqueue(new BatchOperation.Operation(ContentProviderOperation.newDelete(eventsSyncURI())
                .withSelection(Events.ORIGINAL_ID + "=?", new String[] { String.valueOf(id) })));
    }

    protected void buildEvent(Event recurrence, Builder builder) {
        boolean isException = recurrence != null;
        Event event = isException ? recurrence : this.event;

        builder .withValue(Events.CALENDAR_ID, calendar.getId())
                .withValue(Events.ALL_DAY, event.isAllDay() ? 1 : 0)
                .withValue(Events.DTSTART, event.dtStart.getDate().getTime())
                .withValue(Events.EVENT_TIMEZONE, event.getDtStartTzID())
                .withValue(Events.HAS_ATTENDEE_DATA, 1 /* we know information about all attendees and not only ourselves */);

        /* For cases where a "VEVENT" calendar component
           specifies a "DTSTART" property with a DATE value type but no
           "DTEND" nor "DURATION" property, the event's duration is taken to
           be one day. [RFC 5545 3.6.1] */
        if (event.isAllDay() && (event.dtEnd == null || !event.dtEnd.getDate().after(event.dtStart.getDate()))) {
            // ical4j is not set to use floating times, so DATEs are UTC times internally
            Constants.log.log(Level.INFO, "Changing all-day event for Android compatibility: dtend := dtstart + 1 day");
            java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getTimeZone(TimeZones.UTC_ID));
            c.setTime(event.dtStart.getDate());
            c.add(java.util.Calendar.DATE, 1);
            event.dtEnd = new DtEnd(new Date(c.getTimeInMillis()));
            event.duration = null;
        }

        /* For cases where a "VEVENT" calendar component
           specifies a "DTSTART" property with a DATE-TIME value type but no
           "DTEND" property, the event ends on the same calendar date and
           time of day specified by the "DTSTART" property. [RFC 5545 3.6.1] */
        else if (event.dtEnd == null || event.dtEnd.getDate().getTime() < event.dtStart.getDate().getTime()) {
            Constants.log.info("Event without duration, setting dtend := dtstart");
            event.dtEnd = new DtEnd(event.dtStart.getDate());
        }

        boolean recurring = false;
        if (event.rRule != null) {
            recurring = true;
            builder.withValue(Events.RRULE, event.rRule.getValue());
        }
        if (!event.rDates.isEmpty()) {
            recurring = true;
            try {
                builder.withValue(Events.RDATE, DateUtils.recurrenceSetsToAndroidString(event.rDates, event.isAllDay()));
            } catch (ParseException e) {
                Constants.log.log(Level.WARNING, "Couldn't parse RDate(s)", e);
            }
        }
        if (event.exRule != null)
            builder.withValue(Events.EXRULE, event.exRule.getValue());
        if (!event.exDates.isEmpty())
            try {
                builder.withValue(Events.EXDATE, DateUtils.recurrenceSetsToAndroidString(event.exDates, event.isAllDay()));
            } catch (ParseException e) {
                Constants.log.log(Level.WARNING, "Couldn't parse ExDate(s)", e);
            }

        // set either DTEND for single-time events or DURATION for recurring events
        // because that's the way Android likes it
        if (recurring) {
            // calculate DURATION from start and end date
            Duration duration = (event.duration != null) ?
                    event.duration :
                    new Duration(event.dtStart.getDate(), event.dtEnd.getDate());
            builder .withValue(Events.DURATION, duration.getValue());
        } else
            builder .withValue(Events.DTEND, event.dtEnd.getDate().getTime())
                    .withValue(Events.EVENT_END_TIMEZONE, event.getDtEndTzID());

        if (event.summary != null)
            builder.withValue(Events.TITLE, event.summary);
        if (event.location != null)
            builder.withValue(Events.EVENT_LOCATION, event.location);
        if (event.description != null)
            builder.withValue(Events.DESCRIPTION, event.description);

        if (event.organizer != null) {
            final URI uri = event.organizer.getCalAddress();
            String email = null;
            if (uri != null && "mailto".equalsIgnoreCase(uri.getScheme()))
                email = uri.getSchemeSpecificPart();
            else {
                iCalendar.Email emailParam = (iCalendar.Email)event.organizer.getParameter(iCalendar.Email.PARAMETER_NAME);
                if (emailParam != null)
                    email = emailParam.getValue();
            }
            if (email != null)
                builder.withValue(Events.ORGANIZER, email);
            else
                Constants.log.warning("Got ORGANIZER without email address which is not supported by Android, ignoring");
        }

        if (event.status != null) {
            int statusCode = Events.STATUS_TENTATIVE;
            if (event.status == Status.VEVENT_CONFIRMED)
                statusCode = Events.STATUS_CONFIRMED;
            else if (event.status == Status.VEVENT_CANCELLED)
                statusCode = Events.STATUS_CANCELED;
            builder.withValue(Events.STATUS, statusCode);
        }

        builder.withValue(Events.AVAILABILITY, event.opaque ? Events.AVAILABILITY_BUSY : Events.AVAILABILITY_FREE);

        if (event.forPublic != null)
            builder.withValue(Events.ACCESS_LEVEL, event.forPublic ? Events.ACCESS_PUBLIC : Events.ACCESS_PRIVATE);

        Constants.log.log(Level.FINE, "Built event object", builder.build());
    }

    protected void insertReminder(BatchOperation batch, int idxEvent, VAlarm alarm) {
        Builder builder = ContentProviderOperation.newInsert(calendar.syncAdapterURI(Reminders.CONTENT_URI));

        final Action action = alarm.getAction();
        final int method;
        if (action == null ||                                         // (required) ACTION not set, assume DISPLAY
            Action.DISPLAY.getValue().equals(action.getValue()) ||    // alarm should be shown on display
            Action.AUDIO.getValue().equals(action.getValue()))        // alarm should play some sound
            method = Reminders.METHOD_ALERT;
        else if (Action.EMAIL.getValue().equals(action.getValue()))
            method = Reminders.METHOD_EMAIL;
        else
            method = Reminders.METHOD_DEFAULT;

        final int minutes = iCalendar.alarmMinBefore(alarm);
        builder .withValue(Reminders.METHOD, method)
                .withValue(Reminders.MINUTES, minutes);

        Constants.log.fine("Adding alarm " + minutes + " minutes before event, method: " + method);
        batch.enqueue(new BatchOperation.Operation(builder, Reminders.EVENT_ID, idxEvent));
    }

    protected void insertAttendee(BatchOperation batch, int idxEvent, Attendee attendee) {
        Builder builder = ContentProviderOperation.newInsert(calendar.syncAdapterURI(Attendees.CONTENT_URI));

        final URI member = attendee.getCalAddress();
        if ("mailto".equalsIgnoreCase(member.getScheme()))
            // attendee identified by email
            builder = builder.withValue(Attendees.ATTENDEE_EMAIL, member.getSchemeSpecificPart());
        else if (Build.VERSION.SDK_INT >= 16) {
            // attendee identified by other URI
            builder = builder
                    .withValue(Attendees.ATTENDEE_ID_NAMESPACE, member.getScheme())
                    .withValue(Attendees.ATTENDEE_IDENTITY, member.getSchemeSpecificPart());
            iCalendar.Email email = (iCalendar.Email)attendee.getParameter(iCalendar.Email.PARAMETER_NAME);
            if (email != null)
                builder = builder.withValue(Attendees.ATTENDEE_EMAIL, email.getValue());
        }

        final Cn cn = (Cn)attendee.getParameter(Parameter.CN);
        if (cn != null)
            builder.withValue(Attendees.ATTENDEE_NAME, cn.getValue());

        int type = Attendees.TYPE_NONE;

        CuType cutype = (CuType)attendee.getParameter(Parameter.CUTYPE);
        if ((cutype == CuType.RESOURCE || cutype == CuType.ROOM) && Build.VERSION.SDK_INT >= 16)
            // "attendee" is a (physical) resource
            type = Attendees.TYPE_RESOURCE;
        else {
            // attendee is not a (physical) resource
            Role role = (Role)attendee.getParameter(Parameter.ROLE);
            int relationship;
            if (role == Role.CHAIR)
                relationship = Attendees.RELATIONSHIP_ORGANIZER;
            else {
                relationship = Attendees.RELATIONSHIP_ATTENDEE;
                if (role == Role.OPT_PARTICIPANT)
                    type = Attendees.TYPE_OPTIONAL;
                else if (role == Role.REQ_PARTICIPANT)
                    type = Attendees.TYPE_REQUIRED;
            }
            builder.withValue(Attendees.ATTENDEE_RELATIONSHIP, relationship);
        }

        int status = Attendees.ATTENDEE_STATUS_NONE;
        PartStat partStat = (PartStat)attendee.getParameter(Parameter.PARTSTAT);
        if (partStat == null || partStat == PartStat.NEEDS_ACTION)
            status = Attendees.ATTENDEE_STATUS_INVITED;
        else if (partStat == PartStat.ACCEPTED)
            status = Attendees.ATTENDEE_STATUS_ACCEPTED;
        else if (partStat == PartStat.DECLINED)
            status = Attendees.ATTENDEE_STATUS_DECLINED;
        else if (partStat == PartStat.TENTATIVE)
            status = Attendees.ATTENDEE_STATUS_TENTATIVE;

        builder .withValue(Attendees.ATTENDEE_TYPE, type)
                .withValue(Attendees.ATTENDEE_STATUS, status);

        batch.enqueue(new BatchOperation.Operation(builder, Attendees.EVENT_ID, idxEvent));
    }

    protected void insertUnknownProperty(BatchOperation batch, int idxEvent, Property property) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            @Cleanup ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(property);

            if (baos.size() > MAX_UNKNOWN_PROPERTY_SIZE) {
                Constants.log.warning("Ignoring unknown property with " + baos.size() + " octets");
                return;
            }

            Builder builder = ContentProviderOperation.newInsert(calendar.syncAdapterURI(ExtendedProperties.CONTENT_URI));
            builder .withValue(ExtendedProperties.NAME, EXT_UNKNOWN_PROPERTY)
                    .withValue(ExtendedProperties.VALUE, Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP));

            batch.enqueue(new BatchOperation.Operation(builder, ExtendedProperties.EVENT_ID, idxEvent));
        } catch(IOException e) {
            Constants.log.log(Level.WARNING, "Couldn't serialize unknown property", e);
        }
    }


    protected Uri eventsSyncURI() {
        return calendar.syncAdapterURI(Events.CONTENT_URI);
    }

    protected Uri eventSyncURI() {
        if (id == null)
            throw new IllegalStateException("Event doesn't have an ID yet");
        return calendar.syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, id));
    }

}
