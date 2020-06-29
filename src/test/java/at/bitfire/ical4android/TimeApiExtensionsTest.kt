package at.bitfire.ical4android

import at.bitfire.ical4android.util.TimeApiExtensions.toDuration
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toRfc5545Duration
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.*

class TimeApiExtensionsTest {

    @Test
    fun testDateToLocalDate() {
        val date = DateTime("20200620T223000").toLocalDate()
        assertEquals(2020, date.year)
        assertEquals(6, date.monthValue)
        assertEquals(20, date.dayOfMonth)
        assertEquals(DayOfWeek.SATURDAY, date.dayOfWeek)
    }

    @Test
    fun testLocalDateToIcal4jDate() {
        assertEquals(Date("19000118"), LocalDate.of(1900, 1, 18).toIcal4jDate())
        assertEquals(Date("20200620"), LocalDate.of(2020, 6, 20).toIcal4jDate())
    }

    @Test
    fun testTemporalAmountToDuration() {
        assertEquals(Duration.ofHours(1), Duration.ofHours(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(1), Duration.ofDays(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(1), Period.ofDays(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(7), Period.ofWeeks(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(365), Period.ofYears(1).toDuration(Instant.EPOCH))
        assertEquals(Duration.ofDays(366), Period.ofYears(1).toDuration(Instant.ofEpochSecond(1577836800)))
    }

    @Test
    fun testTemporalAmountToRfc5545Duration_Duration() {
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