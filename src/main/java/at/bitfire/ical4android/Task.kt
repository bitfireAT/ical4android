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
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.*
import net.fortuna.ical4j.validate.ValidationException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.Charset
import java.util.*
import java.util.logging.Level

// TODO @ToString(of={"uid","summary"})
class Task: iCalendar() {

	var createdAt: Long? = null
    var lastModified: Long? = null

    var summary: String? = null
    var location: String? = null
    var description: String? = null
    var url: String? = null
    var organizer: Organizer? = null
    var geoPosition: Geo? = null
    var priority: Int = Priority.UNDEFINED.level
    var classification: Clazz? = null
    var status: Status? = null

    var dtStart: DtStart? = null
    var due: Due? = null
    var duration: Duration? = null
    var completedAt: Completed? = null
    var percentComplete: Int? = null

    var rRule: RRule? = null
    val rDates = LinkedList<RDate>()
    val exDates = LinkedList<ExDate>()

    companion object {

        /**
         * Parses an InputStream that contains iCalendar VTODOs.
         *
         * @param stream  input stream containing the VTODOs
         * @param charset charset of the input stream or null (will assume UTF-8)
         * @return array of filled Task data objects (may have size 0) – doesn't return null
         * @throws IOException
         * @throws InvalidCalendarException on parser exceptions
         */
        @JvmStatic
        @Throws(IOException::class, InvalidCalendarException::class)
        fun fromStream(stream: InputStream, charset: Charset?): List<Task>
        {
            var ical = Calendar()
            try {
                if (charset != null)
                    InputStreamReader(stream, charset).use { ical = calendarBuilder.build(it) }
                else
                    ical = calendarBuilder.build(stream)
            } catch (e: ParserException) {
                throw InvalidCalendarException("Couldn't parse iCalendar resource", e)
            }

            val vToDos = ical.getComponents<VToDo>(Component.VTODO)
            return vToDos.mapTo(LinkedList<Task>(), this::fromVToDo)
        }

        @Throws(InvalidCalendarException::class)
        private fun fromVToDo(todo: VToDo): Task {
            val t = Task()

            if (todo.uid != null)
                t.uid = todo.uid.value
            else {
                Constants.log.warning("Received VTODO without UID, generating new one")
                t.generateUID()
            }

            // sequence must only be null for locally created, not-yet-synchronized events
            t.sequence = 0

            for (prop in todo.properties)
                when (prop) {
                    is Sequence -> t.sequence = prop.sequenceNo
                    is Created -> t.createdAt = prop.dateTime.time
                    is LastModified -> t.lastModified = prop.dateTime.time
                    is Summary -> t.summary = prop.value
                    is Location -> t.location = prop.value
                    is Geo -> t.geoPosition = prop
                    is Description -> t.description = prop.value
                    is Url -> t.url = prop.value
                    is Organizer -> t.organizer = prop
                    is Priority -> t.priority = prop.level
                    is Clazz -> t.classification = prop
                    is Status -> t.status = prop
                    is Due -> { t.due = prop; validateTimeZone(t.due) }
                    is Duration -> t.duration = prop
                    is DtStart -> { t.dtStart= prop; validateTimeZone(t.dtStart) }
                    is Completed -> { t.completedAt = prop; validateTimeZone(t.completedAt) }
                    is PercentComplete -> t.percentComplete = prop.percentage
                    is RRule -> t.rRule = prop
                    is RDate -> t.rDates.add(prop)
                    is ExDate -> t.exDates.add(prop)
                }

            // there seem to be many invalid tasks out there because of some defect clients,
            // do some validation
            val dtStart = t.dtStart
            val due = t.due
            if (dtStart != null && due != null && !due.date.after(dtStart.date)) {
                Constants.log.warning("Invalid DTSTART >= DUE; ignoring DTSTART")
                t.dtStart = null
            }

            return t
        }

    }


    @Throws(IOException::class)
	fun write(os: OutputStream) {
		val ical = net.fortuna.ical4j.model.Calendar()
		ical.properties.add(Version.VERSION_2_0)
		ical.properties.add(prodId)

		val todo = VToDo()
		ical.components.add(todo)
		val props = todo.properties

        uid?.let { props.add(Uid(it)) }
        if (sequence != null && sequence != 0)
            props.add(Sequence(sequence as Int))

		createdAt?.let { props.add(Created(DateTime(it))) }
        lastModified?.let { props.add(LastModified(DateTime(it))) }

        summary?.let { props.add(Summary(it)) }
        location?.let { props.add(Location(it)) }
        geoPosition?.let(props::add)
        description?.let { props.add(Description(it)) }
		url?.let {
            try {
                props.add(Url(URI(it)))
            } catch (e: URISyntaxException) {
                Constants.log.log(Level.WARNING, "Ignoring invalid task URL: $url", e)
            }
        }
        organizer?.let(props::add)

		if (priority != Priority.UNDEFINED.level)
			props.add(Priority(priority))
        classification?.let(props::add)
        status?.let(props::add)

        rRule?.let(props::add)
        rDates.forEach { props.add(it) }
        exDates.forEach { props.add(it) }

		// remember used time zones
		val usedTimeZones = HashSet<TimeZone>()
		due?.let {
            props.add(it)
            it.timeZone?.let(usedTimeZones::add)
		}
        duration?.let(props::add)
        dtStart?.let {
            props.add(it)
            it.timeZone?.let(usedTimeZones::add)
        }
        completedAt?.let {
            props.add(it)
            it.timeZone?.let(usedTimeZones::add)
        }
		percentComplete?.let { props.add(PercentComplete(it)) }

		// add VTIMEZONE components
        usedTimeZones.forEach { ical.components.add(it.vTimeZone) }

        try {
            ical.validate()
        } catch (e: ValidationException) {
            Constants.log.log(Level.INFO, "VTODO validation result", e)
        }

        CalendarOutputter(false).output(ical, os)
	}


    fun isAllDay(): Boolean {
        val dtStart = dtStart
        val due = due
        return (dtStart != null && !(dtStart.date is DateTime)) ||
               (due != null && !(due.date is DateTime))
    }

    fun getTimeZone(): java.util.TimeZone {
        var tz: java.util.TimeZone? = null
        dtStart?.timeZone?.let { tz = it }

        tz = tz ?: due?.timeZone

        // fallback
        if (tz == null)
            tz = TimeZone.getDefault()

        return tz!!
    }

}
