package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.impl.TestJtxCollection
import at.bitfire.jtx.JtxContract
import at.bitfire.jtx.JtxContract.JtxICalObject.Component
import at.bitfire.jtx.JtxContract.JtxICalObject
import at.bitfire.jtx.JtxContract.asSyncAdapter
import junit.framework.TestCase.*
import org.junit.After
import org.junit.Before
import org.junit.Test


class JtxICalObjectTest {

    private val testAccount = Account("TEST", JtxContract.JtxCollection.TEST_ACCOUNT_TYPE)
    private lateinit var contentResolver: ContentResolver
    private lateinit var client: ContentProviderClient
    var collection: TestJtxCollection? = null
    var sample: at.bitfire.ical4android.JtxICalObject? = null
    lateinit var context: Context

    private val url = "https://jtx.techbee.at"
    private val displayname = "jtxTest"
    private val syncversion = JtxContract.CONTRACT_VERSION

    private val cvCollection = ContentValues().apply {
        put(JtxContract.JtxCollection.ACCOUNT_TYPE, testAccount.type)
        put(JtxContract.JtxCollection.ACCOUNT_NAME, testAccount.name)
        put(JtxContract.JtxCollection.URL, url)
        put(JtxContract.JtxCollection.DISPLAYNAME, displayname)
        put(JtxContract.JtxCollection.SYNC_VERSION, syncversion)
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        contentResolver = context.contentResolver

        TestUtils.requestPermissions(TaskProvider.ProviderName.JtxBoard.permissions)

        client = contentResolver.acquireContentProviderClient(JtxContract.AUTHORITY)!!

        val collectionUri = JtxCollection.create(testAccount, client, cvCollection)
        assertNotNull(collectionUri)
        collection = JtxCollection.find(testAccount, client, TestJtxCollection.Factory, null, null)[0]
        assertNotNull(collection)

        sample = JtxICalObject(collection!!).apply {
            this.summary = "summ"
            this.description = "desc"
            this.dtstart = System.currentTimeMillis()
            this.dtstartTimezone = "Europe/Vienna"
            this.dtend = System.currentTimeMillis()
            this.dtendTimezone = "Europe/Paris"
            this.status = JtxICalObject.StatusJournal.FINAL.name
            this.classification = JtxICalObject.Classification.PUBLIC.name
            this.url = "https://jtx.techbee.at"
            this.contact = "jtx@techbee.at"
            this.geoLat = 48.2082
            this.geoLong = 16.3738
            this.location = "Vienna"
            this.locationAltrep = "Wien"
            this.percent = 99
            this.priority = 1
            this.due = System.currentTimeMillis()
            this.dueTimezone = "Europe/Berlin"
            this.completed = System.currentTimeMillis()
            this.completedTimezone = "Europe/Budapest"
            this.duration = "P15DT5H0M20S"
            this.rrule = "FREQ=YEARLY;INTERVAL=2;BYMONTH=1;BYDAY=SU;BYHOUR=8,9;BYMINUTE=30"
            this.exdate = System.currentTimeMillis().toString()
            this.rdate = System.currentTimeMillis().toString()
            this.recurid = "1635796608864-b228364a-e633-449a-aeb2-d1a96941377c@at.techbee.jtx"
            this.uid = "1635796608864-b228364a-e633-449a-aeb2-d1a96941377c@at.techbee.jtx"
            this.created = System.currentTimeMillis()
            this.lastModified = System.currentTimeMillis()
            this.dtstamp = System.currentTimeMillis()
            this.sequence = 1
            this.color = -2298423
            this.dirty = false
            this.deleted = false
            this.fileName = "test.ics"
            this.eTag = "0"
            this.scheduleTag = "0"
            this.flags = 0
        }

    }

    @After
    fun tearDown() {

        collection?.delete()
        val collections = JtxCollection.find(testAccount, client, TestJtxCollection.Factory, null, null)
        assertEquals(0, collections.size)
    }


