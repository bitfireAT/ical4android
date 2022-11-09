/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.EntityIterator
import android.net.Uri
import android.os.RemoteException
import android.provider.CalendarContract.*
import android.util.Patterns
import androidx.annotation.CallSuper
import at.bitfire.ical4android.BatchOperation.CpoBuilder
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.MiscUtils
import at.bitfire.ical4android.util.MiscUtils.CursorHelper.toValues
import at.bitfire.ical4android.util.MiscUtils.UriHelper.asSyncAdapter
import at.bitfire.ical4android.util.TimeApiExtensions
import at.bitfire.ical4android.util.TimeApiExtensions.requireZoneId
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalTime
import at.bitfire.ical4android.util.TimeApiExtensions.toRfc5545Duration
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.*
import net.fortuna.ical4j.model.property.*
import net.fortuna.ical4j.util.TimeZones
import java.io.FileNotFoundException
import java.net.URI
import java.net.URISyntaxException
import java.time.*
import java.time.Duration
import java.time.Period
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

        const val MUTATORS_SEPARATOR = ','

        /**
         * VEVENT CATEGORIES are stored as an extended property with this [ExtendedProperties.NAME].
         *
         * The [ExtendedProperties.VALUE] format is the same as used by the AOSP Exchange ActiveSync adapter:
         * the category values are stored as list, separated by [CATEGORIES_SEPARATOR]. (If a category
         * value contains [CATEGORIES_SEPARATOR], [CATEGORIES_SEPARATOR] will be dropped.)
         *
         * Example: `Cat1\Cat2`
         */
        const val MIMETYPE_CATEGORIES = "categories"
        const val CATEGORIES_SEPARATOR = '\\'

        /**
         * VEVENT URL is stored as an extended property with this [ExtendedProperties.NAME].
         * The URL is directly put into [ExtendedProperties.VALUE].
         */
        const val MIMETYPE_URL = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.ical4android.url"
    }

    var id: Long? = null
        protected set


    /**
     * Creates a new object from an event which already exists in the calendar storage.
     * @param values database row with all columns, as returned by the calendar provider
     */
    constructor(calendar: AndroidCalendar<AndroidEvent>, values: ContentValues) : this(calendar) {
        this.id = values.getAsLong(Events._ID)
        // derived classes process SYNC1 etc.
    }

    /**
     * Creates a new object from an event which doesn't exist in the calendar storage yet.
     * @param event event that can be saved into the calendar storage
     */
    constructor(calendar: AndroidCalendar<AndroidEvent>, event: Event) : this(calendar) {
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
                iterEvents = EventsEntity.newEntityIterator(
                        calendar.provider.query(
                                ContentUris.withAppendedId(EventsEntity.CONTENT_URI, id).asSyncAdapter(calendar.account),
                                null, null, null, null),
                        calendar.provider
                )

                if (iterEvents.hasNext()) {
                    val e = iterEvents.next()

                    // create new Event which will be populated
                    val newEvent = Event()
                    field = newEvent

                    // calculate some scheduling properties
                    val groupScheduled = e.subValues.any { it.uri == Attendees.CONTENT_URI }
                    val isOrganizer = (e.entityValues.getAsInteger(Events.IS_ORGANIZER) ?: 0) != 0

                    populateEvent(MiscUtils.removeEmptyStrings(e.entityValues), groupScheduled)

                    for (subValue in e.subValues) {
                        val subValues = MiscUtils.removeEmptyStrings(subValue.values)
                        when (subValue.uri) {
                            Attendees.CONTENT_URI -> populateAttendee(subValues, isOrganizer)
                            Reminders.CONTENT_URI -> populateReminder(subValues)
                            ExtendedProperties.CONTENT_URI -> populateExtended(subValues)
                        }
                    }
                    populateExceptions()
                    useRetainedClassification()
                    return newEvent
                }
            } catch (e: Exception) {
                /* Populating event has been interrupted by an exception, so we reset the event to
                avoid an inconsistent state. This also ensures that the exception will be thrown
                again on the next get() call. */
                field = null
                throw e
            } finally {
                iterEvents?.close()
            }
            throw FileNotFoundException("Couldn't find event $id")
        }

    /**
     * Reads event data from the calendar provider.
     *
     * @param row values of an [Events] row, as returned by the calendar provider
     */
    @Suppress("UNUSED_VALUE")
    @CallSuper
    protected open fun populateEvent(row: ContentValues, groupScheduled: Boolean) {
        Ical4Android.log.log(Level.FINE, "Read event entity from calender provider", row)
        val event = requireNotNull(event)

        row.getAsString(Events.MUTATORS)?.let { strPackages ->
            val packages = strPackages.split(MUTATORS_SEPARATOR).toSet()
            event.userAgents.addAll(packages)
        }

        val allDay = (row.getAsInteger(Events.ALL_DAY) ?: 0) != 0
        val tsStart = row.getAsLong(Events.DTSTART) ?: throw CalendarStorageException("Found event without DTSTART")

        var tsEnd = row.getAsLong(Events.DTEND)
        var duration =   // only use DURATION of DTEND is not defined
                if (tsEnd == null)
                    row.getAsString(Events.DURATION)?.let { AndroidTimeUtils.parseDuration(it) }
                else
                    null

        if (allDay) {
            event.dtStart = DtStart(Date(tsStart))

            // Android events MUST have duration or dtend [https://developer.android.com/reference/android/provider/CalendarContract.Events#operations].
            // Assume 1 day if missing (should never occur, but occurs).
            if (tsEnd == null && duration == null)
                duration = Duration.ofDays(1)

            if (duration != null) {
                // Some servers have problems with DURATION, so we always generate DTEND.
                val startDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(tsStart), ZoneOffset.UTC).toLocalDate()
                if (duration is Duration)
                    duration = Period.ofDays(duration.toDays().toInt())
                tsEnd = (startDate + duration).toEpochDay() * TimeApiExtensions.MILLIS_PER_DAY
                duration = null
            }

            if (tsEnd != null) {
                when {
                    tsEnd < tsStart ->
                        Ical4Android.log.warning("dtEnd $tsEnd (allDay) < dtStart $tsStart (allDay), ignoring")

                    tsEnd == tsStart ->
                        Ical4Android.log.fine("dtEnd $tsEnd (allDay) = dtStart, won't generate DTEND property")

                    else /* tsEnd > tsStart */ ->
                        event.dtEnd = DtEnd(Date(tsEnd))
                }
            }

        } else /* !allDay */ {
            // use DATE-TIME values

            // check time zone ID (calendar apps may insert no or an invalid ID)
            val startTzId = DateUtils.findAndroidTimezoneID(row.getAsString(Events.EVENT_TIMEZONE))
            val startTz = DateUtils.ical4jTimeZone(startTzId)
            val dtStartDateTime = DateTime(tsStart).apply {
                if (startTz != null) {  // null if there was not ical4j time zone for startTzId, which should not happen, but technically may happen
                    if (TimeZones.isUtc(startTz))
                        isUtc = true
                    else
                        timeZone = startTz
                }
            }
            event.dtStart = DtStart(dtStartDateTime)

            // Android events MUST have duration or dtend [https://developer.android.com/reference/android/provider/CalendarContract.Events#operations].
            // Assume 1 hour if missing (should never occur, but occurs).
            if (tsEnd == null && duration == null)
                duration = Duration.ofHours(1)

            if (duration != null) {
                // Some servers have problems with DURATION, so we always generate DTEND.
                val zonedStart = dtStartDateTime.toZonedDateTime()
                tsEnd = (zonedStart + duration).toInstant().toEpochMilli()
                duration = null
            }

            if (tsEnd != null) {
                if (tsEnd < tsStart)
                    Ical4Android.log.warning("dtEnd $tsEnd < dtStart $tsStart, ignoring")
                /*else if (tsEnd == tsStart)    // iCloud sends 404 when it receives an iCalendar with DTSTART but without DTEND
                    Ical4Android.log.fine("dtEnd $tsEnd == dtStart, won't generate DTEND property")*/
                else /* tsEnd > tsStart */ {
                    val endTz = row.getAsString(Events.EVENT_END_TIMEZONE)?.let { tzId ->
                        DateUtils.ical4jTimeZone(tzId)
                    } ?: startTz
                    event.dtEnd = DtEnd(DateTime(tsEnd).apply {
                        if (endTz != null) {
                            if (TimeZones.isUtc(endTz))
                                isUtc = true
                            else
                                timeZone = endTz
                        }
                    })
                }
            }

        }

        // recurrence
        try {
            row.getAsString(Events.RRULE)?.let { rulesStr ->
                for (rule in rulesStr.split(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR))
                    event.rRules += RRule(rule)
            }
            row.getAsString(Events.RDATE)?.let { datesStr ->
                val rDate = AndroidTimeUtils.androidStringToRecurrenceSet(datesStr, allDay, tsStart) { RDate(it) }
                event.rDates += rDate
            }

            row.getAsString(Events.EXRULE)?.let { rulesStr ->
                for (rule in rulesStr.split(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR))
                    event.exRules += ExRule(null, rule)
            }
            row.getAsString(Events.EXDATE)?.let { datesStr ->
                val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(datesStr, allDay) { ExDate(it) }
                event.exDates += exDate
            }
        } catch (e: Exception) {
            Ical4Android.log.log(Level.WARNING, "Couldn't parse recurrence rules, ignoring", e)
        }

        event.summary = row.getAsString(Events.TITLE)
        event.location = row.getAsString(Events.EVENT_LOCATION)
        event.description = row.getAsString(Events.DESCRIPTION)

        row.getAsString(Events.EVENT_COLOR_KEY)?.let { name ->
            try {
                event.color = Css3Color.valueOf(name)
            } catch (e: IllegalArgumentException) {
                Ical4Android.log.warning("Ignoring unknown color $name from Calendar Provider")
            }
        }

        // status
        when (row.getAsInteger(Events.STATUS)) {
            Events.STATUS_CONFIRMED -> event.status = Status.VEVENT_CONFIRMED
            Events.STATUS_TENTATIVE -> event.status = Status.VEVENT_TENTATIVE
            Events.STATUS_CANCELED -> event.status = Status.VEVENT_CANCELLED
        }

        // availability
        event.opaque = row.getAsInteger(Events.AVAILABILITY) != Events.AVAILABILITY_FREE

        // scheduling
        if (groupScheduled) {
            // ORGANIZER must only be set for group-scheduled events (= events with attendees)
            if (row.containsKey(Events.ORGANIZER) && groupScheduled)
                try {
                    event.organizer = Organizer(URI("mailto", row.getAsString(Events.ORGANIZER), null))
                } catch (e: URISyntaxException) {
                    Ical4Android.log.log(Level.WARNING, "Error when creating ORGANIZER mailto URI, ignoring", e)
                }
        }

        // classification
        when (row.getAsInteger(Events.ACCESS_LEVEL)) {
            Events.ACCESS_PUBLIC -> event.classification = Clazz.PUBLIC
            Events.ACCESS_PRIVATE -> event.classification = Clazz.PRIVATE
            Events.ACCESS_CONFIDENTIAL -> event.classification = Clazz.CONFIDENTIAL
        }

        // exceptions from recurring events
        row.getAsLong(Events.ORIGINAL_INSTANCE_TIME)?.let { originalInstanceTime ->
            val originalAllDay = (row.getAsInteger(Events.ORIGINAL_ALL_DAY) ?: 0) != 0
            val originalDate =
                    if (originalAllDay)
                        Date(originalInstanceTime)
                    else
                        DateTime(originalInstanceTime)
            if (originalDate is DateTime) {
                event.dtStart?.let { dtStart ->
                    if (dtStart.isUtc)
                        originalDate.isUtc = true
                    else if (dtStart.timeZone != null)
                        originalDate.timeZone = dtStart.timeZone
                }
            }
            event.recurrenceId = RecurrenceId(originalDate)
        }
    }

    protected open fun populateAttendee(row: ContentValues, isOrganizer: Boolean) {
        Ical4Android.log.log(Level.FINE, "Read event attendee from calender provider", row)

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

            // always add RSVP (offer attendees to accept/decline)
            params.add(Rsvp.TRUE)

            row.getAsString(Attendees.ATTENDEE_NAME)?.let { cn -> params.add(Cn(cn)) }

            // type/relation mapping is complex and thus outsourced to AttendeeMappings
            AttendeeMappings.androidToICalendar(row, attendee)

            // status
            when (row.getAsInteger(Attendees.ATTENDEE_STATUS)) {
                Attendees.ATTENDEE_STATUS_INVITED -> params.add(PartStat.NEEDS_ACTION)
                Attendees.ATTENDEE_STATUS_ACCEPTED -> params.add(PartStat.ACCEPTED)
                Attendees.ATTENDEE_STATUS_DECLINED -> params.add(PartStat.DECLINED)
                Attendees.ATTENDEE_STATUS_TENTATIVE -> params.add(PartStat.TENTATIVE)
                Attendees.ATTENDEE_STATUS_NONE -> { /* no information, don't add PARTSTAT */ }
            }

            event!!.attendees.add(attendee)
        } catch (e: URISyntaxException) {
            Ical4Android.log.log(Level.WARNING, "Couldn't parse attendee information, ignoring", e)
        }
    }

    protected open fun populateReminder(row: ContentValues) {
        Ical4Android.log.log(Level.FINE, "Read event reminder from calender provider", row)
        val event = requireNotNull(event)

        val alarm = VAlarm(Duration.ofMinutes(-row.getAsLong(Reminders.MINUTES)))

        val props = alarm.properties
        when (row.getAsInteger(Reminders.METHOD)) {
            Reminders.METHOD_EMAIL -> {
                val accountName = calendar.account.name
                if (Patterns.EMAIL_ADDRESS.matcher(accountName).matches()) {
                    props += Action.EMAIL
                    // ACTION:EMAIL requires SUMMARY, DESCRIPTION, ATTENDEE
                    props += Summary(event.summary)
                    props += Description(event.description ?: event.summary)
                    // Android doesn't allow to save email reminder recipients, so we always use the
                    // account name (should be account owner's email address)
                    props += Attendee(URI("mailto", calendar.account.name, null))
                } else {
                    Ical4Android.log.warning("Account name is not an email address; changing EMAIL reminder to DISPLAY")
                    props += Action.DISPLAY
                    props += Description(event.summary)
                }
            }

            // default: set ACTION:DISPLAY (requires DESCRIPTION)
            else -> {
                props += Action.DISPLAY
                props += Description(event.summary)
            }
        }
        event.alarms += alarm
    }

    protected open fun populateExtended(row: ContentValues) {
        val mimeType = row.getAsString(ExtendedProperties.NAME)
        val rawValue = row.getAsString(ExtendedProperties.VALUE)
        Ical4Android.log.log(Level.FINE, "Read extended property from calender provider", arrayOf(mimeType, rawValue))
        val event = requireNotNull(event)

        try {
            when (mimeType) {
                MIMETYPE_CATEGORIES ->
                    event.categories += rawValue.split(CATEGORIES_SEPARATOR)

                MIMETYPE_URL ->
                    try {
                        event.url = URI(rawValue)
                    } catch(e: URISyntaxException) {
                        Ical4Android.log.warning("Won't process invalid local URL: $rawValue")
                    }

                UnknownProperty.CONTENT_ITEM_TYPE ->
                    event.unknownProperties += UnknownProperty.fromJsonString(rawValue)
            }
        } catch (e: Exception) {
            Ical4Android.log.log(Level.WARNING, "Couldn't parse extended property", e)
        }
    }

    protected open fun populateExceptions() {
        requireNotNull(id)
        val event = requireNotNull(event)

        calendar.provider.query(Events.CONTENT_URI.asSyncAdapter(calendar.account),
                null,
                Events.ORIGINAL_ID + "=?", arrayOf(id.toString()), null)?.use { c ->
            while (c.moveToNext()) {
                val values = c.toValues(true)
                try {
                    val exception = calendar.eventFactory.fromProvider(calendar, values)
                    val exceptionEvent = exception.event!!
                    val recurrenceId = exceptionEvent.recurrenceId!!

                    // generate EXDATE instead of RECURRENCE-ID exceptions for cancelled instances
                    if (exceptionEvent.status == Status.VEVENT_CANCELLED) {
                        val list = DateList(
                                if (DateUtils.isDate(recurrenceId)) Value.DATE else Value.DATE_TIME,
                                recurrenceId.timeZone
                        )
                        list.add(recurrenceId.date)
                        event.exDates += ExDate(list).apply {
                            if (DateUtils.isDateTime(recurrenceId)) {
                                if (recurrenceId.isUtc)
                                    setUtc(true)
                                else
                                    timeZone = recurrenceId.timeZone
                            }
                        }

                    } else /* exceptionEvent.status != Status.VEVENT_CANCELLED */ {
                        // make sure that all components have the same ORGANIZER [RFC 6638 3.1]
                        exceptionEvent.organizer = event.organizer

                        // add exception to list of exceptions
                        event.exceptions += exceptionEvent
                    }
                } catch (e: Exception) {
                    Ical4Android.log.log(Level.WARNING, "Couldn't find exception details", e)
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
     * Saves an unsaved event into the calendar storage.
     *
     * @return content URI of the created event
     *
     * @throws CalendarStorageException when the calendar provider doesn't return a result row
     * @throws RemoteException on calendar provider errors
     */
    fun add(): Uri {
        val batch = BatchOperation(calendar.provider)
        val idxEvent = addOrUpdateRows(batch) ?: throw AssertionError("Expected Events._ID backref")
        batch.commit()

        val resultUri = batch.getResult(idxEvent)?.uri
                ?: throw CalendarStorageException("Empty result from content provider when adding event")
        id = ContentUris.parseId(resultUri)
        return resultUri
    }

    /**
     * Adds or updates the calendar provider [Events] main row for this [event].
     *
     * @param batch batch operation for insert/update operation
     *
     * @return [Events._ID] of the created/updated row; *null* if now ID is available
     */
    fun addOrUpdateRows(batch: BatchOperation): Int? {
        val event = requireNotNull(event)
        val builder =
                if (id == null)
                    CpoBuilder.newInsert(Events.CONTENT_URI.asSyncAdapter(calendar.account))
                else
                    CpoBuilder.newUpdate(eventSyncURI())

        val idxEvent = if (id == null) batch.nextBackrefIdx() else null
        buildEvent(null, builder)
        batch.enqueue(builder)

        // add reminders
        event.alarms.forEach { insertReminder(batch, idxEvent, it) }

        // add attendees
        val organizer = event.organizerEmail ?:
                /* no ORGANIZER, use current account owner as ORGANIZER */
                calendar.ownerAccount ?: calendar.account.name
        event.attendees.forEach { insertAttendee(batch, idxEvent, it, organizer) }

        // add extended properties
        // CATEGORIES
        if (event.categories.isNotEmpty())
            insertCategories(batch, idxEvent)
        // CLASS
        retainClassification()
        // URL
        event.url?.let { url ->
            insertExtendedProperty(batch, idxEvent, MIMETYPE_URL, url.toString())
        }
        // unknown properties
        event.unknownProperties.forEach {
            insertUnknownProperty(batch, idxEvent, it)
        }

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

               It's also noteworthy that linking the main event to the exception only works using _SYNC_ID
               and ORIGINAL_SYNC_ID (and not ID and ORIGINAL_ID, as one could assume). So, if you don't
               set _SYNC_ID in the main event and ORIGINAL_SYNC_ID in the exception, the exception will
               appear additionally (and not *instead* of the instance).
             */

            val recurrenceId = exception.recurrenceId
            if (recurrenceId == null) {
                Ical4Android.log.warning("Ignoring exception of event ${event.uid} without recurrenceId")
                continue
            }

            val exBuilder = CpoBuilder
                    .newInsert(Events.CONTENT_URI.asSyncAdapter(calendar.account))
                    .withEventId(Events.ORIGINAL_ID, idxEvent)

            buildEvent(exception, exBuilder)
            if (exBuilder.values[Events.ORIGINAL_SYNC_ID] == null && exBuilder.valueBackrefs[Events.ORIGINAL_SYNC_ID] == null)
                throw AssertionError("buildEvent(exception) must set ORIGINAL_SYNC_ID")

            var recurrenceDate = recurrenceId.date
            val dtStartDate = event.dtStart!!.date
            if (recurrenceDate is DateTime && dtStartDate !is DateTime) {
                // rewrite RECURRENCE-ID;VALUE=DATE-TIME to VALUE=DATE for all-day events
                val localDate = recurrenceDate.toLocalDate()
                recurrenceDate = Date(localDate.toIcal4jDate())

            } else if (recurrenceDate !is DateTime && dtStartDate is DateTime) {
                // rewrite RECURRENCE-ID;VALUE=DATE to VALUE=DATE-TIME for non-all-day-events
                val localDate = recurrenceDate.toLocalDate()
                // guess time and time zone from DTSTART
                val zonedTime = ZonedDateTime.of(
                        localDate,
                        dtStartDate.toLocalTime(),
                        dtStartDate.requireZoneId()
                )
                recurrenceDate = zonedTime.toIcal4jDateTime()
            }
            exBuilder   .withValue(Events.ORIGINAL_ALL_DAY, if (DateUtils.isDate(event.dtStart)) 1 else 0)
                        .withValue(Events.ORIGINAL_INSTANCE_TIME, recurrenceDate.time)

            val idxException = batch.nextBackrefIdx()
            batch.enqueue(exBuilder)

            // add exception reminders
            exception.alarms.forEach { insertReminder(batch, idxException, it) }

            // add exception attendees
            exception.attendees.forEach { insertAttendee(batch, idxException, it, organizer) }
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
        val existingId = requireNotNull(id)

        // There are cases where the event cannot be updated, but must be completely re-created.
        // Case 1: Events.STATUS shall be updated from a non-null value (like STATUS_CONFIRMED) to null.
        var rebuild = false
        if (event.status == null)
            calendar.provider.query(eventSyncURI(), arrayOf(Events.STATUS), null, null, null)?.use { cursor ->
                cursor.moveToNext()
                if (!cursor.isNull(0))      // Events.STATUS != null
                    rebuild = true
            }

        if (rebuild) {  // delete whole event and insert updated event
            delete()
            return add()

        } else {        // update event
            // remove associated rows which are added later again
            val batch = BatchOperation(calendar.provider)
            deleteExceptions(batch)
            batch   .enqueue(CpoBuilder
                            .newDelete(Reminders.CONTENT_URI.asSyncAdapter(calendar.account))
                            .withSelection("${Reminders.EVENT_ID}=?", arrayOf(existingId.toString())))
                    .enqueue(CpoBuilder
                            .newDelete(Attendees.CONTENT_URI.asSyncAdapter(calendar.account))
                            .withSelection("${Attendees.EVENT_ID}=?", arrayOf(existingId.toString())))
                    .enqueue(CpoBuilder
                            .newDelete(ExtendedProperties.CONTENT_URI.asSyncAdapter(calendar.account))
                            .withSelection(
                                    "${ExtendedProperties.EVENT_ID}=? AND ${ExtendedProperties.NAME} IN (?,?,?)",
                                    arrayOf(existingId.toString(), MIMETYPE_CATEGORIES, MIMETYPE_URL, UnknownProperty.CONTENT_ITEM_TYPE)
                            ))

            addOrUpdateRows(batch)
            batch.commit()

            return ContentUris.withAppendedId(Events.CONTENT_URI, existingId)
        }
    }

    /**
     * Deletes an existing event from the calendar storage.
     *
     * @return number of affected rows
     *
     * @throws RemoteException on calendar provider errors
     */
    fun delete(): Int {
        val batch = BatchOperation(calendar.provider)

        // remove exceptions of event, too (CalendarProvider doesn't do this)
        deleteExceptions(batch)

        // remove event and unset known id
        batch.enqueue(CpoBuilder.newDelete(eventSyncURI()))
        id = null

        return batch.commit()
    }

    protected fun deleteExceptions(batch: BatchOperation) {
        val existingId = requireNotNull(id)
        batch.enqueue(CpoBuilder
                .newDelete(Events.CONTENT_URI.asSyncAdapter(calendar.account))
                .withSelection("${Events.ORIGINAL_ID}=?", arrayOf(existingId.toString())))
    }


    /**
     * Builds an Android [Events] row for a given ical4android [Event].
     *
     * @param recurrence   event to be used as data source; *null*: use this AndroidEvent's main [event] as source
     * @param builder      data row builder to be used
     */
    @CallSuper
    protected open fun buildEvent(recurrence: Event?, builder: CpoBuilder) {
        val event = recurrence ?: requireNotNull(event)

        val dtStart = event.dtStart ?: throw InvalidCalendarException("Events must have DTSTART")
        val allDay = DateUtils.isDate(dtStart)

        // make sure that time zone is supported by Android
        AndroidTimeUtils.androidifyTimeZone(dtStart)

        val recurring = event.rRules.isNotEmpty() || event.rDates.isNotEmpty()

        /* [CalendarContract.Events SDK documentation]
           When inserting a new event the following fields must be included:
           - dtstart
           - dtend if the event is non-recurring
           - duration if the event is recurring
           - rrule or rdate if the event is recurring
           - eventTimezone
           - a calendar_id */

        builder .withValue(Events.CALENDAR_ID, calendar.id)
                .withValue(Events.DTSTART, dtStart.date.time)
                .withValue(Events.ALL_DAY, if (allDay) 1 else 0)
                .withValue(Events.EVENT_TIMEZONE, AndroidTimeUtils.storageTzId(dtStart))

        var dtEnd = event.dtEnd
        AndroidTimeUtils.androidifyTimeZone(dtEnd)

        var duration =
                if (dtEnd == null)
                    event.duration?.duration
                else
                    null
        if (allDay && duration is Duration)
            duration = Period.ofDays(duration.toDays().toInt())

        if (recurring) {
            // duration must be set
            if (duration == null) {
                if (dtEnd != null) {
                    // calculate duration from dtEnd
                    duration = if (allDay)
                        Period.between(dtStart.date.toLocalDate(), dtEnd.date.toLocalDate())
                    else
                        Duration.between(dtStart.date.toInstant(), dtEnd.date.toInstant())
                } else {
                    // no dtEnd and no duration
                    duration = if (allDay)
                        /* [RFC 5545 3.6.1 Event Component]
                           For cases where a "VEVENT" calendar component
                           specifies a "DTSTART" property with a DATE value type but no
                           "DTEND" nor "DURATION" property, the event's duration is taken to
                           be one day. */
                        Period.ofDays(1)
                    else
                        /* For cases where a "VEVENT" calendar component
                           specifies a "DTSTART" property with a DATE-TIME value type but no
                           "DTEND" property, the event ends on the same calendar date and
                           time of day specified by the "DTSTART" property. */

                        // Duration.ofSeconds(0) causes the calendar provider to crash
                        Period.ofDays(0)
                }
            }

            // iCalendar doesn't permit years and months, only PwWdDThHmMsS
            builder .withValue(Events.DURATION, duration?.toRfc5545Duration(dtStart.date.toInstant()))
                    .withValue(Events.DTEND, null)

            // add RRULEs
            if (event.rRules.isNotEmpty()) {
                builder.withValue(Events.RRULE, event.rRules
                    .joinToString(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR) { it.value })
            } else
                builder.withValue(Events.RRULE, null)

            if (event.rDates.isNotEmpty()) {
                for (rDate in event.rDates)
                    AndroidTimeUtils.androidifyTimeZone(rDate)

                // Calendar provider drops DTSTART instance when using RDATE [https://code.google.com/p/android/issues/detail?id=171292]
                val listWithDtStart = DateList()
                listWithDtStart.add(dtStart.date)
                event.rDates.addFirst(RDate(listWithDtStart))

                builder.withValue(Events.RDATE, AndroidTimeUtils.recurrenceSetsToAndroidString(event.rDates, allDay))
            } else
                builder.withValue(Events.RDATE, null)

            if (event.exRules.isNotEmpty())
                builder.withValue(Events.EXRULE, event.exRules.joinToString(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR) { it.value })
            else
                builder.withValue(Events.EXRULE, null)

            if (event.exDates.isNotEmpty()) {
                for (exDate in event.exDates)
                    AndroidTimeUtils.androidifyTimeZone(exDate)
                builder.withValue(Events.EXDATE, AndroidTimeUtils.recurrenceSetsToAndroidString(event.exDates, allDay))
            } else
                builder.withValue(Events.EXDATE, null)

        } else /* !recurring */ {
            // dtend must be set
            if (dtEnd == null) {
                if (duration != null) {
                    // calculate dtEnd from duration
                    if (allDay) {
                        val calcDtEnd = dtStart.date.toLocalDate() + duration
                        dtEnd = DtEnd(calcDtEnd.toIcal4jDate())
                    } else {
                        val zonedStartTime = (dtStart.date as DateTime).toZonedDateTime()
                        val calcEnd = zonedStartTime + duration
                        val calcDtEnd = DtEnd(calcEnd.toIcal4jDateTime())
                        calcDtEnd.timeZone = dtStart.timeZone
                        dtEnd = calcDtEnd
                    }
                } else {
                    // no dtEnd and no duration
                    dtEnd = if (allDay) {
                        /* [RFC 5545 3.6.1 Event Component]
                           For cases where a "VEVENT" calendar component
                           specifies a "DTSTART" property with a DATE value type but no
                           "DTEND" nor "DURATION" property, the event's duration is taken to
                           be one day. */
                        val calcDtEnd = dtStart.date.toLocalDate() + Period.ofDays(1)
                        DtEnd(calcDtEnd.toIcal4jDate())
                    } else
                        /* For cases where a "VEVENT" calendar component
                           specifies a "DTSTART" property with a DATE-TIME value type but no
                           "DTEND" property, the event ends on the same calendar date and
                           time of day specified by the "DTSTART" property. */
                        DtEnd(dtStart.value, dtStart.timeZone)
                }
            }

            AndroidTimeUtils.androidifyTimeZone(dtEnd)
            builder .withValue(Events.DTEND, dtEnd.date.time)
                    .withValue(Events.EVENT_END_TIMEZONE, AndroidTimeUtils.storageTzId(dtEnd))
                    .withValue(Events.DURATION, null)
                    .withValue(Events.RRULE, null)
                    .withValue(Events.RDATE, null)
                    .withValue(Events.EXRULE, null)
                    .withValue(Events.EXDATE, null)
        }

        builder.withValue(Events.TITLE, event.summary)
        builder.withValue(Events.EVENT_LOCATION, event.location)
        builder.withValue(Events.DESCRIPTION, event.description)
        builder.withValue(Events.EVENT_COLOR_KEY, event.color?.let { color ->
            val colorName = color.name
            // set event color (if it's available for this account)
            calendar.provider.query(Colors.CONTENT_URI.asSyncAdapter(calendar.account), arrayOf(Colors.COLOR_KEY),
                    "${Colors.COLOR_KEY}=? AND ${Colors.COLOR_TYPE}=${Colors.TYPE_EVENT}", arrayOf(colorName), null)?.use { cursor ->
                if (cursor.moveToNext())
                    return@let colorName
                else
                    Ical4Android.log.fine("Ignoring event color: $colorName (not available for this account)")
            }
            null
        })

        // scheduling
        val groupScheduled = event.attendees.isNotEmpty()
        if (groupScheduled) {
            builder .withValue(Events.HAS_ATTENDEE_DATA, 1)
                    .withValue(Events.ORGANIZER, event.organizer?.let { organizer ->
                        val uri = organizer.calAddress
                        val email = if (uri.scheme.equals("mailto", true))
                            uri.schemeSpecificPart
                        else
                            organizer.getParameter<Email>(Parameter.EMAIL)?.value
                        if (email != null)
                            return@let email
                        Ical4Android.log.warning("Ignoring ORGANIZER without email address (not supported by Android)")
                        null
                    } ?: calendar.ownerAccount)

        } else /* !groupScheduled */
            builder .withValue(Events.HAS_ATTENDEE_DATA, 0)
                    .withValue(Events.ORGANIZER, calendar.ownerAccount)

        // Attention: don't update event with STATUS != null to STATUS = null (causes calendar provider operation to fail)!
        // In this case, the whole event must be deleted and inserted again.
        if (/* insert, not an update */ id == null || /* update, but we're not updating to null */ event.status != null)
            builder.withValue(Events.STATUS, when (event.status) {
                null /* not possible by if statement */ -> null
                Status.VEVENT_CONFIRMED -> Events.STATUS_CONFIRMED
                Status.VEVENT_CANCELLED -> Events.STATUS_CANCELED
                else -> Events.STATUS_TENTATIVE
            })

        builder .withValue(Events.AVAILABILITY, if (event.opaque) Events.AVAILABILITY_BUSY else Events.AVAILABILITY_FREE)
                .withValue(Events.ACCESS_LEVEL, when (event.classification) {
                    null, Clazz.PUBLIC -> Events.ACCESS_PUBLIC
                    Clazz.CONFIDENTIAL -> Events.ACCESS_CONFIDENTIAL
                    else /* including Events.ACCESS_PRIVATE */ -> Events.ACCESS_PRIVATE
                })
    }

    protected open fun insertReminder(batch: BatchOperation, idxEvent: Int?, alarm: VAlarm) {
        val builder = CpoBuilder
                .newInsert(Reminders.CONTENT_URI.asSyncAdapter(calendar.account))
                .withEventId(Reminders.EVENT_ID, idxEvent)

        val method = when (alarm.action?.value?.uppercase(Locale.ROOT)) {
            Action.DISPLAY.value,
            Action.AUDIO.value -> Reminders.METHOD_ALERT

            // Note: The calendar provider doesn't support saving specific attendees for email reminders.
            Action.EMAIL.value -> Reminders.METHOD_EMAIL

            else               -> Reminders.METHOD_DEFAULT
        }

        val minutes = ICalendar.vAlarmToMin(alarm, event!!, false)?.second ?: Reminders.MINUTES_DEFAULT

        builder .withValue(Reminders.METHOD, method)
                .withValue(Reminders.MINUTES, minutes)
        batch.enqueue(builder)
    }

    protected open fun insertAttendee(batch: BatchOperation, idxEvent: Int?, attendee: Attendee, organizer: String) {
        val builder = CpoBuilder
                .newInsert(Attendees.CONTENT_URI.asSyncAdapter(calendar.account))
                .withEventId(Attendees.EVENT_ID, idxEvent)

        val member = attendee.calAddress
        if (member.scheme.equals("mailto", true))
            // attendee identified by email
            builder .withValue(Attendees.ATTENDEE_EMAIL, member.schemeSpecificPart)
        else {
            // attendee identified by other URI
            builder .withValue(Attendees.ATTENDEE_ID_NAMESPACE, member.scheme)
                    .withValue(Attendees.ATTENDEE_IDENTITY, member.schemeSpecificPart)

            attendee.getParameter<Email>(Parameter.EMAIL)?.let { email ->
                builder.withValue(Attendees.ATTENDEE_EMAIL, email.value)
            }
        }

        attendee.getParameter<Cn>(Parameter.CN)?.let { cn ->
            builder.withValue(Attendees.ATTENDEE_NAME, cn.value)
        }

        // type/relation mapping is complex and thus outsourced to AttendeeMappings
        AttendeeMappings.iCalendarToAndroid(attendee, builder, organizer)

        val status = when(attendee.getParameter(Parameter.PARTSTAT) as? PartStat) {
            PartStat.ACCEPTED     -> Attendees.ATTENDEE_STATUS_ACCEPTED
            PartStat.DECLINED     -> Attendees.ATTENDEE_STATUS_DECLINED
            PartStat.TENTATIVE    -> Attendees.ATTENDEE_STATUS_TENTATIVE
            PartStat.DELEGATED    -> Attendees.ATTENDEE_STATUS_NONE
            else /* default: PartStat.NEEDS_ACTION */ -> Attendees.ATTENDEE_STATUS_INVITED
        }
        builder.withValue(Attendees.ATTENDEE_STATUS, status)
        batch.enqueue(builder)
    }

    protected open fun insertExtendedProperty(batch: BatchOperation, idxEvent: Int?, mimeType: String, value: String) {
        val builder = CpoBuilder
                .newInsert(ExtendedProperties.CONTENT_URI.asSyncAdapter(calendar.account))
                .withEventId(ExtendedProperties.EVENT_ID, idxEvent)
                .withValue(ExtendedProperties.NAME, mimeType)
                .withValue(ExtendedProperties.VALUE, value)
        batch.enqueue(builder)
    }

    protected open fun insertCategories(batch: BatchOperation, idxEvent: Int?) {
        val rawCategories = event!!.categories      // concatenate, separate by backslash
                .joinToString(CATEGORIES_SEPARATOR.toString()) { category ->
                    // drop occurrences of CATEGORIES_SEPARATOR in category names
                    category.filter { it != CATEGORIES_SEPARATOR }
                }
        insertExtendedProperty(batch, idxEvent, MIMETYPE_CATEGORIES, rawCategories)
    }

    protected open fun insertUnknownProperty(batch: BatchOperation, idxEvent: Int?, property: Property) {
        if (property.value.length > UnknownProperty.MAX_UNKNOWN_PROPERTY_SIZE) {
            Ical4Android.log.warning("Ignoring unknown property with ${property.value.length} octets (too long)")
            return
        }

        insertExtendedProperty(batch, idxEvent, UnknownProperty.CONTENT_ITEM_TYPE, UnknownProperty.toJsonString(property))
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


    protected fun CpoBuilder.withEventId(column: String, idxEvent: Int?): CpoBuilder {
        if (idxEvent != null)
            withValueBackReference(column, idxEvent)
        else
            withValue(column, requireNotNull(id))
        return this
    }


    protected fun eventSyncURI(): Uri {
        val id = requireNotNull(id)
        return ContentUris.withAppendedId(Events.CONTENT_URI, id).asSyncAdapter(calendar.account)
    }

    override fun toString() = MiscUtils.reflectionToString(this)

}
