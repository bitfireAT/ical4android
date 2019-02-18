/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import android.content.ContentProviderOperation
import android.content.ContentProviderOperation.Builder
import android.content.ContentUris
import android.content.ContentValues
import android.content.EntityIterator
import android.database.DatabaseUtils
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.ObjectInputStream
import java.net.URI
import java.net.URISyntaxException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level

/**
 * Stores and retrieves VEVENT iCalendar objects (represented as [Event]s) to/from the
 * Android Calendar provider.
 *
 * Extend this class to process specific fields of the event.
 *
 * Important: To use recurrence exceptions, you MUST set _SYNC_ID and ORIGINAL_SYNC_ID
 * in populateEvent() / buildEvent. Setting _ID and ORIGINAL_ID is not sufficient.
 */
abstract class AndroidEvent(
        val calendar: AndroidCalendar<AndroidEvent>
) {

    companion object {

        /** [ExtendedProperties.NAME] for unknown iCal properties */
        @Deprecated("New serialization format", ReplaceWith("EXT_UNKNOWN_PROPERTY2"))
        const val EXT_UNKNOWN_PROPERTY = "unknown-property"
        const val EXT_UNKNOWN_PROPERTY2 = "unknown-property.v2"
        const val MAX_UNKNOWN_PROPERTY_SIZE = 25000

        // not declared in ical4j Parameters class yet
        private const val PARAMETER_EMAIL = "EMAIL"

    }

    var id: Long? = null
        protected set

    /**
     * Creates a new object from an event which already exists in the calendar storage.
     * @param values database row with all columns, as returned by the calendar provider
     */
    constructor(calendar: AndroidCalendar<AndroidEvent>, values: ContentValues): this(calendar) {
        this.id = values.getAsLong(Events._ID)
        // derived classes process SYNC1 etc.
    }

    /**
     * Creates a new object from an event which doesn't exist in the calendar storage yet.
     * @param event event that can be saved into the calendar storage
     */
    constructor(calendar: AndroidCalendar<AndroidEvent>, event: Event): this(calendar) {
        this.event = event
    }

    var event: Event? = null
        /**
         * This getter returns the full event data, either from [event] or, if [event] is null, by reading event
         * number [id] from the Android calendar storage
         * @throws IllegalArgumentException if event has not been saved yet
         * @throws FileNotFoundException if there's no event with [id] in the calendar storage
         * @throws RemoteException on calendar provider errors
         */
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

                    useRetainedClassification()

                    /* remove ORGANIZER from all components if there are no attendees
                       (i.e. this is not a group-scheduled calendar entity) */
                    if (event.attendees.isEmpty()) {
                        event.organizer = null
                        event.exceptions.forEach { it.organizer = null }
                    }

                    return field
                }
            } finally {
                iterEvents?.close()
            }
            throw FileNotFoundException("Couldn't find event $id")
        }

    /**
     * Reads event data from the calendar provider.
     * @param row values of an [Events] row, as returned by the calendar provider
     */
    protected open fun populateEvent(row: ContentValues) {
        val event = requireNotNull(event)

        Constants.log.log(Level.FINE, "Read event entity from calender provider", row)
        MiscUtils.removeEmptyStrings(row)

        event.summary = row.getAsString(Events.TITLE)
        event.location = row.getAsString(Events.EVENT_LOCATION)
        event.description = row.getAsString(Events.DESCRIPTION)

        row.getAsString(Events.EVENT_COLOR_KEY)?.let { name ->
            try {
                event.color = Css3Color.valueOf(name)
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
            tz?.let { start.timeZone = it }
            event.dtStart = DtStart(start)

            when {
                tsEnd != null -> {
                    val end = DateTime(tsEnd)
                    tz?.let { end.timeZone = it }
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
        when (row.getAsInteger(Events.STATUS)) {
            Events.STATUS_CONFIRMED -> event.status = Status.VEVENT_CONFIRMED
            Events.STATUS_TENTATIVE -> event.status = Status.VEVENT_TENTATIVE
            Events.STATUS_CANCELED  -> event.status = Status.VEVENT_CANCELLED
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
        when (row.getAsInteger(Events.ACCESS_LEVEL)) {
            Events.ACCESS_PUBLIC       -> event.classification = Clazz.PUBLIC
            Events.ACCESS_PRIVATE      -> event.classification = Clazz.PRIVATE
            Events.ACCESS_CONFIDENTIAL -> event.classification = Clazz.CONFIDENTIAL
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

    protected open fun populateAttendee(row: ContentValues) {
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
                email?.let { attendee.parameters.add(Email(it)) }
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

    protected open fun populateReminder(row: ContentValues) {
        Constants.log.log(Level.FINE, "Read event reminder from calender provider", row)

        val event = requireNotNull(event)
        val alarm = VAlarm(Dur(0, 0, -row.getAsInteger(Reminders.MINUTES), 0))

        val props = alarm.properties
        props += when (row.getAsInteger(Reminders.METHOD)) {
            Reminders.METHOD_ALARM,
            Reminders.METHOD_ALERT ->
                Action.DISPLAY
            Reminders.METHOD_EMAIL,
            Reminders.METHOD_SMS ->
                Action.EMAIL
            else ->
                // show alarm by default
                Action.DISPLAY
        }
        props += Description(event.summary)
        event.alarms += alarm
    }

    protected open fun populateExtended(row: ContentValues) {
        Constants.log.log(Level.FINE, "Read extended property from calender provider", row.getAsString(ExtendedProperties.NAME))
        val event = requireNotNull(event)

        try {
            when (row.getAsString(ExtendedProperties.NAME)) {
                EXT_UNKNOWN_PROPERTY -> {
                    // deserialize unknown property v1 (deprecated)
                    val stream = ByteArrayInputStream(Base64.decode(row.getAsString(ExtendedProperties.VALUE), Base64.NO_WRAP))
                    ObjectInputStream(stream).use {
                        event.unknownProperties += it.readObject() as Property
                    }
                }

                EXT_UNKNOWN_PROPERTY2 -> {
                    // deserialize unknown property v2
                    event.unknownProperties += UnknownProperty.fromExtendedProperty(row.getAsString(ExtendedProperties.VALUE))
                }
            }
        } catch(e: Exception) {
            Constants.log.log(Level.WARNING, "Couldn't parse extended property", e)
        }
    }

    protected open fun populateExceptions() {
        requireNotNull(id)
        val event = requireNotNull(event)

        calendar.provider.query(calendar.syncAdapterURI(Events.CONTENT_URI),
                null,
                Events.ORIGINAL_ID + "=?", arrayOf(id.toString()), null)?.use { c ->
            while (c.moveToNext()) {
                val values = ContentValues(c.columnCount)
                DatabaseUtils.cursorRowToContentValues(c, values)
                try {
                    val exception = calendar.eventFactory.fromProvider(calendar, values)

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

    private fun retainClassification() {
        /* retain classification other than PUBLIC and PRIVATE as unknown property so
           that it can be reused when "server default" is selected */
        val event = requireNotNull(event)
        event.classification?.let {
            if (it != Clazz.PUBLIC && it != Clazz.PRIVATE)
                event.unknownProperties += it
        }
    }


    /**
     * Saves an unsaved instance into the calendar storage.
     * @throws CalendarStorageException when the calendar provider doesn't return a result row
     * @throws RemoteException on calendar provider errors
     */
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
        retainClassification()
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

            val exBuilder = ContentProviderOperation.newInsert(calendar.syncAdapterURI(eventsSyncURI()))
            buildEvent(exception, exBuilder)

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
            exBuilder.withValue(Events.ORIGINAL_ALL_DAY, if (event.isAllDay()) 1 else 0)
                    .withValue(Events.ORIGINAL_INSTANCE_TIME, date.time)

            val idxException = batch.nextBackrefIdx()
            batch.enqueue(BatchOperation.Operation(exBuilder, Events.ORIGINAL_ID, idxEvent))

            // add exception reminders
            exception.alarms.forEach { insertReminder(batch, idxException, it) }

            // add exception attendees
            exception.attendees.forEach { insertAttendee(batch, idxException, it) }
        }

        return idxEvent
    }

    /**
     * Updates an already existing event in the calendar storage with the values
     * from the instance.
     * @throws CalendarStorageException when the calendar provider doesn't return a result row
     * @throws RemoteException on calendar provider errors
     */
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

    /**
     * Deletes an existing event from the calendar storage.
     * @throws RemoteException on calendar provider errors
     */
    fun delete(): Int {
        val batch = BatchOperation(calendar.provider)
        delete(batch)
        return batch.commit()
    }

    protected fun delete(batch: BatchOperation) {
        // remove exceptions of event, too (CalendarProvider doesn't do this)
        batch.enqueue(BatchOperation.Operation(ContentProviderOperation.newDelete(eventsSyncURI())
                .withSelection(Events.ORIGINAL_ID + "=?", arrayOf(id.toString()))))

        // remove event
        batch.enqueue(BatchOperation.Operation(ContentProviderOperation.newDelete(eventSyncURI())))
    }


    protected open fun buildEvent(recurrence: Event?, builder: Builder) {
        val isException = recurrence != null
        val event = if (isException)
            recurrence!!
        else
            requireNotNull(event)

        val dtStart = event.dtStart ?: throw CalendarStorageException("Events must have dtStart")
        MiscUtils.androidifyTimeZone(dtStart)

        builder .withValue(Events.CALENDAR_ID, calendar.id)
                .withValue(Events.ALL_DAY, if (event.isAllDay()) 1 else 0)
                .withValue(Events.EVENT_TIMEZONE, MiscUtils.getTzId(dtStart))
                .withValue(Events.HAS_ATTENDEE_DATA, 1 /* we know information about all attendees and not only ourselves */)

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
        MiscUtils.androidifyTimeZone(dtEnd)

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
                    .withValue(Events.EVENT_END_TIMEZONE, MiscUtils.getTzId(dtEnd))

        event.summary?.let { builder.withValue(Events.TITLE, it) }
        event.location?.let { builder.withValue(Events.EVENT_LOCATION, it) }
        event.description?.let { builder.withValue(Events.DESCRIPTION, it) }
        event.color?.let {
            val colorName = it.name
            // set event color (if it's available for this account)
            calendar.provider.query(calendar.syncAdapterURI(Colors.CONTENT_URI), arrayOf(Colors.COLOR_KEY),
                    "${Colors.COLOR_KEY}=? AND ${Colors.COLOR_TYPE}=${Colors.TYPE_EVENT}", arrayOf(colorName), null)?.use { cursor ->
                if (cursor.moveToNext())
                    builder.withValue(Events.EVENT_COLOR_KEY, colorName)
                else
                    Constants.log.fine("Ignoring event color: $colorName (not available for this account)")
            }
        }

        event.organizer?.let { organizer ->
            val email: String?
            val uri = organizer.calAddress
            email = if (uri.scheme.equals("mailto", true))
                uri.schemeSpecificPart
            else {
                val emailParam = organizer.getParameter(PARAMETER_EMAIL) as? Email
                emailParam?.value
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

        when (event.classification) {
            Clazz.PUBLIC       -> builder.withValue(Events.ACCESS_LEVEL, Events.ACCESS_PUBLIC)
            Clazz.PRIVATE      -> builder.withValue(Events.ACCESS_LEVEL, Events.ACCESS_PRIVATE)
            Clazz.CONFIDENTIAL -> builder.withValue(Events.ACCESS_LEVEL, Events.ACCESS_CONFIDENTIAL)
        }

        Constants.log.log(Level.FINE, "Built event object", builder.build())
    }

    protected open fun insertReminder(batch: BatchOperation, idxEvent: Int, alarm: VAlarm) {
        val builder = ContentProviderOperation.newInsert(calendar.syncAdapterURI(Reminders.CONTENT_URI))

        val action = alarm.action
        val method = when (action?.value) {
            Action.DISPLAY.value,
            Action.AUDIO.value -> Reminders.METHOD_ALERT
            Action.EMAIL.value -> Reminders.METHOD_EMAIL
            else               -> Reminders.METHOD_DEFAULT
        }

        val minutes = ICalendar.alarmMinBefore(alarm)
        builder .withValue(Reminders.METHOD, method)
                .withValue(Reminders.MINUTES, minutes)

        Constants.log.log(Level.FINE, "Built alarm $minutes minutes before event", builder.build())
        batch.enqueue(BatchOperation.Operation(builder, Reminders.EVENT_ID, idxEvent))
    }

    protected open fun insertAttendee(batch: BatchOperation, idxEvent: Int, attendee: Attendee) {
        val builder = ContentProviderOperation.newInsert(calendar.syncAdapterURI(Attendees.CONTENT_URI))

        val member = attendee.calAddress
        if (member.scheme.equals("mailto", true))
            // attendee identified by email
            builder.withValue(Attendees.ATTENDEE_EMAIL, member.schemeSpecificPart)
        else {
            // attendee identified by other URI
            builder .withValue(Attendees.ATTENDEE_ID_NAMESPACE, member.scheme)
                    .withValue(Attendees.ATTENDEE_IDENTITY, member.schemeSpecificPart)
            (attendee.getParameter(PARAMETER_EMAIL) as? Email)?.let { email ->
                builder.withValue(Attendees.ATTENDEE_EMAIL, email.value)
            }
        }

        attendee.getParameter(Parameter.CN)?.let { cn ->
            builder.withValue(Attendees.ATTENDEE_NAME, cn.value)
        }

        var type = Attendees.TYPE_NONE
        val cutype = attendee.getParameter(Parameter.CUTYPE) as? CuType
        if (cutype in arrayOf(CuType.RESOURCE, CuType.ROOM))
            // "attendee" is a (physical) resource
            type = Attendees.TYPE_RESOURCE
        else {
            // attendee is not a (physical) resource
            val role = attendee.getParameter(Parameter.ROLE) as? Role
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

        val partStat = attendee.getParameter(Parameter.PARTSTAT) as? PartStat
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

    protected open fun insertUnknownProperty(batch: BatchOperation, idxEvent: Int, property: Property) {
        if (property.value.length > MAX_UNKNOWN_PROPERTY_SIZE) {
            Constants.log.warning("Ignoring unknown property with ${property.value.length} octets (too long)")
            return
        }

        val builder = ContentProviderOperation.newInsert(calendar.syncAdapterURI(ExtendedProperties.CONTENT_URI))
        builder .withValue(ExtendedProperties.NAME, EXT_UNKNOWN_PROPERTY2)
                .withValue(ExtendedProperties.VALUE, UnknownProperty.toExtendedProperty(property))

        batch.enqueue(BatchOperation.Operation(builder, ExtendedProperties.EVENT_ID, idxEvent))
    }

    private fun useRetainedClassification() {
        val event = requireNotNull(event)

        var retainedClazz: Clazz? = null
        val it = event.unknownProperties.iterator()
        while (it.hasNext()) {
            val prop = it.next()
            if (prop is Clazz) {
                retainedClazz = prop
                it.remove()
            }
        }

        if (event.classification == null)
            // no classification, use retained one if possible
            event.classification = retainedClazz
    }


    protected fun eventsSyncURI() = calendar.syncAdapterURI(Events.CONTENT_URI)

    protected fun eventSyncURI(): Uri {
        val id = requireNotNull(id)
        return calendar.syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, id))
    }

    override fun toString() = MiscUtils.reflectionToString(this)


    /**
     * Helpers to (de)serialize unknown properties as JSON to store it in an Android ExtendedProperty row.
     *
     * Format: `{ propertyName, propertyValue, { param1Name: param1Value, ... } }`, with the third
     * array (parameters) being optional.
     */
    object UnknownProperty {

        /**
         * Deserializes a JSON string from an ExtendedProperty value to an ical4j property.
         *
         * @param jsonString JSON representation of an ical4j property
         * @return ical4j property, generated from [jsonString]
         * @throws org.json.JSONException when the input value can't be parsed
         */
        fun fromExtendedProperty(jsonString: String): Property {
            val json = JSONArray(jsonString)
            val name = json.getString(0)
            val value = json.getString(1)

            val params = ParameterList()
            json.optJSONObject(2)?.let { jsonParams ->
                for (paramName in jsonParams.keys())
                    params.add(ICalendar.parameterFactoryRegistry.createParameter(
                            paramName,
                            jsonParams.getString(paramName)
                    ))
            }

            return ICalendar.propertyFactoryRegistry.createProperty(name, params, value)
        }

        /**
         * Serializes an ical4j property to a JSON string that can be stored in an ExtendedProperty.
         *
         * @param prop property to serialize as JSON
         * @return JSON representation of [prop]
         */
        fun toExtendedProperty(prop: Property): String {
            val json = JSONArray()
            json.put(prop.name)
            json.put(prop.value)

            if (!prop.parameters.isEmpty) {
                val jsonParams = JSONObject()
                for (param in prop.parameters)
                    jsonParams.put(param.name, param.value)
                json.put(jsonParams)
            }

            return json.toString()
        }

    }

}
