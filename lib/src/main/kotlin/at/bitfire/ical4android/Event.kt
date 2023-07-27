/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import at.bitfire.ical4android.ICalendar.Companion.CALENDAR_NAME
import at.bitfire.ical4android.util.DateUtils.getZoneId
import at.bitfire.ical4android.util.DateUtils.isDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.requireTimeZone
import at.bitfire.ical4android.validation.EventValidator
import at.bitfire.ical4android.validation.ICalPreprocessor
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.property.*
import java.io.IOException
import java.io.OutputStream
import java.io.Reader
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoField
import java.time.temporal.Temporal
import java.util.*
import kotlin.jvm.optionals.getOrNull
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VTimeZone

class Event: ICalendar() {

    // uid and sequence are inherited from iCalendar
    var recurrenceId: RecurrenceId<Temporal>? = null

    var summary: String? = null
    var location: String? = null
    var url: URI? = null
    var description: String? = null
    var color: Css3Color? = null

    var dtStart: DtStart<Temporal>? = null
    var dtEnd: DtEnd<Temporal>? = null

    var duration: Duration? = null
    val rRules = LinkedList<RRule<Temporal>>()
    val exRules = LinkedList<ExRule>()
    val rDates = LinkedList<RDate<Temporal>>()
    val exDates = LinkedList<ExDate<Temporal>>()

    val exceptions = LinkedList<Event>()

    var classification: Clazz? = null
    var status: Status? = null

    var opaque = true

    var organizer: Organizer? = null
    val attendees = LinkedList<Attendee>()

    val alarms = LinkedList<VAlarm>()

    var lastModified: LastModified? = null

    val categories = LinkedList<String>()
    val unknownProperties = LinkedList<Property>()

