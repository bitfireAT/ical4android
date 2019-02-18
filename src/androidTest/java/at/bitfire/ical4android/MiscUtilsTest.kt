/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import android.content.ContentValues
import androidx.test.filters.SmallTest
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.util.TimeZones
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class MiscUtilsTest {

    private val tzVienna = DateUtils.tzRegistry.getTimeZone("Europe/Vienna")

    @Test
    @SmallTest
    fun testAndroidifyTimeZone() {
        assertNotNull(tzVienna)

        // date (no time zone) should be ignored
        val date = DtStart(Date("20150101"))
        MiscUtils.androidifyTimeZone(date)
        assertNull(date.timeZone)

        // date-time (Europe/Vienna) should be unchanged
        var dtStart = DtStart("20150101T230350", tzVienna)
        MiscUtils.androidifyTimeZone(dtStart)
        assertEquals(tzVienna, dtStart.timeZone)

        // time zone that is not available on Android systems should be changed to system default
        val builder = CalendarBuilder()
        val cal = builder.build(StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:CustomTime\n" +
                "BEGIN:STANDARD\n" +
                "TZOFFSETFROM:+0310\n" +
                "TZOFFSETTO:+0310\n" +
                "DTSTART:19600101T000000\n" +
                "END:STANDARD\n" +
                "END:VTIMEZONE\n" +
                "END:VCALENDAR"))
        val tzCustom = TimeZone(cal.getComponent(VTimeZone.VTIMEZONE) as VTimeZone)
        dtStart = DtStart("20150101T031000", tzCustom)
        MiscUtils.androidifyTimeZone(dtStart)
        assertEquals(1420070400000L, dtStart.date.time)
        assertEquals(java.util.TimeZone.getDefault().id, dtStart.timeZone.id)
    }

    @Test
    @SmallTest
    fun testGetTzID() {
        // DATE (without time)
        assertEquals(TimeZones.UTC_ID, MiscUtils.getTzId(DtStart(Date("20150101"))))

        // DATE-TIME without time zone (floating time): should be local time zone (because Android doesn't support floating times)
        assertEquals(java.util.TimeZone.getDefault().id, MiscUtils.getTzId(DtStart(DateTime("20150101T000000"))))

        // DATE-TIME without time zone (UTC)
        assertEquals(TimeZones.UTC_ID, MiscUtils.getTzId(DtStart(DateTime(1438607288000L), true)))

        // DATE-TIME with time zone
        assertEquals(tzVienna.id, MiscUtils.getTzId(DtStart(DateTime("20150101T000000", tzVienna))))
    }

    @Test
    @SmallTest
    fun testReflectionToString() {
        val s = MiscUtils.reflectionToString(TestClass())
        assertTrue(s.startsWith("TestClass=["))
        assertTrue(s.contains("s=test"))
        assertTrue(s.contains("i=2"))
    }

    @Test
    @SmallTest
    fun testRemoveEmptyStrings() {
        val values = ContentValues(2)
        values.put("key1", "value")
        values.put("key2", 1L)
        values.put("key3", "")
        MiscUtils.removeEmptyStrings(values)
        assertEquals("value", values.getAsString("key1"))
        assertEquals(1L, values.getAsLong("key2").toLong())
        assertNull(values.get("key3"))
    }


    @Suppress("unused")
    private class TestClass {
        private val s = "test"
        val i = 2
    }

}
