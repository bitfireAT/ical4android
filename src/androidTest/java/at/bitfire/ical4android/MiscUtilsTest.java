/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android;

import android.content.ContentValues;
import android.test.InstrumentationTestCase;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.util.TimeZones;

import java.io.StringReader;
import java.text.ParseException;

public class MiscUtilsTest extends InstrumentationTestCase {

    protected final TimeZone tzVienna = DateUtils.tzRegistry.getTimeZone("Europe/Vienna");

    public void testAndroidifyTimeZone() throws Exception {
        assertNotNull(tzVienna);

        // date (no time zone) should be ignored
        DtStart date = new DtStart(new Date("20150101"));
        MiscUtils.androidifyTimeZone(date);
        assertNull(date.getTimeZone());

        // date-time (Europe/Vienna) should be unchanged
        DtStart dtStart = new DtStart("20150101T230350", tzVienna);
        MiscUtils.androidifyTimeZone(dtStart);
        assertEquals(tzVienna, dtStart.getTimeZone());

        // time zone that is not available on Android systems should be changed to system default
        CalendarBuilder builder = new CalendarBuilder();
        net.fortuna.ical4j.model.Calendar cal = builder.build(new StringReader("BEGIN:VCALENDAR\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:CustomTime\n" +
                "BEGIN:STANDARD\n" +
                "TZOFFSETFROM:+0310\n" +
                "TZOFFSETTO:+0310\n" +
                "DTSTART:19600101T000000\n" +
                "END:STANDARD\n" +
                "END:VTIMEZONE\n" +
                "END:VCALENDAR"));
        final TimeZone tzCustom = new TimeZone((VTimeZone)cal.getComponent(VTimeZone.VTIMEZONE));
        dtStart = new DtStart("20150101T031000", tzCustom);
        MiscUtils.androidifyTimeZone(dtStart);
        assertEquals(1420070400000L, dtStart.getDate().getTime());
        assertEquals(java.util.TimeZone.getDefault().getID(), dtStart.getTimeZone().getID());
    }

    public void testGetTzID() throws ParseException {
        // DATE (without time)
        assertEquals(TimeZones.UTC_ID, MiscUtils.getTzId(new DtStart(new Date("20150101"))));

        // DATE-TIME without time zone (floating time): should be UTC (because net.fortuna.ical4j.timezone.date.floating=false)
        assertEquals(TimeZones.UTC_ID, MiscUtils.getTzId(new DtStart(new DateTime("20150101T000000"))));

        // DATE-TIME without time zone (UTC)
        assertEquals(TimeZones.UTC_ID, MiscUtils.getTzId(new DtStart(new DateTime(1438607288000L))));

        // DATE-TIME with time zone
        assertEquals(tzVienna.getID(), MiscUtils.getTzId(new DtStart(new DateTime("20150101T000000", tzVienna))));
    }

    public void testReflectionToString() {
        String s = MiscUtils.reflectionToString(new TestClass());
        assertTrue(s.startsWith("TestClass=["));
        assertTrue(s.contains("s=test"));
        assertTrue(s.contains("i=2"));
    }

    public void testRemoveEmptyStrings() {
        ContentValues values = new ContentValues(2);
        values.put("key1", "value");
        values.put("key2", 1L);
        values.put("key3", "");
        MiscUtils.removeEmptyStrings(values);
        assertEquals("value", values.getAsString("key1"));
        assertEquals(1L, (long)values.getAsLong("key2"));
        assertNull(values.get("key3"));
    }


    private static class TestClass {
        final private String s = "test";
        public int i = 2;
    }

}
