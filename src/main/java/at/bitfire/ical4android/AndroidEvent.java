package at.bitfire.ical4android;

import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Entity;
import android.content.EntityIterator;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.*;
import android.util.Log;

import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.parameter.*;
import net.fortuna.ical4j.model.property.*;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.List;

import lombok.Cleanup;
import lombok.Getter;

public abstract class AndroidEvent {
    private static final String TAG = "ical4android.Event";

    public static final String
            COLUMN_UID = android.os.Build.VERSION.SDK_INT >= 17 ? Events.UID_2445 : Events.SYNC_DATA2,
            COLUMN_SEQUENCE = Events.SYNC_DATA1;

    final protected AndroidCalendar calendar;

    protected Long id;
    private Event event;


    protected AndroidEvent(AndroidCalendar calendar, long id) {
        this.calendar = calendar;
        this.id = id;
    }

    protected AndroidEvent(AndroidCalendar calendar, Event event) {
        this.calendar = calendar;
        this.event = event;
    }


    public Event getEvent() throws CalendarStorageException {
        if (event != null)
            return event;

        try {
            @Cleanup EntityIterator iterEvents = CalendarContract.EventsEntity.newEntityIterator(
                    calendar.providerClient.query(
                            calendar.syncAdapterURI(ContentUris.withAppendedId(CalendarContract.EventsEntity.CONTENT_URI, id)),
                            null, null, null, null),
                    calendar.providerClient
            );
            while (iterEvents.hasNext()) {
                Entity e = iterEvents.next();

                populateEvent(e.getEntityValues());

                List<Entity.NamedContentValues> subValues = e.getSubValues();
                for (Entity.NamedContentValues subValue : subValues) {
                    if (Attendees.CONTENT_URI.equals(subValue.uri))
                        populateAttendee(subValue.values);
                    if (Reminders.CONTENT_URI.equals(subValue.uri))
                        populateReminder(subValue.values);
                }
                populateExceptions();
            }
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't read locally stored event", e);
        }
        return event;
    }

    protected void populateEvent(ContentValues values) {
        event.uid = values.getAsString(COLUMN_UID);
        event.sequence = values.getAsInteger(COLUMN_SEQUENCE);

        event.summary = values.getAsString(Events.TITLE);
        event.location = values.getAsString(Events.EVENT_LOCATION);
        event.description = values.getAsString(Events.DESCRIPTION);

        final boolean allDay = values.getAsInteger(Events.ALL_DAY) != 0;
        final long tsStart = values.getAsLong(Events.DTSTART);
        final String duration = values.getAsString(Events.DURATION);

        String tzId;
        Long tsEnd = values.getAsLong(Events.DTEND);
        if (allDay) {
            event.setDtStart(tsStart, null);
            if (tsEnd == null) {
                Dur dur = new Dur(duration);
                java.util.Date dEnd = dur.getTime(new java.util.Date(tsStart));
                tsEnd = dEnd.getTime();
            }
            event.setDtEnd(tsEnd, null);

        } else {
            // use the start time zone for the end time, too
            // because apps like Samsung Planner allow the user to change "the" time zone but change the start time zone only
            tzId = values.getAsString(Events.EVENT_TIMEZONE);
            event.setDtStart(tsStart, tzId);
            if (tsEnd != null)
                event.setDtEnd(tsEnd, tzId);
            else if (!StringUtils.isEmpty(duration))
                event.duration = new Duration(new Dur(duration));
        }

        // recurrence
        try {
            String strRRule = values.getAsString(Events.RRULE);
            if (!StringUtils.isEmpty(strRRule))
                event.rrule = new RRule(strRRule);

            String strRDate = values.getAsString(Events.RDATE);
            if (!StringUtils.isEmpty(strRDate)) {
                RDate rDate = (RDate)DateUtils.androidStringToRecurrenceSet(strRDate, RDate.class, allDay);
                event.getRdates().add(rDate);
            }

            String strExRule = values.getAsString(Events.EXRULE);
            if (!StringUtils.isEmpty(strExRule)) {
                ExRule exRule = new ExRule();
                exRule.setValue(strExRule);
                event.exrule = exRule;
            }

            String strExDate = values.getAsString(Events.EXDATE);
            if (!StringUtils.isEmpty(strExDate)) {
                ExDate exDate = (ExDate)DateUtils.androidStringToRecurrenceSet(strExDate, ExDate.class, allDay);
                event.getExdates().add(exDate);
            }
        } catch (ParseException ex) {
            Log.w(TAG, "Couldn't parse recurrence rules, ignoring", ex);
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Invalid recurrence rules, ignoring", ex);
        }

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
        if (values.getAsInteger(Events.HAS_ATTENDEE_DATA) != 0)
            try {
                event.organizer = new Organizer(new URI("mailto", values.getAsString(Events.ORGANIZER), null));
            } catch (URISyntaxException ex) {
                Log.e(TAG, "Error when creating ORGANIZER mailto URI, ignoring", ex);
            }

        // classification
        switch (values.getAsInteger(Events.ACCESS_LEVEL)) {
            case Events.ACCESS_CONFIDENTIAL:
            case Events.ACCESS_PRIVATE:
                event.forPublic = false;
                break;
            case Events.ACCESS_PUBLIC:
                event.forPublic = true;
        }
    }

