/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.ical4android

import android.Manifest
import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.database.DatabaseUtils
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.AndroidCalendar.Companion.syncAdapterURI
import at.bitfire.ical4android.MiscUtils.ContentProviderClientHelper.closeCompat
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.impl.TestEvent
import at.bitfire.ical4android.util.AndroidTimeUtils
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.*
import net.fortuna.ical4j.util.TimeZones
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URI
import java.time.Duration
import java.util.*

class AndroidEventTest {

    @JvmField
    @Rule
    val permissionRule = GrantPermissionRule.grant(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
    )!!

    private val testAccount = Account("ical4android.AndroidEventTest", CalendarContract.ACCOUNT_TYPE_LOCAL)

    private val tzVienna = DateUtils.ical4jTimeZone("Europe/Vienna")!!
    private val tzShanghai = DateUtils.ical4jTimeZone("Asia/Shanghai")!!

    private val tzIdDefault = java.util.TimeZone.getDefault().id
    private val tzDefault = DateUtils.ical4jTimeZone(tzIdDefault)


    private val provider by lazy {
        getInstrumentation().targetContext.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
    }
    private lateinit var calendarUri: Uri
    private lateinit var calendar: TestCalendar

    @Before
    fun prepare() {
        AndroidCalendar.insertColors(provider, testAccount)

        calendar = TestCalendar.findOrCreate(testAccount, provider)
        assertNotNull(calendar)

        calendarUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendar.id)
    }

    @After
    fun shutdown() {
        calendar.delete()
        provider.closeCompat()
    }


    /**
     * buildEvent() BASIC TEST MATRIX:
     *
     * all-day event | hasDtEnd | hasDuration | recurring event | notes
     *        0            0            0              0          dtEnd = dtStart
     *        0            0            0              1          duration = 0s, rRule/rDate set
     *        0            0            1              0          dtEnd calulcated from duration
     *        0            0            1              1
     *        0            1            0              0
     *        0            1            0              1          dtEnd calulcated from duration
     *        0            1            1              0          duration ignored
     *        0            1            1              1          dtEnd ignored
     *        1            0            0              0          duration = 1d
     *        1            0            0              1          duration = 1d
     *        1            0            1              0          dtEnd calculated from duration
     *        1            0            1              1
     *        1            1            0              0
     *        1            1            0              1          duration calculated from dtEnd; ignore times in rDate
     *        1            1            1              0          duration ignored
     *        1            1            1              1          dtEnd ignored
     *
     *  buildEvent() EXTRA TESTS:
     *
     *  - floating times
     *  - floating times in rdate/exdate
     *  - UTC times
     */

    private fun buildEvent(eventBuilder: Event.() -> Unit): ContentValues {
        val event = Event().apply {
            eventBuilder()
        }
        val uri = TestEvent(calendar, event).add()
        calendar.provider.query(uri, null, null, null, null)!!.use {
            it.moveToNext()
            val values = ContentValues()
            DatabaseUtils.cursorRowToContentValues(it, values)
            return values
        }
    }

    @Test
    fun testBuildEvent_NonAllDay_NoDtEnd_NoDuration_NonRecurring() {
        val values = buildEvent {
            dtStart = DtStart("20200601T123000", tzVienna)
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1591007400000L, values.getAsLong(Events.DTEND))
        assertEquals(tzVienna.id, values.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun testBuildEvent_NonAllDay_NoDtEnd_NoDuration_Recurring() {
        val values = buildEvent {
            dtStart = DtStart("20200601T123000", tzVienna)
            rRules += RRule("FREQ=DAILY;COUNT=5")
            rRules += RRule("FREQ=WEEKLY;COUNT=10")
            rDates += RDate(DateList("20210601T123000", Value.DATE_TIME, tzVienna))
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals("P0D", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=DAILY;COUNT=5\nFREQ=WEEKLY;COUNT=10", values.getAsString(Events.RRULE))
        assertEquals("${tzVienna.id};20210601T123000", values.getAsString(Events.RDATE))
    }

    @Test
    fun testBuildEvent_NonAllDay_NoDtEnd_Duration_NonRecurring() {
        val values = buildEvent {
            dtStart = DtStart("20200601T123000", tzVienna)
            duration = Duration(null, "PT1H30M")
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1591007400000L + 90*60000, values.getAsLong(Events.DTEND))
        assertEquals(tzVienna.id, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_NonAllDay_NoDtEnd_Duration_Recurring() {
        val values = buildEvent {
            dtStart = DtStart("20200601T123000", tzVienna)
            duration = Duration(null, "PT1H30M")
            rDates += RDate(DateList("20200602T113000", Value.DATE_TIME, tzVienna))
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals("PT1H30M", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("${tzVienna.id};20200602T113000", values.get(Events.RDATE))
    }

    @Test
    fun testBuildEvent_NonAllDay_DtEnd_NoDuration_NonRecurring() {
        val values = buildEvent {
            dtStart = DtStart("20200601T123000", tzVienna)
            dtEnd = DtEnd("20200602T143000", tzShanghai)
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1591079400000L, values.getAsLong(Events.DTEND))
        assertEquals(tzShanghai.id, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_NonAllDay_DtEnd_NoDuration_Recurring() {
        val values = buildEvent {
            dtStart = DtStart("20200601T123000", tzShanghai)
            dtEnd = DtEnd("20200601T123000", tzVienna)
            rDates += RDate(DateList("20200701T123000,20200702T123000", Value.DATE_TIME, tzVienna))
            rDates += RDate(DateList("20200801T123000,20200802T123000", Value.DATE_TIME, tzShanghai))
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590985800000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzShanghai.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals("PT6H", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("${tzVienna.id};20200701T123000,20200702T123000,20200801T063000,20200802T063000", values.getAsString(Events.RDATE))
    }

    @Test
    fun testBuildEvent_NonAllDay_DtEnd_Duration_NonRecurring() {
        val values = buildEvent {
            dtStart = DtStart("20200601T123000", tzVienna)
            dtEnd = DtEnd("20200601T143000", tzVienna)
            duration = Duration(null, "PT10S")
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1591014600000L, values.getAsLong(Events.DTEND))
        assertEquals(tzVienna.id, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_NonAllDay_DtEnd_Duration_Recurring() {
        val values = buildEvent {
            dtStart = DtStart("20200601T123000", tzVienna)
            dtEnd = DtEnd("20200601T143000", tzVienna)
            duration = Duration(null, "PT10S")
            rRules += RRule("FREQ=MONTHLY;COUNT=1")
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals("PT10S", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=MONTHLY;COUNT=1", values.get(Events.RRULE))
    }

    @Test
    fun testBuildEvent_AllDay_NoDtEnd_NoDuration_NonRecurring() {
        val values = buildEvent {
            dtStart = DtStart(Date("20200601"))
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1591056000000L, values.getAsLong(Events.DTEND))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_AllDay_NoDtEnd_NoDuration_Recurring() {
        val values = buildEvent {
            dtStart = DtStart(Date("20200601"))
            rRules += RRule("FREQ=MONTHLY;COUNT=3")
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals("P1D", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=MONTHLY;COUNT=3", values.get(Events.RRULE))
    }

    @Test
    fun testBuildEvent_AllDay_NoDtEnd_Duration_NonRecurring() {
        val values = buildEvent {
            dtStart = DtStart(Date("20200601"))
            duration = Duration(null, "P2W1D")
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1592265600000L, values.getAsLong(Events.DTEND))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_AllDay_NoDtEnd_Duration_Recurring() {
        val values = buildEvent {
            dtStart = DtStart(Date("20200601"))
            duration = Duration(null, "P2D")
            rRules += RRule("FREQ=YEARLY;BYMONTH=4;BYDAY=-1SU")
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals("P2D", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=YEARLY;BYMONTH=4;BYDAY=-1SU", values.get(Events.RRULE))
    }

    @Test
    fun testBuildEvent_AllDay_DtEnd_NoDuration_NonRecurring() {
        val values = buildEvent {
            dtStart = DtStart(Date("20200601"))
            dtEnd = DtEnd(Date("20200701"))
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1593561600000L, values.getAsLong(Events.DTEND))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_AllDay_DtEnd_NoDuration_Recurring() {
        val values = buildEvent {
            dtStart = DtStart(Date("20200601"))
            dtEnd = DtEnd(Date("20200701"))
            rDates += RDate(DateList("20210601", Value.DATE))
            rDates += RDate(DateList("20220601T120030", Value.DATE_TIME))
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals("P30D", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("20210601T000000Z,20220601T000000Z", values.get(Events.RDATE))
    }

    @Test
    fun testBuildEvent_AllDay_DtEnd_Duration_NonRecurring() {
        val values = buildEvent {
            dtStart = DtStart(Date("20200601"))
            dtEnd = DtEnd(Date("20200701"))
            duration = Duration(null, "PT1M")
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1593561600000L, values.getAsLong(Events.DTEND))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_AllDay_DtEnd_Duration_Recurring() {
        val values = buildEvent {
            dtStart = DtStart(Date("20200601"))
            dtEnd = DtEnd(Date("20200701"))
            duration = Duration(null, "PT1M")
            rRules += RRule("FREQ=DAILY;COUNT=1")
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals("PT1M", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=DAILY;COUNT=1", values.get(Events.RRULE))
    }

    @Test
    fun testBuildEvent_FloatingTimes() {
        val values = buildEvent {
            dtStart = DtStart("20200601T123000")
            dtEnd = DtEnd("20200601T123001")
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(DateTime("20200601T123000", tzDefault).time, values.getAsLong(Events.DTSTART))
        assertEquals(tzIdDefault, values.get(Events.EVENT_TIMEZONE))

        assertEquals(DateTime("20200601T123001", tzDefault).time, values.getAsLong(Events.DTEND))
        assertEquals(tzIdDefault, values.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun testBuildEvent_FloatingTimesInRecurrenceDates() {
        val values = buildEvent {
            dtStart = DtStart("20200601T123000", tzShanghai)
            duration = Duration(null, "PT5M30S")
            rDates += RDate(DateList("20200602T113000", Value.DATE_TIME))
            exDates += ExDate(DateList("20200602T113000", Value.DATE_TIME))
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590985800000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzShanghai.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals("PT5M30S", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("$tzIdDefault;20200602T113000", values.get(Events.RDATE))
        assertEquals("$tzIdDefault;20200602T113000", values.get(Events.EXDATE))
    }

    @Test
    fun testBuildEvent_UTC() {
        val values = buildEvent {
            dtStart = DtStart("20200601T123000Z")
            dtEnd = DtEnd("20200601T143001Z")
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591014600000L, values.getAsLong(Events.DTSTART))
        assertEquals(TimeZones.UTC_ID, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1591021801000L, values.getAsLong(Events.DTEND))
        assertEquals(TimeZones.UTC_ID, values.get(Events.EVENT_END_TIMEZONE))
    }


    @Test
    fun testAddRecurringEvent() {
        // build and write recurring event to calendar provider
        val event = Event()
        event.uid = "sample1@testAddEvent"
        event.summary = "Sample event"
        event.description = "Sample event with date/time"
        event.location = "Sample location"
        event.dtStart = DtStart("20150501T120000", tzVienna)
        event.dtEnd = DtEnd("20150501T130000", tzVienna)
        event.organizer = Organizer(URI("mailto:organizer@example.com"))
        event.rRules += RRule("FREQ=DAILY;COUNT=10")
        event.rRules += RRule("FREQ=WEEKLY;COUNT=8")
        event.classification = Clazz.PRIVATE
        event.status = Status.VEVENT_CONFIRMED
        event.color = Css3Color.aliceblue

        // TODO test rDates, exDate, duration

        // set an alarm one day, two hours, three minutes and four seconds before begin of event
        event.alarms += VAlarm(Duration.parse("-P1DT2H3M4S"))

        // add two attendees
        event.attendees += Attendee(URI("mailto:user1@example.com"))
        event.attendees += Attendee(URI("mailto:user2@example.com"))

        // add exception with alarm and attendee
        val exception = Event()
        exception.recurrenceId = RecurrenceId("20150502T120000", tzVienna)
        exception.summary = "Exception for sample event"
        exception.dtStart = DtStart("20150502T140000", tzVienna)
        exception.dtEnd = DtEnd("20150502T150000", tzVienna)
        exception.alarms += VAlarm(Duration.parse("-P2DT3H4M5S"))
        exception.attendees += Attendee(URI("mailto:only.here@today"))
        event.exceptions += exception

        // add EXDATE
        event.exDates += ExDate(DateList("20150502T120000", Value.DATE_TIME, tzVienna))

        // add special properties
        event.unknownProperties.add(Categories("CAT1,CAT2"))
        event.unknownProperties.add(XProperty("X-NAME", "X-Value"))

        // add to calendar
        val uri = TestEvent(calendar, event).add()
        assertNotNull(uri)

        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {

            // read and parse event from calendar provider
            assertNotNull(testEvent)
            val event2 = testEvent.event!!

            // compare with original event
            assertEquals(event.summary, event2.summary)
            assertEquals(event.description, event2.description)
            assertEquals(event.location, event2.location)
            assertEquals(event.dtStart, event2.dtStart)

            // recurring event: duration is calculated from dtStart and dtEnd; dtEnd is set to null
            assertEquals(Duration.ofHours(1), event2.duration?.duration)
            assertNull(event2.dtEnd)

            assertEquals(event.organizer, event2.organizer)
            assertArrayEquals(event.rRules.toTypedArray(), event2.rRules.toTypedArray())
            assertEquals(event.classification, event2.classification)
            assertEquals(event.status, event2.status)
            assertEquals(event.color, event2.color)

            // compare alarm
            assertEquals(1, event2.alarms.size)
            var alarm2 = event2.alarms.first
            assertEquals(event.summary, alarm2.description.value)  // should be built from event title
            assertEquals(Duration.ofMinutes(-(24*60 + 2*60 + 3)), alarm2.trigger.duration)   // calendar provider stores trigger in minutes

            // compare attendees
            assertEquals(2, event2.attendees.size)
            assertEquals(event.attendees[0].calAddress, event2.attendees[0].calAddress)
            assertEquals(event.attendees[1].calAddress, event2.attendees[1].calAddress)

            // compare exception
            assertEquals(1, event2.exceptions.size)
            val exception2 = event2.exceptions.first
            assertEquals(exception.recurrenceId!!.date, exception2.recurrenceId!!.date)
            assertEquals(exception.summary, exception2.summary)
            assertEquals(exception.dtStart, exception2.dtStart)
            assertEquals(exception.dtEnd, exception2.dtEnd)

            // compare exception alarm
            assertEquals(1, exception2.alarms.size)
            alarm2 = exception2.alarms.first
            assertEquals(exception.summary, alarm2.description.value)
            assertEquals(Duration.ofMinutes(-(2*24*60 + 60*3 + 4)), alarm2.trigger.duration)   // calendar provider stores trigger in minutes

            // compare exception attendee
            assertEquals(1, exception2.attendees.size)
            assertEquals(exception.attendees.first.calAddress, exception2.attendees.first.calAddress)

            // compare EXDATE
            assertEquals(1, event2.exDates.size)
            assertEquals(event.exDates.first, event2.exDates.first)

            // compare unknown properties
            assertArrayEquals(event.unknownProperties.toArray(), event2.unknownProperties.toArray())
        } finally {
            testEvent.delete()
        }
    }

    @Test
    fun testUpdateEvent() {
        // add test event without reminder
        val event = Event()
        event.uid = "sample1@testAddEvent"
        event.summary = "Sample event"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        event.organizer = Organizer(URI("mailto:organizer@example.com"))
        val uri = TestEvent(calendar, event).add()

        // update test event in calendar
        val testEvent = calendar.findById(ContentUris.parseId(uri))
        val event2 = testEvent.event!!
        event2.summary = "Updated event"
        // add data rows
        event2.alarms += VAlarm(Duration.parse("-P1DT2H3M4S"))
        event2.attendees += Attendee(URI("mailto:user@example.com"))
        val uri2 = testEvent.update(event2)

        // read again and verify result
        val updatedEvent = calendar.findById(ContentUris.parseId(uri2))
        try {
            val event3 = updatedEvent.event!!
            assertEquals(event2.summary, event3.summary)
            assertEquals(1, event3.alarms.size)
            assertEquals(1, event3.attendees.size)
        } finally {
            updatedEvent.delete()
        }
    }

    /*@LargeTest
    @Test
    fun testLargeTransactionManyRows() {
        val event = Event()
        event.uid = "sample1@testLargeTransaction"
        event.summary = "Large event"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        for (i in 0 until 4000)
            event.attendees += Attendee(URI("mailto:att$i@example.com"))
        val uri = TestEvent(calendar, event).add()

        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {
            assertEquals(4000, testEvent.event!!.attendees.size)
        } finally {
            testEvent.delete()
        }
    }*/

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

    @Test
    fun testAllDayWithoutDtEndOrDuration() {
        // add event without dtEnd/duration to calendar provider
        val event = Event()
        event.summary = "Event without duration"
        event.dtStart = DtStart(Date("20150501"))
        val uri = TestEvent(calendar, event).add()
        assertNotNull(uri)

        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {
            // read again and verify result
            val event2 = testEvent.event!!
            // should now be an all-day event (converted by ical4android because events without duration don't show up in Android calendar)
            assertEquals(event.dtStart, event2.dtStart)
            assertEquals(event.dtStart!!.date.time + 86400000, event2.dtEnd!!.date.time)
        } finally {
            testEvent.delete()
        }
    }

    @Test
    fun testCalculateEndFromDuration() {
        // add event without dtEnd/duration to calendar provider
        val event = Event()
        event.summary = "Event without end, but with duration"
        event.dtStart = DtStart(DateTime("20150501T020304", tzVienna))
        event.duration = Duration(null, "PT1H30M")
        val uri = TestEvent(calendar, event).add()
        assertNotNull(uri)

        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {
            // read again and verify result
            val event2 = testEvent.event!!
            assertEquals(event.dtStart, event2.dtStart)
            assertEquals(event.dtStart!!.date.time + 90*60000, event2.dtEnd!!.date.time)
        } finally {
            testEvent.delete()
        }
    }

    @Test
    fun testAllDayWithZeroDuration() {
        // add all-day event without dtEnd and duration
        val event = Event()
        event.summary = "Event with zero duration"
        event.dtStart = DtStart(Date("20150501"))
        val uri = TestEvent(calendar, event).add()
        assertNotNull(uri)

        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {
            // read again and verify result
            val event2 = testEvent.event!!
            // should now be an all-day event with a duration of one day
            assertTrue(DateUtils.isDate(event2.dtStart))
            assertEquals(event.dtStart, event2.dtStart)
            assertEquals(event.dtStart!!.date.time + 86400000, event2.dtEnd!!.date.time)
        } finally {
            testEvent.delete()
        }
    }

    @Test
    fun testClassificationConfidential() {
        val event = Event()
        event.summary = "Confidential event"
        event.dtStart = DtStart(Date("20150501"))
        event.dtEnd = DtEnd(Date("20150502"))
        event.classification = Clazz.CONFIDENTIAL
        val uri = TestEvent(calendar, event).add()
        assertNotNull(uri)
        val id = ContentUris.parseId(uri)

        // now, the calendar app changes to ACCESS_DEFAULT
        val values = ContentValues(1)
        values.put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT)
        calendar.provider.update(calendar.syncAdapterURI(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)),
                values, null, null)

        val testEvent = calendar.findById(id)
        try {
            // read again and verify result
            val event2 = testEvent.event!!
            // CONFIDENTIAL has been retained
            assertTrue(event.unknownProperties.contains(Clazz.CONFIDENTIAL))
            // should still be CONFIDENTIAL
            assertEquals(event.classification, event2.classification)

            // now, the calendar app changes to ACCESS_PRIVATE
            values.put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE)
            calendar.provider.update(calendar.syncAdapterURI(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)),
                    values, null, null)

            // read again and verify result
            val testEventPrivate = calendar.findById(id)
            val eventPrivate = testEventPrivate.event!!
            // should be PRIVATE
            assertEquals(Clazz.PRIVATE, eventPrivate.classification)
            // the retained value is not used in this case
            assertFalse(eventPrivate.unknownProperties.contains(Clazz.CONFIDENTIAL))
        } finally {
            testEvent.delete()
        }
    }

    @Test
    fun testClassificationPrivate() {
        val event = Event()
        event.summary = "Private event"
        event.dtStart = DtStart(Date("20150501"))
        event.dtEnd = DtEnd(Date("20150502"))
        event.classification = Clazz.PRIVATE
        val uri = TestEvent(calendar, event).add()
        assertNotNull(uri)
        val id = ContentUris.parseId(uri)

        val testEvent = calendar.findById(id)
        try {
            // read again and verify result
            val event2 = testEvent.event!!
            // PRIVATE has not been retained
            assertFalse(event.unknownProperties.contains(Clazz.PRIVATE))
            // should still be PRIVATE
            assertEquals(Clazz.PRIVATE, event2.classification)
        } finally {
            testEvent.delete()
        }
    }

    @Test
    fun testNoOrganizerWithoutAttendees() {
        val event = Event()
        event.summary = "Not a group-scheduled event"
        event.dtStart = DtStart(Date("20150501"))
        event.dtEnd = DtEnd(Date("20150502"))
        event.rRules += RRule("FREQ=DAILY;COUNT=10;INTERVAL=1")
        event.organizer = Organizer("mailto:test@test.at")

        val exception = Event()
        exception.recurrenceId = RecurrenceId(Date("20150502"))
        exception.dtStart = DtStart(Date("20150502"))
        exception.dtEnd = DtEnd(Date("20150503"))
        exception.status = Status.VEVENT_CANCELLED
        exception.organizer = event.organizer
        event.exceptions += exception

        val uri = TestEvent(calendar, event).add()
        assertNotNull(uri)

        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {
            // read again and verify result
            val event2 = testEvent.event!!
            assertNull(event2.organizer)
            val exception2 = event2.exceptions.first
            assertNull(exception2.organizer)
        } finally {
            testEvent.delete()
        }
    }

    @Test
    fun testPopulateEventWithoutDuration() {
        val values = ContentValues()
        values.put(CalendarContract.Events.CALENDAR_ID, calendar.id)
        values.put(CalendarContract.Events.DTSTART, 1381330800000L)
        values.put(CalendarContract.Events.EVENT_TIMEZONE, "Europe/Vienna")
        values.put(CalendarContract.Events.TITLE, "Without dtend/duration")
        val uri = provider.insert(syncAdapterURI(CalendarContract.Events.CONTENT_URI, testAccount), values)!!

        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {
            assertNull(testEvent.event!!.dtEnd)
        } finally {
            testEvent.delete()
        }
    }


}
