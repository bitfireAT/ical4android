/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.content.ContentUris
import android.content.ContentValues
import android.net.ParseException
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import at.bitfire.ical4android.util.MiscUtils.CursorHelper.toValues
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.JtxICalObject.TZ_ALLDAY
import at.techbee.jtx.JtxContract.asSyncAdapter
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.DelegatedFrom
import net.fortuna.ical4j.model.parameter.DelegatedTo
import net.fortuna.ical4j.model.parameter.Dir
import net.fortuna.ical4j.model.parameter.FmtType
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.parameter.Member
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.parameter.SentBy
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.*
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.io.Reader
import java.net.URI
import java.net.URISyntaxException
import java.time.format.DateTimeParseException
import java.util.TimeZone
import java.util.UUID
import java.util.logging.Level

open class JtxICalObject(
    val collection: JtxCollection<JtxICalObject>
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
    var xstatus: String? = null

    var priority: Int? = null

    var due: Long? = null      // VTODO only!
    var dueTimezone: String? = null //VTODO only!
    var completed: Long? = null // VTODO only!
    var completedTimezone: String? = null //VTODO only!
    var duration: String? = null //VTODO only!

    var percent: Int? = null
    var url: String? = null
    var contact: String? = null
    var geoLat: Double? = null
    var geoLong: Double? = null
    var location: String? = null
    var locationAltrep: String? = null
    var geofenceRadius: Int? = null

    var uid: String = UUID.randomUUID().toString()

    var created: Long = System.currentTimeMillis()
    var dtstamp: Long = System.currentTimeMillis()
    var lastModified: Long = System.currentTimeMillis()
    var sequence: Long = 0

    var color: Int? = null

    var rrule: String? = null    //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5.3
    var exdate: String? = null   //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5.1
    var rdate: String? = null    //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5.2
    var recurid: String? = null  //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5
    var recuridTimezone: String? = null
    //var rstatus: String? = null

    var collectionId: Long = collection.id

    var dirty: Boolean = false     // default false to avoid instant sync after insert, can be overwritten
    var deleted: Boolean = false

    var fileName: String? = null
    var eTag: String? = null
    var scheduleTag: String? = null
    var flags: Int = 0

    var categories: MutableList<Category> = mutableListOf()
    var attachments: MutableList<Attachment> = mutableListOf()
    var attendees: MutableList<Attendee> = mutableListOf()
    var comments: MutableList<Comment> = mutableListOf()
    var organizer: Organizer? = null
    var resources: MutableList<Resource> = mutableListOf()
    var relatedTo: MutableList<RelatedTo> = mutableListOf()
    var alarms: MutableList<Alarm> = mutableListOf()
    var unknown: MutableList<Unknown> = mutableListOf()

    private var recurInstances: MutableList<JtxICalObject> = mutableListOf()




    data class Category(
        var categoryId: Long = 0L,
        var text: String? = null,
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
        var text: String? = null,
        var altrep: String? = null,
        var language: String? = null,
        var other: String? = null
    )

    data class RelatedTo(
        var relatedtoId: Long = 0L,
        var text: String? = null,
        var reltype: String? = null,
        var other: String? = null
    )

    data class Attendee(
        var attendeeId: Long = 0L,
        var caladdress: String? = null,
        var cutype: String? = JtxContract.JtxAttendee.Cutype.INDIVIDUAL.name,
        var member: String? = null,
        var role: String? = JtxContract.JtxAttendee.Role.`REQ-PARTICIPANT`.name,
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

    data class Resource(
        var resourceId: Long = 0L,
        var text: String? = null,
        var altrep: String? = null,
        var language: String? = null,
        var other: String? = null
    )

    data class Organizer(
        var organizerId: Long = 0L,
        var caladdress: String? = null,
        var cn: String? = null,
        var dir: String? = null,
        var sentby: String? = null,
        var language: String? = null,
        var other: String? = null
    )

    data class Alarm(
        var alarmId: Long = 0L,
        var action: String? = null,
        var description: String? = null,
        var summary: String? = null,
        var attendee: String? = null,
        var duration: String? = null,
        var repeat: String? = null,
        var attach: String? = null,
        var other: String? = null,
        var triggerTime: Long? = null,
        var triggerTimezone: String? = null,
        var triggerRelativeTo: String? = null,
        var triggerRelativeDuration: String? = null
    )

    data class Unknown(
        var unknownId: Long = 0L,
        var value: String? = null
    )


    companion object {

        const val X_PROP_COMPLETEDTIMEZONE = "X-COMPLETEDTIMEZONE"
        const val X_PARAM_ATTACH_LABEL = "X-LABEL"     // used for filename in KOrganizer
        const val X_PARAM_FILENAME = "FILENAME"     // used for filename in GNOME Evolution
        const val X_PROP_XSTATUS = "X-STATUS"   // used to define an extended status (additionally to standard status)
        const val X_PROP_GEOFENCE_RADIUS = "X-GEOFENCE-RADIUS"   // used to define a Geofence-Radius to notifiy the user when close

        /**
         * Parses an iCalendar resource and extracts the VTODOs and/or VJOURNALS.
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

            val iCalObjectList = mutableListOf<JtxICalObject>()

            ical.components.forEach { component ->

                val iCalObject = JtxICalObject(collection)
                when(component) {
                    is VToDo -> {
                        iCalObject.component = JtxContract.JtxICalObject.Component.VTODO.name
                        if (component.uid != null)
                            iCalObject.uid = component.uid.value                         // generated UID is overwritten here (if present)
                        extractProperties(iCalObject, component.properties)
                        extractVAlarms(iCalObject, component.components)                 // accessing the components needs an explicit type
                        iCalObjectList.add(iCalObject)
                    }
                    is VJournal -> {
                        iCalObject.component = JtxContract.JtxICalObject.Component.VJOURNAL.name
                        if (component.uid != null)
                            iCalObject.uid = component.uid.value
                        extractProperties(iCalObject, component.properties)
                        extractVAlarms(iCalObject, component.components)                  // accessing the components needs an explicit type
                        iCalObjectList.add(iCalObject)
                    }
                }
            }
            return iCalObjectList
        }

        /**
         * Extracts VAlarms from the given Component (VJOURNAL or VTODO). The VAlarm is supposed to be a component within the VJOURNAL or VTODO component.
         * Other components than VAlarms should not occur.
         * @param [iCalObject] where the VAlarms should be inserted
         * @param [calComponents] from which the VAlarms should be extracted
         */
        private fun extractVAlarms(iCalObject: JtxICalObject, calComponents: ComponentList<*>) {

            calComponents.forEach { component ->
                if(component is VAlarm) {
                    val jtxAlarm = Alarm().apply {
                        component.action?.value?.let { vAlarmAction -> this.action = vAlarmAction }
                        component.summary?.value?.let { vAlarmSummary -> this.summary = vAlarmSummary }
                        component.description?.value?.let { vAlarmDesc -> this.description = vAlarmDesc }
                        component.duration?.value?.let { vAlarmDur -> this.duration = vAlarmDur }
                        component.attachment?.uri?.let { uri -> this.attach = uri.toString() }
                        component.repeat?.value?.let { vAlarmRep -> this.repeat = vAlarmRep }

                        // alarms can have a duration or an absolute dateTime, but not both!
                        if(component.trigger.duration != null) {
                            component.trigger?.duration?.let { duration -> this.triggerRelativeDuration = duration.toString() }
                            component.trigger?.getParameter<Related>(Parameter.RELATED)?.let { related -> this.triggerRelativeTo = related.value }
                        } else if(component.trigger.dateTime != null) {
                            component.trigger?.dateTime?.let { dateTime -> this.triggerTime = dateTime.time  }
                            component.trigger?.dateTime?.timeZone?.let { timezone -> this.triggerTimezone = timezone.id }
                        }

                        // remove properties to add the rest to other
                        component.properties.removeAll(
                            setOf(
                                component.action,
                                component.summary,
                                component.description,
                                component.duration,
                                component.attachment,
                                component.repeat,
                                component.trigger
                            )
                        )
                        component.properties?.let { vAlarmProps -> this.other = JtxContract.getJsonStringFromXProperties(vAlarmProps) }
                    }
                    iCalObject.alarms.add(jtxAlarm)
                }
            }
        }

        /**
         * Extracts properties from a given Property list and maps it to a JtxICalObject
         * @param [iCalObject] where the properties should be mapped to
         * @param [properties] from which the properties can be extracted
         */
        private fun extractProperties(iCalObject: JtxICalObject, properties: PropertyList<*>) {

            // sequence must only be null for locally created, not-yet-synchronized events
            iCalObject.sequence = 0

            for (prop in properties) {
                when (prop) {
                    is Sequence -> iCalObject.sequence = prop.sequenceNo.toLong()
                    is Created -> iCalObject.created = prop.dateTime.time
                    is LastModified -> iCalObject.lastModified = prop.dateTime.time
                    is Summary -> iCalObject.summary = prop.value
                    is Location -> {
                        iCalObject.location = prop.value
                        if(!prop.parameters.isEmpty && prop.parameters.getParameter<AltRep>(Parameter.ALTREP) != null)
                            iCalObject.locationAltrep = prop.parameters.getParameter<AltRep>(Parameter.ALTREP).value
                    }
                    is Geo -> {
                        iCalObject.geoLat = prop.latitude.toDouble()
                        iCalObject.geoLong = prop.longitude.toDouble()
                    }
                    is Description -> iCalObject.description = prop.value
                    is Color -> iCalObject.color = Css3Color.fromString(prop.value)?.argb
                    is Url -> iCalObject.url = prop.value
                    is Contact -> iCalObject.contact = prop.value
                    is Priority -> iCalObject.priority = prop.level
                    is Clazz -> iCalObject.classification = prop.value
                    is Status -> iCalObject.status = prop.value
                    is DtEnd -> Ical4Android.log.warning("The property DtEnd must not be used for VTODO and VJOURNAL, this value is rejected.")
                    is Completed -> {
                        if (iCalObject.component == JtxContract.JtxICalObject.Component.VTODO.name) {
                            iCalObject.completed = prop.date.time
                        } else
                            Ical4Android.log.warning("The property Completed is only supported for VTODO, this value is rejected.")
                    }

                    is Due -> {
                        if (iCalObject.component == JtxContract.JtxICalObject.Component.VTODO.name) {
                            iCalObject.due = prop.date.time
                            when {
                                prop.date is DateTime && prop.timeZone != null -> iCalObject.dueTimezone = prop.timeZone.id
                                prop.date is DateTime && prop.isUtc -> iCalObject.dueTimezone = TimeZone.getTimeZone("UTC").id
                                prop.date is DateTime && !prop.isUtc && prop.timeZone == null -> iCalObject.dueTimezone = null                   // this comparison is kept on purpose as "prop.date is Date" did not work as expected.
                                else -> iCalObject.dueTimezone = TZ_ALLDAY     // prop.date is Date (and not DateTime), therefore it must be Allday
                            }
                        } else
                            Ical4Android.log.warning("The property Due is only supported for VTODO, this value is rejected.")
                    }

                    is Duration -> iCalObject.duration = prop.value

                    is DtStart -> {
                        iCalObject.dtstart = prop.date.time
                        when {
                            prop.date is DateTime && prop.timeZone != null -> iCalObject.dtstartTimezone = prop.timeZone.id
                            prop.date is DateTime && prop.isUtc -> iCalObject.dtstartTimezone = TimeZone.getTimeZone("UTC").id
                            prop.date is DateTime && !prop.isUtc && prop.timeZone == null -> iCalObject.dtstartTimezone = null                   // this comparison is kept on purpose as "prop.date is Date" did not work as expected.
                            else -> iCalObject.dtstartTimezone = TZ_ALLDAY     // prop.date is Date (and not DateTime), therefore it must be Allday
                        }
                    }

                    is PercentComplete -> {
                        if (iCalObject.component == JtxContract.JtxICalObject.Component.VTODO.name)
                            iCalObject.percent = prop.percentage
                        else
                            Ical4Android.log.warning("The property PercentComplete is only supported for VTODO, this value is rejected.")
                    }

                    is RRule -> iCalObject.rrule = prop.value
                    is RDate -> {
                        val rdateList = if(iCalObject.rdate.isNullOrEmpty())
                            mutableListOf()
                        else
                            JtxContract.getLongListFromString(iCalObject.rdate!!)
                        prop.dates.forEach {
                            rdateList.add(it.time)
                        }
                        iCalObject.rdate = rdateList.toTypedArray().joinToString(separator = ",")
                    }
                    is ExDate -> {
                        val exdateList = if(iCalObject.exdate.isNullOrEmpty())
                            mutableListOf()
                        else
                            JtxContract.getLongListFromString(iCalObject.exdate!!)
                        prop.dates.forEach {
                            exdateList.add(it.time)
                        }
                        iCalObject.exdate = exdateList.toTypedArray().joinToString(separator = ",")
                    }
                    is RecurrenceId -> {
                        iCalObject.recurid = prop.date.toString()
                        when {
                            prop.date is DateTime && prop.timeZone != null -> iCalObject.recuridTimezone = prop.timeZone.id
                            prop.date is DateTime && prop.isUtc -> iCalObject.recuridTimezone = TimeZone.getTimeZone("UTC").id
                            prop.date is DateTime && !prop.isUtc && prop.timeZone == null -> iCalObject.recuridTimezone = null
                            else -> iCalObject.recuridTimezone = TZ_ALLDAY     // prop.date is Date (and not DateTime), therefore it must be Allday
                        }
                    }

                    //is RequestStatus -> iCalObject.rstatus = prop.value

                    is Categories ->
                        for (category in prop.categories)
                            iCalObject.categories.add(Category(text = category))

                    is net.fortuna.ical4j.model.property.Comment -> {
                        iCalObject.comments.add(
                            Comment().apply {
                                this.text = prop.value
                                this.language = prop.parameters?.getParameter<Language>(Parameter.LANGUAGE)?.value
                                this.altrep = prop.parameters?.getParameter<AltRep>(Parameter.ALTREP)?.value

                                // remove the known parameter
                                prop.parameters?.removeAll(Parameter.LANGUAGE)
                                prop.parameters?.removeAll(Parameter.ALTREP)

                                // save unknown parameters in the other field
                                this.other = JtxContract.getJsonStringFromXParameters(prop.parameters)
                            })

                    }

                    is Resources ->
                        for (resource in prop.resources)
                            iCalObject.resources.add(Resource(text = resource))

                    is Attach -> {
                        val attachment = Attachment()
                        prop.uri?.let { attachment.uri = it.toString() }
                        prop.binary?.let {
                            attachment.binary = Base64.encodeToString(it, Base64.DEFAULT)
                        }
                        prop.parameters?.getParameter<FmtType>(Parameter.FMTTYPE)?.let {
                            attachment.fmttype = it.value
                            prop.parameters?.remove(it)
                        }
                        prop.parameters?.getParameter<XParameter>(X_PARAM_ATTACH_LABEL)?.let {
                            attachment.filename = it.value
                            prop.parameters.remove(it)
                        }
                        prop.parameters?.getParameter<XParameter>(X_PARAM_FILENAME)?.let {
                            attachment.filename = it.value
                            prop.parameters.remove(it)
                        }

                        attachment.other = JtxContract.getJsonStringFromXParameters(prop.parameters)

                        if (attachment.uri?.isNotEmpty() == true || attachment.binary?.isNotEmpty() == true)   // either uri or value must be present!
                            iCalObject.attachments.add(attachment)
                    }

                    is net.fortuna.ical4j.model.property.RelatedTo -> {

                        iCalObject.relatedTo.add(
                            RelatedTo().apply {
                                this.text = prop.value
                                this.reltype = prop.getParameter<RelType>(RelType.RELTYPE)?.value ?: JtxContract.JtxRelatedto.Reltype.PARENT.name

                                // remove the known parameter
                                prop.parameters?.removeAll(RelType.RELTYPE)

                                // save unknown parameters in the other field
                                this.other = JtxContract.getJsonStringFromXParameters(prop.parameters)
                            })
                    }

                    is net.fortuna.ical4j.model.property.Attendee -> {
                        iCalObject.attendees.add(
                            Attendee().apply {
                                this.caladdress = prop.calAddress.toString()
                                this.cn = prop.parameters?.getParameter<Cn>(Parameter.CN)?.value
                                this.delegatedto = prop.parameters?.getParameter<DelegatedTo>(Parameter.DELEGATED_TO)?.value
                                this.delegatedfrom = prop.parameters?.getParameter<DelegatedFrom>(Parameter.DELEGATED_FROM)?.value
                                this.cutype = prop.parameters?.getParameter<CuType>(Parameter.CUTYPE)?.value
                                this.dir = prop.parameters?.getParameter<Dir>(Parameter.DIR)?.value
                                this.language = prop.parameters?.getParameter<Language>(Parameter.LANGUAGE)?.value
                                this.member = prop.parameters?.getParameter<Member>(Parameter.MEMBER)?.value
                                this.partstat = prop.parameters?.getParameter<PartStat>(Parameter.PARTSTAT)?.value
                                this.role = prop.parameters?.getParameter<Role>(Parameter.ROLE)?.value
                                this.rsvp = prop.parameters?.getParameter<Rsvp>(Parameter.RSVP)?.value?.toBoolean()
                                this.sentby = prop.parameters?.getParameter<SentBy>(Parameter.SENT_BY)?.value

                                // remove all known parameters so that only unknown parameters remain
                                prop.parameters?.removeAll(Parameter.CN)
                                prop.parameters?.removeAll(Parameter.DELEGATED_TO)
                                prop.parameters?.removeAll(Parameter.DELEGATED_FROM)
                                prop.parameters?.removeAll(Parameter.CUTYPE)
                                prop.parameters?.removeAll(Parameter.DIR)
                                prop.parameters?.removeAll(Parameter.LANGUAGE)
                                prop.parameters?.removeAll(Parameter.MEMBER)
                                prop.parameters?.removeAll(Parameter.PARTSTAT)
                                prop.parameters?.removeAll(Parameter.ROLE)
                                prop.parameters?.removeAll(Parameter.RSVP)
                                prop.parameters?.removeAll(Parameter.SENT_BY)

                                // save unknown parameters in the other field
                                this.other = JtxContract.getJsonStringFromXParameters(prop.parameters)
                            }
                        )
                    }
                    is net.fortuna.ical4j.model.property.Organizer -> {
                    iCalObject.organizer = Organizer().apply {
                        this.caladdress = prop.calAddress.toString()
                        this.cn = prop.parameters?.getParameter<Cn>(Parameter.CN)?.value
                        this.dir = prop.parameters?.getParameter<Dir>(Parameter.DIR)?.value
                        this.language = prop.parameters?.getParameter<Language>(Parameter.LANGUAGE)?.value
                        this.sentby = prop.parameters?.getParameter<SentBy>(Parameter.SENT_BY)?.value

                        // remove all known parameters so that only unknown parameters remain
                        prop.parameters?.removeAll(Parameter.CN)
                        prop.parameters?.removeAll(Parameter.DIR)
                        prop.parameters?.removeAll(Parameter.LANGUAGE)
                        prop.parameters?.removeAll(Parameter.SENT_BY)

                        // save unknown parameters in the other field
                        this.other = JtxContract.getJsonStringFromXParameters(prop.parameters)
                        }
                    }

                    is Uid -> iCalObject.uid = prop.value
                    //is Uid,
                    is ProdId, is DtStamp -> {
                    }    /* don't save these as unknown properties */
                    else -> when(prop.name) {
                        X_PROP_COMPLETEDTIMEZONE -> iCalObject.completedTimezone = prop.value
                        X_PROP_XSTATUS -> iCalObject.xstatus = prop.value
                        X_PROP_GEOFENCE_RADIUS -> iCalObject.geofenceRadius = try { prop.value.toInt() } catch (e: NumberFormatException) { Ical4Android.log.warning("Wrong format for geofenceRadius: ${prop.value}"); null }
                        else -> iCalObject.unknown.add(Unknown(value = UnknownProperty.toJsonString(prop)))               // save the whole property for unknown properties
                    }
                }
            }


            // There seem to be many invalid tasks out there because of some defect clients, do some validation.
            val dtStartTZ = iCalObject.dtstartTimezone
            val dueTZ = iCalObject.dueTimezone

            if (dtStartTZ != null && dueTZ != null) {
                if (dtStartTZ == TZ_ALLDAY && dueTZ != TZ_ALLDAY) {
                    Ical4Android.log.warning("DTSTART is DATE but DUE is DATE-TIME, rewriting DTSTART to DATE-TIME")
                    iCalObject.dtstartTimezone = dueTZ
                } else if (dtStartTZ != TZ_ALLDAY && dueTZ == TZ_ALLDAY) {
                    Ical4Android.log.warning("DTSTART is DATE-TIME but DUE is DATE, rewriting DUE to DATE-TIME")
                    iCalObject.dueTimezone = dtStartTZ
                }

                //previously due was dropped, now reduced to a warning, see also https://github.com/bitfireAT/ical4android/issues/70
                if ( iCalObject.dtstart != null && iCalObject.due != null && iCalObject.due!! < iCalObject.dtstart!!)
                    Ical4Android.log.warning("Found invalid DUE < DTSTART")
            }

            if (iCalObject.duration != null && iCalObject.dtstart == null) {
                Ical4Android.log.warning("Found DURATION without DTSTART; ignoring")
                iCalObject.duration = null
            }
        }
    }

    /**
     * Takes the current JtxICalObject and transforms it to a Calendar (ical4j)
     * @return The current JtxICalObject transformed into a ical4j Calendar
     */
    @UsesThreadContextClassLoader
    fun getICalendarFormat(): Calendar? {
        Ical4Android.checkThreadContextClassLoader()

        val ical = Calendar()
        ical.properties += Version.VERSION_2_0
        ical.properties += ICalendar.prodId(listOf(TaskProvider.ProviderName.JtxBoard.packageName))

        val calComponent = when (component) {
            JtxContract.JtxICalObject.Component.VTODO.name -> VToDo(true /* generates DTSTAMP */)
            JtxContract.JtxICalObject.Component.VJOURNAL.name -> VJournal(true /* generates DTSTAMP */)
            else -> return null
        }
        ical.components += calComponent
        addProperties(calComponent.properties)

        alarms.forEach { alarm ->

            val vAlarm = VAlarm()
            vAlarm.properties.apply {
                alarm.action?.let {
                    when (it) {
                        JtxContract.JtxAlarm.AlarmAction.DISPLAY.name -> add(Action.DISPLAY)
                        JtxContract.JtxAlarm.AlarmAction.AUDIO.name -> add(Action.AUDIO)
                        JtxContract.JtxAlarm.AlarmAction.EMAIL.name -> add(Action.EMAIL)
                        else -> return@let
                    }
                }
                if(alarm.triggerRelativeDuration != null) {
                    add(Trigger().apply {
                        try {
                            val dur = java.time.Duration.parse(alarm.triggerRelativeDuration)
                            this.duration = dur

                            // Add the RELATED parameter if present
                            alarm.triggerRelativeTo?.let {
                                if(it == JtxContract.JtxAlarm.AlarmRelativeTo.START.name)
                                    this.parameters.add(Related.START)
                                if(it == JtxContract.JtxAlarm.AlarmRelativeTo.END.name)
                                    this.parameters.add(Related.END)
                            }
                        } catch (e: DateTimeParseException) {
                            Ical4Android.log.log(Level.WARNING, "Could not parse Trigger duration as Duration.", e)
                        }
                    })

                } else if (alarm.triggerTime != null) {
                    add(Trigger().apply {
                        try {
                            when {
                                alarm.triggerTimezone == TimeZone.getTimeZone("UTC").id -> this.dateTime = DateTime(alarm.triggerTime!!).apply {
                                    this.isUtc = true
                                }
                                alarm.triggerTimezone.isNullOrEmpty() -> this.dateTime = DateTime(alarm.triggerTime!!).apply {
                                    this.isUtc = true
                                }
                                else -> {
                                    val timezone = TimeZoneRegistryFactory.getInstance().createRegistry()
                                        .getTimeZone(alarm.triggerTimezone)
                                    this.dateTime = DateTime(alarm.triggerTime!!).apply{
                                        this.timeZone = timezone
                                    }
                                }
                            }
                        } catch (e: ParseException) {
                            Ical4Android.log.log(Level.WARNING, "TriggerTime could not be parsed.", e)
                        }})
                }
                alarm.summary?.let { add(Summary(it)) }
                alarm.repeat?.let { add(Repeat().apply { value = it }) }
                alarm.duration?.let { add(Duration().apply {
                    try {
                        val dur = java.time.Duration.parse(it)
                        this.duration = dur
                    } catch (e: DateTimeParseException) {
                        Ical4Android.log.log(Level.WARNING, "Could not parse duration as Duration.", e)
                    }
                }) }
                alarm.description?.let { add(Description(it)) }
                alarm.attach?.let { add(Attach().apply { value = it }) }
                alarm.other?.let { addAll(JtxContract.getXPropertyListFromJson(it)) }

            }
            calComponent.components.add(vAlarm)
        }


        recurInstances.forEach { recurInstance ->
            val recurCalComponent = when (recurInstance.component) {
                JtxContract.JtxICalObject.Component.VTODO.name -> VToDo(true /* generates DTSTAMP */)
                JtxContract.JtxICalObject.Component.VJOURNAL.name -> VJournal(true /* generates DTSTAMP */)
                else -> return null
            }
            ical.components += recurCalComponent
            recurInstance.addProperties(recurCalComponent.properties)
        }

        ICalendar.softValidate(ical)
        return ical
    }

    /**
     * Takes the current JtxICalObject, transforms it to an iCalendar and writes it in an OutputStream
     * @param [os] OutputStream where iCalendar should be written to
     */
    @UsesThreadContextClassLoader
    fun write(os: OutputStream) {
        Ical4Android.checkThreadContextClassLoader()
        CalendarOutputter(false).output(this.getICalendarFormat(), os)
    }

    /**
     * This function maps the current JtxICalObject to a iCalendar property list
     * @param [props] The PropertyList where the properties should be added
     */
    private fun addProperties(props: PropertyList<Property>) {

        uid.let { props += Uid(it) }
        sequence.let { props += Sequence(it.toInt()) }

        created.let { props += Created(DateTime(it).apply {
            this.isUtc = true
        }) }
        lastModified.let { props += LastModified(DateTime(it).apply {
            this.isUtc = true
        }) }

        summary.let { props += Summary(it) }
        description?.let { props += Description(it) }

        location?.let { location ->
            val loc = Location(location)
            locationAltrep?.let { locationAltrep ->
                loc.parameters.add(AltRep(locationAltrep))
            }
            props += loc
        }
        if (geoLat != null && geoLong != null) {
            props += Geo(geoLat!!.toBigDecimal(), geoLong!!.toBigDecimal())
        }
        geofenceRadius?.let { geofenceRadius ->
            props += XProperty(X_PROP_GEOFENCE_RADIUS, geofenceRadius.toString())
        }
        color?.let { props += Color(null, Css3Color.nearestMatch(it).name) }
        url?.let {
            try {
                props += Url(URI(it))
            } catch (e: URISyntaxException) {
                Ical4Android.log.log(Level.WARNING, "Ignoring invalid task URL: $url", e)
            }
        }
        contact?.let {  props += Contact(it) }

        classification?.let { props += Clazz(it) }
        status?.let { props += Status(it) }
        xstatus?.let { xstatus ->
            props += XProperty(X_PROP_XSTATUS, xstatus)
        }

        val categoryTextList = TextList()
        categories.forEach {
            categoryTextList.add(it.text)
        }
        if (!categoryTextList.isEmpty)
            props += Categories(categoryTextList)


        val resourceTextList = TextList()
        resources.forEach {
            resourceTextList.add(it.text)
        }
        if (!resourceTextList.isEmpty)
            props += Resources(resourceTextList)


        comments.forEach { comment ->
            val c = Comment(comment.text).apply {
                comment.altrep?.let { this.parameters.add(AltRep(it)) }
                comment.language?.let { this.parameters.add(Language(it)) }
                comment.other?.let {
                    val xparams = JtxContract.getXParametersFromJson(it)
                    xparams.forEach { xparam ->
                        this.parameters.add(xparam)
                    }
                }
            }
            props += c
        }


        attendees.forEach { attendee ->
            val attendeeProp = net.fortuna.ical4j.model.property.Attendee().apply {
                this.calAddress = URI(attendee.caladdress)

                attendee.cn?.let {
                    this.parameters.add(Cn(it))
                }
                attendee.cutype?.let {
                    when {
                        it.equals(CuType.INDIVIDUAL.value, ignoreCase = true) -> this.parameters.add(CuType.INDIVIDUAL)
                        it.equals(CuType.GROUP.value, ignoreCase = true) -> this.parameters.add(CuType.GROUP)
                        it.equals(CuType.ROOM.value, ignoreCase = true) -> this.parameters.add(CuType.ROOM)
                        it.equals(CuType.RESOURCE.value, ignoreCase = true) -> this.parameters.add(CuType.RESOURCE)
                        it.equals(CuType.UNKNOWN.value, ignoreCase = true) -> this.parameters.add(CuType.UNKNOWN)
                        else -> this.parameters.add(CuType.UNKNOWN)
                    }
                }
                attendee.delegatedfrom?.let {
                    this.parameters.add(DelegatedFrom(it))
                }
                attendee.delegatedto?.let {
                    this.parameters.add(DelegatedTo(it))
                }
                attendee.dir?.let {
                    this.parameters.add(Dir(it))
                }
                attendee.language?.let {
                    this.parameters.add(Language(it))
                }
                attendee.member?.let {
                    this.parameters.add(Member(it))
                }
                attendee.partstat?.let {
                    this.parameters.add(PartStat(it))
                }
                attendee.role?.let {
                    this.parameters.add(Role(it))
                }
                attendee.rsvp?.let {
                    this.parameters.add(Rsvp(it))
                }
                attendee.sentby?.let {
                    this.parameters.add(SentBy(it))
                }
                attendee.other?.let {
                    val params = JtxContract.getXParametersFromJson(it)
                    params.forEach { xparam ->
                        this.parameters.add(xparam)
                    }
                }
            }
            props += attendeeProp
        }

        organizer?.let { organizer ->
            val organizerProp = net.fortuna.ical4j.model.property.Organizer().apply {
                if(organizer.caladdress?.isNotEmpty() == true)
                    this.calAddress = URI(organizer.caladdress)

                organizer.cn?.let {
                    this.parameters.add(Cn(it))
                }
                organizer.dir?.let {
                    this.parameters.add(Dir(it))
                }
                organizer.language?.let {
                    this.parameters.add(Language(it))
                }
                organizer.sentby?.let {
                    this.parameters.add(SentBy(it))
                }
                organizer.other?.let {
                    val params = JtxContract.getXParametersFromJson(it)
                    params.forEach { xparam ->
                        this.parameters.add(xparam)
                    }
                }
            }
            props += organizerProp
        }

        attachments.forEach { attachment ->

            try {
                if (attachment.uri?.startsWith("content://") == true) {

                    val attachmentUri = ContentUris.withAppendedId(JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(collection.account), attachment.attachmentId)
                    val attachmentFile = collection.client.openFile(attachmentUri, "r")
                    val attachmentBytes = ParcelFileDescriptor.AutoCloseInputStream(attachmentFile).readBytes()
                    val att = Attach(attachmentBytes).apply {
                        attachment.fmttype?.let { this.parameters.add(FmtType(it)) }
                        attachment.filename?.let {
                            this.parameters.add(XParameter(X_PARAM_ATTACH_LABEL, it))
                            this.parameters.add(XParameter(X_PARAM_FILENAME, it))
                        }
                    }
                    props += att

                } else {
                    attachment.uri?.let { uri ->
                        val att = Attach(URI(uri)).apply {
                            attachment.fmttype?.let { this.parameters.add(FmtType(it)) }
                            attachment.filename?.let {
                                this.parameters.add(XParameter(X_PARAM_ATTACH_LABEL, it))
                                this.parameters.add(XParameter(X_PARAM_FILENAME, it))
                            }
                        }
                        props += att
                    }
                }
            } catch (e: FileNotFoundException) {
                Ical4Android.log.log(Level.WARNING, "File not found at the given Uri: ${attachment.uri}", e)
            } catch (e: NullPointerException) {
                Ical4Android.log.log(Level.WARNING, "Provided Uri was empty: ${attachment.uri}", e)
            } catch (e: IllegalArgumentException) {
                Ical4Android.log.log(Level.WARNING, "Uri could not be parsed: ${attachment.uri}", e)
            }
        }

        unknown.forEach {
            it.value?.let {  jsonString ->
                props.add(UnknownProperty.fromJsonString(jsonString))
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
                dtstartTimezone == TZ_ALLDAY -> props += DtStart(Date(it))
                dtstartTimezone == TimeZone.getTimeZone("UTC").id -> props += DtStart(DateTime(it).apply {
                    this.isUtc = true
                })
                dtstartTimezone.isNullOrEmpty() -> props += DtStart(DateTime(it).apply {
                    this.isUtc = false
                })
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
        recurid?.let { recurid ->
            props += when {
                recuridTimezone == TZ_ALLDAY -> RecurrenceId(Date(recurid))
                recuridTimezone == TimeZone.getTimeZone("UTC").id -> RecurrenceId(DateTime(recurid).apply { this.isUtc = true })
                recuridTimezone.isNullOrEmpty() -> RecurrenceId(DateTime(recurid).apply { this.isUtc = false })
                else -> RecurrenceId(DateTime(recurid, TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone(recuridTimezone)))
            }
        }

        rdate?.let { rdateString ->

            when {
                dtstartTimezone == TZ_ALLDAY -> {
                    val dateListDate = DateList(Value.DATE)
                    JtxContract.getLongListFromString(rdateString).forEach {
                        dateListDate.add(Date(it))
                    }
                    props += RDate(dateListDate)

                }
                dtstartTimezone == TimeZone.getTimeZone("UTC").id -> {
                    val dateListDateTime = DateList(Value.DATE_TIME)
                    JtxContract.getLongListFromString(rdateString).forEach {
                        dateListDateTime.add(DateTime(it).apply {
                            this.isUtc = true
                        })
                    }
                    props += RDate(dateListDateTime)
                }
                dtstartTimezone.isNullOrEmpty() -> {
                    val dateListDateTime = DateList(Value.DATE_TIME)
                    JtxContract.getLongListFromString(rdateString).forEach {
                        dateListDateTime.add(DateTime(it).apply {
                            this.isUtc = false
                        })
                    }
                    props += RDate(dateListDateTime)
                }
                else -> {
                    val dateListDateTime = DateList(Value.DATE_TIME)
                    val timezone = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone(dtstartTimezone)
                    JtxContract.getLongListFromString(rdateString).forEach {
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
                dtstartTimezone == TZ_ALLDAY -> {
                    val dateListDate = DateList(Value.DATE)
                    JtxContract.getLongListFromString(exdateString).forEach {
                        dateListDate.add(Date(it))
                    }
                    props += ExDate(dateListDate)

                }
                dtstartTimezone == TimeZone.getTimeZone("UTC").id -> {
                    val dateListDateTime = DateList(Value.DATE_TIME)
                    JtxContract.getLongListFromString(exdateString).forEach {
                        dateListDateTime.add(DateTime(it).apply {
                            this.isUtc = true
                        })
                    }
                    props += ExDate(dateListDateTime)
                }
                dtstartTimezone.isNullOrEmpty() -> {
                    val dateListDateTime = DateList(Value.DATE_TIME)
                    JtxContract.getLongListFromString(exdateString).forEach {
                        dateListDateTime.add(DateTime(it).apply {
                            this.isUtc = false
                        })
                    }
                    props += ExDate(dateListDateTime)
                }
                else -> {
                    val dateListDateTime = DateList(Value.DATE_TIME)
                    val timezone = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone(dtstartTimezone)
                    JtxContract.getLongListFromString(exdateString).forEach {
                        val withTimezone = DateTime(it)
                        withTimezone.timeZone = timezone
                        dateListDateTime.add(DateTime(withTimezone))
                    }
                    props += ExDate(dateListDateTime)
                }
            }
        }

        duration?.let {
            val dur = Duration()
            dur.value = it
            props += dur
        }


        /*
// remember used time zones
val usedTimeZones = HashSet<TimeZone>()
duration?.let(props::add)
*/


        if(component == JtxContract.JtxICalObject.Component.VTODO.name) {
            completed?.let {
                //Completed is defines as always DateTime! And is always UTC! But the X_PROP_COMPLETEDTIMEZONE can still define a timezone
                props += Completed(DateTime(it))

                // only take completedTimezone if there was the completed time set
                completedTimezone?.let { complTZ ->
                    props += XProperty(X_PROP_COMPLETEDTIMEZONE, complTZ)
                }
            }

            percent?.let {
                props += PercentComplete(it)
            }


            if (priority != null && priority != Priority.UNDEFINED.level)
                priority?.let {
                    props += Priority(it)
                }
            else {
                props += Priority(Priority.UNDEFINED.level)
            }

            due?.let {
                when {
                    dueTimezone == TZ_ALLDAY -> props += Due(Date(it))
                    dueTimezone == TimeZone.getTimeZone("UTC").id -> props += Due(DateTime(it).apply {
                    this.isUtc = true
                    })
                    dueTimezone.isNullOrEmpty() -> props += Due(DateTime(it).apply {
                        this.isUtc = false
                    })
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

    /**
     * Updates the fileName, eTag and scheduleTag of the current JtxICalObject
     */
    fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {

        var updateUri = JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())

        val values = ContentValues()
        fileName?.let { values.put(JtxContract.JtxICalObject.FILENAME, fileName) }
        eTag?.let { values.put(JtxContract.JtxICalObject.ETAG, eTag) }
        scheduleTag?.let { values.put(JtxContract.JtxICalObject.SCHEDULETAG, scheduleTag) }
        values.put(JtxContract.JtxICalObject.DIRTY, false)

        collection.client.update(updateUri, values, null, null)
    }

    /**
     * Updates the flags of the current JtxICalObject
     * @param [flags] to be set as [Int]
     */
    fun updateFlags(flags: Int) {

        var updateUri = JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())

        val values = ContentValues(1)
        values.put(JtxContract.JtxICalObject.FLAGS, flags)
        collection.client.update(updateUri, values, null, null)
        this.flags = flags
    }

    /**
     * adds the current JtxICalObject in the jtx DB through the provider
     * @return the Content [Uri] of the inserted object
     */
    fun add(): Uri {
        val values = this.toContentValues()

        val newUri = collection.client.insert(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account),
            values
        ) ?: return Uri.EMPTY
        this.id = newUri.lastPathSegment?.toLong() ?: return Uri.EMPTY

        insertOrUpdateListProperties(false)

        return newUri
    }

    /**
     * Updates the current JtxICalObject with the given data
     * @param [data] The JtxICalObject with the information that should be applied to this object and updated in the provider
     * @return [Uri] of the updated entry
     */
    fun update(data: JtxICalObject): Uri {

        this.applyNewData(data)
        val values = this.toContentValues()

        var updateUri = JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())
        collection.client.update(
            updateUri,
            values,
            "${JtxContract.JtxICalObject.ID} = ?",
            arrayOf(this.id.toString())
        )

        insertOrUpdateListProperties(true)

        return updateUri
    }


    /**
     * This function takes care of all list properties and inserts them in the DB through the provider
     * @param isUpdate if true then the list properties are deleted through the provider before they are inserted
     */
    private fun insertOrUpdateListProperties(isUpdate: Boolean) {

        // delete the categories, attendees, ... and insert them again after. Only relevant for Update, for an insert there will be no entries
        if (isUpdate) {
            val deleteBatch = BatchOperation(collection.client)

            deleteBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newDelete(JtxContract.JtxCategory.CONTENT_URI.asSyncAdapter(collection.account))
                    .withSelection("${JtxContract.JtxCategory.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))
            )

            deleteBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newDelete(JtxContract.JtxComment.CONTENT_URI.asSyncAdapter(collection.account))
                    .withSelection("${JtxContract.JtxComment.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))
            )

            deleteBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newDelete(JtxContract.JtxResource.CONTENT_URI.asSyncAdapter(collection.account))
                    .withSelection("${JtxContract.JtxResource.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))
            )

            deleteBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newDelete(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(collection.account))
                    .withSelection("${JtxContract.JtxRelatedto.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))
            )

            deleteBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newDelete(JtxContract.JtxAttendee.CONTENT_URI.asSyncAdapter(collection.account))
                    .withSelection("${JtxContract.JtxAttendee.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))
            )

            deleteBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newDelete(JtxContract.JtxOrganizer.CONTENT_URI.asSyncAdapter(collection.account))
                    .withSelection("${JtxContract.JtxOrganizer.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))
            )

            deleteBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newDelete(JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(collection.account))
                    .withSelection("${JtxContract.JtxAttachment.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))
            )

            deleteBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newDelete(JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(collection.account))
                    .withSelection("${JtxContract.JtxAlarm.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))
            )

            deleteBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newDelete(JtxContract.JtxUnknown.CONTENT_URI.asSyncAdapter(collection.account))
                    .withSelection("${JtxContract.JtxUnknown.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))
            )

            deleteBatch.commit()
        }

        val insertBatch = BatchOperation(collection.client)

        this.categories.forEach { category ->
            insertBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newInsert(JtxContract.JtxCategory.CONTENT_URI.asSyncAdapter(collection.account))
                    .withValue(JtxContract.JtxCategory.ICALOBJECT_ID, id)
                    .withValue(JtxContract.JtxCategory.TEXT, category.text)
                    .withValue(JtxContract.JtxCategory.ID, category.categoryId)
                    .withValue(JtxContract.JtxCategory.LANGUAGE, category.language)
                    .withValue(JtxContract.JtxCategory.OTHER, category.other)
            )
        }

        this.comments.forEach { comment ->
            insertBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newInsert(JtxContract.JtxComment.CONTENT_URI.asSyncAdapter(collection.account))
                    .withValue(JtxContract.JtxComment.ICALOBJECT_ID, id)
                    .withValue(JtxContract.JtxComment.ID, comment.commentId)
                    .withValue(JtxContract.JtxComment.TEXT, comment.text)
                    .withValue(JtxContract.JtxComment.LANGUAGE, comment.language)
                    .withValue(JtxContract.JtxComment.OTHER, comment.other)
            )
        }


        this.resources.forEach { resource ->
            insertBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newInsert(JtxContract.JtxResource.CONTENT_URI.asSyncAdapter(collection.account))
                    .withValue(JtxContract.JtxResource.ICALOBJECT_ID, id)
                    .withValue(JtxContract.JtxResource.ID, resource.resourceId)
                    .withValue(JtxContract.JtxResource.TEXT, resource.text)
                    .withValue(JtxContract.JtxResource.LANGUAGE, resource.language)
                    .withValue(JtxContract.JtxResource.OTHER, resource.other)
            )
        }

        this.relatedTo.forEach { related ->
            insertBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newInsert(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(collection.account))
                    .withValue(JtxContract.JtxRelatedto.ICALOBJECT_ID, id)
                    .withValue(JtxContract.JtxRelatedto.TEXT, related.text)
                    .withValue(JtxContract.JtxRelatedto.RELTYPE, related.reltype)
                    .withValue(JtxContract.JtxRelatedto.OTHER, related.other)
            )
        }

        this.attendees.forEach { attendee ->
            insertBatch.enqueue(
                BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxAttendee.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxAttendee.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxAttendee.CALADDRESS, attendee.caladdress)
                .withValue(JtxContract.JtxAttendee.CN, attendee.cn)
                .withValue(JtxContract.JtxAttendee.CUTYPE, attendee.cutype)
                .withValue(JtxContract.JtxAttendee.DELEGATEDFROM, attendee.delegatedfrom)
                .withValue(JtxContract.JtxAttendee.DELEGATEDTO, attendee.delegatedto)
                .withValue(JtxContract.JtxAttendee.DIR, attendee.dir)
                .withValue(JtxContract.JtxAttendee.LANGUAGE, attendee.language)
                .withValue(JtxContract.JtxAttendee.MEMBER, attendee.member)
                .withValue(JtxContract.JtxAttendee.PARTSTAT, attendee.partstat)
                .withValue(JtxContract.JtxAttendee.ROLE, attendee.role)
                .withValue(JtxContract.JtxAttendee.RSVP, attendee.rsvp)
                .withValue(JtxContract.JtxAttendee.SENTBY, attendee.sentby)
                .withValue(JtxContract.JtxAttendee.OTHER, attendee.other)
            )
        }

        this.organizer?.let { organizer ->
            insertBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newInsert(JtxContract.JtxOrganizer.CONTENT_URI.asSyncAdapter(collection.account))
                    .withValue(JtxContract.JtxOrganizer.ICALOBJECT_ID, id)
                    .withValue(JtxContract.JtxOrganizer.CALADDRESS, organizer.caladdress)
                    .withValue(JtxContract.JtxOrganizer.CN, organizer.cn)
                    .withValue(JtxContract.JtxOrganizer.DIR, organizer.dir)
                    .withValue(JtxContract.JtxOrganizer.LANGUAGE, organizer.language)
                    .withValue(JtxContract.JtxOrganizer.SENTBY, organizer.sentby)
                    .withValue(JtxContract.JtxOrganizer.OTHER, organizer.other)
            )
        }

        this.attachments.forEach { attachment ->
            val attachmentContentValues = ContentValues().apply {
                put(JtxContract.JtxAttachment.ICALOBJECT_ID, id)
                put(JtxContract.JtxAttachment.URI, attachment.uri)
                put(JtxContract.JtxAttachment.FMTTYPE, attachment.fmttype)
                put(JtxContract.JtxAttachment.OTHER, attachment.other)
                put(JtxContract.JtxAttachment.FILENAME, attachment.filename)
            }
            val newAttachment = collection.client.insert(JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(collection.account), attachmentContentValues)
            if(attachment.uri.isNullOrEmpty() && newAttachment != null) {
                val attachmentPFD = collection.client.openFile(newAttachment, "w")
                ParcelFileDescriptor.AutoCloseOutputStream(attachmentPFD).write(Base64.decode(attachment.binary, Base64.DEFAULT))
            }
        }

        this.alarms.forEach { alarm ->
            insertBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newInsert(JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(collection.account))
                    .withValue(JtxContract.JtxAlarm.ICALOBJECT_ID, id)
                    .withValue(JtxContract.JtxAlarm.ACTION, alarm.action)
                    .withValue(JtxContract.JtxAlarm.ATTACH, alarm.attach)
                    //.withValue(JtxContract.JtxAlarm.ATTENDEE, alarm.attendee)
                    .withValue(JtxContract.JtxAlarm.DESCRIPTION, alarm.description)
                    .withValue(JtxContract.JtxAlarm.DURATION, alarm.duration)
                    .withValue(JtxContract.JtxAlarm.REPEAT, alarm.repeat)
                    .withValue(JtxContract.JtxAlarm.SUMMARY, alarm.summary)
                    .withValue(JtxContract.JtxAlarm.TRIGGER_RELATIVE_TO, alarm.triggerRelativeTo)
                    .withValue(JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION, alarm.triggerRelativeDuration)
                    .withValue(JtxContract.JtxAlarm.TRIGGER_TIME, alarm.triggerTime)
                    .withValue(JtxContract.JtxAlarm.TRIGGER_TIMEZONE, alarm.triggerTimezone)
                    .withValue(JtxContract.JtxAlarm.OTHER, alarm.other)
            )
        }

        this.unknown.forEach { unknown ->
            insertBatch.enqueue(
                BatchOperation.CpoBuilder
                    .newInsert(JtxContract.JtxUnknown.CONTENT_URI.asSyncAdapter(collection.account))
                    .withValue(JtxContract.JtxUnknown.ICALOBJECT_ID, id)
                    .withValue(JtxContract.JtxUnknown.UNKNOWN_VALUE, unknown.value)
            )
        }

        insertBatch.commit()
    }

    /**
     * Deletes the current JtxICalObject
     * @return The number of deleted records (should always be 1)
     */
    fun delete(): Int {
        val uri = Uri.withAppendedPath(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account),
            id.toString()
        )
        return collection.client.delete(uri, null, null)
    }


    /**
     * This function is used for empty JtxICalObjects that need new data applied, usually a LocalJtxICalObject.
     * @param [newData], the (local) JtxICalObject that should be mapped onto the given JtxICalObject
     */
    fun applyNewData(newData: JtxICalObject) {

        this.component = newData.component
        this.sequence = newData.sequence
        this.created = newData.created
        this.lastModified = newData.lastModified
        this.summary = newData.summary
        this.description = newData.description
        this.uid = newData.uid

        this.location = newData.location
        this.locationAltrep = newData.locationAltrep
        this.geoLat = newData.geoLat
        this.geoLong = newData.geoLong
        this.geofenceRadius = newData.geofenceRadius
        this.percent = newData.percent
        this.classification = newData.classification
        this.status = newData.status
        this.xstatus = newData.xstatus
        this.priority = newData.priority
        this.color = newData.color
        this.url = newData.url
        this.contact = newData.contact

        this.dtstart = newData.dtstart
        this.dtstartTimezone = newData.dtstartTimezone
        this.dtend = newData.dtend
        this.dtendTimezone = newData.dtendTimezone
        this.completed = newData.completed
        this.completedTimezone = newData.completedTimezone
        this.due = newData.due
        this.dueTimezone = newData.dueTimezone
        this.duration = newData.duration

        this.rrule = newData.rrule
        this.rdate = newData.rdate
        this.exdate = newData.exdate
        this.recurid = newData.recurid
        this.recuridTimezone = newData.recuridTimezone


        this.categories = newData.categories
        this.comments = newData.comments
        this.resources = newData.resources
        this.relatedTo = newData.relatedTo
        this.attendees = newData.attendees
        this.organizer = newData.organizer
        this.attachments = newData.attachments
        this.alarms = newData.alarms
        this.unknown = newData.unknown
    }

    /**
     * Takes Content Values, applies them on the current JtxICalObject and retrieves all further list properties from the content provier and adds them.
     * @param [values] The Content Values with the information about the JtxICalObject
     */
    fun populateFromContentValues(values: ContentValues) {
        values.getAsLong(JtxContract.JtxICalObject.ID)?.let { id -> this.id = id }

        values.getAsString(JtxContract.JtxICalObject.COMPONENT)?.let { component -> this.component = component }
        values.getAsString(JtxContract.JtxICalObject.SUMMARY)?.let { summary -> this.summary = summary }
        values.getAsString(JtxContract.JtxICalObject.DESCRIPTION)?.let { description -> this.description = description }
        values.getAsLong(JtxContract.JtxICalObject.DTSTART)?.let { dtstart -> this.dtstart = dtstart }
        values.getAsString(JtxContract.JtxICalObject.DTSTART_TIMEZONE)?.let { dtstartTimezone -> this.dtstartTimezone = dtstartTimezone }
        values.getAsLong(JtxContract.JtxICalObject.DTEND)?.let { dtend -> this.dtend = dtend }
        values.getAsString(JtxContract.JtxICalObject.DTEND_TIMEZONE)?.let { dtendTimezone -> this.dtendTimezone = dtendTimezone }
        values.getAsString(JtxContract.JtxICalObject.STATUS)?.let { status -> this.status = status }
        values.getAsString(JtxContract.JtxICalObject.EXTENDED_STATUS)?.let { xstatus -> this.xstatus = xstatus }
        values.getAsString(JtxContract.JtxICalObject.CLASSIFICATION)?.let { classification -> this.classification = classification }
        values.getAsString(JtxContract.JtxICalObject.URL)?.let { url -> this.url = url }
        values.getAsString(JtxContract.JtxICalObject.CONTACT)?.let { contact -> this.contact = contact }
        values.getAsDouble(JtxContract.JtxICalObject.GEO_LAT)?.let { geoLat -> this.geoLat = geoLat }
        values.getAsDouble(JtxContract.JtxICalObject.GEO_LONG)?.let { geoLong -> this.geoLong = geoLong }
        values.getAsString(JtxContract.JtxICalObject.LOCATION)?.let { location -> this.location = location }
        values.getAsString(JtxContract.JtxICalObject.LOCATION_ALTREP)?.let { locationAltrep -> this.locationAltrep = locationAltrep }
        values.getAsInteger(JtxContract.JtxICalObject.GEOFENCE_RADIUS)?.let { geofenceRadius -> this.geofenceRadius = geofenceRadius  }
        values.getAsInteger(JtxContract.JtxICalObject.PERCENT)?.let { percent -> this.percent = percent }
        values.getAsInteger(JtxContract.JtxICalObject.PRIORITY)?.let { priority -> this.priority = priority }
        values.getAsLong(JtxContract.JtxICalObject.DUE)?.let { due -> this.due = due }
        values.getAsString(JtxContract.JtxICalObject.DUE_TIMEZONE)?.let { dueTimezone -> this.dueTimezone = dueTimezone }
        values.getAsLong(JtxContract.JtxICalObject.COMPLETED)?.let { completed -> this.completed = completed }
        values.getAsString(JtxContract.JtxICalObject.COMPLETED_TIMEZONE)?.let { completedTimezone -> this.completedTimezone = completedTimezone }
        values.getAsString(JtxContract.JtxICalObject.DURATION)?.let { duration -> this.duration = duration }
        values.getAsString(JtxContract.JtxICalObject.UID)?.let { uid -> this.uid = uid }
        values.getAsLong(JtxContract.JtxICalObject.CREATED)?.let { created -> this.created = created }
        values.getAsLong(JtxContract.JtxICalObject.DTSTAMP)?.let { dtstamp -> this.dtstamp = dtstamp }
        values.getAsLong(JtxContract.JtxICalObject.LAST_MODIFIED)?.let { lastModified -> this.lastModified = lastModified }
        values.getAsLong(JtxContract.JtxICalObject.SEQUENCE)?.let { sequence -> this.sequence = sequence }
        values.getAsInteger(JtxContract.JtxICalObject.COLOR)?.let { color -> this.color = color }

        values.getAsString(JtxContract.JtxICalObject.RRULE)?.let { rrule -> this.rrule = rrule }
        values.getAsString(JtxContract.JtxICalObject.EXDATE)?.let { exdate -> this.exdate = exdate }
        values.getAsString(JtxContract.JtxICalObject.RDATE)?.let { rdate -> this.rdate = rdate }
        values.getAsString(JtxContract.JtxICalObject.RECURID)?.let { recurid -> this.recurid = recurid }
        values.getAsString(JtxContract.JtxICalObject.RECURID_TIMEZONE)?.let { recuridTimezone -> this.recuridTimezone = recuridTimezone }

        this.collectionId = collection.id
        values.getAsString(JtxContract.JtxICalObject.DIRTY)?.let { dirty -> this.dirty = dirty == "1" || dirty == "true" }
        values.getAsString(JtxContract.JtxICalObject.DELETED)?.let { deleted -> this.deleted = deleted == "1" || deleted == "true" }

        values.getAsString(JtxContract.JtxICalObject.FILENAME)?.let { fileName -> this.fileName = fileName }
        values.getAsString(JtxContract.JtxICalObject.ETAG)?.let { eTag -> this.eTag = eTag }
        values.getAsString(JtxContract.JtxICalObject.SCHEDULETAG)?.let { scheduleTag -> this.scheduleTag = scheduleTag }
        values.getAsInteger(JtxContract.JtxICalObject.FLAGS)?.let { flags -> this.flags = flags }


        // Take care of categories
        getAsContentValues(
            uri = JtxContract.JtxCategory.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxCategory.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { catValues ->
            val category = Category().apply {
                catValues.getAsLong(JtxContract.JtxCategory.ID)?.let { id -> this.categoryId = id }
                catValues.getAsString(JtxContract.JtxCategory.TEXT)?.let { text -> this.text = text }
                catValues.getAsString(JtxContract.JtxCategory.LANGUAGE)?.let { language -> this.language = language }
                catValues.getAsString(JtxContract.JtxCategory.OTHER)?.let { other -> this.other = other }
            }
            categories.add(category)
        }

        // Take care of comments
        getAsContentValues(
            uri = JtxContract.JtxComment.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxComment.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { commentValues ->
            val comment = Comment().apply {
                commentValues.getAsLong(JtxContract.JtxComment.ID)?.let { id -> this.commentId = id }
                commentValues.getAsString(JtxContract.JtxComment.TEXT)?.let { text -> this.text = text }
                commentValues.getAsString(JtxContract.JtxComment.LANGUAGE)?.let { language -> this.language = language }
                commentValues.getAsString(JtxContract.JtxComment.OTHER)?.let { other -> this.other = other }
            }
            comments.add(comment)
        }

        // Take care of resources
        getAsContentValues(
            uri = JtxContract.JtxResource.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxResource.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { resourceValues ->
            val resource = Resource().apply {
                resourceValues.getAsLong(JtxContract.JtxResource.ID)?.let { id -> this.resourceId = id }
                resourceValues.getAsString(JtxContract.JtxResource.TEXT)?.let { text -> this.text = text }
                resourceValues.getAsString(JtxContract.JtxResource.LANGUAGE)?.let { language -> this.language = language }
                resourceValues.getAsString(JtxContract.JtxResource.OTHER)?.let { other -> this.other = other }
            }
            resources.add(resource)
        }


        // Take care of related-to
        getAsContentValues(
            uri = JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxRelatedto.ICALOBJECT_ID} = ? AND ${JtxContract.JtxRelatedto.RELTYPE} = ?",
            selectionArgs = arrayOf(this.id.toString(), JtxContract.JtxRelatedto.Reltype.PARENT.name)
        ).forEach { relatedToValues ->
            val relTo = RelatedTo().apply {
                relatedToValues.getAsLong(JtxContract.JtxRelatedto.ID)?.let { id -> this.relatedtoId = id }
                relatedToValues.getAsString(JtxContract.JtxRelatedto.TEXT)?.let { text -> this.text = text }
                relatedToValues.getAsString(JtxContract.JtxRelatedto.RELTYPE)?.let { reltype -> this.reltype = reltype }
                relatedToValues.getAsString(JtxContract.JtxRelatedto.OTHER)?.let { other -> this.other = other }

            }
            relatedTo.add(relTo)
        }

        // Take care of attendees
        getAsContentValues(
            uri = JtxContract.JtxAttendee.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxAttendee.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { attendeeValues ->
            val attendee = Attendee().apply {
                attendeeValues.getAsLong(JtxContract.JtxAttendee.ID)?.let { id -> this.attendeeId = id }
                attendeeValues.getAsString(JtxContract.JtxAttendee.CALADDRESS)?.let { caladdress -> this.caladdress = caladdress }
                attendeeValues.getAsString(JtxContract.JtxAttendee.CUTYPE)?.let { cutype -> this.cutype = cutype }
                attendeeValues.getAsString(JtxContract.JtxAttendee.MEMBER)?.let { member -> this.member = member }
                attendeeValues.getAsString(JtxContract.JtxAttendee.ROLE)?.let { role -> this.role = role }
                attendeeValues.getAsString(JtxContract.JtxAttendee.PARTSTAT)?.let { partstat -> this.partstat = partstat }
                attendeeValues.getAsString(JtxContract.JtxAttendee.RSVP)?.let { rsvp -> this.rsvp = rsvp == "1" }
                attendeeValues.getAsString(JtxContract.JtxAttendee.DELEGATEDTO)?.let { delto -> this.delegatedto = delto }
                attendeeValues.getAsString(JtxContract.JtxAttendee.DELEGATEDFROM)?.let { delfrom -> this.delegatedfrom = delfrom }
                attendeeValues.getAsString(JtxContract.JtxAttendee.SENTBY)?.let { sentby -> this.sentby = sentby }
                attendeeValues.getAsString(JtxContract.JtxAttendee.CN)?.let { cn -> this.cn = cn }
                attendeeValues.getAsString(JtxContract.JtxAttendee.DIR)?.let { dir -> this.dir = dir }
                attendeeValues.getAsString(JtxContract.JtxAttendee.LANGUAGE)?.let { lang -> this.language = lang }
                attendeeValues.getAsString(JtxContract.JtxAttendee.OTHER)?.let { other -> this.other = other }
            }
            attendees.add(attendee)
        }

        // Take care of organizer
        getAsContentValues(
            uri = JtxContract.JtxOrganizer.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxOrganizer.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).firstOrNull()?.let { organizerContentValues ->
            val orgnzr = Organizer().apply {
                organizerId = organizerContentValues.getAsLong(JtxContract.JtxOrganizer.ID) ?: 0L
                caladdress = organizerContentValues.getAsString(JtxContract.JtxOrganizer.CALADDRESS)
                sentby = organizerContentValues.getAsString(JtxContract.JtxOrganizer.SENTBY)
                cn = organizerContentValues.getAsString(JtxContract.JtxOrganizer.CN)
                dir = organizerContentValues.getAsString(JtxContract.JtxOrganizer.DIR)
                language = organizerContentValues.getAsString(JtxContract.JtxOrganizer.LANGUAGE)
                other = organizerContentValues.getAsString(JtxContract.JtxOrganizer.OTHER)
            }
            if(orgnzr.caladdress?.isNotEmpty() == true)   // we only take the organizer if there was a caladdress (otherwise an empty ORGANIZER is created)
                organizer = orgnzr
        }

        // Take care of attachments
        getAsContentValues(
            uri = JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxAttachment.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { attachmentValues ->
            val attachment = Attachment().apply {
                attachmentValues.getAsLong(JtxContract.JtxAttachment.ID)?.let { id -> this.attachmentId = id }
                attachmentValues.getAsString(JtxContract.JtxAttachment.URI)?.let { uri -> this.uri = uri }
                attachmentValues.getAsString(JtxContract.JtxAttachment.BINARY)?.let { value -> this.binary = value }
                attachmentValues.getAsString(JtxContract.JtxAttachment.FMTTYPE)?.let { fmttype -> this.fmttype = fmttype }
                attachmentValues.getAsString(JtxContract.JtxAttachment.OTHER)?.let { other -> this.other = other }
                attachmentValues.getAsString(JtxContract.JtxAttachment.FILENAME)?.let { filename -> this.filename = filename }
            }
            attachments.add(attachment)
        }

        // Take care of alarms
        getAsContentValues(
            uri = JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxAlarm.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { alarmValues ->
            val alarm = Alarm().apply {
                alarmValues.getAsLong(JtxContract.JtxAlarm.ID)?.let { id -> this.alarmId = id }
                alarmValues.getAsString(JtxContract.JtxAlarm.ACTION)?.let { action -> this.action = action }
                alarmValues.getAsString(JtxContract.JtxAlarm.DESCRIPTION)?.let { desc -> this.description = desc }
                alarmValues.getAsLong(JtxContract.JtxAlarm.TRIGGER_TIME)?.let { time -> this.triggerTime = time }
                alarmValues.getAsString(JtxContract.JtxAlarm.TRIGGER_TIMEZONE)?.let { tz -> this.triggerTimezone = tz }
                alarmValues.getAsString(JtxContract.JtxAlarm.TRIGGER_RELATIVE_TO)?.let { relative -> this.triggerRelativeTo = relative }
                alarmValues.getAsString(JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION)?.let { duration -> this.triggerRelativeDuration = duration }
                alarmValues.getAsString(JtxContract.JtxAlarm.SUMMARY)?.let { summary -> this.summary = summary }
                alarmValues.getAsString(JtxContract.JtxAlarm.DURATION)?.let { dur -> this.duration = dur }
                alarmValues.getAsString(JtxContract.JtxAlarm.REPEAT)?.let { repeat -> this.repeat = repeat }
                alarmValues.getAsString(JtxContract.JtxAlarm.ATTACH)?.let { attach -> this.attach = attach }
                alarmValues.getAsString(JtxContract.JtxAlarm.OTHER)?.let { other -> this.other = other }
            }
            alarms.add(alarm)
        }


        // Take care of unknown properties
        getAsContentValues(
            uri = JtxContract.JtxUnknown.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxUnknown.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString()),
        ).forEach { unknownValues ->
            val unknwn = Unknown().apply {
                unknownValues.getAsLong(JtxContract.JtxUnknown.ID)?.let { id -> this.unknownId = id }
                unknownValues.getAsString(JtxContract.JtxUnknown.UNKNOWN_VALUE)?.let { value -> this.value = value }
            }
            unknown.add(unknwn)
        }


        if(rrule?.isNotEmpty() == true) {
            getAsContentValues(
                uri = JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account),
                selection = "${JtxContract.JtxICalObject.UID} = ? AND ${JtxContract.JtxICalObject.RECURID} IS NOT NULL AND ${JtxContract.JtxICalObject.SEQUENCE} > 0",
                selectionArgs = arrayOf(uid)
            ).forEach { recurInstanceValues ->
                recurInstances.add(
                    JtxICalObject(collection).apply { populateFromContentValues(recurInstanceValues) }
                )
            }
        }
    }

    /**
     * Puts the current JtxICalObjects attributes into Content Values
     * @return The JtxICalObject attributes as [ContentValues] (exluding list properties)
     */
    private fun toContentValues() = ContentValues().apply {
        put(JtxContract.JtxICalObject.ID, id)
        put(JtxContract.JtxICalObject.SUMMARY, summary)
        put(JtxContract.JtxICalObject.DESCRIPTION, description)
        put(JtxContract.JtxICalObject.COMPONENT, component)
        put(JtxContract.JtxICalObject.STATUS, status)
        put(JtxContract.JtxICalObject.EXTENDED_STATUS, xstatus)
        put(JtxContract.JtxICalObject.CLASSIFICATION, classification)
        put(JtxContract.JtxICalObject.PRIORITY, priority)
        put(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collectionId)
        put(JtxContract.JtxICalObject.UID, uid)
        put(JtxContract.JtxICalObject.COLOR, color)
        put(JtxContract.JtxICalObject.URL, url)
        put(JtxContract.JtxICalObject.CONTACT, contact)
        put(JtxContract.JtxICalObject.GEO_LAT, geoLat)
        put(JtxContract.JtxICalObject.GEO_LONG, geoLong)
        put(JtxContract.JtxICalObject.LOCATION, location)
        put(JtxContract.JtxICalObject.LOCATION_ALTREP, locationAltrep)
        put(JtxContract.JtxICalObject.GEOFENCE_RADIUS, geofenceRadius)
        put(JtxContract.JtxICalObject.PERCENT, percent)
        put(JtxContract.JtxICalObject.DTSTAMP, dtstamp)
        put(JtxContract.JtxICalObject.DTSTART, dtstart)
        put(JtxContract.JtxICalObject.DTSTART_TIMEZONE, dtstartTimezone)
        put(JtxContract.JtxICalObject.DTEND, dtend)
        put(JtxContract.JtxICalObject.DTEND_TIMEZONE, dtendTimezone)
        put(JtxContract.JtxICalObject.COMPLETED, completed)
        put(JtxContract.JtxICalObject.COMPLETED_TIMEZONE, completedTimezone)
        put(JtxContract.JtxICalObject.DUE, due)
        put(JtxContract.JtxICalObject.DUE_TIMEZONE, dueTimezone)
        put(JtxContract.JtxICalObject.DURATION, duration)
        put(JtxContract.JtxICalObject.CREATED, created)
        put(JtxContract.JtxICalObject.LAST_MODIFIED, lastModified)
        put(JtxContract.JtxICalObject.SEQUENCE, sequence)
        put(JtxContract.JtxICalObject.RRULE, rrule)
        put(JtxContract.JtxICalObject.RDATE, rdate)
        put(JtxContract.JtxICalObject.EXDATE, exdate)
        put(JtxContract.JtxICalObject.RECURID, recurid)
        put(JtxContract.JtxICalObject.RECURID_TIMEZONE, recuridTimezone)

        put(JtxContract.JtxICalObject.FILENAME, fileName)
        put(JtxContract.JtxICalObject.ETAG, eTag)
        put(JtxContract.JtxICalObject.SCHEDULETAG, scheduleTag)
        put(JtxContract.JtxICalObject.FLAGS, flags)
        put(JtxContract.JtxICalObject.DIRTY, dirty)
    }

    /**
     * @return The result of the given query as content values of the given JtxICalObject as a list of ContentValues
     */
    private fun getAsContentValues(
        uri: Uri,
        projection: Array<String>? = null,
        selection: String,
        selectionArgs: Array<String>,
        sortOrder: String? = null
    ): List<ContentValues> {

        val values: MutableList<ContentValues> = mutableListOf()
        collection.client.query(uri, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) { values.add(cursor.toValues()) }
        }
        return values
    }
}