    companion object {
        /**
         * Parses an iCalendar resource, applies [ICalPreprocessor] to increase compatibility
         * and extracts the VEVENTs.
         *
         * @param reader where the iCalendar is taken from
         * @param properties Known iCalendar properties (like [CALENDAR_NAME]) will be put into this map.
         * Key: property name; value: property value
         *
         * @return array of filled [Event] data objects (may have size 0)
         *
         * @throws ParserException when the iCalendar can't be parsed
         * @throws IllegalArgumentException when the iCalendar resource contains an invalid value
         * @throws IOException on I/O errors
         * @throws InvalidCalendarException on parsing exceptions
         */
        @UsesThreadContextClassLoader
        fun eventsFromReader(reader: Reader, properties: MutableMap<String, String>? = null): List<Event> {
            val ical = fromReader(reader, properties)

            // process VEVENTs
            val vEvents = ical.getComponents<VEvent>(Component.VEVENT)
                // make sure every event has an UID
                .map { vEvent ->
                    if (vEvent.uid.isEmpty) {
                        val uid = Uid(UUID.randomUUID().toString())
                        Ical4Android.log.warning("Found VEVENT without UID, using a random one: ${uid.value}")
                        vEvent.withProperty(uid) as VEvent
                    } else {
                        vEvent
                    }
                }

            Ical4Android.log.fine("Assigning exceptions to main events")
            val mainEvents = mutableMapOf<String /* UID */,VEvent>()
            val exceptions = mutableMapOf<String /* UID */,MutableMap<String /* RECURRENCE-ID */,VEvent>>()
            for (vEvent in vEvents) {
                val uid = vEvent.uid.get().value
                val sequence = vEvent.sequence?.get()?.sequenceNo ?: 0

                if (vEvent.getRecurrenceId<Temporal>() == null) {
                    // main event (no RECURRENCE-ID)

                    // If there are multiple entries, compare SEQUENCE and use the one with higher SEQUENCE.
                    // If the SEQUENCE is identical, use latest version.
                    val event = mainEvents[uid]
                    if (event == null ||
                        (event.sequence?.get() != null && sequence >= event.sequence!!.get().sequenceNo))
                        mainEvents[uid] = vEvent

                } else {
                    // exception (RECURRENCE-ID)
                    var ex = exceptions[uid]
                    // first index level: UID
                    if (ex == null) {
                        ex = mutableMapOf()
                        exceptions[uid] = ex
                    }
                    // second index level: RECURRENCE-ID
                    val recurrenceID = vEvent.getRecurrenceId<Temporal>().get().value
                    val event = ex[recurrenceID]
                    if (event == null ||
                        (event.sequence?.get() != null && sequence >= event.sequence!!.get().sequenceNo))
                        ex[recurrenceID] = vEvent
                }
            }

            /* There may be UIDs which have only RECURRENCE-ID entries and not a main entry (for instance, a recurring
            event with an exception where the current user has been invited only to this exception. In this case,
            the UID will not appear in mainEvents but only in exceptions. */

            val events = mutableListOf<Event>()
            for ((uid, vEvent) in mainEvents) {
                val event = fromVEvent(vEvent)

                // assign exceptions to main event and then remove them from exceptions array
                exceptions.remove(uid)?.let { eventExceptions ->
                    event.exceptions.addAll(eventExceptions.values.map { fromVEvent(it) })
                }

                // make sure that exceptions have at least a SUMMARY
                event.exceptions.forEach { it.summary = it.summary ?: event.summary }

                events += event
            }

            for ((uid, onlyExceptions) in exceptions) {
                Ical4Android.log.info("UID $uid doesn't have a main event but only exceptions: $onlyExceptions")

                // create a fake main event from the first exception
                val fakeEvent = fromVEvent(onlyExceptions.values.first())
                fakeEvent.exceptions.addAll(onlyExceptions.values.map { fromVEvent(it) })

                events += fakeEvent
            }

            return events
        }

        private fun fromVEvent(event: VEvent): Event {
            val e = Event()

            // sequence must only be null for locally created, not-yet-synchronized events
            e.sequence = 0

            // process properties
            // We can safely cast to erased Temporal since it's the base of all properties in Event
            @Suppress("UNCHECKED_CAST")
            for (prop in event.getProperties<Property>())
                when (prop) {
                    is Uid -> e.uid = prop.value
                    is RecurrenceId<*> -> e.recurrenceId = prop as RecurrenceId<Temporal>
                    is Sequence -> e.sequence = prop.sequenceNo
                    is Summary -> e.summary = prop.value
                    is Location -> e.location = prop.value
                    is Url -> e.url = prop.uri
                    is Description -> e.description = prop.value
                    is Categories ->
                        for (category in prop.categories)
                            e.categories += category
                    is Color -> e.color = Css3Color.fromString(prop.value)
                    is DtStart<*> -> e.dtStart = prop as DtStart<Temporal>
                    is DtEnd<*> -> e.dtEnd = prop as DtEnd<Temporal>
                    is Duration -> e.duration = prop
                    is RRule<*> -> e.rRules += prop as RRule<Temporal>
                    is RDate<*> -> e.rDates += prop as RDate<Temporal>
                    is ExRule -> e.exRules += prop
                    is ExDate<*> -> e.exDates += prop as ExDate<Temporal>
                    is Clazz -> e.classification = prop
                    is Status -> e.status = prop
                    is Transp -> e.opaque = prop.value == Transp.VALUE_OPAQUE
                    is Organizer -> e.organizer = prop
                    is Attendee -> e.attendees += prop
                    is LastModified -> e.lastModified = prop
                    is ProdId, is DtStamp -> { /* don't save these as unknown properties */ }
                    else -> e.unknownProperties += prop
                }

            e.alarms.addAll(event.alarms)

            // validate and repair
            EventValidator(e).repair()

            return e
        }
    }


