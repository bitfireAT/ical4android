package at.bitfire.ical4android


import android.content.ContentValues
import android.content.Context
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
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.*
import org.apache.commons.io.IOUtils
import java.io.*
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
    var attendees: MutableList<Attendee> = mutableListOf()
    var comments: MutableList<Comment> = mutableListOf()

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

    data class Comment (
        var commentId: Long = 0L,
        var text: String = "",
        var altrep: String? = null,
        var language: String? = null,
        var other: String? = null
    )

    data class RelatedTo(
        var relatedtoId: Long = 0L,
        //var icalObjectId: Long = 0L,
        //var linkedICalObjectId: Long = 0L,
        var text: String? = null,
        var reltype: String? = null,
        var other: String? = null
    )

    data class Attendee(
        var attendeeId: Long = 0L,
        //var icalObjectId: Long = 0L,
        var caladdress: String = "",
        var cutype: String? = NotesX5Contract.X5Attendee.Cutype.INDIVIDUAL.name,
        var member: String? = null,
        var role: String? = NotesX5Contract.X5Attendee.Role.`REQ-PARTICIPANT`.name,
        var partstat: String? = null,
        var rsvp: Boolean? = null,
        var delegatedto: String? = null,
        var delegatedfrom: String? = null,
        var sentby: String? = null,
        var cn: String? = null,
        var dir: String? = null,
        var language: String? = null,
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
                Ical4Android.log.warning("Received VJOURNAL without UID, generating new one")
                t.uid = UUID.randomUUID().toString()
            }

            processProperties(t, todo.properties)

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

            processProperties(j, journal.properties)

            return j
        }

        private fun processProperties(iCalObject: Notesx5ICalObject, properties: PropertyList<*>) {

            // sequence must only be null for locally created, not-yet-synchronized events
            iCalObject.sequence = 0

            for (prop in properties)
                when (prop) {
                    is Sequence -> iCalObject.sequence = prop.sequenceNo.toLong()
                    is Created -> iCalObject.created = prop.dateTime.time
                    is LastModified -> iCalObject.lastModified = prop.dateTime.time
                    is Summary -> iCalObject.summary = prop.value
                    is Location -> iCalObject.location = prop.value
                    is Geo -> {
                        iCalObject.geoLat = prop.latitude.toFloat()
                        iCalObject.geoLong = prop.longitude.toFloat()
                        // TODO: name and attributes might get lost here!! Doublecheck what could be a good solution!
                    }
                    is Description -> iCalObject.description = prop.value
                    is Color -> iCalObject.color = Css3Color.fromString(prop.value)?.argb
                    is Url -> iCalObject.url = prop.value
                    //is Organizer -> t.organizer = prop
                    is Priority -> iCalObject.priority = prop.level
                    is Clazz -> iCalObject.classification = prop.value
                    is Status -> iCalObject.status = prop.value
                    is DtEnd -> Ical4Android.log.warning("The property DtEnd must not be used for VTODO and VJOURNAL, this value is rejected.")
                    is Completed -> {
                        if (iCalObject.component == X5ICalObject.Component.VTODO.name) {
                            iCalObject.completed = prop.date.time
                        } else
                            Ical4Android.log.warning("The property Completed is only supported for VTODO, this value is rejected.")
                    }

                    is Due -> {
                        if (iCalObject.component == X5ICalObject.Component.VTODO.name) {
                            iCalObject.due = prop.date.time
                            when {
                                prop.date is DateTime && prop.timeZone != null -> iCalObject.dueTimezone = prop.timeZone.id
                                prop.date is DateTime && prop.timeZone == null -> iCalObject.dueTimezone = null                   // this comparison is kept on purpose as "prop.date is Date" did not work as expected.
                                else -> iCalObject.dueTimezone = "ALLDAY"     // prop.date is Date (and not DateTime), therefore it must be Allday
                            }
                        } else
                            Ical4Android.log.warning("The property Due is only supported for VTODO, this value is rejected.")
                    }

                    //is Duration -> t.duration = prop.duration.

                    is DtStart -> {
                        iCalObject.dtstart = prop.date.time
                        when {
                            prop.date is DateTime && prop.timeZone != null -> iCalObject.dtstartTimezone = prop.timeZone.id
                            prop.date is DateTime && prop.timeZone == null -> iCalObject.dtstartTimezone = null                   // this comparison is kept on purpose as "prop.date is Date" did not work as expected.
                            else -> iCalObject.dtstartTimezone = "ALLDAY"     // prop.date is Date (and not DateTime), therefore it must be Allday
                        }
                        /* if(!prop.isUtc)
                            t.dtstartTimezone = prop.timeZone.displayName
                         */
                    }

                    is PercentComplete -> {
                        if (iCalObject.component == X5ICalObject.Component.VTODO.name)
                            iCalObject.percent = prop.percentage
                        else
                            Ical4Android.log.warning("The property PercentComplete is only supported for VTODO, this value is rejected.")
                    }
                    /*
                    is RRule -> t.rRule = prop
                    is RDate -> t.rDates += prop
                    is ExDate -> t.exDates += prop

                     */
                    is Categories ->
                        for (category in prop.categories)
                            iCalObject.categories.add(Category(text = category))

                    is net.fortuna.ical4j.model.property.Comment ->
                        iCalObject.comments.add(Comment(text = prop.value))

                    is Attach -> {
                        val attachment = Attachment()
                        //attachment.value = prop.value
                        attachment.uri = prop.uri.toString()
                        iCalObject.attachments.add(attachment)
                        //todo: get additional parameters
                    }

                    is net.fortuna.ical4j.model.property.RelatedTo -> {

                        val relatedTo = RelatedTo()
                        relatedTo.reltype = prop.getParameter<RelType>(RelType.RELTYPE).value
                        relatedTo.text = prop.value
                        iCalObject.relatedTo.add(relatedTo)
                    }

                    is net.fortuna.ical4j.model.property.Attendee -> {
                        iCalObject.attendees.add(
                            Attendee(caladdress = prop.calAddress.toString())
                            //todo: take care of other attributes for attendees
                        )
                    }


                    /*
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

        }
    }


    @UsesThreadContextClassLoader
    fun write(os: OutputStream, context: Context) {
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

            comments.forEach {
                props += Comment(it.text)
            }

            attendees.forEach {
                props += net.fortuna.ical4j.model.property.Attendee(it.caladdress)
                //todo: take care of other attributes for attendees
            }

            attachments.forEach {
                props += Attach().apply {
                    this.uri = URI(it.uri)
                    //val file = context.contentResolver.openInputStream(Uri.parse(this.uri.toString()))
                    //this.binary = IOUtils.toByteArray(file)
                    //TODO: Find a solution to store the binary
                //this.value = it.value
                    // todo: take care of additional parameters
                }
            }

            relatedTo.forEach {
                val param: Parameter =
                    when (it.reltype) {
                        RelType.CHILD.value -> RelType.CHILD
                        RelType.SIBLING.value -> RelType.SIBLING
                        RelType.PARENT.value -> RelType.PARENT
                        else -> return@forEach
                    }
                val parameterList = ParameterList()
                parameterList.add(param)
                props += RelatedTo(parameterList, it.text)
            }


            /*
        props.addAll(relatedTo)
        props.addAll(unknownProperties)

        // remember used time zones
        val usedTimeZones = HashSet<TimeZone>()
        duration?.let(props::add)
        */
            due?.let {
                when {
                    dueTimezone == "ALLDAY" -> props += Due(Date(it))
                    dueTimezone.isNullOrEmpty() -> props += Due(DateTime(it))
                    else -> {
                        val timezone = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone(dueTimezone)
                        val withTimezone = Due(DateTime(it))
                        withTimezone.timeZone = timezone
                        props += withTimezone
                    }
                }
            }

            dtstart?.let {
                when {
                    dtstartTimezone == "ALLDAY" -> props += DtStart(Date(it))
                    dtstartTimezone.isNullOrEmpty() -> props += DtStart(DateTime(it))
                    else -> {
                        val timezone = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone(dtstartTimezone)
                        val withTimezone = DtStart(DateTime(it))
                        withTimezone.timeZone = timezone
                        props += withTimezone
                    }
                }
            }
            dtend?.let {
                when {
                    dtendTimezone == "ALLDAY" -> props += DtEnd(Date(it))
                    dtendTimezone.isNullOrEmpty() -> props += DtEnd(DateTime(it))
                    else -> {
                        val timezone = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone(dtendTimezone)
                        val withTimezone = DtEnd(DateTime(it))
                        withTimezone.timeZone = timezone
                        props += withTimezone
                    }
                }
            }
            completed?.let {
                //Completed is defines as always DateTime! And is always UTC!?
                props += Completed(DateTime(it))
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

            comments.forEach {
                props += Comment(it.text)
            }

            attendees.forEach {
                props += net.fortuna.ical4j.model.property.Attendee(it.caladdress)
                //todo: take care of other attributes for attendees
            }

            attachments.forEach {
                /*
                props += Attach().apply {
                    this.uri = URI(it.uri)
                    val file = context.contentResolver.openInputStream(Uri.parse(this.uri.toString()))
                    this.binary = IOUtils.toByteArray(file)
                    //TODO: Find a solution to store the binary
                    //this.value = it.value
                    // todo: take care of additional parameters
                }

                 */
                context.contentResolver.openInputStream(Uri.parse(URI(it.uri).toString())).use { file ->
                    props += Attach(IOUtils.toByteArray(file))
                }
            }

            relatedTo.forEach {
                val param: Parameter =
                    when (it.reltype) {
                        RelType.CHILD.value -> RelType.CHILD
                        RelType.SIBLING.value -> RelType.SIBLING
                        RelType.PARENT.value -> RelType.PARENT
                        else -> return@forEach
                    }
                val parameterList = ParameterList()
                parameterList.add(param)
                props += RelatedTo(parameterList, it.text)
            }

            dtstart?.let {
                when {
                    dtstartTimezone == "ALLDAY" -> props += DtStart(Date(it))
                    dtstartTimezone.isNullOrEmpty() -> props += DtStart(DateTime(it))
                    else -> {
                        val timezone = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone(dtstartTimezone)
                        val withTimezone = DtStart(DateTime(it))
                        withTimezone.timeZone = timezone
                        props += withTimezone
                    }
                }
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
        ) ?: return Uri.EMPTY
        this.id = newUri.lastPathSegment?.toLong() ?: return Uri.EMPTY

        insertOrUpdateListProperties(false)

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

        insertOrUpdateListProperties(true)

        return updateUri

        //TODO("Not yet implemented")
    }


    fun insertOrUpdateListProperties(isUpdate: Boolean) {

        // delete the categories, attendees, ... and insert them again after. Only relevant for Update, for an insert there will be no entries
        if(isUpdate) {
            collection.client.delete(
                NotesX5Contract.X5Category.CONTENT_URI.asSyncAdapter(collection.account),
                "${NotesX5Contract.X5Category.ICALOBJECT_ID} = ?",
                arrayOf(this.id.toString())
            )

            collection.client.delete(
                NotesX5Contract.X5Comment.CONTENT_URI.asSyncAdapter(collection.account),
                "${NotesX5Contract.X5Comment.ICALOBJECT_ID} = ?",
                arrayOf(this.id.toString())
            )

            collection.client.delete(
                NotesX5Contract.X5Relatedto.CONTENT_URI.asSyncAdapter(collection.account),
                "${NotesX5Contract.X5Relatedto.ICALOBJECT_ID} = ?",
                arrayOf(this.id.toString())
            )

            collection.client.delete(
                NotesX5Contract.X5Attendee.CONTENT_URI.asSyncAdapter(collection.account),
                "${NotesX5Contract.X5Attendee.ICALOBJECT_ID} = ?",
                arrayOf(this.id.toString())
            )

            collection.client.delete(
                NotesX5Contract.X5Attachment.CONTENT_URI.asSyncAdapter(collection.account),
                "${NotesX5Contract.X5Attachment.ICALOBJECT_ID} = ?",
                arrayOf(this.id.toString())
            )
        }

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

        this.comments.forEach {
            val commentContentValues = ContentValues().apply {
                put(NotesX5Contract.X5Comment.ICALOBJECT_ID, id)
                put(NotesX5Contract.X5Comment.TEXT, it.text)
                put(NotesX5Contract.X5Comment.ID, it.commentId)
                put(NotesX5Contract.X5Comment.LANGUAGE, it.language)
                put(NotesX5Contract.X5Comment.OTHER, it.other)
            }
            collection.client.insert(
                NotesX5Contract.X5Comment.CONTENT_URI.asSyncAdapter(collection.account),
                commentContentValues
            )
        }


        this.relatedTo.forEach {
            val relatedToContentValues = ContentValues().apply {
                put(NotesX5Contract.X5Relatedto.ICALOBJECT_ID, id)
                put(NotesX5Contract.X5Relatedto.TEXT, it.text)
                put(NotesX5Contract.X5Relatedto.RELTYPE, it.reltype)
                put(NotesX5Contract.X5Relatedto.OTHER, it.other)
            }
            collection.client.insert(
                NotesX5Contract.X5Relatedto.CONTENT_URI.asSyncAdapter(collection.account),
                relatedToContentValues
            )
        }

        this.attendees.forEach {
            val attendeeContentValues = ContentValues().apply {
                put(NotesX5Contract.X5Attendee.ICALOBJECT_ID, id)
                put(NotesX5Contract.X5Attendee.CALADDRESS, it.caladdress)

                put(NotesX5Contract.X5Attendee.CN, it.cn)
                put(NotesX5Contract.X5Attendee.CUTYPE, it.cutype)
                put(NotesX5Contract.X5Attendee.DELEGATEDFROM, it.delegatedfrom)
                put(NotesX5Contract.X5Attendee.DELEGATEDTO, it.delegatedto)
                put(NotesX5Contract.X5Attendee.DIR, it.dir)
                put(NotesX5Contract.X5Attendee.LANGUAGE, it.language)
                put(NotesX5Contract.X5Attendee.MEMBER, it.member)
                put(NotesX5Contract.X5Attendee.PARTSTAT, it.partstat)
                put(NotesX5Contract.X5Attendee.ROLE, it.role)
                put(NotesX5Contract.X5Attendee.RSVP, it.rsvp)
                put(NotesX5Contract.X5Attendee.SENTBY, it.sentby)
                put(NotesX5Contract.X5Attendee.OTHER, it.other)
            }
            collection.client.insert(
                NotesX5Contract.X5Attendee.CONTENT_URI.asSyncAdapter(
                    collection.account
                ), attendeeContentValues
            )
        }

        this.attachments.forEach {
            val attachmentContentValues = ContentValues().apply {
                put(NotesX5Contract.X5Attachment.ICALOBJECT_ID, id)
                put(NotesX5Contract.X5Attachment.URI, it.uri)
                put(NotesX5Contract.X5Attachment.VALUE, it.value)
                put(NotesX5Contract.X5Attachment.FMTTYPE, it.fmttype)
                put(NotesX5Contract.X5Attachment.OTHER, it.other)
            }
            collection.client.insert(NotesX5Contract.X5Attachment.CONTENT_URI.asSyncAdapter(collection.account), attachmentContentValues)
        }
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
        this.description = newData.description
        this.uid = newData.uid

        this.location = newData.location
        this.geoLat = newData.geoLat
        this.geoLong = newData.geoLong
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
        this.comments = newData.comments
        this.relatedTo = newData.relatedTo
        this.attendees = newData.attendees
        this.attachments = newData.attachments
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
        values.put(X5ICalObject.GEO_LAT, geoLat)
        values.put(X5ICalObject.GEO_LONG, geoLong)
        values.put(X5ICalObject.LOCATION, location)
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

    fun getCommentContentValues(): List<ContentValues> {

        val commentUrl = NotesX5Contract.X5Comment.CONTENT_URI.asSyncAdapter(collection.account)
        val commentValues: MutableList<ContentValues> = mutableListOf()
        collection.client.query(
            commentUrl,
            null,
            "${NotesX5Contract.X5Comment.ICALOBJECT_ID} = ?",
            arrayOf(this.id.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                commentValues.add(cursor.toValues())
            }
        }

        return commentValues
    }


    fun getRelatedToContentValues(): List<ContentValues> {

        val relatedToUrl = NotesX5Contract.X5Relatedto.CONTENT_URI.asSyncAdapter(collection.account)
        val relatedToValues: MutableList<ContentValues> = mutableListOf()
        collection.client.query(
            relatedToUrl,
            null,
            "${NotesX5Contract.X5Relatedto.ICALOBJECT_ID} = ?",
            arrayOf(this.id.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                relatedToValues.add(cursor.toValues())
            }
        }

        return relatedToValues
    }

    fun getAttendeesContentValues(): List<ContentValues> {

        val attendeesUrl = NotesX5Contract.X5Attendee.CONTENT_URI.asSyncAdapter(collection.account)
        val attendeesValues: MutableList<ContentValues> = mutableListOf()
        collection.client.query(
            attendeesUrl,
            null,
            "${NotesX5Contract.X5Attendee.ICALOBJECT_ID} = ?",
            arrayOf(this.id.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                attendeesValues.add(cursor.toValues())
            }
        }

        return attendeesValues
    }

    fun getAttachmentsContentValues(): List<ContentValues> {

        val attachmentsUrl = NotesX5Contract.X5Attachment.CONTENT_URI.asSyncAdapter(collection.account)
        val attachmentsValues: MutableList<ContentValues> = mutableListOf()
        collection.client.query(
            attachmentsUrl,
            null,
            "${NotesX5Contract.X5Attachment.ICALOBJECT_ID} = ?",
            arrayOf(this.id.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                attachmentsValues.add(cursor.toValues())
            }
        }

        return attachmentsValues
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
