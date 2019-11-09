/*
 * Copyright © Ricki Hirner and contributors (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 *
 * Contributors: Alex Baker
 */

package at.bitfire.ical4android

import at.bitfire.ical4android.MiscUtils.TextListHelper.toList
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.*
import java.io.IOException
import java.io.OutputStream
import java.io.Reader
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.logging.Level

class Task: ICalendar() {

    var createdAt: Long? = null
    var lastModified: Long? = null

    var summary: String? = null
    var location: String? = null
    var description: String? = null
    var color: Int? = null
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

    val alarms = LinkedList<VAlarm>()
    val categories = LinkedList<String>()
    val unknownProperties = LinkedList<Property>()

    companion object {

        /**
         * Parses an InputStream that contains iCalendar VTODOs.
         *
         * @param reader  reader for the input stream containing the VTODOs (pay attention to the charset)
         * @return array of filled Task data objects (may have size 0) – doesn't return null
         * @throws IOException
         * @throws InvalidCalendarException on parser exceptions
         */
        fun fromReader(reader: Reader): List<Task> {
            val ical: Calendar
            try {
                ical = CalendarBuilder().build(reader)
            } catch(e: ParserException) {
                throw InvalidCalendarException("Couldn't parse iCalendar object", e)
            } catch(e: IllegalArgumentException) {
                throw InvalidCalendarException("iCalendar object contains invalid value", e)
            }

            val vToDos = ical.getComponents<VToDo>(Component.VTODO)
            return vToDos.mapTo(LinkedList()) { this.fromVToDo(it) }
        }

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
                    is Color -> t.color = Css3Color.fromString(prop.value)?.argb
                    is Url -> t.url = prop.value
                    is Organizer -> t.organizer = prop
                    is Priority -> t.priority = prop.level
                    is Clazz -> t.classification = prop
                    is Status -> t.status = prop
                    is Due -> { t.due = prop }
                    is Duration -> t.duration = prop
                    is DtStart -> { t.dtStart = prop }
                    is Completed -> { t.completedAt = prop }
                    is PercentComplete -> t.percentComplete = prop.percentage
                    is RRule -> t.rRule = prop
                    is RDate -> t.rDates += prop
                    is ExDate -> t.exDates += prop
                    is Categories -> t.categories.addAll(prop.categories.toList())
                    is ProdId, is DtStamp, is Uid -> { /* don't save these as unknown properties */ }
                    else -> t.unknownProperties += prop
                }

            t.alarms.addAll(todo.alarms)

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


    fun write(os: OutputStream) {
        val props = PropertyList<Property>()
        uid?.let { props += Uid(uid) }
        sequence?.let { if (it != 0) props += Sequence(sequence as Int) }

        createdAt?.let { props += Created(DateTime(it)) }
        lastModified?.let { props += LastModified(DateTime(it)) }

        summary?.let { props += Summary(it) }
        location?.let { props += Location(it) }
        geoPosition?.let { props += it }
        description?.let { props += Description(it) }
        color?.let { props += Color(null, Css3Color.nearestMatch(it).name) }
        url?.let {
            try {
                props += Url(URI(it))
            } catch (e: URISyntaxException) {
                Constants.log.log(Level.WARNING, "Ignoring invalid task URL: $url", e)
            }
        }
        organizer?.let { props += it }

        if (priority != Priority.UNDEFINED.level)
            props += Priority(priority)
        classification?.let { props += it }
        status?.let { props += it }

        rRule?.let { props += it }
        rDates.forEach { props += it }
        exDates.forEach { props += it }

        if (categories.isNotEmpty())
            props += Categories(TextList(categories.toTypedArray()))

        props.addAll(unknownProperties)

        // remember used time zones
        val usedTimeZones = HashSet<TimeZone>()
        due?.let {
            props += it
            it.timeZone?.let(usedTimeZones::add)
        }
        duration?.let(props::add)
        dtStart?.let {
            props += it
            it.timeZone?.let(usedTimeZones::add)
        }
        completedAt?.let {
            props += it
            it.timeZone?.let(usedTimeZones::add)
        }
        percentComplete?.let { props += PercentComplete(it) }

        // generate VTODO
        val iCalProps = PropertyList<Property>(2)
        iCalProps += Version.VERSION_2_0
        iCalProps += prodId

        val vTodo = VToDo(props)
        if (alarms.isNotEmpty())
            vTodo.alarms.addAll(alarms)

        val iCalComponents = ComponentList<CalendarComponent>(2)
        iCalComponents.add(vTodo)
        iCalComponents.addAll(usedTimeZones.map { it.vTimeZone })

        val ical = Calendar(iCalProps, iCalComponents)
        CalendarOutputter(false).output(ical, os)
    }


    fun isAllDay(): Boolean {
        val dtStart = dtStart
        val due = due
        return (dtStart != null && dtStart.date !is DateTime) ||
               (due != null && due.date !is DateTime)
    }

}
