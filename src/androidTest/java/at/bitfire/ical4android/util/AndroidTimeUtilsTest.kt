/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.util

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.*
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DateListProperty
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.util.TimeZones
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.time.Duration
import java.time.Period

class AndroidTimeUtilsTest {

    val tzBerlin: TimeZone = DateUtils.ical4jTimeZone("Europe/Berlin")!!
    val tzToronto: TimeZone = DateUtils.ical4jTimeZone("America/Toronto")!!

    val tzCustom by lazy {
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
        TimeZone(cal.getComponent(VTimeZone.VTIMEZONE) as VTimeZone)
    }

    val tzIdDefault = java.util.TimeZone.getDefault().id
    val tzDefault = DateUtils.ical4jTimeZone(tzIdDefault)

    // androidifyTimeZone

    @Test
    fun testAndroidifyTimeZone_Null() {
        // must not throw an exception
        AndroidTimeUtils.androidifyTimeZone(null)
    }

    // androidifyTimeZone
    // DateProperty

    @Test
    fun testAndroidifyTimeZone_DateProperty_Date() {
        // dates (without time) should be ignored
        val dtStart = DtStart(Date("20150101"))
        AndroidTimeUtils.androidifyTimeZone(dtStart)
        assertTrue(DateUtils.isDate(dtStart))
        assertNull(dtStart.timeZone)
        assertFalse(dtStart.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateProperty_KnownTimeZone() {
        // date-time with known time zone should be unchanged
        val dtStart = DtStart("20150101T230350", tzBerlin)
        AndroidTimeUtils.androidifyTimeZone(dtStart)
        assertEquals(tzBerlin, dtStart.timeZone)
        assertFalse(dtStart.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateProperty_UnknownTimeZone() {
        // time zone that is not available on Android systems should be rewritten to system default
        val dtStart = DtStart("20150101T031000", tzCustom)
        // 20150101T031000 CustomTime [+0310] = 20150101T000000 UTC = 1420070400 UNIX
        AndroidTimeUtils.androidifyTimeZone(dtStart)
        assertEquals(1420070400000L, dtStart.date.time)
        assertEquals(tzIdDefault, dtStart.timeZone.id)
        assertFalse(dtStart.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateProperty_FloatingTime() {
        // times with floating time should be treated as system default time zone
        val dtStart = DtStart("20150101T230350")
        AndroidTimeUtils.androidifyTimeZone(dtStart)
        assertEquals(DateTime("20150101T230350", tzDefault).time, dtStart.date.time)
        assertEquals(tzIdDefault, dtStart.timeZone.id)
        assertFalse(dtStart.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateProperty_UTC() {
        // times with UTC should be unchanged
        val dtStart = DtStart("20150101T230350Z")
        AndroidTimeUtils.androidifyTimeZone(dtStart)
        assertEquals(1420153430000L, dtStart.date.time)
        assertNull(dtStart.timeZone)
        assertTrue(dtStart.isUtc)
    }

    // androidifyTimeZone
    // DateListProperty - date

    @Test
    fun testAndroidifyTimeZone_DateListProperty_Date() {
        // dates (without time) should be ignored
        val rDate = RDate(DateList("20150101,20150102", Value.DATE))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(1420070400000L, rDate.dates[0].time)
        assertEquals(1420156800000L, rDate.dates[1].time)
        assertNull(rDate.timeZone)
        assertEquals(Value.DATE, rDate.dates.type)
        assertNull(rDate.dates.timeZone)
        assertFalse(rDate.dates.isUtc)
    }

    // androidifyTimeZone
    // DateListProperty - date-time

    @Test
    fun testAndroidifyTimeZone_DateListProperty_KnownTimeZone() {
        // times with known time zone should be unchanged
        val rDate = RDate(DateList("20150101T150000,20150102T150000", Value.DATE_TIME, tzToronto))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(1420142400000L, rDate.dates[0].time)
        assertEquals(1420228800000L, rDate.dates[1].time)
        assertEquals(tzToronto, rDate.timeZone)
        assertEquals(Value.DATE_TIME, rDate.dates.type)
        assertEquals(tzToronto, rDate.dates.timeZone)
        assertFalse(rDate.dates.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateListProperty_UnknownTimeZone() {
        // time zone that is not available on Android systems should be rewritten to system default
        val rDate = RDate(DateList("20150101T031000,20150102T031000", Value.DATE_TIME, tzCustom))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(DateTime("20150101T031000", tzCustom).time, rDate.dates[0].time)
        assertEquals(DateTime("20150102T031000", tzCustom).time, rDate.dates[1].time)
        assertEquals(tzIdDefault, rDate.timeZone.id)
        assertEquals(Value.DATE_TIME, rDate.dates.type)
        assertEquals(tzIdDefault, rDate.dates.timeZone.id)
        assertFalse(rDate.dates.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateListProperty_FloatingTime() {
        // times with floating time should be treated as system default time zone
        val rDate = RDate(DateList("20150101T031000,20150102T031000", Value.DATE_TIME))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(DateTime("20150101T031000", tzDefault).time, rDate.dates[0].time)
        assertEquals(DateTime("20150102T031000", tzDefault).time, rDate.dates[1].time)
        assertEquals(tzIdDefault, rDate.timeZone.id)
        assertEquals(Value.DATE_TIME, rDate.dates.type)
        assertEquals(tzIdDefault, rDate.dates.timeZone.id)
        assertFalse(rDate.dates.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateListProperty_UTC() {
        // times with UTC should be unchanged
        val rDate = RDate(DateList("20150101T031000Z,20150102T031000Z", Value.DATE_TIME))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(DateTime("20150101T031000Z").time, rDate.dates[0].time)
        assertEquals(DateTime("20150102T031000Z").time, rDate.dates[1].time)
        assertNull(rDate.timeZone)
        assertEquals(Value.DATE_TIME, rDate.dates.type)
        assertNull(rDate.dates.timeZone)
        assertTrue(rDate.dates.isUtc)
    }

    // androidifyTimeZone
    // DateListProperty - period-explicit

    @Test
    fun testAndroidifyTimeZone_DateListProperty_Period_FloatingTime() {
        // times with floating time should be treated as system default time zone
        val rDate = RDate(PeriodList("19970101T180000/19970102T070000,20220103T000000/20220108T000000"))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(
            setOf(Period(DateTime("19970101T18000000"), DateTime("19970102T07000000")),
                Period(DateTime("20220103T000000"), DateTime("20220108T000000"))),
            rDate.periods)
        assertNull(rDate.timeZone)
        assertNull(rDate.periods.timeZone)
        assertTrue(rDate.periods.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateListProperty_Period_KnownTimezone() {
        // periods with known time zone should be unchanged
        val rDate = RDate(PeriodList("19970101T180000/19970102T070000,19970102T180000/19970108T090000"))
        rDate.periods.timeZone = tzToronto
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(
            setOf(Period("19970101T180000/19970102T070000"), Period("19970102T180000/19970108T090000")),
            mutableSetOf<net.fortuna.ical4j.model.Period>().also { it.addAll(rDate.periods) }
        )
        assertEquals(tzToronto, rDate.periods.timeZone)
        assertNull(rDate.timeZone)
        assertFalse(rDate.dates.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateListProperty_Periods_UnknownTimeZone() {
        // time zone that is not available on Android systems should be rewritten to system default
        val rDate = RDate(PeriodList("19970101T180000/19970102T070000,19970102T180000/19970108T090000"))
        rDate.periods.timeZone = tzCustom
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(
            setOf(Period("19970101T180000/19970102T070000"), Period("19970102T180000/19970108T090000")),
            mutableSetOf<net.fortuna.ical4j.model.Period>().also { it.addAll(rDate.periods) }
        )
        assertEquals(tzIdDefault, rDate.periods.timeZone.id)
        assertNull(rDate.timeZone)
        assertFalse(rDate.dates.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateListProperty_Period_UTC() {
        // times with UTC should be unchanged
        val rDate = RDate(PeriodList("19970101T180000Z/19970102T070000Z,20220103T0000Z/20220108T0000Z"))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(
            setOf(Period(DateTime("19970101T180000Z"), DateTime("19970102T070000Z")),
                Period(DateTime("20220103T0000Z"), DateTime("20220108T0000Z"))),
            rDate.periods)
        assertTrue(rDate.periods.isUtc)
    }

    // androidifyTimeZone
    // DateListProperty - period-start

    @Test
    fun testAndroidifyTimeZone_DateListProperty_PeriodStart_UTC() {
        // times with UTC should be unchanged
        val rDate = RDate(PeriodList("19970101T180000Z/PT5H30M,20220103T0000Z/PT2H30M10S"))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(
            setOf(Period(DateTime("19970101T180000Z"), Duration.parse("PT5H30M")),
                Period(DateTime("20220103T0000Z"), Duration.parse("PT2H30M10S"))),
            rDate.periods)
        assertTrue(rDate.periods.isUtc)
    }

    // storageTzId

    @Test
    fun testStorageTzId_Date() =
            assertEquals(AndroidTimeUtils.TZID_ALLDAY, AndroidTimeUtils.storageTzId(DtStart(Date("20150101"))))

    @Test
    fun testStorageTzId_FloatingTime() =
            assertEquals(TimeZone.getDefault().id, AndroidTimeUtils.storageTzId(DtStart(DateTime("20150101T000000"))))

    @Test
    fun testStorageTzId_UTC() =
            assertEquals(TimeZones.UTC_ID, AndroidTimeUtils.storageTzId(DtStart(DateTime("20150101T000000Z"))))

    @Test
    fun testStorageTzId_ZonedTime() {
        assertEquals(tzToronto.id, AndroidTimeUtils.storageTzId(DtStart("20150101T000000", tzToronto)))
    }


    // androidStringToRecurrenceSets

    @Test
    fun testAndroidStringToRecurrenceSets_UtcTimes() {
        // list of UTC times
        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet("20150101T103010Z,20150702T103020Z", false) { ExDate(it) }
        assertNull(exDate.timeZone)
        val exDates = exDate.dates
        assertEquals(Value.DATE_TIME, exDates.type)
        assertTrue(exDates.isUtc)
        assertEquals(2, exDates.size)
        assertEquals(1420108210000L, exDates[0].time)
        assertEquals(1435833020000L, exDates[1].time)
    }

    @Test
    fun testAndroidStringToRecurrenceSets_ZonedTimes() {
        // list of time zone times
        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet("${tzToronto.id};20150103T113030,20150704T113040",false) { ExDate(it) }
        assertEquals(tzToronto, exDate.timeZone)
        assertEquals(tzToronto.id, (exDate.getParameter(Parameter.TZID) as TzId).value)
        val exDates = exDate.dates
        assertEquals(Value.DATE_TIME, exDates.type)
        assertEquals(tzToronto, exDates.timeZone)
        assertEquals(2, exDates.size)
        assertEquals(1420302630000L, exDates[0].time)
        assertEquals(1436023840000L, exDates[1].time)
    }

    @Test
    fun testAndroidStringToRecurrenceSets_Dates() {
        // list of dates
        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet("20150101T103010Z,20150702T103020Z", true) { ExDate(it) }
        val exDates = exDate.dates
        assertEquals(Value.DATE, exDates.type)
        assertEquals(2, exDates.size)
        assertEquals("20150101", exDates[0].toString())
        assertEquals("20150702", exDates[1].toString())
    }

    @Test
    fun testAndroidStringToRecurrenceSets_Exclude() {
        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet("${tzToronto.id};20150103T113030",false, 1420302630000L) { ExDate(it) }
        assertEquals(0, exDate.dates.size)
    }

    // recurrenceSetsToAndroidString

    @Test
    fun testRecurrenceSetsToAndroidString_Date() {
        // DATEs (without time) have to be converted to <date>T000000Z for Android
        val list = ArrayList<DateListProperty>(1)
        list.add(RDate(DateList("20150101,20150702", Value.DATE)))
        assertEquals("20150101T000000Z,20150702T000000Z", AndroidTimeUtils.recurrenceSetsToAndroidString(list, true))
    }

    @Test
    fun testRecurrenceSetsToAndroidString_Period() {
        // PERIODs are not supported yet — should be implemented later
        val list = listOf(
            RDate(PeriodList("19960403T020000Z/19960403T040000Z,19960404T010000Z/PT3H"))
        )
        assertEquals("", AndroidTimeUtils.recurrenceSetsToAndroidString(list, false))
    }

    @Test
    fun testRecurrenceSetsToAndroidString_TimeAlthoughAllDay() {
        // DATE-TIME (floating time or UTC) recurrences for all-day events have to converted to <date>T000000Z for Android
        val list = ArrayList<DateListProperty>(1)
        list.add(RDate(DateList("20150101T000000,20150702T000000Z", Value.DATE_TIME)))
        assertEquals("20150101T000000Z,20150702T000000Z", AndroidTimeUtils.recurrenceSetsToAndroidString(list, true))
    }

    @Test
    fun testRecurrenceSetsToAndroidString_TwoTimesWithSameTimezone() {
        // two separate entries, both with timezone Toronto
        val list = ArrayList<DateListProperty>(2)
        list.add(RDate(DateList("20150103T113030", Value.DATE_TIME, tzToronto)))
        list.add(RDate(DateList("20150704T113040", Value.DATE_TIME, tzToronto)))
        assertEquals("America/Toronto;20150103T113030,20150704T113040", AndroidTimeUtils.recurrenceSetsToAndroidString(list, false))
    }

    @Test
    fun testRecurrenceSetsToAndroidString_TwoTimesWithDifferentTimezone() {
        // two separate entries, one with timezone Toronto, one with Berlin
        // 2015/01/03 11:30:30 Toronto [-5] = 2015/01/03 16:30:30 UTC
        // DST: 2015/07/04 11:30:40 Berlin  [+2] = 2015/07/04 09:30:40 UTC = 2015/07/04 05:30:40 Toronto [-4]
        val list = ArrayList<DateListProperty>(2)
        list.add(RDate(DateList("20150103T113030", Value.DATE_TIME, tzToronto)))
        list.add(RDate(DateList("20150704T113040", Value.DATE_TIME, tzBerlin)))
        assertEquals("America/Toronto;20150103T113030,20150704T053040", AndroidTimeUtils.recurrenceSetsToAndroidString(list, false))
    }

    @Test
    fun testRecurrenceSetsToAndroidString_TwoTimesWithOneUtc() {
        // two separate entries, one with timezone Toronto, one with Berlin
        // 2015/01/03 11:30:30 Toronto [-5] = 2015/01/03 16:30:30 UTC
        // DST: 2015/07/04 11:30:40 Berlin  [+2] = 2015/07/04 09:30:40 UTC = 2015/07/04 05:30:40 Toronto [-4]
        val list = ArrayList<DateListProperty>(2)
        list.add(RDate(DateList("20150103T113030Z", Value.DATE_TIME)))
        list.add(RDate(DateList("20150704T113040", Value.DATE_TIME, tzBerlin)))
        assertEquals("20150103T113030Z,20150704T093040Z", AndroidTimeUtils.recurrenceSetsToAndroidString(list, false))
    }

    @Test
    fun testRecurrenceSetsToAndroidString_UtcTime() {
        val list = ArrayList<DateListProperty>(1)
        list.add(RDate(DateList("20150101T103010Z,20150102T103020Z", Value.DATE_TIME)))
        assertEquals("20150101T103010Z,20150102T103020Z", AndroidTimeUtils.recurrenceSetsToAndroidString(list, false))
    }


    // recurrenceSetsToOpenTasksString

    @Test
    fun testRecurrenceSetsToOpenTasksString_UtcTimes() {
        val list = ArrayList<DateListProperty>(1)
        list.add(RDate(DateList("20150101T060000Z,20150702T060000Z", Value.DATE_TIME)))
        assertEquals("20150101T060000Z,20150702T060000Z", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, tzBerlin))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_ZonedTimes() {
        val list = ArrayList<DateListProperty>(1)
        list.add(RDate(DateList("20150101T060000,20150702T060000", Value.DATE_TIME, tzToronto)))
        assertEquals("20150101T120000,20150702T120000", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, tzBerlin))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_MixedTimes() {
        val list = ArrayList<DateListProperty>(1)
        list.add(RDate(DateList("20150101T060000Z,20150702T060000", Value.DATE_TIME, tzToronto)))
        assertEquals("20150101T070000,20150702T120000", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, tzBerlin))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_TimesAlthougAllDay() {
        val list = ArrayList<DateListProperty>(1)
        list.add(RDate(DateList("20150101T060000,20150702T060000", Value.DATE_TIME, tzToronto)))
        assertEquals("20150101,20150702", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, null))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_Dates() {
        val list = ArrayList<DateListProperty>(1)
        list.add(RDate(DateList("20150101,20150702", Value.DATE)))
        assertEquals("20150101,20150702", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, null))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_DatesAlthoughTimeZone() {
        val list = ArrayList<DateListProperty>(1)
        list.add(RDate(DateList("20150101,20150702", Value.DATE)))
        assertEquals("20150101T000000,20150702T000000", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, tzBerlin))
    }


    @Test
    fun testParseDuration() {
        assertEquals(Duration.parse("PT3600S"), AndroidTimeUtils.parseDuration("3600S"))
        assertEquals(Duration.parse("PT3600S"), AndroidTimeUtils.parseDuration("P3600S"))
        assertEquals(Duration.parse("+PT3600S"), AndroidTimeUtils.parseDuration("+P3600S"))
        assertEquals(Duration.parse("PT3600S"), AndroidTimeUtils.parseDuration("PT3600S"))
        assertEquals(Duration.parse("+PT3600S"), AndroidTimeUtils.parseDuration("+PT3600S"))
        assertEquals(Period.parse("P10D"), AndroidTimeUtils.parseDuration("P1W3D"))
        assertEquals(Period.parse("P1D"), AndroidTimeUtils.parseDuration("1DT"))
        assertEquals(Duration.parse("P14DT3600S"), AndroidTimeUtils.parseDuration("P2W3600S"))
        assertEquals(Duration.parse("-P3DT4H5M6S"), AndroidTimeUtils.parseDuration("-P3D4H5M6S"))
        assertEquals(Duration.parse("PT3H2M1S"), AndroidTimeUtils.parseDuration("P1S2M3H"))
        assertEquals(Duration.parse("P4DT3H2M1S"), AndroidTimeUtils.parseDuration("P1S2M3H4D"))
        assertEquals(Duration.parse("P11DT3H2M1S"), AndroidTimeUtils.parseDuration("P1S2M3H4D1W"))
        assertEquals(Duration.parse("PT1H0M10S"), AndroidTimeUtils.parseDuration("1H10S"))
    }

}