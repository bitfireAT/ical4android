/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
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
        assertNotNull(DateUtils.tzRegistry.getTimeZone("Europe/Vienna"))

        // https://github.com/ical4j/ical4j/issues/207
        assertNotNull(DateUtils.tzRegistry.getTimeZone("EST"))
    }


    @Test
    fun testFindAndroidTimezoneID() {
        assertEquals("Europe/Vienna", DateUtils.findAndroidTimezoneID("Europe/Vienna"))
        assertEquals("Europe/Vienna", DateUtils.findAndroidTimezoneID("Vienna"))
        assertEquals("Europe/Vienna", DateUtils.findAndroidTimezoneID("Something with Europe/Vienna in between"))
        assertEquals(TimeZone.getDefault().id, DateUtils.findAndroidTimezoneID(null))
        assertEquals(TimeZone.getDefault().id, DateUtils.findAndroidTimezoneID("nothing-to-be-found"))
    }

    @Test
    fun testIsDate() {
        assertTrue(DateUtils.isDate(DtStart(Date("20200101"))))
        assertFalse(DateUtils.isDate(DtStart(DateTime("20200101T010203Z"))))
        assertFalse(DateUtils.isDate(null))
    }

    @Test
    fun testIsDateTime() {
        assertFalse(DateUtils.isDateTime(DtEnd(Date("20200101"))))
        assertTrue(DateUtils.isDateTime(DtEnd(DateTime("20200101T010203Z"))))
        assertFalse(DateUtils.isDateTime(null))
    }

    @Test
    fun testFixDuration() {
        assertEquals("PT3600S", DateUtils.fixDuration("3600S"))
        assertEquals("PT3600S", DateUtils.fixDuration("P3600S"))
        assertEquals("+PT3600S", DateUtils.fixDuration("+P3600S"))
        assertEquals("PT3600S", DateUtils.fixDuration("PT3600S"))
        assertEquals("+PT3600S", DateUtils.fixDuration("+PT3600S"))
        assertEquals("P10D", DateUtils.fixDuration("P1W3D"))
        assertEquals("P14DT3600S", DateUtils.fixDuration("P2W3600S"))
        assertEquals("-P3DT4H5M6S", DateUtils.fixDuration("-P3D4H5M6S"))
        assertEquals("PT3H2M1S", DateUtils.fixDuration("P1S2M3H"))
        assertEquals("P4DT3H2M1S", DateUtils.fixDuration("P1S2M3H4D"))
        assertEquals("P11DT3H2M1S", DateUtils.fixDuration("P1S2M3H4D1W"))
        assertEquals("PT1H0M10S", DateUtils.fixDuration("1H10S"))
    }

}
