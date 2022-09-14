/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.util

import at.bitfire.ical4android.util.TimeApiExtensions.requireTimeZone
import at.bitfire.ical4android.util.TimeApiExtensions.toDuration
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalTime
import at.bitfire.ical4android.util.TimeApiExtensions.toRfc5545Duration
import at.bitfire.ical4android.util.TimeApiExtensions.toZoneIdCompat
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.util.TimeZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.*

class TimeApiExtensionsTest {

    val tzBerlin: TimeZone = DateUtils.ical4jTimeZone("Europe/Berlin")!!


    @Test
    fun testTimeZone_toZoneIdCompat_NotUtc() {
        assertEquals(ZoneId.of("Europe/Berlin"), tzBerlin.toZoneId())
    }

    @Test
    fun testTimeZone_toZoneIdCompat_Utc() {
        assertEquals(ZoneOffset.UTC, TimeZones.getUtcTimeZone().toZoneIdCompat())
    }


    @Test
    fun testDate_toLocalDate() {
        val date = Date("20200620").toLocalDate()
        assertEquals(2020, date.year)
        assertEquals(6, date.monthValue)
        assertEquals(20, date.dayOfMonth)
        assertEquals(DayOfWeek.SATURDAY, date.dayOfWeek)
    }


    @Test
    fun testDateTime_requireTimeZone() {
        val time = DateTime("2020707T010203", tzBerlin)
        assertEquals(tzBerlin, time.requireTimeZone())
    }

    @Test
    fun testDateTime_requireTimeZone_Floating() {
        val time = DateTime("2020707T010203")
        assertEquals(TimeZone.getDefault(), time.requireTimeZone())
    }

    @Test
    fun testDateTime_requireTimeZone_Utc() {
        val time = DateTime("2020707T010203Z").apply { isUtc = true }
        assertTrue(time.isUtc)
        assertEquals(TimeZones.getUtcTimeZone(), time.requireTimeZone())
    }


    @Test
    fun testDateTime_toLocalDate_TimezoneBoundary() {
        val date = DateTime("20200620T000000", tzBerlin).toLocalDate()
        assertEquals(2020, date.year)
        assertEquals(6, date.monthValue)
        assertEquals(20, date.dayOfMonth)
        assertEquals(DayOfWeek.SATURDAY, date.dayOfWeek)
    }

    @Test
    fun testDateTime_toLocalDate_TimezoneDuringDay() {
        val date = DateTime("20200620T123000", tzBerlin).toLocalDate()
        assertEquals(2020, date.year)
        assertEquals(6, date.monthValue)
        assertEquals(20, date.dayOfMonth)
        assertEquals(DayOfWeek.SATURDAY, date.dayOfWeek)
    }

    @Test
    fun testDateTime_toLocalDate_UtcDuringDay() {
        val date = DateTime("20200620T123000Z").apply { isUtc = true }.toLocalDate()
        assertEquals(2020, date.year)
        assertEquals(6, date.monthValue)
        assertEquals(20, date.dayOfMonth)
        assertEquals(DayOfWeek.SATURDAY, date.dayOfWeek)
    }


    @Test
    fun testDateTime_toLocalTime() {
        assertEquals(LocalTime.of(12, 30), DateTime("20200620T123000", tzBerlin).toLocalTime())
    }

    @Test
    fun testDateTime_toLocalTime_Floating() {
        assertEquals(LocalTime.of(12, 30), DateTime("20200620T123000").toLocalTime())
    }

    @Test
    fun testDateTime_toLocalTime_Utc() {
        assertEquals(LocalTime.of(12, 30), DateTime("20200620T123000Z").apply { isUtc = true }.toLocalTime())
    }


    @Test
    fun testDateTime_toZonedDateTime() {
        assertEquals(
                ZonedDateTime.of(2020, 7, 7, 10, 30, 0, 0, tzBerlin.toZoneIdCompat()),
                DateTime("20200707T103000", tzBerlin).toZonedDateTime()
        )
    }

