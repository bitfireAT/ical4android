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
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
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
}