    @Test fun check_SUMMARY() = insertRetrieveAssertString(JtxICalObject.SUMMARY, sample?.summary, Component.VJOURNAL.name)
    @Test fun check_DESCRIPTION() = insertRetrieveAssertString(JtxICalObject.DESCRIPTION, sample?.description, Component.VJOURNAL.name)
    @Test fun check_DTSTART() = insertRetrieveAssertLong(JtxICalObject.DTSTART, sample?.dtstart, Component.VJOURNAL.name)
    @Test fun check_DTSTART_TIMEZONE() = insertRetrieveAssertString(JtxICalObject.DTSTART_TIMEZONE, sample?.dtstartTimezone, Component.VJOURNAL.name)
    @Test fun check_DTEND() = insertRetrieveAssertLong(JtxICalObject.DTEND, sample?.dtend, Component.VJOURNAL.name)
    @Test fun check_DTEND_TIMEZONE() = insertRetrieveAssertString(JtxICalObject.DTEND_TIMEZONE, sample?.dtendTimezone, Component.VJOURNAL.name)
    @Test fun check_STATUS() = insertRetrieveAssertString(JtxICalObject.STATUS, sample?.status, Component.VJOURNAL.name)
    @Test fun check_CLASSIFICATION() = insertRetrieveAssertString(JtxICalObject.CLASSIFICATION, sample?.classification, Component.VJOURNAL.name)
    @Test fun check_URL() = insertRetrieveAssertString(JtxICalObject.URL, sample?.url, Component.VJOURNAL.name)
    @Test fun check_CONTACT() = insertRetrieveAssertString(JtxICalObject.CONTACT, sample?.contact, Component.VJOURNAL.name)
    @Test fun check_GEO_LAT() = insertRetrieveAssertDouble(JtxICalObject.GEO_LAT, sample?.geoLat, Component.VJOURNAL.name)
    @Test fun check_GEO_LONG() = insertRetrieveAssertDouble(JtxICalObject.GEO_LONG, sample?.geoLong, Component.VJOURNAL.name)
    @Test fun check_LOCATION() = insertRetrieveAssertString(JtxICalObject.LOCATION, sample?.location, Component.VJOURNAL.name)
    @Test fun check_LOCATION_ALTREP() = insertRetrieveAssertString(JtxICalObject.LOCATION_ALTREP, sample?.locationAltrep, Component.VJOURNAL.name)
    @Test fun check_PERCENT() = insertRetrieveAssertInt(JtxICalObject.PERCENT, sample?.percent, Component.VJOURNAL.name)
    @Test fun check_PRIORITY() = insertRetrieveAssertInt(JtxICalObject.PRIORITY, sample?.priority, Component.VJOURNAL.name)
    @Test fun check_DUE() = insertRetrieveAssertLong(JtxICalObject.DUE, sample?.due, Component.VJOURNAL.name)
    @Test fun check_DUE_TIMEZONE() = insertRetrieveAssertString(JtxICalObject.DUE_TIMEZONE, sample?.dueTimezone, Component.VJOURNAL.name)
    @Test fun check_COMPLETED() = insertRetrieveAssertLong(JtxICalObject.COMPLETED, sample?.completed, Component.VJOURNAL.name)
    @Test fun check_COMPLETED_TIMEZONE() = insertRetrieveAssertString(JtxICalObject.COMPLETED_TIMEZONE, sample?.completedTimezone, Component.VJOURNAL.name)
    @Test fun check_DURATION() = insertRetrieveAssertString(JtxICalObject.DURATION, sample?.duration, Component.VJOURNAL.name)
    @Test fun check_RRULE() = insertRetrieveAssertString(JtxICalObject.RRULE, sample?.rrule, Component.VJOURNAL.name)
    @Test fun check_RDATE() = insertRetrieveAssertString(JtxICalObject.RDATE, sample?.rdate, Component.VJOURNAL.name)
    @Test fun check_EXDATE() = insertRetrieveAssertString(JtxICalObject.EXDATE, sample?.exdate, Component.VJOURNAL.name)
    @Test fun check_RECURID() = insertRetrieveAssertString(JtxICalObject.RECURID, sample?.recurid, Component.VJOURNAL.name)
    @Test fun check_UID() = insertRetrieveAssertString(JtxICalObject.UID, sample?.uid, Component.VJOURNAL.name)
    @Test fun check_CREATED() = insertRetrieveAssertLong(JtxICalObject.CREATED, sample?.created, Component.VJOURNAL.name)
    @Test fun check_DTSTAMP() = insertRetrieveAssertLong(JtxICalObject.DTSTAMP, sample?.dtstamp, Component.VJOURNAL.name)
    @Test fun check_LAST_MODIFIED() = insertRetrieveAssertLong(JtxICalObject.LAST_MODIFIED, sample?.lastModified, Component.VJOURNAL.name)
    @Test fun check_SEQUENCE() = insertRetrieveAssertLong(JtxICalObject.SEQUENCE, sample?.sequence, Component.VJOURNAL.name)
    @Test fun check_COLOR() = insertRetrieveAssertInt(JtxICalObject.COLOR, sample?.color, Component.VJOURNAL.name)
    @Test fun check_DIRTY() = insertRetrieveAssertBoolean(JtxICalObject.DIRTY, sample?.dirty, Component.VJOURNAL.name)
    @Test fun check_DELETED() = insertRetrieveAssertBoolean(JtxICalObject.DELETED,sample?.deleted, Component.VJOURNAL.name)
    @Test fun check_FILENAME() = insertRetrieveAssertString(JtxICalObject.FILENAME, sample?.fileName, Component.VJOURNAL.name)
    @Test fun check_ETAG() = insertRetrieveAssertString(JtxICalObject.ETAG, sample?.eTag, Component.VJOURNAL.name)
    @Test fun check_SCHEDULETAG() = insertRetrieveAssertString(JtxICalObject.SCHEDULETAG, sample?.scheduleTag, Component.VJOURNAL.name)
    @Test fun check_FLAGS() = insertRetrieveAssertInt(JtxICalObject.FLAGS, sample?.flags, Component.VJOURNAL.name)


