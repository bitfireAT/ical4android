/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import android.annotation.SuppressLint
import android.content.ContentProviderOperation
import android.content.ContentProviderOperation.Builder
import android.content.ContentUris
import android.content.ContentValues
import android.content.EntityIterator
import android.net.Uri
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.CalendarContract.*
import android.util.Base64
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.*
import net.fortuna.ical4j.model.property.*
import net.fortuna.ical4j.util.TimeZones
import java.io.*
import java.net.URI
import java.net.URISyntaxException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level

/**
 * Extend this class for your local implementation of the
 * event that's stored in the Android Calendar Provider.
 *
 * Important: To use recurrence exceptions, you MUST set _SYNC_ID and ORIGINAL_SYNC_ID
 * in populateEvent() / buildEvent. Setting _ID and ORIGINAL_ID is not sufficient.
 */
abstract class AndroidEvent(
        val calendar: AndroidCalendar<AndroidEvent>
) {

    companion object {

        /** {@link ExtendedProperties#NAME} for unknown iCal properties */
        @JvmField val EXT_UNKNOWN_PROPERTY = "unknown-property"
        @JvmField val MAX_UNKNOWN_PROPERTY_SIZE = 25000

    }

    var id: Long? = null

    constructor(calendar: AndroidCalendar<AndroidEvent>, id: Long, baseInfo: ContentValues?): this(calendar) {
        this.id = id
        // baseInfo is used by derived classes which process SYNC1 etc.
    }

    constructor(calendar: AndroidCalendar<AndroidEvent>, event: Event): this(calendar) {
        this.event = event
    }

    var event: Event? = null
    /**
     * This getter returns the full event data, either from [event] or, if [event] is null, by reading event
     * number [id] from the Android calendar storage
     * @throws FileNotFoundException if there's no event with [id] in the calendar storage
     * @throws CalendarStorageException on calendar storage I/O errors
     */
    @SuppressLint("Recycle")
    @Throws(FileNotFoundException::class, CalendarStorageException::class)
    get() {
        if (field != null)
            return field
        val id = requireNotNull(id)

        var iterEvents: EntityIterator? = null
        try {
            iterEvents = CalendarContract.EventsEntity.newEntityIterator(
                    calendar.provider.query(
                            calendar.syncAdapterURI(ContentUris.withAppendedId(CalendarContract.EventsEntity.CONTENT_URI, id)),
                            null, null, null, null),
                    calendar.provider
            )
            if (iterEvents.hasNext()) {
                val event = Event()
                field = event

                val e = iterEvents.next()
                populateEvent(e.entityValues)

                for (subValue in e.subValues)
                    when (subValue.uri) {
                        Attendees.CONTENT_URI -> populateAttendee(subValue.values)
                        Reminders.CONTENT_URI -> populateReminder(subValue.values)
                        CalendarContract.ExtendedProperties.CONTENT_URI -> populateExtended(subValue.values)
                    }
                populateExceptions()

                /* remove ORGANIZER from all components if there are no attendees
                   (i.e. this is not a group-scheduled calendar entity) */
                if (event.attendees.isEmpty()) {
                    event.organizer = null
                    event.exceptions.forEach { it.organizer = null }
                }

                return field
            }
        } catch(e: RemoteException) {
            throw CalendarStorageException("Couldn't read locally stored event", e)
        } finally {
            iterEvents?.close()
        }
        throw FileNotFoundException("Couldn't find event $id")
    }

    @Throws(FileNotFoundException::class, CalendarStorageException::class)
    protected open fun populateEvent(row: ContentValues) {
        val event = requireNotNull(event)

        Constants.log.log(Level.FINE, "Read event entity from calender provider", row)
        MiscUtils.removeEmptyStrings(row)

        event.summary = row.getAsString(Events.TITLE)
        event.location = row.getAsString(Events.EVENT_LOCATION)
        event.description = row.getAsString(Events.DESCRIPTION)

        row.getAsString(Events.EVENT_COLOR_KEY)?.let { name ->
            try {
                event.color = EventColor.valueOf(name)
            } catch(e: IllegalArgumentException) {
                Constants.log.warning("Ignoring unknown color $name from Calendar Provider")
            }
        }

        val allDay = (row.getAsInteger(Events.ALL_DAY) ?: 0) != 0
        val tsStart = row.getAsLong(Events.DTSTART)
        val tsEnd = row.getAsLong(Events.DTEND)
        val duration = row.getAsString(Events.DURATION)

        if (allDay) {
            // use DATE values
            event.dtStart = DtStart(Date(tsStart))
            when {
                tsEnd != null -> event.dtEnd = DtEnd(Date(tsEnd))
                duration != null -> event.duration = Duration(Dur(duration))
            }
        } else {
            // use DATE-TIME values
            var tz: TimeZone? = null
            row.getAsString(Events.EVENT_TIMEZONE)?.let { tzId ->
                tz = DateUtils.tzRegistry.getTimeZone(tzId)
            }

            val start = DateTime(tsStart)
            start.timeZone = tz
            event.dtStart = DtStart(start)

            when {
                tsEnd != null -> {
                    val end = DateTime(tsEnd)
                    end.timeZone = tz
                    event.dtEnd = DtEnd(end)
                }
                duration != null -> event.duration = Duration(Dur(duration))
            }
        }

        // recurrence
        try {
            row.getAsString(Events.RRULE)?.let { event.rRule = RRule(it) }
            row.getAsString(Events.RDATE)?.let {
                val rDate = DateUtils.androidStringToRecurrenceSet(it, RDate::class.java, allDay)
                event.rDates += rDate
            }

            row.getAsString(Events.EXRULE)?.let {
                val exRule = ExRule()
                exRule.value = it
                event.exRule = exRule
            }
            row.getAsString(Events.EXDATE)?.let {
                val exDate = DateUtils.androidStringToRecurrenceSet(it, ExDate::class.java, allDay)
                event.exDates += exDate
            }
        } catch (e: ParseException) {
            Constants.log.log(Level.WARNING, "Couldn't parse recurrence rules, ignoring", e)
        } catch (e: IllegalArgumentException) {
            Constants.log.log(Level.WARNING, "Invalid recurrence rules, ignoring", e)
        }

        // status
        event.status = when (row.getAsInteger(Events.STATUS)) {
            Events.STATUS_CONFIRMED -> Status.VEVENT_CONFIRMED
            Events.STATUS_TENTATIVE -> Status.VEVENT_TENTATIVE
            Events.STATUS_CANCELED  -> Status.VEVENT_CANCELLED
            else                    -> null
        }

        // availability
        event.opaque = row.getAsInteger(Events.AVAILABILITY) != Events.AVAILABILITY_FREE

        // set ORGANIZER if there's attendee data
        if (row.getAsInteger(Events.HAS_ATTENDEE_DATA) != 0 && row.containsKey(Events.ORGANIZER))
            try {
                event.organizer = Organizer(URI("mailto", row.getAsString(Events.ORGANIZER), null))
            } catch (e: URISyntaxException) {
                Constants.log.log(Level.WARNING, "Error when creating ORGANIZER mailto URI, ignoring", e)
            }

        // classification
        event.forPublic = when (row.getAsInteger(Events.ACCESS_LEVEL)) {
            Events.ACCESS_PUBLIC  -> true
            Events.ACCESS_PRIVATE -> false
            else                  -> null
        }

        // exceptions from recurring events
        row.getAsLong(Events.ORIGINAL_INSTANCE_TIME)?.let { originalInstanceTime ->
            var originalAllDay = false
            row.getAsInteger(Events.ORIGINAL_ALL_DAY)?.let { originalAllDay = it != 0 }

            val originalDate = if (originalAllDay)
                    Date(originalInstanceTime) else
                    DateTime(originalInstanceTime)
            if (originalDate is DateTime)
                originalDate.isUtc = true
            event.recurrenceId = RecurrenceId(originalDate)
        }
    }

    protected fun populateAttendee(row: ContentValues) {
        Constants.log.log(Level.FINE, "Read event attendee from calender provider", row)
        MiscUtils.removeEmptyStrings(row)

        try {
            val attendee: Attendee
            val email = row.getAsString(Attendees.ATTENDEE_EMAIL)
            val idNS = row.getAsString(Attendees.ATTENDEE_ID_NAMESPACE)
            val id = row.getAsString(Attendees.ATTENDEE_IDENTITY)

            if (idNS != null || id != null) {
                // attendee identified by namespace and ID
                attendee = Attendee(URI(idNS, id, null))
                email?.let { attendee.parameters.add(iCalendar.Email(it)) }
            } else
                // attendee identified by email address
                attendee = Attendee(URI("mailto", email, null))
            val params = attendee.parameters

            row.getAsString(Attendees.ATTENDEE_NAME)?.let { cn -> params.add(Cn(cn)) }

            // type
            val type = row.getAsInteger(Attendees.ATTENDEE_TYPE)
            params.add(if (type == Attendees.TYPE_RESOURCE) CuType.RESOURCE else CuType.INDIVIDUAL)

            // role
            val relationship = row.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            when (relationship) {
                Attendees.RELATIONSHIP_ORGANIZER,
                Attendees.RELATIONSHIP_ATTENDEE,
                Attendees.RELATIONSHIP_PERFORMER,
                Attendees.RELATIONSHIP_SPEAKER -> {
                    params.add(if (type == Attendees.TYPE_REQUIRED) Role.REQ_PARTICIPANT else Role.OPT_PARTICIPANT)
                    params.add(Rsvp(true))     // ask server to send invitations
                }
                else /* RELATIONSHIP_NONE */ ->
                    params.add(Role.NON_PARTICIPANT)
            }

            // status
            when (row.getAsInteger(Attendees.ATTENDEE_STATUS)) {
                Attendees.ATTENDEE_STATUS_INVITED ->   params.add(PartStat.NEEDS_ACTION)
                Attendees.ATTENDEE_STATUS_ACCEPTED ->  params.add(PartStat.ACCEPTED)
                Attendees.ATTENDEE_STATUS_DECLINED ->  params.add(PartStat.DECLINED)
                Attendees.ATTENDEE_STATUS_TENTATIVE -> params.add(PartStat.TENTATIVE)
            }

            event!!.attendees.add(attendee)
        } catch (e: URISyntaxException) {
            Constants.log.log(Level.WARNING, "Couldn't parse attendee information, ignoring", e)
        }
    }

    protected fun populateReminder(row: ContentValues) {
        Constants.log.log(Level.FINE, "Read event reminder from calender provider", row)

        val event = requireNotNull(event)
        val alarm = VAlarm(Dur(0, 0, -row.getAsInteger(Reminders.MINUTES), 0))

        val props = alarm.properties
        when (row.getAsInteger(Reminders.METHOD)) {
            Reminders.METHOD_ALARM,
            Reminders.METHOD_ALERT ->
                props += Action.DISPLAY
            Reminders.METHOD_EMAIL,
            Reminders.METHOD_SMS ->
                props += Action.EMAIL
            else ->
                // show alarm by default
                props += Action.DISPLAY
        }
        props += Description(event.summary)
        event.alarms += alarm
    }

    protected fun populateExtended(row: ContentValues) {
        Constants.log.log(Level.FINE, "Read extended property from calender provider", row.getAsString(ExtendedProperties.NAME))

        if (row.getAsString(ExtendedProperties.NAME) == EXT_UNKNOWN_PROPERTY) {
            // de-serialize unknown property
            val stream = ByteArrayInputStream(Base64.decode(row.getAsString(ExtendedProperties.VALUE), Base64.NO_WRAP))
            try {
                ObjectInputStream(stream).use { stream ->
                    val property = stream.readObject() as Property
                    event!!.unknownProperties += property
                }
            } catch(e: Exception) {
                Constants.log.log(Level.WARNING, "Couldn't de-serialize unknown property", e)
            }
        }
    }

    @SuppressWarnings("Recycle")
    @Throws(FileNotFoundException::class, RemoteException::class)
    protected fun populateExceptions() {
        requireNotNull(id)
        val event = requireNotNull(event)

        calendar.provider.query(calendar.syncAdapterURI(Events.CONTENT_URI),
                arrayOf(Events._ID),
                Events.ORIGINAL_ID + "=?", arrayOf(id.toString()), null)?.use { c ->
            while (c.moveToNext()) {
                val exceptionId = c.getLong(0)
                try {
                    val exception = calendar.eventFactory.newInstance(calendar, exceptionId)

                    // make sure that all components have the same ORGANIZER [RFC 6638 3.1]
                    val exceptionEvent = exception.event!!
                    exceptionEvent.organizer = event.organizer
                    event.exceptions += exceptionEvent
                } catch (e: Exception) {
                    Constants.log.log(Level.WARNING, "Couldn't find exception details", e)
                }
            }
        }
    }


    @Throws(CalendarStorageException::class)
    fun add(): Uri {
        val batch = BatchOperation(calendar.provider)
        val idxEvent = add(batch)
        batch.commit()

        val result = batch.getResult(idxEvent) ?: throw CalendarStorageException("Empty result from content provider when adding event")
        id = ContentUris.parseId(result.uri)
        return result.uri
    }

    fun add(batch: BatchOperation): Int {
        val event = requireNotNull(event)
        val builder = ContentProviderOperation.newInsert(calendar.syncAdapterURI(eventsSyncURI()))

        val idxEvent = batch.nextBackrefIdx()
        buildEvent(null, builder)
        batch.enqueue(BatchOperation.Operation(builder))

        // add reminders
        event.alarms.forEach { insertReminder(batch, idxEvent, it) }

        // add attendees
        event.attendees.forEach { insertAttendee(batch, idxEvent, it) }

        // add unknown properties
        event.unknownProperties.forEach { insertUnknownProperty(batch, idxEvent, it) }

        // add exceptions
        for (exception in event.exceptions) {
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

            val builder = ContentProviderOperation.newInsert(calendar.syncAdapterURI(eventsSyncURI()))
            buildEvent(exception, builder)

            var date = exception.recurrenceId!!.date
            if (event.isAllDay() && date is DateTime) {       // correct VALUE=DATE-TIME RECURRENCE-IDs to VALUE=DATE for all-day events
                val dateFormatDate = SimpleDateFormat("yyyyMMdd", Locale.US)
                val dateString = dateFormatDate.format(date)
                try {
                    date = Date(dateString)
                } catch (e: ParseException) {
                    Constants.log.log(Level.WARNING, "Couldn't parse DATE part of DATE-TIME RECURRENCE-ID", e)
                }
            }
            builder .withValue(Events.ORIGINAL_ALL_DAY, if (event.isAllDay()) 1 else 0)
                    .withValue(Events.ORIGINAL_INSTANCE_TIME, date.time)

            val idxException = batch.nextBackrefIdx()
            batch.enqueue(BatchOperation.Operation(builder, Events.ORIGINAL_ID, idxEvent))

            // add exception reminders
            exception.alarms.forEach { insertReminder(batch, idxException, it) }

            // add exception attendees
            exception.attendees.forEach { insertAttendee(batch, idxException, it) }
        }

        return idxEvent
    }

    @Throws(CalendarStorageException::class)
    fun update(event: Event): Uri {
        this.event = event

        val batch = BatchOperation(calendar.provider)
        delete(batch)

        val idxEvent = batch.nextBackrefIdx()

        add(batch)
        batch.commit()

        val uri = batch.getResult(idxEvent)?.uri ?: throw CalendarStorageException("Couldn't update event")
        id = ContentUris.parseId(uri)
        return uri
    }

    @Throws(CalendarStorageException::class)
    fun delete(): Int {
        val batch = BatchOperation(calendar.provider)
        delete(batch)
        return batch.commit()
    }

    protected fun delete(batch: BatchOperation) {
        // remove event
        batch.enqueue(BatchOperation.Operation(ContentProviderOperation.newDelete(eventSyncURI())))

        // remove exceptions of that event, too (CalendarProvider doesn't do this)
        batch.enqueue(BatchOperation.Operation(ContentProviderOperation.newDelete(eventsSyncURI())
                .withSelection(Events.ORIGINAL_ID + "=?", arrayOf(id.toString()))))
    }

    @Throws(FileNotFoundException::class, CalendarStorageException::class)
    open protected fun buildEvent(recurrence: Event?, builder: Builder) {
        val isException = recurrence != null
        val event = if (isException)
            recurrence!!
        else
            requireNotNull(event)

        builder .withValue(Events.CALENDAR_ID, calendar.id)
                .withValue(Events.ALL_DAY, if (event.isAllDay()) 1 else 0)
                .withValue(Events.EVENT_TIMEZONE, event.getDtStartTzID())
                .withValue(Events.HAS_ATTENDEE_DATA, 1 /* we know information about all attendees and not only ourselves */)

        val dtStart = event.dtStart ?: throw CalendarStorageException("Events must have dtStart")
        dtStart.date?.time.let { builder.withValue(Events.DTSTART, it) }

        /* For cases where a "VEVENT" calendar component
           specifies a "DTSTART" property with a DATE value type but no
           "DTEND" nor "DURATION" property, the event's duration is taken to
           be one day. [RFC 5545 3.6.1] */
        var dtEnd = event.dtEnd
        if (event.isAllDay() && (dtEnd == null || !dtEnd.date.after(dtStart.date))) {
            // ical4j is not set to use floating times, so DATEs are UTC times internally
            Constants.log.log(Level.INFO, "Changing all-day event for Android compatibility: dtend := dtstart + 1 day")
            val c = java.util.Calendar.getInstance(TimeZone.getTimeZone(TimeZones.UTC_ID))
            c.time = dtStart.date
            c.add(java.util.Calendar.DATE, 1)
            event.dtEnd = DtEnd(Date(c.timeInMillis))
            dtEnd = event.dtEnd
            event.duration = null
        }

        /* For cases where a "VEVENT" calendar component
           specifies a "DTSTART" property with a DATE-TIME value type but no
           "DTEND" property, the event ends on the same calendar date and
           time of day specified by the "DTSTART" property. [RFC 5545 3.6.1] */
        else if (dtEnd == null || dtEnd.date.before(dtStart.date)) {
            Constants.log.info("Event without duration, setting dtend := dtstart")
            event.dtEnd = DtEnd(dtStart.date)
            dtEnd = event.dtEnd
        }
        dtEnd = requireNotNull(dtEnd)     // dtEnd is now guaranteed to not be null

        var recurring = false
        event.rRule?.let { rRule ->
            recurring = true
            builder.withValue(Events.RRULE, rRule.value)
        }
        if (!event.rDates.isEmpty()) {
            recurring = true
            builder.withValue(Events.RDATE, DateUtils.recurrenceSetsToAndroidString(event.rDates, event.isAllDay()))
        }
        event.exRule?.let { exRule -> builder.withValue(Events.EXRULE, exRule.value) }
        if (!event.exDates.isEmpty())
            builder.withValue(Events.EXDATE, DateUtils.recurrenceSetsToAndroidString(event.exDates, event.isAllDay()))

        // set either DTEND for single-time events or DURATION for recurring events
        // because that's the way Android likes it
        if (recurring) {
            // calculate DURATION from start and end date
            val duration = event.duration ?: Duration(dtStart.date, dtEnd.date)
            builder .withValue(Events.DURATION, duration.value)
        } else
            builder .withValue(Events.DTEND, dtEnd.date.time)
                    .withValue(Events.EVENT_END_TIMEZONE, event.getDtEndTzID())

        event.summary?.let { builder.withValue(Events.TITLE, it) }
        event.location?.let { builder.withValue(Events.EVENT_LOCATION, it) }
        event.description?.let { builder.withValue(Events.DESCRIPTION, it) }
        event.color?.let { builder.withValue(Events.EVENT_COLOR_KEY, it.name) }

        event.organizer?.let { organizer ->
            val email: String?
            val uri = organizer.calAddress
            if (uri.scheme.equals("mailto", true))
                email = uri.schemeSpecificPart
            else {
                val emailParam = organizer.getParameter(iCalendar.Email.PARAMETER_NAME) as iCalendar.Email?
                email = emailParam?.value
            }
            if (email != null)
                builder.withValue(Events.ORGANIZER, email)
            else
                Constants.log.warning("Ignoring ORGANIZER without email address (not supported by Android)")
        }

        event.status?.let {
            builder.withValue(Events.STATUS, when(it) {
                Status.VEVENT_CONFIRMED -> Events.STATUS_CONFIRMED
                Status.VEVENT_CANCELLED -> Events.STATUS_CANCELED
                else                    -> Events.STATUS_TENTATIVE
            })
        }

        builder.withValue(Events.AVAILABILITY, if (event.opaque) Events.AVAILABILITY_BUSY else Events.AVAILABILITY_FREE)

        event.forPublic?.let { forPublic ->
            builder.withValue(Events.ACCESS_LEVEL, if (forPublic) Events.ACCESS_PUBLIC else Events.ACCESS_PRIVATE)
        }

        Constants.log.log(Level.FINE, "Built event object", builder.build())
    }

    protected fun insertReminder(batch: BatchOperation, idxEvent: Int, alarm: VAlarm) {
        val builder = ContentProviderOperation.newInsert(calendar.syncAdapterURI(Reminders.CONTENT_URI))

        val action = alarm.action
        val method = when (action?.value) {
            Action.DISPLAY.value,
            Action.AUDIO.value -> Reminders.METHOD_ALERT
            Action.EMAIL.value -> Reminders.METHOD_EMAIL
            else               -> Reminders.METHOD_DEFAULT
        }

        val minutes = iCalendar.alarmMinBefore(alarm)
        builder .withValue(Reminders.METHOD, method)
                .withValue(Reminders.MINUTES, minutes)

        Constants.log.log(Level.FINE, "Built alarm $minutes minutes before event", builder.build())
        batch.enqueue(BatchOperation.Operation(builder, Reminders.EVENT_ID, idxEvent))
    }

    protected fun insertAttendee(batch: BatchOperation, idxEvent: Int, attendee: Attendee) {
        val builder = ContentProviderOperation.newInsert(calendar.syncAdapterURI(Attendees.CONTENT_URI))

        val member = attendee.calAddress
        if (member.scheme.equals("mailto", true))
            // attendee identified by email
            builder.withValue(Attendees.ATTENDEE_EMAIL, member.schemeSpecificPart)
        else {
            // attendee identified by other URI
            builder .withValue(Attendees.ATTENDEE_ID_NAMESPACE, member.scheme)
                    .withValue(Attendees.ATTENDEE_IDENTITY, member.schemeSpecificPart)
            (attendee.getParameter(iCalendar.Email.PARAMETER_NAME) as iCalendar.Email?)?.let { email ->
                builder.withValue(Attendees.ATTENDEE_EMAIL, email.value)
            }
        }

        attendee.getParameter(Parameter.CN)?.let { cn ->
            builder.withValue(Attendees.ATTENDEE_NAME, cn.value)
        }

        var type = Attendees.TYPE_NONE
        val cutype = attendee.getParameter(Parameter.CUTYPE) as CuType?
        if (cutype in arrayOf(CuType.RESOURCE, CuType.ROOM))
            // "attendee" is a (physical) resource
            type = Attendees.TYPE_RESOURCE
        else {
            // attendee is not a (physical) resource
            val role = attendee.getParameter(Parameter.ROLE) as Role?
            val relationship: Int
            if (role == Role.CHAIR)
                relationship = Attendees.RELATIONSHIP_ORGANIZER
            else {
                relationship = Attendees.RELATIONSHIP_ATTENDEE
                when(role) {
                    Role.OPT_PARTICIPANT -> type = Attendees.TYPE_OPTIONAL
                    Role.REQ_PARTICIPANT -> type = Attendees.TYPE_REQUIRED
                }
            }
            builder.withValue(Attendees.ATTENDEE_RELATIONSHIP, relationship)
        }

        val partStat = attendee.getParameter(Parameter.PARTSTAT) as PartStat?
        val status = when(partStat) {
            null,
            PartStat.NEEDS_ACTION -> Attendees.ATTENDEE_STATUS_INVITED
            PartStat.ACCEPTED     -> Attendees.ATTENDEE_STATUS_ACCEPTED
            PartStat.DECLINED     -> Attendees.ATTENDEE_STATUS_DECLINED
            PartStat.TENTATIVE    -> Attendees.ATTENDEE_STATUS_TENTATIVE
            else -> Attendees.ATTENDEE_STATUS_NONE
        }

        builder .withValue(Attendees.ATTENDEE_TYPE, type)
                .withValue(Attendees.ATTENDEE_STATUS, status)

        Constants.log.log(Level.FINE, "Built attendee", builder.build())
        batch.enqueue(BatchOperation.Operation(builder, Attendees.EVENT_ID, idxEvent))
    }

    fun insertUnknownProperty(batch: BatchOperation, idxEvent: Int, property: Property) {
        val baos = ByteArrayOutputStream()
        try {
            ObjectOutputStream(baos).use { oos ->
                oos.writeObject(property)

                if (baos.size() > MAX_UNKNOWN_PROPERTY_SIZE) {
                    Constants.log.warning("Ignoring unknown property with ${baos.size()} octets")
                    return
                }

                val builder = ContentProviderOperation.newInsert(calendar.syncAdapterURI(ExtendedProperties.CONTENT_URI))
                builder .withValue(ExtendedProperties.NAME, EXT_UNKNOWN_PROPERTY)
                        .withValue(ExtendedProperties.VALUE, Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP))

                batch.enqueue(BatchOperation.Operation(builder, ExtendedProperties.EVENT_ID, idxEvent))
            }
        } catch(e: IOException) {
            Constants.log.log(Level.WARNING, "Couldn't serialize unknown property", e)
        }
    }


    protected fun eventsSyncURI() = calendar.syncAdapterURI(Events.CONTENT_URI)

    protected fun eventSyncURI(): Uri {
        val id = requireNotNull(id)
        return calendar.syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, id))
    }


    override fun toString() = MiscUtils.reflectionToString(this)

}
