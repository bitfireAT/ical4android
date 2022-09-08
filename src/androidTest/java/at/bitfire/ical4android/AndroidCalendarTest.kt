/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Colors
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.impl.TestEvent
import at.bitfire.ical4android.util.MiscUtils.ContentProviderClientHelper.closeCompat
import at.bitfire.ical4android.util.MiscUtils.UriHelper.asSyncAdapter
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

class AndroidCalendarTest {

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

    private val testAccount = Account("ical4android.AndroidCalendarTest", CalendarContract.ACCOUNT_TYPE_LOCAL)

    @Before
    fun prepare() {
        // make sure there are no colors for testAccount
        AndroidCalendar.removeColors(provider, testAccount)
        assertEquals(0, countColors(testAccount))
    }


    @Test
    fun testManageCalendars() {
        // create calendar
        val info = ContentValues()
        info.put(Calendars.NAME, "TestCalendar")
        info.put(Calendars.CALENDAR_DISPLAY_NAME, "ical4android Test Calendar")
        info.put(Calendars.VISIBLE, 0)
        info.put(Calendars.SYNC_EVENTS, 0)
        val uri = AndroidCalendar.create(testAccount, provider, info)
        assertNotNull(uri)

        // query calendar
        val calendar = AndroidCalendar.findByID(testAccount, provider, TestCalendar.Factory, ContentUris.parseId(uri))
        assertNotNull(calendar)

        // delete calendar
        assertEquals(1, calendar.delete())
    }


    @Test
    fun testInsertColors() {
        AndroidCalendar.insertColors(provider, testAccount)
        assertEquals(Css3Color.values().size, countColors(testAccount))
    }

    @Test
    fun testInsertColors_AlreadyThere() {
        AndroidCalendar.insertColors(provider, testAccount)
        AndroidCalendar.insertColors(provider, testAccount)
        assertEquals(Css3Color.values().size, countColors(testAccount))
    }

    @Test
    fun testRemoveColors() {
        AndroidCalendar.insertColors(provider, testAccount)

        // insert an event with that color
        val cal = TestCalendar.findOrCreate(testAccount, provider)
        try {
            // add event with color
            TestEvent(cal, Event().apply {
                dtStart = DtStart("20210314T204200Z")
                dtEnd = DtEnd("20210314T204230Z")
                color = Css3Color.limegreen
                summary = "Test event with color"
            }).add()

            AndroidCalendar.removeColors(provider, testAccount)
            assertEquals(0, countColors(testAccount))
        } finally {
            cal.delete()
        }
    }

    private fun countColors(account: Account): Int {
        val uri = Colors.CONTENT_URI.asSyncAdapter(testAccount)
        provider.query(uri, null, null, null, null)!!.use { cursor ->
            cursor.moveToNext()
            return cursor.count
        }
    }

}
