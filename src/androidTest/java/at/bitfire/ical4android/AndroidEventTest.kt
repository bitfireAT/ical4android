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
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.AndroidCalendar.Companion.syncAdapterURI
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.impl.TestEvent
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.Dur
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URI
import java.util.*

class AndroidEventTest {

    @JvmField
    @Rule
    val permissionRule = GrantPermissionRule.grant(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
    )!!

    private val testAccount = Account("ical4android.AndroidEventTest", CalendarContract.ACCOUNT_TYPE_LOCAL)
    private val tzVienna = DateUtils.tzRegistry.getTimeZone("Europe/Vienna")

    private lateinit var provider: ContentProviderClient
    private lateinit var calendarUri: Uri
    private lateinit var calendar: TestCalendar

    @Before
    fun prepare() {
        provider = getInstrumentation().targetContext.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!

        AndroidCalendar.insertColors(provider, testAccount)

        calendar = TestCalendar.findOrCreate(testAccount, provider)
        assertNotNull(calendar)

        calendarUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendar.id)
    }

    @After
    fun shutdown() {
        calendar.delete()

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 24)
            provider.close()
        else
            provider.release()
    }


    @MediumTest
    @Test
    fun testAddEvent() {
        // build and write recurring event to calendar provider
        val event = Event()
        event.uid = ("sample1@testAddEvent")
        event.summary = ("Sample event")
        event.description = ("Sample event with date/time")
        event.location = ("Sample location")
        event.dtStart = (DtStart("20150501T120000", tzVienna))
        event.dtEnd = (DtEnd("20150501T130000", tzVienna))
        event.organizer = (Organizer(URI("mailto:organizer@example.com")))
        event.rRule = (RRule("FREQ=DAILY;COUNT=10"))
        event.classification = (Clazz.PRIVATE)
        event.status = (Status.VEVENT_CONFIRMED)
        event.color = (EventColor.aliceblue)
        assertFalse(event.isAllDay())

        // TODO test rDates, exDate, duration

        // set an alarm one day, two hours, three minutes and four seconds before begin of event
        event.alarms += VAlarm(Dur(-1, -2, -3, -4))

        // add two attendees
        event.attendees += Attendee(URI("mailto:user1@example.com"))
        event.attendees += Attendee(URI("mailto:user2@example.com"))

        // add exception with alarm and attendee
        val exception = Event()
        exception.recurrenceId = RecurrenceId("20150502T120000", tzVienna)
        exception.summary = "Exception for sample event"
        exception.dtStart = DtStart("20150502T140000", tzVienna)
        exception.dtEnd = DtEnd("20150502T150000", tzVienna)
        exception.alarms += VAlarm(Dur(-2, -3, -4, -5))
        exception.attendees += Attendee(URI("mailto:only.here@today"))
        event.exceptions += exception

        // add EXDATE
        event.exDates += ExDate(DateList("20150502T120000", Value.DATE_TIME, tzVienna))

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
            assertFalse(event2.isAllDay())
            assertEquals(event.organizer, event2.organizer)
            assertEquals(event.rRule, event2.rRule)
            assertEquals(event.classification, event2.classification)
            assertEquals(event.status, event2.status)

            if (Build.VERSION.SDK_INT >= 23)
                assertEquals(event.color, event2.color)

            // compare alarm
            assertEquals(1, event2.alarms.size)
            var alarm2 = event2.alarms.first
            assertEquals(event.summary, alarm2.description.value)  // should be built from event title
            assertEquals(Dur(0, 0, -(24 * 60 + 60 * 2 + 3), 0), alarm2.trigger.duration)   // calendar provider stores trigger in minutes

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
            assertEquals(Dur(0, 0, -(2 * 24 * 60 + 60 * 3 + 4), 0), alarm2.trigger.duration)   // calendar provider stores trigger in minutes

            // compare exception attendee
            assertEquals(1, exception2.attendees.size)
            assertEquals(exception.attendees.first.calAddress, exception2.attendees.first.calAddress)

            // compare EXDATE
            assertEquals(1, event2.exDates.size)
            assertEquals(event.exDates.first, event2.exDates.first)
        } finally {
            testEvent.delete()
        }
    }

    @MediumTest
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
        event2.alarms += VAlarm(Dur(-1, -2, -3, -4))
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

    @MediumTest
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
    }

    @MediumTest
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

    @MediumTest
    @Test
    fun testAllDay() {
        // add all-day event to calendar provider
        val event = Event()
        event.summary = "All-day event"
        event.description = "All-day event for testing"
        event.location = "Sample location testBuildAllDayEntry"
        event.dtStart = DtStart(Date("20150501"))
        event.dtEnd = DtEnd(Date("20150501"))  // "events on same day" are not understood by Android, so it should be changed to next day
        assertTrue(event.isAllDay())
        val uri = TestEvent(calendar, event).add()
        assertNotNull(uri)

        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {
            // read again and verify result
            val event2 = testEvent.event!!
            // compare with original event
            assertEquals(event.summary, event2.summary)
            assertEquals(event.description, event2.description)
            assertEquals(event.location, event2.location)
            assertEquals(event.dtStart, event2.dtStart)
            assertEquals(event.dtEnd!!.date, Date("20150502"))
            assertTrue(event2.isAllDay())
        } finally {
            testEvent.delete()
        }
    }

    @MediumTest
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
            assertTrue(event2.isAllDay())
        } finally {
            testEvent.delete()
        }
    }

    @MediumTest
    @Test
    fun testAllDayWithZeroDuration() {
        // add all-day event with 0 sec duration to calendar provider
        val event = Event()
        event.summary = "Event with zero duration"
        event.dtStart = DtStart(Date("20150501"))
        event.duration = Duration(Dur("PT0S"))
        val uri = TestEvent(calendar, event).add()
        assertNotNull(uri)

        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {
            // read again and verify result
            val event2 = testEvent.event!!
            // should now be an all-day event (converted by ical4android because events without duration don't show up in Android calendar)
            assertEquals(event.dtStart, event2.dtStart)
            assertEquals(event.dtStart!!.date.time + 86400000, event2.dtEnd!!.date.time)
            assertTrue(event2.isAllDay())
        } finally {
            testEvent.delete()
        }
    }

    @MediumTest
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

    @MediumTest
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

    @MediumTest
    @Test
    fun testNoOrganizerWithoutAttendees() {
        val event = Event()
        event.summary = "Not a group-scheduled event"
        event.dtStart = DtStart(Date("20150501"))
        event.dtEnd = DtEnd(Date("20150502"))
        event.rRule = RRule("FREQ=DAILY;COUNT=10;INTERVAL=1")
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

    @MediumTest
    @Test
    fun testPopulateEventWithoutDuration() {
        val values = ContentValues()
        values.put(CalendarContract.Events.CALENDAR_ID, calendar.id)
        values.put(CalendarContract.Events.DTSTART, 1381330800000L)
        values.put(CalendarContract.Events.EVENT_TIMEZONE, "Europe/Vienna")
        values.put(CalendarContract.Events.TITLE, "Without dtend/duration")
        val uri = provider.insert(syncAdapterURI(CalendarContract.Events.CONTENT_URI, testAccount), values)

        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {
            assertNull(testEvent.event!!.dtEnd)
        } finally {
            testEvent.delete()
        }
    }

    @MediumTest
    @Test
    fun testWithZeroDuration() {
        // add event with 0 sec duration to calendar provider
        val event = Event()
        event.summary = ("Event with zero duration")
        event.dtStart = (DtStart(Date("20150501T152010Z")))
        event.duration = (Duration(Dur("PT0S")))
        val uri = TestEvent(calendar, event).add()
        assertNotNull(uri)

        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {
            // read again and verify result
            val event2 = testEvent.event!!
            // should now be an event with one day duration (converted by ical4android because events without duration don't show up in Android calendar)
            assertEquals(event.dtStart, event2.dtStart)
            assertEquals(event.dtStart!!.date.time + 86400000, event2.dtEnd!!.date.time)
            assertTrue(event2.isAllDay())
        } finally {
            testEvent.delete()
        }
    }

}
