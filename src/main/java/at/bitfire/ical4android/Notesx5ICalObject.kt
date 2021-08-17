package at.bitfire.ical4android


import android.content.ContentValues
import android.net.Uri
import android.util.Log
import at.bitfire.ical4android.MiscUtils.CursorHelper.toValues
import at.bitfire.notesx5.NotesX5Contract
import at.bitfire.notesx5.NotesX5Contract.X5ICalObject
import at.bitfire.notesx5.NotesX5Contract.asSyncAdapter
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.VJournal
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

    //var classification: X5ICalObject.Classification = X5ICalObject.Classification.PUBLIC    // 0 = PUBLIC, 1 = PRIVATE, 2 = CONFIDENTIAL, -1 = NOT SUPPORTED (value in classificationX)
    var classification: String? = null
    var status: String? = null

    var priority: Int? = null

    var due: Long? = null      // VTODO only!
    var dueTimezone: String? = null //VTODO only!
    var completed: Long? = null // VTODO only!
    var completedTimezone: String? = null //VTODO only!
    var duration: String? = null //VTODO only!

    var percent: Int? = null
    var url: String? = null
    var contact: String? = null
    var geoLat: Float? = null
    var geoLong: Float? = null
    var location: String? = null

    var uid: String =
        "${System.currentTimeMillis()}-${UUID.randomUUID()}@at.bitfire.notesx5"                              //unique identifier, see https://tools.ietf.org/html/rfc5545#section-3.8.4.7

    var created: Long =
        System.currentTimeMillis()   // see https://tools.ietf.org/html/rfc5545#section-3.8.7.1
    var dtstamp: Long =
        System.currentTimeMillis()   // see https://tools.ietf.org/html/rfc5545#section-3.8.7.2
    var lastModified: Long =
        System.currentTimeMillis()   // see https://tools.ietf.org/html/rfc5545#section-3.8.7.3
    var sequence: Long =
        0           // increase on every change (+1), see https://tools.ietf.org/html/rfc5545#section-3.8.7.4

    var color: Int? = null
    var other: String? = null

    var collectionId: Long = collection.id

    //var categories: MutableList<String> = mutableListOf()

    var dirty: Boolean = true
    var deleted: Boolean = false

    //@ricki, ist das ok hier?
    var fileName: String? = null
    var eTag: String? = null
    var scheduleTag: String? = null
    var flags: Int = 0

    var categories: MutableList<Category> = mutableListOf()
    var attachments: MutableList<Attachment> = mutableListOf()
    var relatedTo: MutableList<RelatedTo> = mutableListOf()


    data class Category(
        var categoryId: Long = 0L,
        var text: String = "",
        var language: String? = null,
        var other: String? = null
    )

    data class Attachment(
        var attachmentId: Long = 0L,
        var uri: String? = null,
        //var encoding: String? = null,
        var value: String? = null,
        var fmttype: String? = null,
        var other: String? = null,
        var filename: String? = null,
        var extension: String? = null,
        var filesize: Long? = null
    )

    data class RelatedTo(
        var relatedtoId: Long = 0L,
        //var icalObjectId: Long = 0L,
        var linkedICalObjectId: Long = 0L,
        var text: String? = null,
        var reltype: String? = null,
        var other: String? = null
    )


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
        fun tasksFromReader(
            reader: Reader,
            collection: Notesx5Collection<Notesx5ICalObject>
        ): List<Notesx5ICalObject> {
            val ical = ICalendar.fromReader(reader)
            val vToDos = ical.getComponents<VToDo>(Component.VTODO)
            return vToDos.mapTo(LinkedList()) { this.fromVToDo(collection, it) }
        }

        /**
         * Parses an iCalendar resource, applies [ICalPreprocessor] to increase compatibility
         * and extracts the VJOURNALs.
         *
         * @param reader where the iCalendar is taken from
         *
         * @return array of filled [Notesx5ICalObject] data objects (may have size 0)
         *
         * @throws ParserException when the iCalendar can't be parsed
         * @throws IllegalArgumentException when the iCalendar resource contains an invalid value
         * @throws IOException on I/O errors
         */
        @UsesThreadContextClassLoader
        fun journalsFromReader(
            reader: Reader,
            collection: Notesx5Collection<Notesx5ICalObject>
        ): List<Notesx5ICalObject> {
            val ical = ICalendar.fromReader(reader)
            val vJournals = ical.getComponents<VJournal>(Component.VJOURNAL)
            return vJournals.mapTo(LinkedList()) { this.fromVJournal(collection, it) }
        }

        private fun fromVToDo(
            collection: Notesx5Collection<Notesx5ICalObject>,
            //component: X5ICalObject.Component,
            todo: VToDo
        ): Notesx5ICalObject {

            val t = Notesx5ICalObject(collection)

            t.component = X5ICalObject.Component.VTODO.name

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
                    is Geo -> {
                        t.geoLat = prop.latitude.toFloat()
                        t.geoLong = prop.longitude.toFloat()
                        // TODO: name and attributes might get lost here!! Doublecheck what could be a good solution!
                    }
                    is Description -> t.description = prop.value
                    is Color -> t.color = Css3Color.fromString(prop.value)?.argb
                    is Url -> t.url = prop.value
                    //is Organizer -> t.organizer = prop
                    is Priority -> t.priority = prop.level
                    is Clazz -> t.classification = prop.value
                    is Status -> t.status = prop.value

                    is DtEnd -> {
                        t.dtend = prop.date.toInstant().toEpochMilli()
                        prop.timeZone?.let { t.dtendTimezone = it.toString() }
                        //TODO: Check if this is right!
                    }
                    is Completed -> {
                        t.completed = prop.date.toInstant().toEpochMilli()
                        prop.timeZone?.let { t.completedTimezone = it.toString() }
                        //TODO: Check if this is right!
                    }

                    is Due -> {
                        t.due = prop.date.toInstant().toEpochMilli()
                        prop.timeZone?.let { t.dueTimezone = it.toString() }
                        //TODO: Check if this is right!
                    }
                    //is Duration -> t.duration = prop.duration.

                    is DtStart -> {
                        t.dtstart = prop.date.time
                        /* if(!prop.isUtc)
                            t.dtstartTimezone = prop.timeZone.displayName

                         */
                        //TODO: Check if this is right!
                    }

                    is PercentComplete -> t.percent = prop.percentage
                    /*
                    is RRule -> t.rRule = prop
                    is RDate -> t.rDates += prop
                    is ExDate -> t.exDates += prop

                     */
                    is Categories ->
                        for (category in prop.categories)
                            t.categories.add(Category(text = category))

                    /*
                    is Attach -> {
                        val attachment = Attachment()
                        attachment.value = prop.value
                        attachment.uri = prop.uri.toString()
                    }
                     */

                    is net.fortuna.ical4j.model.property.RelatedTo -> {

                        val relatedTo = RelatedTo()
                        relatedTo.text = prop.value  // TODO ????????
                        relatedTo.reltype = prop.name   // TODO ??????????
                        t.relatedTo.add(relatedTo)
                    }

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
            val dtStart = t.dtstart
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

 */

            /*
            if (t.duration != null && t.dtStart == null) {
                Ical4Android.log.warning("Found DURATION without DTSTART; ignoring")
                t.duration = null
            }
             */

            return t
        }


        private fun fromVJournal(
            collection: Notesx5Collection<Notesx5ICalObject>,
            //component: X5ICalObject.Component,
            journal: VJournal
        ): Notesx5ICalObject {

            val j = Notesx5ICalObject(collection)

            j.component = X5ICalObject.Component.VJOURNAL.name

            if (journal.uid != null)
                j.uid = journal.uid.value
            else {
                Ical4Android.log.warning("Received VJOURNAL without UID, generating new one")
                j.uid = UUID.randomUUID().toString()
            }

            // sequence must only be null for locally created, not-yet-synchronized events
            j.sequence = 0

            for (prop in journal.properties)
                when (prop) {
                    is Sequence -> j.sequence = prop.sequenceNo.toLong()
                    is Created -> j.created = prop.dateTime.time
                    is LastModified -> j.lastModified = prop.dateTime.time
                    is Summary -> j.summary = prop.value
                    is Location -> j.location = prop.value
                    is Geo -> {
                        j.geoLat = prop.latitude.toFloat()
                        j.geoLong = prop.longitude.toFloat()
                        // TODO: name and attributes might get lost here!! Doublecheck what could be a good solution!
                    }
                    is Description -> j.description = prop.value
                    is Color -> j.color = Css3Color.fromString(prop.value)?.argb
                    is Url -> j.url = prop.value
                    //is Organizer -> t.organizer = prop
                    //is Priority -> t.priority = prop.level
                    is Clazz -> j.classification = prop.value
                    is Status -> j.status = prop.value

                    is DtEnd -> {
                        j.dtend = prop.date.toInstant().toEpochMilli()
                        prop.timeZone?.let { j.dtendTimezone = it.toString() }
                        //TODO: Check if this is right!
                    }
                    is Completed -> {
                        j.completed = prop.date.toInstant().toEpochMilli()
                        prop.timeZone?.let { j.completedTimezone = it.toString() }
                        //TODO: Check if this is right!
                    }

                    is DtStart -> {
                        j.dtstart = prop.date.time
                        /* if(!prop.isUtc)
                            t.dtstartTimezone = prop.timeZone.displayName

                         */
                        //TODO: Check if this is right!
                    }

                    is Categories ->
                        for (category in prop.categories)
                            j.categories.add(Category(text = category))

                    /*
                    is Attach -> {
                        val attachment = Attachment()
                        attachment.value = prop.value
                        attachment.uri = prop.uri.toString()
                    }
                     */

                    is net.fortuna.ical4j.model.property.RelatedTo -> {

                        val relatedTo = RelatedTo()
                        relatedTo.text = prop.value  // TODO ????????
                        relatedTo.reltype = prop.name   // TODO ??????????
                        j.relatedTo.add(relatedTo)
                    }
                }

            return j
        }
    }


    @UsesThreadContextClassLoader
    fun write(os: OutputStream) {
        Ical4Android.checkThreadContextClassLoader()

        val ical = Calendar()
        ical.properties += Version.VERSION_2_0
        ical.properties += ICalendar.prodId

        if(component == X5ICalObject.Component.VTODO.name) {
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
            description?.let { props += Description(it) }

            location?.let { props += Location(it) }
            if(geoLat != null && geoLong != null)
                props += Geo(geoLat!!.toBigDecimal(), geoLong!!.toBigDecimal())
            color?.let { props += Color(null, Css3Color.nearestMatch(it).name) }
            url?.let {
                try {
                    props += Url(URI(it))
                } catch (e: URISyntaxException) {
                    Ical4Android.log.log(Level.WARNING, "Ignoring invalid task URL: $url", e)
                }
            }
            //organizer?.let { props += it }

            if (priority != Priority.UNDEFINED.level)
                priority?.let { props += Priority(priority!!) }

            classification?.let { props += Clazz(it) }
            status?.let { props += Status(it) }
/*
        rRule?.let { props += it }
        rDates.forEach { props += it }
        exDates.forEach { props += it }
*/

            if (categories.isNotEmpty()) {
                val textList = TextList()
                categories.forEach {
                    textList.add(it.text)
                }
                props += Categories(textList)
            }

            /*
        if(relatedTo.isNotEmpty()) {
            var rel: net.fortuna.ical4j.model.property.RelatedTo = net.fortuna.ical4j.model.property.RelatedTo()

        }

         */

            /*
        props.addAll(relatedTo)
        props.addAll(unknownProperties)

        // remember used time zones
        val usedTimeZones = HashSet<TimeZone>()
        duration?.let(props::add)
        */
            due?.let {
                props += Due(DateTime(it))
                //it.timeZone?.let(usedTimeZones::add)
            }

            dtstart?.let {
                props += DtStart(DateTime(it))
                //it.timeZone?.let(usedTimeZones::add)
            }
            dtend?.let {
                props += DtEnd(DateTime(it))
                //it.timeZone?.let(usedTimeZones::add)
            }
            completed?.let {
                props += Completed(DateTime(it))
                //it.timeZone?.let(usedTimeZones::add)
            }
            percent?.let { props += PercentComplete(it) }

            /*
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
        } else if(component == X5ICalObject.Component.VJOURNAL.name) {
            val vJournal = VJournal(true /* generates DTSTAMP */)
            ical.components += vJournal
            val props = vJournal.properties

            uid.let { props += Uid(uid) }
            sequence.let {
                if (it != 0L)
                    props += Sequence(it.toInt())
            }

            created.let { props += Created(DateTime(it)) }
            lastModified.let { props += LastModified(DateTime(it)) }

            summary?.let { props += Summary(it) }
            description?.let { props += Description(it) }

            location?.let { props += Location(it) }
            if(geoLat != null && geoLong != null)
                props += Geo(geoLat!!.toBigDecimal(), geoLong!!.toBigDecimal())
            color?.let { props += Color(null, Css3Color.nearestMatch(it).name) }
            url?.let {
                try {
                    props += Url(URI(it))
                } catch (e: URISyntaxException) {
                    Ical4Android.log.log(Level.WARNING, "Ignoring invalid task URL: $url", e)
                }
            }
            //organizer?.let { props += it }

            if (priority != Priority.UNDEFINED.level)
                priority?.let { props += Priority(priority!!) }

            classification?.let { props += Clazz(it) }
            status?.let { props += Status(it) }

            if (categories.isNotEmpty()) {
                val textList = TextList()
                categories.forEach {
                    textList.add(it.text)
                }
                props += Categories(textList)
            }

            dtstart?.let {
                props += DtStart(DateTime(it))
                //it.timeZone?.let(usedTimeZones::add)
            }
            dtend?.let {
                props += DtEnd(DateTime(it))
                //it.timeZone?.let(usedTimeZones::add)
            }
        }


        ICalendar.softValidate(ical)
        CalendarOutputter(false).output(ical, os)

    }


    fun prepareForUpload(): String {
        return "${this.uid}.ics"
    }

    fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {

        var updateUri = X5ICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())

        val values = ContentValues()
        fileName?.let { values.put(X5ICalObject.FILENAME, fileName) }
        eTag?.let { values.put(X5ICalObject.ETAG, eTag) }
        scheduleTag?.let { values.put(X5ICalObject.SCHEDULETAG, scheduleTag) }
        values.put(X5ICalObject.DIRTY, false)

        collection.client.update(updateUri, values, null, null)
    }

    fun updateFlags(flags: Int) {

        var updateUri = X5ICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())

        val values = ContentValues()
        values.put(X5ICalObject.FLAGS, flags)

        collection.client.update(updateUri, values, null, null)
    }

    fun add(): Uri {

        val values = this.toContentValues()

        Log.d("Calling add", "Lets see what happens")
        val newUri = collection.client.insert(
            X5ICalObject.CONTENT_URI.asSyncAdapter(collection.account),
            values
        ) ?: Uri.EMPTY
        val newId = newUri.lastPathSegment?.toInt()
        if (newId != null) {
            this.categories.forEach {
                val categoryContentValues = ContentValues().apply {
                    put(NotesX5Contract.X5Category.ICALOBJECT_ID, newId)
                    put(NotesX5Contract.X5Category.TEXT, it.text)
                    put(NotesX5Contract.X5Category.ID, it.categoryId)
                    put(NotesX5Contract.X5Category.LANGUAGE, it.language)
                    put(NotesX5Contract.X5Category.OTHER, it.other)
                }
                collection.client.insert(
                    NotesX5Contract.X5Category.CONTENT_URI.asSyncAdapter(
                        collection.account
                    ), categoryContentValues
                )
            }
        }
        return newUri
        //TODO("Not yet implemented")

    }

    fun update(data: Notesx5ICalObject): Uri {

        this.applyNewData(data)
        val values = this.toContentValues()

        var updateUri = X5ICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())
        collection.client.update(
            updateUri,
            values,
            "${X5ICalObject.ID} = ?",
            arrayOf(this.id.toString())
        )

        collection.client.delete(
            NotesX5Contract.X5Category.CONTENT_URI.asSyncAdapter(collection.account),
            "${NotesX5Contract.X5Category.ICALOBJECT_ID} = ?",
            arrayOf(this.id.toString())
        )
        this.categories.forEach {
            val categoryContentValues = ContentValues().apply {
                put(NotesX5Contract.X5Category.ICALOBJECT_ID, id)
                put(NotesX5Contract.X5Category.TEXT, it.text)
                put(NotesX5Contract.X5Category.ID, it.categoryId)
                put(NotesX5Contract.X5Category.LANGUAGE, it.language)
                put(NotesX5Contract.X5Category.OTHER, it.other)
            }
            collection.client.insert(
                NotesX5Contract.X5Category.CONTENT_URI.asSyncAdapter(collection.account),
                categoryContentValues
            )
        }

        return updateUri

        //TODO("Not yet implemented")
    }

    fun delete(): Int {
        val uri = Uri.withAppendedPath(
            X5ICalObject.CONTENT_URI.asSyncAdapter(collection.account),
            id.toString()
        )
        return collection.client.delete(uri, null, null)
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
        this.classification = newData.classification
        this.status = newData.status
        this.priority = newData.priority

        this.dtstart = newData.dtstart
        this.dtstartTimezone = newData.dtstartTimezone
        this.dtend = newData.dtend
        this.dtendTimezone = newData.dtendTimezone
        this.completed = newData.completed
        this.completedTimezone = newData.completedTimezone
        this.due = newData.due
        this.dueTimezone = newData.dueTimezone

        this.categories = newData.categories
        // tODO: to be continued
    }

    fun toContentValues(): ContentValues {

        val values = ContentValues()
        values.put(X5ICalObject.ID, id)
        values.put(X5ICalObject.SUMMARY, summary)
        values.put(X5ICalObject.DESCRIPTION, description)
        values.put(X5ICalObject.COMPONENT, component)
        if (status?.isNotBlank() == true)
            values.put(X5ICalObject.STATUS, status)
        if (classification?.isNotBlank() == true)
            values.put(X5ICalObject.CLASSIFICATION, classification)
        values.put(X5ICalObject.PRIORITY, priority)
        values.put(X5ICalObject.ICALOBJECT_COLLECTIONID, collectionId)
        values.put(X5ICalObject.UID, uid)
        values.put(X5ICalObject.PERCENT, percent)
        values.put(X5ICalObject.DTSTAMP, dtstamp)
        values.put(X5ICalObject.DTSTART, dtstart)
        values.put(X5ICalObject.DTSTART_TIMEZONE, dtstartTimezone)
        values.put(X5ICalObject.DTEND, dtend)
        values.put(X5ICalObject.DTEND_TIMEZONE, dtendTimezone)
        values.put(X5ICalObject.COMPLETED, completed)
        values.put(X5ICalObject.COMPLETED_TIMEZONE, completedTimezone)
        values.put(X5ICalObject.DUE, due)
        values.put(X5ICalObject.DUE_TIMEZONE, dueTimezone)

        values.put(X5ICalObject.FILENAME, fileName)
        values.put(X5ICalObject.ETAG, eTag)
        values.put(X5ICalObject.SCHEDULETAG, scheduleTag)
        values.put(X5ICalObject.FLAGS, flags)

        return values
    }


    fun getCategoryContentValues(): List<ContentValues> {

        val categoryUrl = NotesX5Contract.X5Category.CONTENT_URI.asSyncAdapter(collection.account)
        val categoryValues: MutableList<ContentValues> = mutableListOf()
        collection.client.query(
            categoryUrl,
            null,
            "${NotesX5Contract.X5Category.ICALOBJECT_ID} = ?",
            arrayOf(this.id.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                categoryValues.add(cursor.toValues())
            }
        }

        return categoryValues
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