    @Test
    fun testDateTime_toZonedDateTime_Floating() {
        assertEquals(
                ZonedDateTime.of(2020, 7, 7, 10, 30, 0, 0, ZoneId.systemDefault()),
                DateTime("20200707T103000").toZonedDateTime()
        )
    }

    @Test
    fun testDateTime_toZonedDateTime_UTC() {
        assertEquals(
                ZonedDateTime.of(2020, 7, 7, 10, 30, 0, 0, ZoneOffset.UTC),
                DateTime("20200707T103000Z").apply { isUtc = true }.toZonedDateTime()
        )
    }


    @Test
    fun testLocalDate_toIcal4jDate() {
        assertEquals(Date("19000118"), LocalDate.of(1900, 1, 18).toIcal4jDate())
        assertEquals(Date("20200620"), LocalDate.of(2020, 6, 20).toIcal4jDate())
    }

    @Test
    fun testZonedDateTime_toIcal4jDateTime_NotUtc() {
        val tzBerlin = DateUtils.ical4jTimeZone("Europe/Berlin")
        assertEquals(
            DateTime("20200705T010203", tzBerlin),
            ZonedDateTime.of(2020, 7, 5, 1, 2, 3, 0, ZoneId.of("Europe/Berlin")).toIcal4jDateTime()
        )
    }

    @Test
    fun testZonedDateTime_toIcal4jDateTime_Utc() {
        assertEquals(
            DateTime("20200705T010203Z"),
            ZonedDateTime.of(2020, 7, 5, 1, 2, 3, 0, ZoneOffset.UTC).toIcal4jDateTime()
        )
    }


    @Test
    fun testTemporalAmount_toDuration() {
        assertEquals(Duration.ofHours(1), Duration.ofHours(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(1), Duration.ofDays(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(1), Period.ofDays(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(7), Period.ofWeeks(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(365), Period.ofYears(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(366), Period.ofYears(1).toDuration(Instant.ofEpochSecond(1577836800)))
    }

    @Test
    fun testTemporalAmount_toRfc5545Duration_Duration() {
        assertEquals("P0S", Duration.ofDays(0).toRfc5545Duration(Instant.EPOCH))
        assertEquals("P2W", Duration.ofDays(14).toRfc5545Duration(Instant.EPOCH))
        assertEquals("P15D", Duration.ofDays(15).toRfc5545Duration(Instant.EPOCH))
        assertEquals("P16DT1H", Duration.parse("P16DT1H").toRfc5545Duration(Instant.EPOCH))
        assertEquals("P16DT1H4M", Duration.parse("P16DT1H4M").toRfc5545Duration(Instant.EPOCH))
        assertEquals("P2DT1H4M5S", Duration.parse("P2DT1H4M5S").toRfc5545Duration(Instant.EPOCH))
        assertEquals("PT1M20S", Duration.parse("PT80S").toRfc5545Duration(Instant.EPOCH))

        assertEquals("P0D", Period.ofWeeks(0).toRfc5545Duration(Instant.EPOCH))

        val date20200601 = Instant.ofEpochSecond(1590969600L)
        // 2020/06/01 + 1 year   = 2021/06/01 (365 days)
        // 2021/06/01 + 2 months = 2020/08/01 (30 days + 31 days = 61 days)
        // 2020/08/01 + 3 days   = 2020/08/04 (3 days)
        // total: 365 days + 61 days + 3 days = 429 days
        assertEquals("P429D", Period.of(1, 2, 3).toRfc5545Duration(date20200601))
        assertEquals("P2W", Period.ofWeeks(2).toRfc5545Duration(date20200601))
        assertEquals("P2W", Period.ofDays(14).toRfc5545Duration(date20200601))
        assertEquals("P15D", Period.ofDays(15).toRfc5545Duration(date20200601))
        assertEquals("P30D", Period.ofMonths(1).toRfc5545Duration(date20200601))
    }

}