    private fun insertRetrieveAssertString(field: String, fieldContent: String?, component: String) {

        assertNotNull(fieldContent)    // fieldContent should not be null, check if the testcase was built correctly

        val cv = ContentValues().apply {
            put(field, fieldContent)
            put(JtxICalObject.COMPONENT, component)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        client.query(uri, null, null, null, null)?.use {
            val itemCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, itemCV)
            assertEquals(fieldContent, itemCV.getAsString(field))
        }
    }

    private fun insertRetrieveAssertBoolean(field: String, fieldContent: Boolean?, component: String) {

        assertNotNull(fieldContent)    // fieldContent should not be null, check if the testcase was built correctly

        val cv = ContentValues().apply {
            put(field, fieldContent)
            put(JtxICalObject.COMPONENT, component)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        client.query(uri, null, null, null, null)?.use {
            val itemCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, itemCV)
            assertEquals(fieldContent, itemCV.getAsBoolean(field))
        }
    }

    private fun insertRetrieveAssertLong(field: String, fieldContent: Long?, component: String) {

        assertNotNull(fieldContent)    // fieldContent should not be null, check if the testcase was built correctly

        val cv = ContentValues().apply {
            put(field, fieldContent)
            put(JtxICalObject.COMPONENT, component)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        client.query(uri, null, null, null, null)?.use {
            val itemCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, itemCV)
            assertEquals(fieldContent, itemCV.getAsLong(field))
        }
    }

    private fun insertRetrieveAssertInt(field: String, fieldContent: Int?, component: String) {

        assertNotNull(fieldContent)    // fieldContent should not be null, check if the testcase was built correctly

        val cv = ContentValues().apply {
            put(field, fieldContent)
            put(JtxICalObject.COMPONENT, component)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        client.query(uri, null, null, null, null)?.use {
            val itemCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, itemCV)
            assertEquals(fieldContent, itemCV.getAsInteger(field))
        }
    }

    private fun insertRetrieveAssertDouble(field: String, fieldContent: Double?, component: String) {

        assertNotNull(fieldContent)    // fieldContent should not be null, check if the testcase was built correctly

        val cv = ContentValues().apply {
            put(field, fieldContent)
            put(JtxICalObject.COMPONENT, component)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        client.query(uri, null, null, null, null)?.use {
            val itemCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, itemCV)
            assertEquals(fieldContent, itemCV.getAsDouble(field))
        }
    }



    @Test
    fun assertComment() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val comment = at.bitfire.ical4android.JtxICalObject.Comment(
            text = "comment",
            altrep = "Kommentar",
            language = "de",
            other = "X-OTHER:Test"
        )