    @UsesThreadContextClassLoader
    fun write(os: OutputStream) {
        Ical4Android.checkThreadContextClassLoader()

        var ical: Calendar = Calendar()
            .withProperty(Version().apply { value = Version.VALUE_2_0 })
            .withProdId(prodId().value) as Calendar

        val dtStart = dtStart ?: throw InvalidCalendarException("Won't generate event without start time")

        EventValidator(this).repair() // validate and repair this event before creating VEVENT

        // "main event" (without exceptions)
        val components = ical.getComponents<CalendarComponent>()
        val mainEvent = toVEvent()
        components += mainEvent

        // remember used time zones
        val usedTimeZones = mutableSetOf<VTimeZone>()
        (dtStart.date as? ZonedDateTime?)?.zone
            ?.let { VTimeZone().withProperty(TzId(it.id)) as VTimeZone }
            ?.let(usedTimeZones::add)
        (dtEnd?.date as? ZonedDateTime?)?.zone
            ?.let { VTimeZone().withProperty(TzId(it.id)) as VTimeZone }
            ?.let(usedTimeZones::add)

        // recurrence exceptions
        for (exception in exceptions) {
            // exceptions must always have the same UID as the main event
            exception.uid = uid

            val recurrenceId = exception.recurrenceId
            if (recurrenceId == null) {
                Ical4Android.log.warning("Ignoring exception without recurrenceId")
                continue
            }

            /* Exceptions must always have the same value type as DTSTART [RFC 5545 3.8.4.4].
               If this is not the case, we don't add the exception to the event because we're
               strict in what we send (and servers may reject such a case).
             */
            if (isDateTime(recurrenceId) != isDateTime(dtStart)) {
                Ical4Android.log.warning("Ignoring exception $recurrenceId with other date type than dtStart: $dtStart")
                continue
            }

            // for simplicity and compatibility, rewrite date-time exceptions to the same time zone as DTSTART
            if (isDateTime(recurrenceId) && getZoneId(recurrenceId) != getZoneId(dtStart)) {
                Ical4Android.log.fine("Changing timezone of $recurrenceId to same time zone as dtStart: $dtStart")
                val recurrenceIdDate = ZonedDateTime.from(recurrenceId.date).withZoneSameInstant(getZoneId(dtStart))
                recurrenceId.date = recurrenceIdDate
            }

            // create and add VEVENT for exception
            val vException = exception.toVEvent()
            components += vException

            // remember used time zones
            (exception.dtStart?.date as? ZonedDateTime?)?.zone
                ?.let { VTimeZone().withProperty(TzId(it.id)) as VTimeZone }
                ?.let(usedTimeZones::add)
            (exception.dtEnd?.date as? ZonedDateTime?)?.zone
                ?.let { VTimeZone().withProperty(TzId(it.id)) as VTimeZone }
                ?.let(usedTimeZones::add)
        }

        // determine first dtStart (there may be exceptions with an earlier DTSTART that the main event)
        val dtStarts = mutableListOf(dtStart.date)
        dtStarts.addAll(exceptions.mapNotNull { it.dtStart?.date })
        val earliest = dtStarts
            .minOfOrNull { Instant.from(it).toEpochMilli() }
            ?.let { Instant.ofEpochMilli(it) }
        // add VTIMEZONE components
        for (tz in usedTimeZones)
            ical = ical.withComponent(minifyVTimeZone(tz, earliest)) as Calendar

        softValidate(ical)
        CalendarOutputter(false).output(ical, os)
    }

    /**
     * Generates a VEvent representation of this event.
     *
     * @return generated VEvent
     */
    private fun toVEvent(): VEvent {
        var event: VEvent = VEvent(/* generates DTSTAMP */)
            .withProperty(Uid(uid)) as VEvent

        recurrenceId?.let { event = event.withProperty(it) as VEvent }
        sequence?.let {
            if (it != 0)  event = event.withProperty(Sequence(it)) as VEvent
        }

        summary?.let {  event = event.withProperty(Summary(it)) as VEvent }
        location?.let {  event = event.withProperty(Location(it)) as VEvent }
        url?.let {  event = event.withProperty(Url(it)) as VEvent }
        description?.let {  event = event.withProperty(Description(it)) as VEvent }
        color?.let {  event = event.withProperty(Color(null, it.name)) as VEvent }

        dtStart?.let { event = event.withProperty(it) as VEvent }
        dtEnd?.let { event = event.withProperty(it) as VEvent }
        duration?.let { event = event.withProperty(it) as VEvent }

        rRules.forEach { event = event.withProperty(it) as VEvent }
        rDates.forEach { event = event.withProperty(it) as VEvent }
        exRules.forEach { event = event.withProperty(it) as VEvent }
        exDates.forEach { event = event.withProperty(it) as VEvent }

        classification?.let { event = event.withProperty(it) as VEvent }
        status?.let { event = event.withProperty(it) as VEvent }
        if (!opaque) event = event.withProperty(Transp(Transp.VALUE_TRANSPARENT)) as VEvent

        organizer?.let { event = event.withProperty(it) as VEvent }
        attendees.forEach { event = event.withProperty(it) as VEvent }

        if (categories.isNotEmpty())
            event = event.withProperty(Categories(TextList(categories))) as VEvent
        unknownProperties.forEach { event = event.withProperty(it) as VEvent }

        lastModified?.let { event = event.withProperty(it) as VEvent }

        event.alarms.addAll(alarms)

        return event
    }


    val organizerEmail: String?
    get() {
        var email: String? = null
        organizer?.let { organizer ->
            val uri = organizer.calAddress
            email = if (uri.scheme.equals("mailto", true))
                uri.schemeSpecificPart
            else
                organizer.getParameter<Email>(Parameter.EMAIL)?.getOrNull()?.value
        }
        return email
    }

}