    protected void populateAttendee(ContentValues values) {
        try {
            final Attendee attendee;
            final String
                    email = values.getAsString(Attendees.ATTENDEE_EMAIL),
                    idNS = values.getAsString(Attendees.ATTENDEE_ID_NAMESPACE),
                    id = values.getAsString(Attendees.ATTENDEE_IDENTITY);
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

            event.getAttendees().add(attendee);
        } catch (URISyntaxException ex) {
            Log.e(TAG, "Couldn't parse attendee information, ignoring", ex);
        }
    }

    protected void populateReminder(ContentValues row) {
        VAlarm alarm = new VAlarm(new Dur(0, 0, -row.getAsInteger(Reminders.MINUTES), 0));

        PropertyList props = alarm.getProperties();
        props.add(Action.DISPLAY);
        props.add(new Description(event.summary));
        event.getAlarms().add(alarm);
    }

    protected void populateExceptions() throws RemoteException {
        @Cleanup Cursor c = calendar.providerClient.query(calendar.syncAdapterURI(Events.CONTENT_URI),
                new String[] { Events._ID },
                Events.ORIGINAL_ID + "=?", new String[] { String.valueOf(id) }, null);
        while (c != null && c.moveToNext()) {
            long exceptionId = c.getLong(0);
            try {
                AndroidEvent exception = calendar.eventFactory.newInstance(calendar, exceptionId);
                event.getExceptions().add(exception.getEvent());
            } catch (CalendarStorageException e) {
                Log.e(TAG, "Couldn't find exception details, ignoring", e);
            }
        }
    }


    public void add() throws CalendarStorageException {
        BatchOperation batch = new BatchOperation(calendar.providerClient);
        Builder builder = ContentProviderOperation.newInsert(eventsURI());
        buildEntry(builder);
        batch.enqueue(builder.build());
        batch.commit();
    }

    public void update(Event event) throws CalendarStorageException {
        this.event = event;

        BatchOperation batch = new BatchOperation(calendar.providerClient);
        Builder builder = ContentProviderOperation.newUpdate(eventURI());
        buildEntry(builder);
        batch.enqueue(builder.build());
        batch.commit();
    }

    protected void buildEntry(Builder builder) {
        builder	.withValue(Events.CALENDAR_ID, calendar.getId())
                .withValue(Events.ALL_DAY, event.isAllDay() ? 1 : 0)
                .withValue(Events.DTSTART, event.getDtStartInMillis())
                .withValue(Events.EVENT_TIMEZONE, event.getDtStartTzID())
                .withValue(Events.HAS_ALARM, event.getAlarms().isEmpty() ? 0 : 1)
                .withValue(Events.HAS_ATTENDEE_DATA, event.getAttendees().isEmpty() ? 0 : 1)
                .withValue(COLUMN_UID, event.uid)
                .withValue(COLUMN_SEQUENCE, event.sequence);

        boolean recurring = false;
        if (event.rrule != null) {
            recurring = true;
            builder.withValue(Events.RRULE, event.rrule.getValue());
        }
        if (!event.getRdates().isEmpty()) {
            recurring = true;
            try {
                builder.withValue(Events.RDATE, DateUtils.recurrenceSetsToAndroidString(event.getRdates(), event.isAllDay()));
            } catch (ParseException e) {
                Log.e(TAG, "Couldn't parse RDate(s)", e);
            }
        }
        if (event.exrule != null)
            builder.withValue(Events.EXRULE, event.exrule.getValue());
        if (!event.getExceptions().isEmpty())
            try {
                builder.withValue(Events.EXDATE, DateUtils.recurrenceSetsToAndroidString(event.getExdates(), event.isAllDay()));
            } catch (ParseException e) {
                Log.e(TAG, "Couldn't parse ExDate(s)", e);
            }

        // set either DTEND for single-time events or DURATION for recurring events
        // because that's the way Android likes it (see docs)
        if (recurring) {
            // calculate DURATION from start and end date
            Duration duration = new Duration(event.dtStart.getDate(), event.dtEnd.getDate());
            builder.withValue(Events.DURATION, duration.getValue());
        } else
            builder	.withValue(Events.DTEND, event.getDtEndInMillis())
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
                Log.w(TAG, "Got ORGANIZER without email address which is not supported by Android, ignoring");
        }

        if (event.status!= null) {
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
    }


    protected Uri eventsURI() {
        return calendar.syncAdapterURI(Events.CONTENT_URI);
    }

    protected Uri eventURI() {
        if (id == null)
            throw new IllegalStateException("Event doesn't have an ID yet");
        return calendar.syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, id));
    }

}