        val commentCV = ContentValues().apply {
            put(JtxContract.JtxComment.TEXT, comment.text)
            put(JtxContract.JtxComment.ALTREP, comment.altrep)
            put(JtxContract.JtxComment.LANGUAGE, comment.language)
            put(JtxContract.JtxComment.OTHER, comment.other)
            put(JtxContract.JtxComment.ICALOBJECT_ID, id)
        }

        val commentUri = client.insert(JtxContract.JtxComment.CONTENT_URI.asSyncAdapter(testAccount), commentCV)!!
        client.query(commentUri, null, null, null, null)?.use {
            val retrievedCommentCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedCommentCV)
            assertEquals(comment.text, retrievedCommentCV.getAsString(JtxContract.JtxComment.TEXT))
            assertEquals(comment.altrep, retrievedCommentCV.getAsString(JtxContract.JtxComment.ALTREP))
            assertEquals(comment.language, retrievedCommentCV.getAsString(JtxContract.JtxComment.LANGUAGE))
            assertEquals(comment.other, retrievedCommentCV.getAsString(JtxContract.JtxComment.OTHER))
        }
    }




    @Test
    fun assertResource() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val resource = at.bitfire.ical4android.JtxICalObject.Resource(
            text = "projector",
            altrep = "Projektor",
            language = "de",
            other = "X-OTHER:Test"
        )

        val resourceCV = ContentValues().apply {
            put(JtxContract.JtxResource.TEXT, resource.text)
            put(JtxContract.JtxResource.LANGUAGE, resource.language)
            put(JtxContract.JtxResource.OTHER, resource.other)
            put(JtxContract.JtxResource.ICALOBJECT_ID, id)
        }

        val resourceUri = client.insert(JtxContract.JtxResource.CONTENT_URI.asSyncAdapter(testAccount), resourceCV)!!
        client.query(resourceUri, null, null, null, null)?.use {
            val retrievedResourceCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedResourceCV)
            assertEquals(resource.text, retrievedResourceCV.getAsString(JtxContract.JtxResource.TEXT))
            assertEquals(resource.language, retrievedResourceCV.getAsString(JtxContract.JtxResource.LANGUAGE))
            assertEquals(resource.other, retrievedResourceCV.getAsString(JtxContract.JtxResource.OTHER))
        }
    }

    @Test
    fun assertAttendee() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val attendee = at.bitfire.ical4android.JtxICalObject.Attendee(
            caladdress = "jtx@techbee.at",
            cutype = JtxContract.JtxAttendee.Cutype.INDIVIDUAL.name,
            member = "group",
            partstat = "0",
            role = JtxContract.JtxAttendee.Role.`REQ-PARTICIPANT`.name,
            rsvp = false,
            delegatedfrom = "jtx@techbee.at",
            delegatedto = "jtx@techbee.at",
            sentby = "jtx@techbee.at",
            cn = "jtx Board",
            dir = "dir",
            language = "de",
            other = "X-OTHER:Test"
        )

        val attendeeCV = ContentValues().apply {
            put(JtxContract.JtxAttendee.CALADDRESS, attendee.caladdress)
            put(JtxContract.JtxAttendee.CUTYPE, attendee.cutype)
            put(JtxContract.JtxAttendee.MEMBER, attendee.member)
            put(JtxContract.JtxAttendee.PARTSTAT, attendee.partstat)
            put(JtxContract.JtxAttendee.ROLE, attendee.role)
            put(JtxContract.JtxAttendee.RSVP, attendee.rsvp)
            put(JtxContract.JtxAttendee.DELEGATEDFROM, attendee.delegatedfrom)
            put(JtxContract.JtxAttendee.DELEGATEDTO, attendee.delegatedto)
            put(JtxContract.JtxAttendee.SENTBY, attendee.sentby)
            put(JtxContract.JtxAttendee.CN, attendee.cn)
            put(JtxContract.JtxAttendee.DIR, attendee.dir)
            put(JtxContract.JtxAttendee.LANGUAGE, attendee.language)
            put(JtxContract.JtxAttendee.OTHER, attendee.other)
            put(JtxContract.JtxAttendee.ICALOBJECT_ID, id)
        }

        val attendeeUri = client.insert(JtxContract.JtxAttendee.CONTENT_URI.asSyncAdapter(testAccount), attendeeCV)!!
        client.query(attendeeUri, null, null, null, null)?.use {
            val retrievedAttendeeCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedAttendeeCV)
            assertEquals(attendee.caladdress, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.CALADDRESS))
            assertEquals(attendee.cutype, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.CUTYPE))
            assertEquals(attendee.member, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.MEMBER))
            assertEquals(attendee.partstat, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.PARTSTAT))
            assertEquals(attendee.role, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.ROLE))
            assertEquals(attendee.rsvp, retrievedAttendeeCV.getAsBoolean(JtxContract.JtxAttendee.RSVP))
            assertEquals(attendee.delegatedfrom, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.DELEGATEDFROM))
            assertEquals(attendee.delegatedto, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.DELEGATEDTO))
            assertEquals(attendee.sentby, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.SENTBY))
            assertEquals(attendee.cn, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.CN))
            assertEquals(attendee.dir, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.DIR))
            assertEquals(attendee.language, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.LANGUAGE))
            assertEquals(attendee.other, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.OTHER))
        }
    }


    @Test
    fun assertCategory() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val category = at.bitfire.ical4android.JtxICalObject.Category(
            text = "projector",
        )

        val categoryCV = ContentValues().apply {
            put(JtxContract.JtxCategory.TEXT, category.text)
            put(JtxContract.JtxCategory.ICALOBJECT_ID, id)
        }

        val categoryUri = client.insert(JtxContract.JtxCategory.CONTENT_URI.asSyncAdapter(testAccount), categoryCV)!!
        client.query(categoryUri, null, null, null, null)?.use {
            val retrievedCategoryCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedCategoryCV)
            assertEquals(category.text, retrievedCategoryCV.getAsString(JtxContract.JtxCategory.TEXT))
        }
    }


    @Test
    fun assertAttachment_without_binary() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val attachment = at.bitfire.ical4android.JtxICalObject.Attachment(
            uri = "https://jtx.techbee.at/sample.pdf",
            binary = "anR4IEJvYXJk",
            fmttype = "application/pdf",
            other = "X-OTHER:other",
        )

        val attachmentCV = ContentValues().apply {
            put(JtxContract.JtxAttachment.URI, attachment.uri)
            //put(JtxContract.JtxAttachment.BINARY, attachment.binary)
            put(JtxContract.JtxAttachment.FMTTYPE, attachment.fmttype)
            put(JtxContract.JtxAttachment.OTHER, attachment.other)
            put(JtxContract.JtxAttachment.ICALOBJECT_ID, id)
        }

        val attachmentUri = client.insert(JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(testAccount), attachmentCV)!!
        client.query(attachmentUri, null, null, null, null)?.use {
            val retrievedAttachmentCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedAttachmentCV)
            assertEquals(attachment.uri, retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.URI))
            //assertEquals(attachment.binary, retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.BINARY))
            assertEquals(attachment.fmttype, retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.FMTTYPE))
            assertEquals(attachment.other, retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.OTHER))
        }
    }


    @Test
    fun assertAttachment_with_binary() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val attachment = at.bitfire.ical4android.JtxICalObject.Attachment(
            //uri = "https://jtx.techbee.at/sample.pdf",
            binary = "anR4IEJvYXJk",
            fmttype = "application/pdf",
            other = "X-OTHER:other",
        )

        val attachmentCV = ContentValues().apply {
            //put(JtxContract.JtxAttachment.URI, attachment.uri)
            put(JtxContract.JtxAttachment.BINARY, attachment.binary)
            put(JtxContract.JtxAttachment.FMTTYPE, attachment.fmttype)
            put(JtxContract.JtxAttachment.OTHER, attachment.other)
            put(JtxContract.JtxAttachment.ICALOBJECT_ID, id)
        }

        val attachmentUri = client.insert(JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(testAccount), attachmentCV)!!
        client.query(attachmentUri, null, null, null, null)?.use {
            val retrievedAttachmentCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedAttachmentCV)
            assertTrue(retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.URI).startsWith("content://"))   // binary was replaced by content uri
            assertNull(retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.BINARY))
            assertEquals(attachment.fmttype, retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.FMTTYPE))
            assertEquals(attachment.other, retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.OTHER))
        }
    }


    @Test
    fun assertRelatedto() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val relatedto = at.bitfire.ical4android.JtxICalObject.RelatedTo(
            text = "1635164243187-3fd0f89e-d017-471e-a046-71ff1844d58e@at.techbee.jtx",
            reltype = JtxContract.JtxRelatedto.Reltype.CHILD.name,
            other = "X-OTHER: other"
        )

        val relatedtoCV = ContentValues().apply {
            put(JtxContract.JtxRelatedto.TEXT, relatedto.text)
            put(JtxContract.JtxRelatedto.RELTYPE, relatedto.reltype)
            put(JtxContract.JtxRelatedto.OTHER, relatedto.other)
            put(JtxContract.JtxRelatedto.ICALOBJECT_ID, id)
        }

        val relatedtoUri = client.insert(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(testAccount), relatedtoCV)!!
        client.query(relatedtoUri, null, null, null, null)?.use {
            val retrievedRelatedtoCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedRelatedtoCV)
            assertEquals(relatedto.text, retrievedRelatedtoCV.getAsString(JtxContract.JtxRelatedto.TEXT))
            assertEquals(relatedto.reltype, retrievedRelatedtoCV.getAsString(JtxContract.JtxRelatedto.RELTYPE))
            assertEquals(relatedto.other, retrievedRelatedtoCV.getAsString(JtxContract.JtxRelatedto.OTHER))
        }
    }





    @Test
    fun assertAlarm() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val alarm = at.bitfire.ical4android.JtxICalObject.Alarm(
            action = "AUDIO",
            description = "desc",
            summary = "summary",
            trigger = "DATE-TIME:19970317T133000Z",
            attendee = "jtx@techbee.at",
            duration = "PT15M",
            repeat = "4",
            attach = "FMTTYPE=audio/basic:ftp://example.com/pub/sounds/bell-01.aud",
            other = "X-OTHER: other"
        )

        val alarmCV = ContentValues().apply {
            put(JtxContract.JtxAlarm.ALARM_ACTION, alarm.action)
            put(JtxContract.JtxAlarm.ALARM_DESCRIPTION, alarm.description)
            put(JtxContract.JtxAlarm.ALARM_SUMMARY, alarm.summary)
            put(JtxContract.JtxAlarm.ALARM_TRIGGER, alarm.trigger)
            put(JtxContract.JtxAlarm.ALARM_ATTENDEE, alarm.attendee)
            put(JtxContract.JtxAlarm.ALARM_DURATION, alarm.duration)
            put(JtxContract.JtxAlarm.ALARM_REPEAT, alarm.repeat)
            put(JtxContract.JtxAlarm.ALARM_ATTACH, alarm.attach)
            put(JtxContract.JtxAlarm.ALARM_OTHER, alarm.other)
            put(JtxContract.JtxAlarm.ICALOBJECT_ID, id)
        }

        val alarmUri = client.insert(JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(testAccount), alarmCV)!!
        client.query(alarmUri, null, null, null, null)?.use {
            val retrievedAlarmCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedAlarmCV)
            assertEquals(alarm.action, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ALARM_ACTION))
            assertEquals(alarm.description, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ALARM_DESCRIPTION))
            assertEquals(alarm.summary, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ALARM_SUMMARY))
            assertEquals(alarm.trigger, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ALARM_TRIGGER))
            assertEquals(alarm.attendee, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ALARM_ATTENDEE))
            assertEquals(alarm.duration, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ALARM_DURATION))
            assertEquals(alarm.repeat, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ALARM_REPEAT))
            assertEquals(alarm.attach, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ALARM_ATTACH))
            assertEquals(alarm.other, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ALARM_OTHER))
        }
    }



    @Test
    fun assertUnknown() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val unknown = at.bitfire.ical4android.JtxICalObject.Unknown(
            value = "X-PROP:my value"
        )

        val unknownCV = ContentValues().apply {
            put(JtxContract.JtxUnknown.UNKNOWN_VALUE, unknown.value)
            put(JtxContract.JtxUnknown.ICALOBJECT_ID, id)
        }

        val unknownUri = client.insert(JtxContract.JtxUnknown.CONTENT_URI.asSyncAdapter(testAccount), unknownCV)!!
        client.query(unknownUri, null, null, null, null)?.use {
            val retrievedUnknownCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedUnknownCV)
            assertEquals(unknown.value, retrievedUnknownCV.getAsString(JtxContract.JtxUnknown.UNKNOWN_VALUE))
        }
    }

}
