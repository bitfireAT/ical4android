package at.bitfire.ical4android


import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import at.bitfire.ical4android.MiscUtils.CursorHelper.toValues
import at.bitfire.jtx.SyncContentProviderContract
import at.bitfire.jtx.SyncContentProviderContract.asSyncAdapter
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.FmtType
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.*
import org.apache.commons.io.IOUtils
import java.io.*
import java.net.URI
import java.net.URISyntaxException
import java.util.*


open class JtxICalObject(
    val collection: JtxCollection<JtxICalObject>
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
        "${System.currentTimeMillis()}-${UUID.randomUUID()}@at.techbee.jtx"                              //unique identifier, see https://tools.ietf.org/html/rfc5545#section-3.8.4.7

    var created: Long =
        System.currentTimeMillis()   // see https://tools.ietf.org/html/rfc5545#section-3.8.7.1
    var dtstamp: Long =
        System.currentTimeMillis()   // see https://tools.ietf.org/html/rfc5545#section-3.8.7.2
    var lastModified: Long =
        System.currentTimeMillis()   // see https://tools.ietf.org/html/rfc5545#section-3.8.7.3
    var sequence: Long =
        0           // increase on every change (+1), see https://tools.ietf.org/html/rfc5545#section-3.8.7.4

    var color: Int? = null

    var rrule: String? = null    //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5.3
    var exdate: String? = null   //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5.1
    var rdate: String? = null    //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5.2
    //var recurid: String? = null  //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5

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
        var binary: String? = null,
        var fmttype: String? = null,
        var other: String? = null,
        var filename: String? = null,
        var extension: String? = null,
        var filesize: Long? = null
    )

    data class Comment(
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
        var cutype: String? = SyncContentProviderContract.JtxAttendee.Cutype.INDIVIDUAL.name,
        var member: String? = null,
        var role: String? = SyncContentProviderContract.JtxAttendee.Role.`REQ-PARTICIPANT`.name,
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
         * and extracts the VTODOs and/or VJOURNALS.
         *
         * @param reader where the iCalendar is taken from
         *
         * @return array of filled [JtxICalObject] data objects (may have size 0)
         *
         * @throws ParserException when the iCalendar can't be parsed
         * @throws IllegalArgumentException when the iCalendar resource contains an invalid value
         * @throws IOException on I/O errors
         */
        @UsesThreadContextClassLoader
        fun fromReader(
            reader: Reader,
            collection: JtxCollection<JtxICalObject>
        ): List<JtxICalObject> {
            val ical = ICalendar.fromReader(reader)
            val vToDos = ical.getComponents<VToDo>(Component.VTODO)
            val vJournals = ical.getComponents<VJournal>(Component.VJOURNAL)

            val iCalObjectList = mutableListOf<JtxICalObject>()

            // extract vToDos if available
            vToDos.forEach {
                val t = JtxICalObject(collection)
                t.component = SyncContentProviderContract.JtxICalObject.Component.VTODO.name

                if (it.uid != null)
                    t.uid = it.uid.value
                else {
                    Ical4Android.log.warning("Received VTODO without UID, generating new one")
                    t.uid = UUID.randomUUID().toString()
                }

                extractProperties(t, it.properties)
                iCalObjectList.add(t)
            }

            // extract vJournals if available
            vJournals.forEach {
                val j = JtxICalObject(collection)
                j.component = SyncContentProviderContract.JtxICalObject.Component.VJOURNAL.name

                if (it.uid != null)
                    j.uid = it.uid.value
                else {
                    Ical4Android.log.warning("Received VJOURNAL without UID, generating new one")
                    j.uid = UUID.randomUUID().toString()
                }

                extractProperties(j, it.properties)
                iCalObjectList.add(j)
            }

            return iCalObjectList
        }


        private fun extractProperties(iCalObject: JtxICalObject, properties: PropertyList<*>) {

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
                        if (iCalObject.component == SyncContentProviderContract.JtxICalObject.Component.VTODO.name) {
                            iCalObject.completed = prop.date.time
                        } else
                            Ical4Android.log.warning("The property Completed is only supported for VTODO, this value is rejected.")
                    }

                    is Due -> {
                        if (iCalObject.component == SyncContentProviderContract.JtxICalObject.Component.VTODO.name) {
                            iCalObject.due = prop.date.time
                            when {
                                prop.date is DateTime && prop.timeZone != null -> iCalObject.dueTimezone =
                                    prop.timeZone.id
                                prop.date is DateTime && prop.timeZone == null -> iCalObject.dueTimezone =
                                    null                   // this comparison is kept on purpose as "prop.date is Date" did not work as expected.
                                else -> iCalObject.dueTimezone =
                                    "ALLDAY"     // prop.date is Date (and not DateTime), therefore it must be Allday
                            }
                        } else
                            Ical4Android.log.warning("The property Due is only supported for VTODO, this value is rejected.")
                    }

                    //is Duration -> t.duration = prop.duration.

                    is DtStart -> {
                        iCalObject.dtstart = prop.date.time
                        when {
                            prop.date is DateTime && prop.timeZone != null -> iCalObject.dtstartTimezone =
                                prop.timeZone.id
                            prop.date is DateTime && prop.timeZone == null -> iCalObject.dtstartTimezone =
                                null                   // this comparison is kept on purpose as "prop.date is Date" did not work as expected.
                            else -> iCalObject.dtstartTimezone =
                                "ALLDAY"     // prop.date is Date (and not DateTime), therefore it must be Allday
                        }
                        /* if(!prop.isUtc)
                            t.dtstartTimezone = prop.timeZone.displayName
                         */
                    }

                    is PercentComplete -> {
                        if (iCalObject.component == SyncContentProviderContract.JtxICalObject.Component.VTODO.name)
                            iCalObject.percent = prop.percentage
                        else
                            Ical4Android.log.warning("The property PercentComplete is only supported for VTODO, this value is rejected.")
                    }

                    is RRule -> iCalObject.rrule = prop.value
                    is RDate -> {
                        val rdateList = mutableListOf<Long>()
                        prop.dates.forEach {
                            rdateList.add(it.time)
                        }
                        iCalObject.rdate = rdateList.toTypedArray().joinToString(separator = ",")
                    }
                    is ExDate -> {
                        val exdateList = mutableListOf<Long>()
                        prop.dates.forEach {
                            exdateList.add(it.time)
                        }
                        iCalObject.exdate = exdateList.toTypedArray().joinToString(separator = ",")
                    }

                        is Categories ->
                        for (category in prop.categories)
                            iCalObject.categories.add(Category(text = category))

                        is net.fortuna.ical4j.model.property.Comment ->
                        iCalObject.comments.add(Comment(text = prop.value))

                        is Attach -> {
                        val attachment = Attachment()
                        prop.uri?.let { attachment.uri = it.toString() }
                        prop.binary?.let {
                            attachment.binary = Base64.encodeToString(it, Base64.DEFAULT)
                        }
                        prop.parameters?.getParameter<FmtType>(Parameter.FMTTYPE)?.let {
                            attachment.fmttype = it.value
                        }

                        if (attachment.uri?.isNotEmpty() == true || attachment.binary?.isNotEmpty() == true)   // either uri or value must be present!
                            iCalObject.attachments.add(attachment)
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


    @UsesThreadContextClassLoader
    fun write(os: OutputStream, context: Context) {
        Ical4Android.checkThreadContextClassLoader()

        val ical = Calendar()
        ical.properties += Version.VERSION_2_0
        ical.properties += ICalendar.prodId

        if (component == SyncContentProviderContract.JtxICalObject.Component.VTODO.name) {
            val vTodo = VToDo(true /* generates DTSTAMP */)
            ical.components += vTodo
            val props = vTodo.properties
            addProperties(props, context)
        } else if (component == SyncContentProviderContract.JtxICalObject.Component.VJOURNAL.name) {
            val vJournal = VJournal(true /* generates DTSTAMP */)
            ical.components += vJournal
            val props = vJournal.properties
            addProperties(props, context)
        }


        ICalendar.softValidate(ical)
        CalendarOutputter(false).output(ical, os)

    }


    private fun addProperties(props: PropertyList<Property>, context: Context) {

        uid.let { props += Uid(it) }
        sequence.let { props += Sequence(it.toInt()) }

        created.let { props += Created(DateTime(it)) }
        lastModified.let { props += LastModified(DateTime(it)) }

        summary?.let { props += Summary(it) }
        description?.let { props += Description(it) }

        location?.let { props += Location(it) }
        if (geoLat != null && geoLong != null)
            props += Geo(geoLat!!.toBigDecimal(), geoLong!!.toBigDecimal())
        color?.let { props += Color(null, Css3Color.nearestMatch(it).name) }
        url?.let {
            try {
                props += Url(URI(it))
            } catch (e: URISyntaxException) {
                Log.w("ical4j processing", "Ignoring invalid task URL: $url", e)
            }
        }
        //organizer?.let { props += it }


        classification.let { props += Clazz(it) }
        status.let { props += Status(it) }


        val categoryTextList = TextList()
        categories.forEach {
            categoryTextList.add(it.text)
        }
        if (!categoryTextList.isEmpty)
            props += Categories(categoryTextList)


        comments.forEach {
            props += Comment(it.text)
        }

        attendees.forEach {
            props += Attendee(it.caladdress)
            //todo: take care of other attributes for attendees
        }

        attachments.forEach {
            if (it.uri?.isNotEmpty() == true)
                context.contentResolver.openInputStream(Uri.parse(URI(it.uri).toString()))
                    .use { file ->
                        val att = Attach(IOUtils.toByteArray(file))
                        att.parameters.add(FmtType(it.fmttype))
                        props += att
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
    props.addAll(unknownProperties)

    // remember used time zones
    val usedTimeZones = HashSet<TimeZone>()
    duration?.let(props::add)
    */

        /*
    rRule?.let { props += it }
    rDates.forEach { props += it }
    exDates.forEach { props += it }
*/


        dtstart?.let {
            when {
                dtstartTimezone == "ALLDAY" -> props += DtStart(Date(it))
                dtstartTimezone.isNullOrEmpty() -> props += DtStart(DateTime(it))
                else -> {
                    val timezone = TimeZoneRegistryFactory.getInstance().createRegistry()
                        .getTimeZone(dtstartTimezone)
                    val withTimezone = DtStart(DateTime(it))
                    withTimezone.timeZone = timezone
                    props += withTimezone
                }
            }
        }

        rrule?.let { rrule ->
            props += RRule(rrule)
        }

        // exdate and rdate for VJOURNAL (based on dtStart)
        if (component == SyncContentProviderContract.JtxICalObject.Component.VJOURNAL.name) {
                rdate?.let { rdateString ->

                    when {
                        dtstartTimezone == "ALLDAY" -> {
                            val dateListDate = DateList(Value.DATE)
                            getLongListFromString(rdateString).forEach {
                                dateListDate.add(Date(it))
                            }
                            props += RDate(dateListDate)

                        }
                        dtstartTimezone.isNullOrEmpty() -> {
                            val dateListDateTime = DateList(Value.DATE_TIME)
                            getLongListFromString(rdateString).forEach {
                                dateListDateTime.add(DateTime(it))
                            }
                            props += RDate(dateListDateTime)
                        }
                        else -> {
                            val dateListDateTime = DateList(Value.DATE_TIME)
                            val timezone = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone(dtstartTimezone)
                            getLongListFromString(rdateString).forEach {
                                val withTimezone = DateTime(it)
                                withTimezone.timeZone = timezone
                                dateListDateTime.add(DateTime(withTimezone))
                            }
                            props += RDate(dateListDateTime)
                        }
                    }
                }

                exdate?.let { exdateString ->

                    when {
                        dtstartTimezone == "ALLDAY" -> {
                            val dateListDate = DateList(Value.DATE)
                            getLongListFromString(exdateString).forEach {
                                dateListDate.add(Date(it))
                            }
                            props += ExDate(dateListDate)

                        }
                        dtstartTimezone.isNullOrEmpty() -> {
                            val dateListDateTime = DateList(Value.DATE_TIME)
                            getLongListFromString(exdateString).forEach {
                                dateListDateTime.add(DateTime(it))
                            }
                            props += ExDate(dateListDateTime)
                        }
                        else -> {
                            val dateListDateTime = DateList(Value.DATE_TIME)
                            val timezone = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone(dtstartTimezone)
                            getLongListFromString(exdateString).forEach {
                                val withTimezone = DateTime(it)
                                withTimezone.timeZone = timezone
                                dateListDateTime.add(DateTime(withTimezone))
                            }
                            props += ExDate(dateListDateTime)
                        }
                    }
                }
            }



        // exdate and rdate for VTODO (based on Due Date)
        if (component == SyncContentProviderContract.JtxICalObject.Component.VTODO.name) {

            rdate?.let { rdateString ->

                when {
                    dueTimezone == "ALLDAY" -> {
                        val dateListDate = DateList(Value.DATE)
                        getLongListFromString(rdateString).forEach {
                            dateListDate.add(Date(it))
                        }
                        props += RDate(dateListDate)

                    }
                    dueTimezone.isNullOrEmpty() -> {
                        val dateListDateTime = DateList(Value.DATE_TIME)
                        getLongListFromString(rdateString).forEach {
                            dateListDateTime.add(DateTime(it))
                        }
                        props += RDate(dateListDateTime)
                    }
                    else -> {
                        val dateListDateTime = DateList(Value.DATE_TIME)
                        val timezone = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone(dueTimezone)
                        getLongListFromString(rdateString).forEach {
                            val withTimezone = DateTime(it)
                            withTimezone.timeZone = timezone
                            dateListDateTime.add(DateTime(withTimezone))
                        }
                        props += RDate(dateListDateTime)
                    }
                }
            }

            exdate?.let { exdateString ->

                when {
                    dueTimezone == "ALLDAY" -> {
                        val dateListDate = DateList(Value.DATE)
                        getLongListFromString(exdateString).forEach {
                            dateListDate.add(Date(it))
                        }
                        props += ExDate(dateListDate)

                    }
                    dueTimezone.isNullOrEmpty() -> {
                        val dateListDateTime = DateList(Value.DATE_TIME)
                        getLongListFromString(exdateString).forEach {
                            dateListDateTime.add(DateTime(it))
                        }
                        props += ExDate(dateListDateTime)
                    }
                    else -> {
                        val dateListDateTime = DateList(Value.DATE_TIME)
                        val timezone = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone(dueTimezone)
                        getLongListFromString(exdateString).forEach {
                            val withTimezone = DateTime(it)
                            withTimezone.timeZone = timezone
                            dateListDateTime.add(DateTime(withTimezone))
                        }
                        props += ExDate(dateListDateTime)
                    }
                }
            }
        }

        // Attributes only for VTODOs
        if (component == SyncContentProviderContract.JtxICalObject.Component.VTODO.name) {
            dtend?.let {
                when {
                    dtendTimezone == "ALLDAY" -> props += DtEnd(Date(it))
                    dtendTimezone.isNullOrEmpty() -> props += DtEnd(DateTime(it))
                    else -> {
                        val timezone = TimeZoneRegistryFactory.getInstance().createRegistry()
                            .getTimeZone(dtendTimezone)
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


            if (priority != Priority.UNDEFINED.level)
                priority?.let { props += Priority(priority!!) }

            due?.let {
                when {
                    dueTimezone == "ALLDAY" -> props += Due(Date(it))
                    dueTimezone.isNullOrEmpty() -> props += Due(DateTime(it))
                    else -> {
                        val timezone = TimeZoneRegistryFactory.getInstance().createRegistry()
                            .getTimeZone(dueTimezone)
                        val withTimezone = Due(DateTime(it))
                        withTimezone.timeZone = timezone
                        props += withTimezone
                    }
                }
            }
        }

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


    }


    fun prepareForUpload(): String {
        return "${this.uid}.ics"
    }

    fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {

        var updateUri = SyncContentProviderContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())

        val values = ContentValues()
        fileName?.let { values.put(SyncContentProviderContract.JtxICalObject.FILENAME, fileName) }
        eTag?.let { values.put(SyncContentProviderContract.JtxICalObject.ETAG, eTag) }
        scheduleTag?.let { values.put(SyncContentProviderContract.JtxICalObject.SCHEDULETAG, scheduleTag) }
        values.put(SyncContentProviderContract.JtxICalObject.DIRTY, false)

        collection.client.update(updateUri, values, null, null)
    }

    fun updateFlags(flags: Int) {

        var updateUri = SyncContentProviderContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())

        val values = ContentValues()
        values.put(SyncContentProviderContract.JtxICalObject.FLAGS, flags)

        collection.client.update(updateUri, values, null, null)
    }

    fun add(): Uri {

        val values = this.toContentValues()

        Log.d("Calling add", "Lets see what happens")
        val newUri = collection.client.insert(
            SyncContentProviderContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account),
            values
        ) ?: return Uri.EMPTY
        this.id = newUri.lastPathSegment?.toLong() ?: return Uri.EMPTY

        insertOrUpdateListProperties(false)

        return newUri
        //TODO("Not yet implemented")

    }

    fun update(data: JtxICalObject): Uri {

        this.applyNewData(data)
        val values = this.toContentValues()

        var updateUri = SyncContentProviderContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())
        collection.client.update(
            updateUri,
            values,
            "${SyncContentProviderContract.JtxICalObject.ID} = ?",
            arrayOf(this.id.toString())
        )

        insertOrUpdateListProperties(true)

        return updateUri

        //TODO("Not yet implemented")
    }


    fun insertOrUpdateListProperties(isUpdate: Boolean) {

        // delete the categories, attendees, ... and insert them again after. Only relevant for Update, for an insert there will be no entries
        if (isUpdate) {
            collection.client.delete(
                SyncContentProviderContract.JtxCategory.CONTENT_URI.asSyncAdapter(collection.account),
                "${SyncContentProviderContract.JtxCategory.ICALOBJECT_ID} = ?",
                arrayOf(this.id.toString())
            )

            collection.client.delete(
                SyncContentProviderContract.JtxComment.CONTENT_URI.asSyncAdapter(collection.account),
                "${SyncContentProviderContract.JtxComment.ICALOBJECT_ID} = ?",
                arrayOf(this.id.toString())
            )

            collection.client.delete(
                SyncContentProviderContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(collection.account),
                "${SyncContentProviderContract.JtxRelatedto.ICALOBJECT_ID} = ?",
                arrayOf(this.id.toString())
            )

            collection.client.delete(
                SyncContentProviderContract.JtxAttendee.CONTENT_URI.asSyncAdapter(collection.account),
                "${SyncContentProviderContract.JtxAttendee.ICALOBJECT_ID} = ?",
                arrayOf(this.id.toString())
            )

            collection.client.delete(
                SyncContentProviderContract.JtxAttachment.CONTENT_URI.asSyncAdapter(collection.account),
                "${SyncContentProviderContract.JtxAttachment.ICALOBJECT_ID} = ?",
                arrayOf(this.id.toString())
            )
        }

        this.categories.forEach {
            val categoryContentValues = ContentValues().apply {
                put(SyncContentProviderContract.JtxCategory.ICALOBJECT_ID, id)
                put(SyncContentProviderContract.JtxCategory.TEXT, it.text)
                put(SyncContentProviderContract.JtxCategory.ID, it.categoryId)
                put(SyncContentProviderContract.JtxCategory.LANGUAGE, it.language)
                put(SyncContentProviderContract.JtxCategory.OTHER, it.other)
            }
            collection.client.insert(
                SyncContentProviderContract.JtxCategory.CONTENT_URI.asSyncAdapter(collection.account),
                categoryContentValues
            )
        }

        this.comments.forEach {
            val commentContentValues = ContentValues().apply {
                put(SyncContentProviderContract.JtxComment.ICALOBJECT_ID, id)
                put(SyncContentProviderContract.JtxComment.TEXT, it.text)
                put(SyncContentProviderContract.JtxComment.ID, it.commentId)
                put(SyncContentProviderContract.JtxComment.LANGUAGE, it.language)
                put(SyncContentProviderContract.JtxComment.OTHER, it.other)
            }
            collection.client.insert(
                SyncContentProviderContract.JtxComment.CONTENT_URI.asSyncAdapter(collection.account),
                commentContentValues
            )
        }


        this.relatedTo.forEach {
            val relatedToContentValues = ContentValues().apply {
                put(SyncContentProviderContract.JtxRelatedto.ICALOBJECT_ID, id)
                put(SyncContentProviderContract.JtxRelatedto.TEXT, it.text)
                put(SyncContentProviderContract.JtxRelatedto.RELTYPE, it.reltype)
                put(SyncContentProviderContract.JtxRelatedto.OTHER, it.other)
            }
            collection.client.insert(
                SyncContentProviderContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(collection.account),
                relatedToContentValues
            )
        }

        this.attendees.forEach {
            val attendeeContentValues = ContentValues().apply {
                put(SyncContentProviderContract.JtxAttendee.ICALOBJECT_ID, id)
                put(SyncContentProviderContract.JtxAttendee.CALADDRESS, it.caladdress)

                put(SyncContentProviderContract.JtxAttendee.CN, it.cn)
                put(SyncContentProviderContract.JtxAttendee.CUTYPE, it.cutype)
                put(SyncContentProviderContract.JtxAttendee.DELEGATEDFROM, it.delegatedfrom)
                put(SyncContentProviderContract.JtxAttendee.DELEGATEDTO, it.delegatedto)
                put(SyncContentProviderContract.JtxAttendee.DIR, it.dir)
                put(SyncContentProviderContract.JtxAttendee.LANGUAGE, it.language)
                put(SyncContentProviderContract.JtxAttendee.MEMBER, it.member)
                put(SyncContentProviderContract.JtxAttendee.PARTSTAT, it.partstat)
                put(SyncContentProviderContract.JtxAttendee.ROLE, it.role)
                put(SyncContentProviderContract.JtxAttendee.RSVP, it.rsvp)
                put(SyncContentProviderContract.JtxAttendee.SENTBY, it.sentby)
                put(SyncContentProviderContract.JtxAttendee.OTHER, it.other)
            }
            collection.client.insert(
                SyncContentProviderContract.JtxAttendee.CONTENT_URI.asSyncAdapter(
                    collection.account
                ), attendeeContentValues
            )
        }

        this.attachments.forEach {
            val attachmentContentValues = ContentValues().apply {
                put(SyncContentProviderContract.JtxAttachment.ICALOBJECT_ID, id)
                put(SyncContentProviderContract.JtxAttachment.URI, it.uri)
                put(SyncContentProviderContract.JtxAttachment.BINARY, it.binary)
                put(SyncContentProviderContract.JtxAttachment.FMTTYPE, it.fmttype)
                put(SyncContentProviderContract.JtxAttachment.OTHER, it.other)
            }
            collection.client.insert(
                SyncContentProviderContract.JtxAttachment.CONTENT_URI.asSyncAdapter(
                    collection.account
                ), attachmentContentValues
            )
        }
    }


    fun delete(): Int {
        val uri = Uri.withAppendedPath(
            SyncContentProviderContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account),
            id.toString()
        )
        return collection.client.delete(uri, null, null)
    }


    fun applyNewData(newData: JtxICalObject) {

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

        this.rrule = newData.rrule
        this.rdate = newData.rdate
        this.exdate = newData.exdate

        this.categories = newData.categories
        this.comments = newData.comments
        this.relatedTo = newData.relatedTo
        this.attendees = newData.attendees
        this.attachments = newData.attachments
        // tODO: to be continued
    }

    fun toContentValues(): ContentValues {

        val values = ContentValues()
        values.put(SyncContentProviderContract.JtxICalObject.ID, id)
        values.put(SyncContentProviderContract.JtxICalObject.SUMMARY, summary)
        values.put(SyncContentProviderContract.JtxICalObject.DESCRIPTION, description)
        values.put(SyncContentProviderContract.JtxICalObject.COMPONENT, component)
        if (status?.isNotBlank() == true)
            values.put(SyncContentProviderContract.JtxICalObject.STATUS, status)
        if (classification?.isNotBlank() == true)
            values.put(SyncContentProviderContract.JtxICalObject.CLASSIFICATION, classification)
        values.put(SyncContentProviderContract.JtxICalObject.PRIORITY, priority)
        values.put(SyncContentProviderContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collectionId)
        values.put(SyncContentProviderContract.JtxICalObject.UID, uid)
        values.put(SyncContentProviderContract.JtxICalObject.GEO_LAT, geoLat)
        values.put(SyncContentProviderContract.JtxICalObject.GEO_LONG, geoLong)
        values.put(SyncContentProviderContract.JtxICalObject.LOCATION, location)
        values.put(SyncContentProviderContract.JtxICalObject.PERCENT, percent)
        values.put(SyncContentProviderContract.JtxICalObject.DTSTAMP, dtstamp)
        values.put(SyncContentProviderContract.JtxICalObject.DTSTART, dtstart)
        values.put(SyncContentProviderContract.JtxICalObject.DTSTART_TIMEZONE, dtstartTimezone)
        values.put(SyncContentProviderContract.JtxICalObject.DTEND, dtend)
        values.put(SyncContentProviderContract.JtxICalObject.DTEND_TIMEZONE, dtendTimezone)
        values.put(SyncContentProviderContract.JtxICalObject.COMPLETED, completed)
        values.put(SyncContentProviderContract.JtxICalObject.COMPLETED_TIMEZONE, completedTimezone)
        values.put(SyncContentProviderContract.JtxICalObject.DUE, due)
        values.put(SyncContentProviderContract.JtxICalObject.DUE_TIMEZONE, dueTimezone)

        values.put(SyncContentProviderContract.JtxICalObject.RRULE, rrule)
        values.put(SyncContentProviderContract.JtxICalObject.RDATE, rdate)
        values.put(SyncContentProviderContract.JtxICalObject.EXDATE, exdate)

        values.put(SyncContentProviderContract.JtxICalObject.FILENAME, fileName)
        values.put(SyncContentProviderContract.JtxICalObject.ETAG, eTag)
        values.put(SyncContentProviderContract.JtxICalObject.SCHEDULETAG, scheduleTag)
        values.put(SyncContentProviderContract.JtxICalObject.FLAGS, flags)

        return values
    }


    fun getCategoryContentValues(): List<ContentValues> {

        val categoryUrl = SyncContentProviderContract.JtxCategory.CONTENT_URI.asSyncAdapter(collection.account)
        val categoryValues: MutableList<ContentValues> = mutableListOf()
        collection.client.query(
            categoryUrl,
            null,
            "${SyncContentProviderContract.JtxCategory.ICALOBJECT_ID} = ?",
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

        val commentUrl = SyncContentProviderContract.JtxComment.CONTENT_URI.asSyncAdapter(collection.account)
        val commentValues: MutableList<ContentValues> = mutableListOf()
        collection.client.query(
            commentUrl,
            null,
            "${SyncContentProviderContract.JtxComment.ICALOBJECT_ID} = ?",
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

        val relatedToUrl = SyncContentProviderContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(collection.account)
        val relatedToValues: MutableList<ContentValues> = mutableListOf()
        collection.client.query(
            relatedToUrl,
            null,
            "${SyncContentProviderContract.JtxRelatedto.ICALOBJECT_ID} = ?",
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

        val attendeesUrl = SyncContentProviderContract.JtxAttendee.CONTENT_URI.asSyncAdapter(collection.account)
        val attendeesValues: MutableList<ContentValues> = mutableListOf()
        collection.client.query(
            attendeesUrl,
            null,
            "${SyncContentProviderContract.JtxAttendee.ICALOBJECT_ID} = ?",
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

        val attachmentsUrl =
            SyncContentProviderContract.JtxAttachment.CONTENT_URI.asSyncAdapter(collection.account)
        val attachmentsValues: MutableList<ContentValues> = mutableListOf()
        collection.client.query(
            attachmentsUrl,
            null,
            "${SyncContentProviderContract.JtxAttachment.ICALOBJECT_ID} = ?",
            arrayOf(this.id.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                attachmentsValues.add(cursor.toValues())
            }
        }

        return attachmentsValues
    }

    fun getLongListFromString(string: String): List<Long> {

        val stringList = string.split(",")
        val longList = mutableListOf<Long>()

        stringList.forEach {
            try {
                longList.add(it.toLong())
            } catch (e: NumberFormatException) {
                Log.w("getLongListFromString", "String could not be cast to Long ($it)")
                return@forEach
            }
        }
        return longList
    }
}

/*

class Notesx5Todo(account: Account, client: ContentProviderClient, collectionId: Long) :
    NotesSyncContentProviderContract.JtxICalObject(account, client, collectionId, SyncContentProviderContract.JtxICalObject.Component.TODO) {

    var status: SyncContentProviderContract.JtxICalObject.StatusTodo? = null

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

    var status: SyncContentProviderContract.JtxICalObject.StatusJournal? = null


}

 */
