/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import at.bitfire.ical4android.ICalendar.Companion.CALENDAR_NAME
import at.bitfire.ical4android.util.DateUtils.isDateTime
import at.bitfire.ical4android.validation.EventValidator
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Categories
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Transp
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Url
import net.fortuna.ical4j.model.property.Version
import java.io.IOException
import java.io.OutputStream
import java.io.Reader
import java.net.URI
import java.util.LinkedList
import java.util.UUID
import java.util.logging.Logger

data class Event(
    override var uid: String? = null,
    override var sequence: Int? = null,

    /** list of Calendar User Agents which have edited the event since last sync */
    override var userAgents: LinkedList<String> = LinkedList(),

    // uid and sequence are inherited from iCalendar
    var recurrenceId: RecurrenceId? = null,

    var summary: String? = null,
    var location: String? = null,
    var url: URI? = null,
    var description: String? = null,
    var color: Css3Color? = null,

    var dtStart: DtStart? = null,
    var dtEnd: DtEnd? = null,

    var duration: Duration? = null,
    val rRules: LinkedList<RRule> = LinkedList(),
    val exRules: LinkedList<ExRule> = LinkedList(),
    val rDates: LinkedList<RDate> = LinkedList(),
    val exDates: LinkedList<ExDate> = LinkedList(),

    val exceptions: LinkedList<Event> = LinkedList(),

    var classification: Clazz? = null,
    var status: Status? = null,

    var opaque: Boolean = true,

    var organizer: Organizer? = null,
    val attendees: LinkedList<Attendee> = LinkedList(),

    val alarms: LinkedList<VAlarm> = LinkedList(),

    var lastModified: LastModified? = null,

    val categories: LinkedList<String> = LinkedList(),
    val unknownProperties: LinkedList<Property> = LinkedList()
) : ICalendar() {

    companion object {

        private val logger
            get() = Logger.getLogger(Event::class.java.name)

        /**
         * Parses an iCalendar resource, applies [at.bitfire.ical4android.validation.ICalPreprocessor]
         * and [EventValidator] to increase compatibility and extracts the VEVENTs.
         *
         * @param reader where the iCalendar is taken from
         * @param properties Known iCalendar properties (like [CALENDAR_NAME]) will be put into this map. Key: property name; value: property value
         *
         * @return array of filled [Event] data objects (may have size 0)
         *
         * @throws IOException on I/O errors
         * @throws ParserException when the iCalendar can't be parsed
         */
        fun eventsFromReader(
            reader: Reader,
            properties: MutableMap<String, String>? = null
        ): List<Event> {
            val ical = fromReader(reader, properties)

            // process VEVENTs
            val vEvents = ical.getComponents<VEvent>(Component.VEVENT)

            // make sure every event has an UID
            for (vEvent in vEvents)
                if (vEvent.uid == null) {
                    val uid = Uid(UUID.randomUUID().toString())
                    logger.warning("Found VEVENT without UID, using a random one: ${uid.value}")
                    vEvent.properties += uid
                }

            logger.fine("Assigning exceptions to main events")
            val mainEvents = mutableMapOf<String /* UID */, VEvent>()
            val exceptions = mutableMapOf<String /* UID */, MutableMap<String /* RECURRENCE-ID */, VEvent>>()
            for (vEvent in vEvents) {
                val uid = vEvent.uid.value
                val sequence = vEvent.sequence?.sequenceNo ?: 0

                if (vEvent.recurrenceId == null) {
                    // main event (no RECURRENCE-ID)

                    // If there are multiple entries, compare SEQUENCE and use the one with higher SEQUENCE.
                    // If the SEQUENCE is identical, use latest version.
                    val event = mainEvents[uid]
                    if (event == null || (event.sequence != null && sequence >= event.sequence.sequenceNo))
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
                    val recurrenceID = vEvent.recurrenceId.value
                    val event = ex[recurrenceID]
                    if (event == null || (event.sequence != null && sequence >= event.sequence.sequenceNo))
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
                logger.info("UID $uid doesn't have a main event but only exceptions: $onlyExceptions")

                // create a fake main event from the first exception
                val fakeEvent = fromVEvent(onlyExceptions.values.first())
                fakeEvent.exceptions.addAll(onlyExceptions.values.map { fromVEvent(it) })

                events += fakeEvent
            }

            // Try to repair all events after reading the whole iCalendar
            for (event in events)
                EventValidator.repair(event)

            return events
        }

        private fun fromVEvent(event: VEvent): Event {
            val e = Event()

            // sequence must only be null for locally created, not-yet-synchronized events
            e.sequence = 0

            // process properties
            for (prop in event.properties)
                when (prop) {
                    is Uid -> e.uid = prop.value
                    is RecurrenceId -> e.recurrenceId = prop
                    is Sequence -> e.sequence = prop.sequenceNo
                    is Summary -> e.summary = prop.value
                    is Location -> e.location = prop.value
                    is Url -> e.url = prop.uri
                    is Description -> e.description = prop.value
                    is Categories ->
                        for (category in prop.categories)
                            e.categories += category

                    is Color -> e.color = Css3Color.fromString(prop.value)
                    is DtStart -> e.dtStart = prop
                    is DtEnd -> e.dtEnd = prop
                    is Duration -> e.duration = prop
                    is RRule -> e.rRules += prop
                    is RDate -> e.rDates += prop
                    is ExRule -> e.exRules += prop
                    is ExDate -> e.exDates += prop
                    is Clazz -> e.classification = prop
                    is Status -> e.status = prop
                    is Transp -> e.opaque = prop == Transp.OPAQUE
                    is Organizer -> e.organizer = prop
                    is Attendee -> e.attendees += prop
                    is LastModified -> e.lastModified = prop
                    is ProdId, is DtStamp -> { /* don't save these as unknown properties */ }

                    else -> e.unknownProperties += prop
                }

            e.alarms.addAll(event.alarms)

            return e
        }
    }


    fun write(os: OutputStream) {
        val ical = Calendar()
        ical.properties += Version.VERSION_2_0
        ical.properties += prodId()

        val dtStart = dtStart ?: throw InvalidCalendarException("Won't generate event without start time")

        EventValidator.repair(this)     // repair this event before creating the VEVENT

        // "main event" (without exceptions)
        val components = ical.components
        val mainEvent = toVEvent()
        components += mainEvent

        // remember used time zones
        val usedTimeZones = mutableSetOf<TimeZone>()
        dtStart.timeZone?.let(usedTimeZones::add)
        dtEnd?.timeZone?.let(usedTimeZones::add)

        // recurrence exceptions
        for (exception in exceptions) {
            // exceptions must always have the same UID as the main event
            exception.uid = uid

            val recurrenceId = exception.recurrenceId
            if (recurrenceId == null) {
                logger.warning("Ignoring exception without recurrenceId")
                continue
            }

            /* Exceptions must always have the same value type as DTSTART [RFC 5545 3.8.4.4].
               If this is not the case, we don't add the exception to the event because we're
               strict in what we send (and servers may reject such a case).
             */
            if (isDateTime(recurrenceId) != isDateTime(dtStart)) {
                logger.warning("Ignoring exception $recurrenceId with other date type than dtStart: $dtStart")
                continue
            }

            // for simplicity and compatibility, rewrite date-time exceptions to the same time zone as DTSTART
            if (isDateTime(recurrenceId) && recurrenceId.timeZone != dtStart.timeZone) {
                logger.fine("Changing timezone of $recurrenceId to same time zone as dtStart: $dtStart")
                recurrenceId.timeZone = dtStart.timeZone
            }

            // create and add VEVENT for exception
            val vException = exception.toVEvent()
            components += vException

            // remember used time zones
            exception.dtStart?.timeZone?.let(usedTimeZones::add)
            exception.dtEnd?.timeZone?.let(usedTimeZones::add)
        }

        // determine first dtStart (there may be exceptions with an earlier DTSTART that the main event)
        val dtStarts = mutableListOf(dtStart.date)
        dtStarts.addAll(exceptions.mapNotNull { it.dtStart?.date })
        val earliest = dtStarts.minOrNull()
        // add VTIMEZONE components
        for (tz in usedTimeZones)
            ical.components += minifyVTimeZone(tz.vTimeZone, earliest)

        softValidate(ical)
        CalendarOutputter(false).output(ical, os)
    }

    /**
     * Generates a VEvent representation of this event.
     *
     * @return generated VEvent
     */
    private fun toVEvent(): VEvent {
        val event = VEvent(/* generates DTSTAMP */)
        val props = event.properties
        props += Uid(uid)

        recurrenceId?.let { props += it }
        sequence?.let {
            if (it != 0)
                props += Sequence(it)
        }

        summary?.let { props += Summary(it) }
        location?.let { props += Location(it) }
        url?.let { props += Url(it) }
        description?.let { props += Description(it) }
        color?.let { props += Color(null, it.name) }

        dtStart?.let { props += it }
        dtEnd?.let { props += it }
        duration?.let { props += it }

        props.addAll(rRules)
        props.addAll(rDates)
        props.addAll(exRules)
        props.addAll(exDates)

        classification?.let { props += it }
        status?.let { props += it }
        if (!opaque)
            props += Transp.TRANSPARENT

        organizer?.let { props += it }
        props.addAll(attendees)

        if (categories.isNotEmpty())
            props += Categories(TextList(categories.toTypedArray()))
        props.addAll(unknownProperties)

        lastModified?.let { props += it }

        event.components.addAll(alarms)

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
                    organizer.getParameter<Email>(Parameter.EMAIL)?.value
            }
            return email
        }

}
