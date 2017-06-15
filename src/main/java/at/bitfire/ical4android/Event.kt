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
import net.fortuna.ical4j.validate.ValidationException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*
import java.util.logging.Level

//@ToString(of={"uid","dtStart","summary"})
class Event: iCalendar() {

    // uid and sequence are inherited from iCalendar
    var recurrenceId: RecurrenceId? = null

    var summary: String? = null
    var location: String? = null
    var description: String? = null

    var dtStart: DtStart? = null
    var dtEnd: DtEnd? = null

    var duration: Duration? = null
    var rRule: RRule? = null
    var exRule: ExRule? = null
    val rDates = LinkedList<RDate>()
    val exDates = LinkedList<ExDate>()

    val exceptions = LinkedList<Event>()

    var forPublic: Boolean? = null
    var status: Status? = null

    var opaque = true

    var organizer: Organizer? = null
    val attendees = LinkedList<Attendee>()

    val alarms = LinkedList<VAlarm>()

    var lastModified: LastModified? = null

    val unknownProperties = LinkedList<Property>()

    companion object {
        @JvmField
        val CALENDAR_NAME = "X-WR-CALNAME"

        /**
         * Parses an InputStream that contains iCalendar VEVENTs.
         *
         * @param stream        input stream containing the VEVENTs
         * @param charset       charset of the input stream or null (will assume UTF-8)
         * @param properties    map of properties, will be filled with CALENDAR_* values, if applicable (may be null)
         * @return              array of filled Event data objects (may have size 0) – doesn't return null
         * @throws IOException on I/O errors
         * @throws InvalidCalendarException on parsing exceptions
         */
        @JvmStatic
        @JvmOverloads
        @Throws(IOException::class, InvalidCalendarException::class)
        fun fromStream(stream: InputStream, charset: Charset?, properties: MutableMap<String, String>? = null): Array<Event> {
            Constants.log.fine("Parsing iCalendar stream")

            // parse stream
            var ical = Calendar()
            try {
                if (charset != null)
                    InputStreamReader(stream, charset).use { ical = calendarBuilder.build(it) }
                else
                    ical = calendarBuilder.build(stream)
            } catch (e: ParserException) {
                throw InvalidCalendarException("Couldn't parse iCalendar resource", e)
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
                    vEvent.properties.add(uid)
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
                        masterEvents.put(uid, vEvent)

                } else {
                    // exception (RECURRENCE-ID)
                    var ex = exceptions[uid]
                    // first index level: UID
                    if (ex == null) {
                        ex = mutableMapOf<String,VEvent>()
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
                events.add(event)
            }

            return events.toTypedArray()
        }


        @Throws(InvalidCalendarException::class)
        fun fromVEvent(event: VEvent): Event {
            val e = Event()

            // sequence must only be null for locally created, not-yet-synchronized events
            e.sequence = 0

            // process properties
            for (prop in event.properties)
                when (prop) {
                    is Uid -> e.uid = prop.value
                    is RecurrenceId -> e.recurrenceId = prop
                    is Sequence -> e.sequence = prop.sequenceNo
                    is DtStart -> e.dtStart = prop
                    is DtEnd -> e.dtEnd = prop
                    is Duration -> e.duration = prop
                    is RRule -> e.rRule = prop
                    is RDate -> e.rDates.add(prop)
                    is ExRule -> e.exRule = prop
                    is ExDate -> e.exDates.add(prop)
                    is Summary -> e.summary = prop.value
                    is Location -> e.location = prop.value
                    is Description -> e.description = prop.value
                    is Status -> e.status = prop
                    is Transp -> e.opaque = prop == Transp.OPAQUE
                    is Clazz -> e.forPublic = prop == Clazz.PUBLIC
                    is Organizer -> e.organizer = prop
                    is Attendee -> e.attendees.add(prop)
                    is LastModified -> e.lastModified = prop
                    is ProdId, is DtStamp -> { /* don't save those as unknown properties */ }
                    else -> e.unknownProperties.add(prop)
                }

            // calculate DtEnd from Duration
            if (e.dtEnd == null && e.duration != null)
                e.dtEnd = event.getEndDate(true)

            e.alarms.addAll(event.alarms)

            // validation
            if (e.dtStart == null)
                throw InvalidCalendarException("Event without start time")

            validateTimeZone(e.dtStart)
            validateTimeZone(e.dtEnd)

            return e
        }
    }
    

    @Throws(IOException::class)
    fun write(os: OutputStream) {
        val ical = Calendar()
        ical.properties.add(Version.VERSION_2_0)
        ical.properties.add(prodId)

        // "master event" (without exceptions)
        val components = ical.components
        val master = toVEvent(Uid(uid))
        components.add(master)

        // remember used time zones
        val usedTimeZones = mutableSetOf<TimeZone>()
        dtStart?.timeZone?.let(usedTimeZones::add)
        dtEnd?.timeZone?.let(usedTimeZones::add)

        // recurrence exceptions
        for (exception in exceptions) {
            // create VEVENT for exception
            val vException = exception.toVEvent(master.uid)

            components.add(vException)

            // remember used time zones
            exception.dtStart?.timeZone?.let(usedTimeZones::add)
            exception.dtEnd?.timeZone?.let(usedTimeZones::add)
        }

        // add VTIMEZONE components
        usedTimeZones.forEach { ical.components.add(it.vTimeZone) }

        try {
            ical.validate()
        } catch (e: ValidationException) {
            Constants.log.log(Level.INFO, "VEVENT validation result", e);
        }

        CalendarOutputter(false).output(ical, os)
    }

    private fun toVEvent(uid: Uid): VEvent {
        val event = VEvent()
        val props = event.properties

        props.add(uid)
        recurrenceId?.let(props::add)
        sequence?.let { if (it != 0) props.add(Sequence(it)) }

        props.add(dtStart)
        dtEnd?.let(props::add)
        duration?.let(props::add)

        rRule?.let(props::add)
        props.addAll(rDates)
        exRule?.let(props::add)
        props.addAll(exDates)

        summary?.let { if (it.isNotEmpty()) props.add(Summary(it)) }
        location?.let { if (it.isNotEmpty()) props.add(Location(it)) }
        description?.let { if (it.isNotEmpty()) props.add(Description(it)) }

        status?.let(props::add)
        if (!opaque)
            props.add(Transp.TRANSPARENT)

        organizer?.let(props::add)
        props.addAll(attendees)

        forPublic?.let { props.add(if (it) Clazz.PUBLIC else Clazz.PRIVATE) }

        lastModified?.let(props::add)
        props.addAll(unknownProperties)

        event.alarms.addAll(alarms)
        return event
    }

    // TODO toString


    // time helpers

    fun isAllDay() = !isDateTime(dtStart)

    fun getDtStartTzID() = getTzId(dtStart)
    fun getDtEndTzID() = getTzId(dtEnd)

}