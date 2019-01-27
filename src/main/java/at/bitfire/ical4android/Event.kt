/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.*
import java.io.IOException
import java.io.OutputStream
import java.io.Reader
import java.util.*

class Event: ICalendar() {

    // uid and sequence are inherited from iCalendar
    var recurrenceId: RecurrenceId? = null

    var summary: String? = null
    var location: String? = null
    var description: String? = null
    var color: Css3Color? = null

    var dtStart: DtStart? = null
    var dtEnd: DtEnd? = null

    var duration: Duration? = null
    var rRule: RRule? = null
    var exRule: ExRule? = null
    val rDates = LinkedList<RDate>()
    val exDates = LinkedList<ExDate>()

    val exceptions = LinkedList<Event>()

    var classification: Clazz? = null
    var status: Status? = null

    var opaque = true

    var organizer: Organizer? = null
    val attendees = LinkedList<Attendee>()

    val alarms = LinkedList<VAlarm>()

    var lastModified: LastModified? = null

    val unknownProperties = LinkedList<Property>()

    companion object {
        const val CALENDAR_NAME = "X-WR-CALNAME"

        /**
         * Parses an InputStream that contains iCalendar VEVENTs.
         *
         * @param reader        reader for the input stream containing the VEVENTs (pay attention to the charset)
         * @param properties    map of properties, will be filled with CALENDAR_* values, if applicable (may be null)
         * @return              array of filled Event data objects (may have size 0) – doesn't return null
         * @throws IOException on I/O errors
         * @throws InvalidCalendarException on parsing exceptions
         */
        fun fromReader(reader: Reader, properties: MutableMap<String, String>? = null): List<Event> {
            Constants.log.fine("Parsing iCalendar stream")

            // parse stream
            val ical: Calendar
            try {
                ical = calendarBuilder().build(reader)
            } catch(e: ParserException) {
                throw InvalidCalendarException("Couldn't parse iCalendar object", e)
            } catch(e: IllegalArgumentException) {
                throw InvalidCalendarException("iCalendar object contains invalid value", e)
            }

            // fill calendar properties
            properties?.let {
                ical.getProperty(CALENDAR_NAME)?.let { calName ->
                    properties[CALENDAR_NAME] = calName.value
                }
            }

            // process VEVENTs
            val vEvents = ical.getComponents<VEvent>(Component.VEVENT)

            // make sure every event has an UID
            for (vEvent in vEvents)
                if (vEvent.uid == null) {
                    val uid = Uid(UUID.randomUUID().toString())
                    Constants.log.warning("Found VEVENT without UID, using a random one: ${uid.value}")
                    vEvent.properties += uid
                }

            Constants.log.fine("Assigning exceptions to master events")
            val masterEvents = mutableMapOf<String /* UID */,VEvent>()
            val exceptions = mutableMapOf<String /* UID */,MutableMap<String /* RECURRENCE-ID */,VEvent>>()

            for (vEvent in vEvents) {
                val uid = vEvent.uid.value
                val sequence = vEvent.sequence?.sequenceNo ?: 0

                if (vEvent.recurrenceId == null) {
                    // master event (no RECURRENCE-ID)

                    // If there are multiple entries, compare SEQUENCE and use the one with higher SEQUENCE.
                    // If the SEQUENCE is identical, use latest version.
                    val event = masterEvents[uid]
                    if (event == null || (event.sequence != null && sequence >= event.sequence.sequenceNo))
                        masterEvents[uid] = vEvent

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

            val events = mutableListOf<Event>()
            for ((uid, vEvent) in masterEvents) {
                val event = fromVEvent(vEvent)
                exceptions[uid]?.let { eventExceptions ->
                    event.exceptions.addAll(eventExceptions.map { (_,it) -> fromVEvent(it) })
                }

                // make sure that exceptions have at least a SUMMARY
                event.exceptions.forEach { it.summary = it.summary ?: event.summary }

                events += event
            }

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
                    is Description -> e.description = prop.value
                    is Color -> e.color = Css3Color.fromString(prop.value)
                    is DtStart -> e.dtStart = prop
                    is DtEnd -> e.dtEnd = prop
                    is Duration -> e.duration = prop
                    is RRule -> e.rRule = prop
                    is RDate -> e.rDates += prop
                    is ExRule -> e.exRule = prop
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

            // calculate DtEnd from Duration
            if (e.dtEnd == null && e.duration != null)
                e.dtEnd = event.getEndDate(true)

            e.alarms.addAll(event.alarms)

            // validation
            if (e.dtStart == null)
                throw InvalidCalendarException("Event without start time")

            return e
        }
    }


    fun write(os: OutputStream) {
        val ical = Calendar()
        ical.properties += Version.VERSION_2_0
        ical.properties += prodId

        // "master event" (without exceptions)
        val components = ical.components
        val master = toVEvent(Uid(uid))
        components += master

        // remember used time zones
        val usedTimeZones = mutableSetOf<TimeZone>()
        dtStart?.timeZone?.let(usedTimeZones::add)
        dtEnd?.timeZone?.let(usedTimeZones::add)

        // recurrence exceptions
        for (exception in exceptions) {
            // create VEVENT for exception
            val vException = exception.toVEvent(master.uid)
            components += vException

            // remember used time zones
            exception.dtStart?.timeZone?.let(usedTimeZones::add)
            exception.dtEnd?.timeZone?.let(usedTimeZones::add)
        }

        // add VTIMEZONE components
        usedTimeZones.forEach {
            val tz = it.vTimeZone
            // TODO dtStart?.let { minifyVTimeZone(tz, it.date) }
            ical.components += tz
        }

        CalendarOutputter(false).output(ical, os)
    }

    private fun toVEvent(uid: Uid): VEvent {
        val event = VEvent()
        val props = event.properties

        props += uid
        recurrenceId?.let { props += it }
        sequence?.let { if (it != 0) props += Sequence(it) }

        summary?.let { props += Summary(it) }
        location?.let { props += Location(it) }
        description?.let { props += Description(it) }
        color?.let { props += Color(null, it.name) }

        props += dtStart
        dtEnd?.let { props += it }
        duration?.let { props += it }

        rRule?.let { props += it }
        props.addAll(rDates)
        exRule?.let { props += it }
        props.addAll(exDates)

        classification?.let { props += it }
        status?.let { props += it }
        if (!opaque)
            props += Transp.TRANSPARENT

        organizer?.let { props += it }
        props.addAll(attendees)

        props.addAll(unknownProperties)

        lastModified?.let { props += it }

        event.alarms.addAll(alarms)
        return event
    }


    // helpers

    fun isAllDay() = !isDateTime(dtStart)

}
