package at.bitfire.ical4android


import android.content.ContentValues
import android.net.Uri
import android.util.Log
import at.bitfire.notesx5.NotesX5Contract
import at.bitfire.notesx5.NotesX5Contract.X5ICalObject
import at.bitfire.notesx5.NotesX5Contract.asSyncAdapter
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.*
import java.io.IOException
import java.io.OutputStream
import java.io.Reader
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.logging.Level


open class Notesx5ICalObject(
    val collection: Notesx5Collection<Notesx5ICalObject>
    //component: X5ICalObject.Component
) {

    var id: Long = 0L
    lateinit var component: String
    var summary: String? = null
    var description: String? = null
    var dtstart: Long? = null
    var dtstartTimezone: String? = null
    var dtend: Long? = null
    var dtendTimezone: String? = null

    var classification: X5ICalObject.Classification =
        X5ICalObject.Classification.PUBLIC    // 0 = PUBLIC, 1 = PRIVATE, 2 = CONFIDENTIAL, -1 = NOT SUPPORTED (value in classificationX)

    var priority: Int? = null
    var percent: Int? = null
    var url: String? = null
    var contact: String? = null
    var geoLat: Float? = null
    var geoLong: Float? = null
    var location: String? = null

    var uid: String = "${System.currentTimeMillis()}-${UUID.randomUUID()}@at.bitfire.notesx5"                              //unique identifier, see https://tools.ietf.org/html/rfc5545#section-3.8.4.7

    var created: Long =
        System.currentTimeMillis()   // see https://tools.ietf.org/html/rfc5545#section-3.8.7.1
    var dtstamp: Long =
        System.currentTimeMillis()   // see https://tools.ietf.org/html/rfc5545#section-3.8.7.2
    var lastModified: Long =
        System.currentTimeMillis() // see https://tools.ietf.org/html/rfc5545#section-3.8.7.3
    var sequence: Long = 0                             // increase on every change (+1), see https://tools.ietf.org/html/rfc5545#section-3.8.7.4

    var color: Int? = null
    var other: String? = null

    var collectionId: Long = collection.id

    var categories: MutableList<String> = mutableListOf()

    var dirty: Boolean = true
    var deleted: Boolean = false


    companion object {

        /**
         * Parses an iCalendar resource, applies [ICalPreprocessor] to increase compatibility
         * and extracts the VTODOs.
         *
         * @param reader where the iCalendar is taken from
         *
         * @return array of filled [Task] data objects (may have size 0)
         *
         * @throws ParserException when the iCalendar can't be parsed
         * @throws IllegalArgumentException when the iCalendar resource contains an invalid value
         * @throws IOException on I/O errors
         */
        @UsesThreadContextClassLoader
        fun   tasksFromReader(reader: Reader, collection: Notesx5Collection<Notesx5ICalObject>): List<Notesx5ICalObject> {
            val ical = ICalendar.fromReader(reader)
            val vToDos = ical.getComponents<VToDo>(Component.VTODO)
            return vToDos.mapTo(LinkedList()) { this.fromVToDo(collection, it ) }
        }

        private fun fromVToDo(
            collection: Notesx5Collection<Notesx5ICalObject>,
            //component: X5ICalObject.Component,
            todo: VToDo): Notesx5ICalObject {

            val t = Notesx5ICalObject(collection)

            t.component = "VTODO"

            if (todo.uid != null)
                t.uid = todo.uid.value
            else {
                Ical4Android.log.warning("Received VTODO without UID, generating new one")
                t.uid = UUID.randomUUID().toString()
            }

            // sequence must only be null for locally created, not-yet-synchronized events
            t.sequence = 0

            for (prop in todo.properties)
                when (prop) {
                    is Sequence -> t.sequence = prop.sequenceNo.toLong()
                    is Created -> t.created = prop.dateTime.time
                    is LastModified -> t.lastModified = prop.dateTime.time
                    is Summary -> t.summary = prop.value
                    is Location -> t.location = prop.value
                    //is Geo -> t.geoPosition = prop
                    is Description -> t.description = prop.value
                    //is Color -> t.color = Css3Color.fromString(prop.value)?.argb
                    is Url -> t.url = prop.value
                    //is Organizer -> t.organizer = prop
                    is Priority -> t.priority = prop.level
                    //is Clazz -> t.classification = prop
                    //is Status -> t.status = prop
                    /* is Due -> {
                        t.due = prop
                    } */
                    //is Duration -> t.duration = prop
                    /*
                    is DtStart -> {
                        t.dtStart = prop
                    }
                    is Completed -> {
                        t.completedAt = prop
                    }

                     */
                    is PercentComplete -> t.percent = prop.percentage
                    /*
                    is RRule -> t.rRule = prop
                    is RDate -> t.rDates += prop
                    is ExDate -> t.exDates += prop

                     */
                    is Categories ->
                        for (category in prop.categories)
                            t.categories += category
                    /*
                    is RelatedTo -> t.relatedTo.add(prop)
                    is Uid, is ProdId, is DtStamp -> { /* don't save these as unknown properties */
                    }
                    else -> t.unknownProperties += prop

                     */
                }

            //t.alarms.addAll(todo.alarms)

            // There seem to be many invalid tasks out there because of some defect clients, do some validation.
            /*
            val dtStart = t.dtStart
            val due = t.due

            if (dtStart != null && due != null) {
                if (DateUtils.isDate(dtStart) && DateUtils.isDateTime(due)) {
                    Ical4Android.log.warning("DTSTART is DATE but DUE is DATE-TIME, rewriting DTSTART to DATE-TIME")
                    t.dtStart = DtStart(DateTime(dtStart.value, due.timeZone))
                } else if (DateUtils.isDateTime(dtStart) && DateUtils.isDate(due)) {
                    Ical4Android.log.warning("DTSTART is DATE-TIME but DUE is DATE, rewriting DUE to DATE-TIME")
                    t.due = Due(DateTime(due.value, dtStart.timeZone))
                }


                if (due.date <= dtStart.date) {
                    Ical4Android.log.warning("Found invalid DUE <= DTSTART; dropping DTSTART")
                    t.dtStart = null
                }
            }

            if (t.duration != null && t.dtStart == null) {
                Ical4Android.log.warning("Found DURATION without DTSTART; ignoring")
                t.duration = null
            }

             */

            return t
        }
    }


    @UsesThreadContextClassLoader
    fun write(os: OutputStream) {
        Ical4Android.checkThreadContextClassLoader()

        val ical = Calendar()
        ical.properties += Version.VERSION_2_0
        ical.properties += ICalendar.prodId

        val vTodo = VToDo(true /* generates DTSTAMP */)
        ical.components += vTodo
        val props = vTodo.properties

        uid.let { props += Uid(uid) }
        sequence.let {
            if (it != 0L)
                props += Sequence(it.toInt())
        }

        created.let { props += Created(DateTime(it)) }
        lastModified.let { props += LastModified(DateTime(it)) }

        summary?.let { props += Summary(it) }
        location?.let { props += Location(it) }
        //geoPosition?.let { props += it }
        description?.let { props += Description(it) }
        color?.let { props += Color(null, Css3Color.nearestMatch(it).name) }
        url?.let {
            try {
                props += Url(URI(it))
            } catch (e: URISyntaxException) {
                Ical4Android.log.log(Level.WARNING, "Ignoring invalid task URL: $url", e)
            }
        }
        //organizer?.let { props += it }
/*
        if (priority != Priority.UNDEFINED.level)
            props += Priority(priority)
        classification?.let { props += it }
        status?.let { props += it }

        rRule?.let { props += it }
        rDates.forEach { props += it }
        exDates.forEach { props += it }

        if (categories.isNotEmpty())
            props += Categories(TextList(categories.toTypedArray()))
        props.addAll(relatedTo)
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

        if (alarms.isNotEmpty())
            vTodo.alarms.addAll(alarms)

        // determine earliest referenced date
        val earliest = arrayOf(
            dtStart?.date,
            due?.date,
            completedAt?.date
        ).filterNotNull().min()
        // add VTIMEZONE components
        for (tz in usedTimeZones)
            ical.components += ICalendar.minifyVTimeZone(tz.vTimeZone, earliest)


 */
        ICalendar.softValidate(ical)
        CalendarOutputter(false).output(ical, os)


    }



    fun prepareForUpload(): String {
        return "new.ics"
    }

    fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {
    }

    fun updateFlags(flags: Int) {
    }

    fun add(): Uri {

        val values = this.toContentValues()

        Log.d("Calling add", "Lets see what happens")
        val newUri = collection.client.insert(X5ICalObject.CONTENT_URI.asSyncAdapter(collection.account), values) ?: Uri.EMPTY
        val newId = newUri.lastPathSegment?.toInt()
        if(newId != null)  {
            this.categories.forEach {
                val categoryContentValues = ContentValues().apply {
                    put(NotesX5Contract.X5Category.ICALOBJECT_ID, newId)
                    put(NotesX5Contract.X5Category.TEXT, it)
                }
                collection.client.insert(NotesX5Contract.X5Category.CONTENT_URI.asSyncAdapter(collection.account), categoryContentValues)
            }
        }
        return newUri
        //TODO("Not yet implemented")


    }

    fun update(data: Notesx5ICalObject): Uri {

        this.applyNewData(data)
        val values = this.toContentValues()

        var updateUri = X5ICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, data.id.toString())
        collection.client.update(updateUri, values, "${X5ICalObject.ID} = ?", arrayOf(data.id.toString()))
        return updateUri

        TODO("Not yet implemented")
    }

    fun delete(): Int {
        TODO("Not yet implemented")
    }


    fun applyNewData(newData: Notesx5ICalObject) {

        this.component = newData.component
        this.sequence = newData.sequence
        this.created = newData.created
        this.lastModified = newData.lastModified
        this.summary = newData.summary
        this.location = newData.location
        this.description = newData.description
        this.percent = newData.percent

        this.categories = newData.categories
        // tODO: to be continued
    }

    fun toContentValues(): ContentValues {

        val values = ContentValues()
        values.put(X5ICalObject.ID, id)
        values.put(X5ICalObject.SUMMARY, summary)
        values.put(X5ICalObject.DESCRIPTION, description)
        values.put(X5ICalObject.COMPONENT, component)
        values.put(X5ICalObject.DTSTART, dtstart)
        values.put(X5ICalObject.ICALOBJECT_COLLECTIONID, collectionId)
        values.put(X5ICalObject.UID, uid)
        values.put(X5ICalObject.PERCENT, percent)

        return values
    }



}

/*

class Notesx5Todo(account: Account, client: ContentProviderClient, collectionId: Long) :
    Notesx5ICalObject(account, client, collectionId, X5ICalObject.Component.TODO) {

    var status: X5ICalObject.StatusTodo? = null

    var percent: Int? = null    // VTODO only!
    var priority: Int? = null   // VTODO and VEVENT
    var due: Long? = null      // VTODO only!
    var dueTimezone: String? = null //VTODO only!
    var completed: Long? = null // VTODO only!
    var completedTimezone: String? = null //VTODO only!
    var duration: String? = null //VTODO only!

}

class Notesx5Journal(account: Account, client: ContentProviderClient, collectionId: Long) :
    Notesx5ICalObject(account, client, collectionId, X5ICalObject.Component.JOURNAL) {

    var status: X5ICalObject.StatusJournal? = null


}

 */
