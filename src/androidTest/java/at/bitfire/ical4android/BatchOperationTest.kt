/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.filters.FlakyTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.MiscUtils.ContentProviderClientHelper.closeCompat
import at.bitfire.ical4android.MiscUtils.UriHelper.asSyncAdapter
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.impl.TestEvent
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import org.junit.*
import org.junit.Assert.*

import java.net.URI
import java.util.*

class BatchOperationTest {

    companion object {

        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )

        lateinit var provider: ContentProviderClient

        @BeforeClass
        @JvmStatic
        fun connectProvider() {
            provider = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
        }

        @AfterClass
        @JvmStatic
        fun closeProvider() {
            provider.closeCompat()
        }

    }

    private val testAccount = Account("ical4android@example.com", CalendarContract.ACCOUNT_TYPE_LOCAL)

    private lateinit var calendarUri: Uri
    private lateinit var calendar: TestCalendar

    @Before
    fun prepare() {
        calendar = TestCalendar.findOrCreate(testAccount, provider)
        assertNotNull(calendar)
        calendarUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendar.id)
    }

    @After
    fun shutdown() {
        calendar.delete()
    }

    @Test
    fun testTransactionSplitting() {
        val event = Event()
        event.uid = "sample1@testLargeTransaction"
        event.summary = "Large event"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        for (i in 0 until 2000) //  2000 are enough for a transaction split to happen, but won't make the test fail in CI server environment
            event.attendees += Attendee(URI("mailto:att$i@example.com"))
        val uri = TestEvent(calendar, event).add()

        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {
            assertEquals(2000, testEvent.event!!.attendees.size)
        } finally {
            testEvent.delete()
        }
    }

    @FlakyTest
    @Test
    fun testLargeTransactionSplitting() {
        // This test only fails on the CI server emulator (see issue #42)
        val event = Event()
        event.uid = "sample1@testLargeTransaction"
        event.summary = "Large event"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        for (i in 0 until 10000) //  2000 are enough for a transaction split to happen, but won't make the test fail in CI server environment
            event.attendees += Attendee(URI("mailto:att$i@example.com"))
        val uri = TestEvent(calendar, event).add()

        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {
            assertEquals(10000, testEvent.event!!.attendees.size)
        } finally {
            testEvent.delete()
        }
    }

    @Test(expected = CalendarStorageException::class)
    fun testLargeTransactionSingleRow() {
        val event = Event()
        event.uid = "sample1@testLargeTransaction"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")

        // 1 MB SUMMARY ... have fun
        val data = CharArray(1024*1024)
        Arrays.fill(data, 'x')
        event.summary = String(data)

        TestEvent(calendar, event).add()
    }
}