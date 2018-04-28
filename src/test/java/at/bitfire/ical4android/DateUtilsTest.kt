/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DateListProperty
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import org.junit.Assert
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class DateUtilsTest {

    private val tzIdToronto = "America/Toronto"
    private val tzToronto = DateUtils.tzRegistry.getTimeZone(tzIdToronto)
    init {
        assertNotNull(tzToronto)
    }
    
    @Test
    fun testTimeZoneRegistry() {
        Assert.assertNotNull(DateUtils.tzRegistry.getTimeZone("Europe/Vienna"))

        // https://github.com/ical4j/ical4j/issues/207
        // assertNotNull(DateUtils.tzRegistry.getTimeZone("EST"))
    }

    @Test
    fun testRecurrenceSetsToAndroidString() {
        // one entry with implicitly set UTC
        val list = ArrayList<DateListProperty>(2)
        list.add(RDate(DateList("20150101T103010Z,20150102T103020Z", Value.DATE_TIME)))
        assertEquals("20150101T103010Z,20150102T103020Z", DateUtils.recurrenceSetsToAndroidString(list, false))

        // two separate entries, both with time zone Toronto
        // 2015/01/03 11:30:30 Toronto = 2015/01/03 16:30:30 UTC = 1420302630 UNIX
        // 2015/07/04 11:30:40 Toronto = 2015/07/04 15:30:40 UTC = 1436023840 UNIX
        list.clear()
        list.add(RDate(DateList("20150103T113030", Value.DATE_TIME, tzToronto)))
        list.add(RDate(DateList("20150704T113040", Value.DATE_TIME, tzToronto)))
        assertEquals("20150103T163030Z,20150704T153040Z", DateUtils.recurrenceSetsToAndroidString(list, false))

        // DATEs (without time) have to be converted to <date>T000000Z for Android
        list.clear()
        list.add(RDate(DateList("20150101,20150702", Value.DATE)))
        assertEquals("20150101T000000Z,20150702T000000Z", DateUtils.recurrenceSetsToAndroidString(list, true))

        // DATE-TIME (floating time or UTC) recurrences for all-day events have to converted to <date>T000000Z for Android
        list.clear()
        list.add(RDate(DateList("20150101T000000,20150702T000000Z", Value.DATE_TIME)))
        assertEquals("20150101T000000Z,20150702T000000Z", DateUtils.recurrenceSetsToAndroidString(list, true))
    }

    @Test
    fun testAndroidStringToRecurrenceSets() {
        // list of UTC times
        var exDate = DateUtils.androidStringToRecurrenceSet("20150101T103010Z,20150702T103020Z", ExDate::class.java, false)
        var exDates = exDate.dates
        assertEquals(Value.DATE_TIME, exDates.type)
        assertTrue(exDates.isUtc)
        assertEquals(2, exDates.size)
        assertEquals(1420108210000L, exDates[0].time)
        assertEquals(1435833020000L, exDates[1].time)

        // list of time zone times
        exDate = DateUtils.androidStringToRecurrenceSet("$tzIdToronto;20150103T113030,20150704T113040", ExDate::class.java, false)
        exDates = exDate.dates
        assertEquals(Value.DATE_TIME, exDates.type)
        assertEquals(DateUtils.tzRegistry.getTimeZone(tzIdToronto), exDates.timeZone)
        assertEquals(2, exDates.size)
        assertEquals(1420302630000L, exDates[0].time)
        assertEquals(1436023840000L, exDates[1].time)

        // list of dates
        exDate = DateUtils.androidStringToRecurrenceSet("20150101T103010Z,20150702T103020Z", ExDate::class.java, true)
        exDates = exDate.dates
        assertEquals(Value.DATE, exDates.type)
        assertEquals(2, exDates.size)
        assertEquals("20150101", exDates[0].toString())
        assertEquals("20150702", exDates[1].toString())
    }

}
