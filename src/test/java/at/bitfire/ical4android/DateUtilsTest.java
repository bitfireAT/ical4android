/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android;

import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.RDate;

import org.junit.Test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DateUtilsTest {
	private static final String tzIdToronto = "America/Toronto";
    static final TimeZone tzToronto = DateUtils.tzRegistry.getTimeZone(tzIdToronto);
    static {
        assertNotNull(tzToronto);
    }

    @Test
	public void testRecurrenceSetsToAndroidString() throws ParseException {
		// one entry with implicitly set UTC
		final List<RDate> list = new ArrayList<>(2);
		list.add(new RDate(new DateList("20150101T103010Z,20150102T103020Z", Value.DATE_TIME)));
		assertEquals("20150101T103010Z,20150102T103020Z", DateUtils.recurrenceSetsToAndroidString(list, false));

		// two separate entries, both with time zone Toronto
        // 2015/01/03 11:30:30 Toronto = 2015/01/03 16:30:30 UTC = 1420302630 UNIX
        // 2015/07/04 11:30:40 Toronto = 2015/07/04 15:30:40 UTC = 1436023840 UNIX
        list.clear();
        list.add(new RDate(new DateList("20150103T113030", Value.DATE_TIME, tzToronto)));
		list.add(new RDate(new DateList("20150704T113040", Value.DATE_TIME, tzToronto)));
		assertEquals("20150103T163030Z,20150704T153040Z", DateUtils.recurrenceSetsToAndroidString(list, false));

		// DATEs (without time) have to be converted to <date>T000000Z for Android
		list.clear();
		list.add(new RDate(new DateList("20150101,20150702", Value.DATE)));
		assertEquals("20150101T000000Z,20150702T000000Z", DateUtils.recurrenceSetsToAndroidString(list, true));

		// DATE-TIME (floating time or UTC) recurrences for all-day events have to converted to <date>T000000Z for Android
		list.clear();
		list.add(new RDate(new DateList("20150101T000000,20150702T000000Z", Value.DATE_TIME)));
		assertEquals("20150101T000000Z,20150702T000000Z", DateUtils.recurrenceSetsToAndroidString(list, true));
	}

    @Test
	public void testAndroidStringToRecurrenceSets() throws ParseException {
		// list of UTC times
		ExDate exDate = (ExDate)DateUtils.androidStringToRecurrenceSet("20150101T103010Z,20150702T103020Z", ExDate.class, false);
		DateList exDates = exDate.getDates();
		assertEquals(Value.DATE_TIME, exDates.getType());
		assertTrue(exDates.isUtc());
		assertEquals(2, exDates.size());
		assertEquals(1420108210000L, exDates.get(0).getTime());
		assertEquals(1435833020000L, exDates.get(1).getTime());

		// list of time zone times
		exDate = (ExDate)DateUtils.androidStringToRecurrenceSet(tzIdToronto + ";20150103T113030,20150704T113040", ExDate.class, false);
		exDates = exDate.getDates();
		assertEquals(Value.DATE_TIME, exDates.getType());
		assertEquals(DateUtils.tzRegistry.getTimeZone(tzIdToronto), exDates.getTimeZone());
		assertEquals(2, exDates.size());
		assertEquals(1420302630000L, exDates.get(0).getTime());
		assertEquals(1436023840000L, exDates.get(1).getTime());

		// list of dates
		exDate = (ExDate)DateUtils.androidStringToRecurrenceSet("20150101T103010Z,20150702T103020Z", ExDate.class, true);
		exDates = exDate.getDates();
		assertEquals(Value.DATE, exDates.getType());
		assertEquals(2, exDates.size());
		assertEquals("20150101", exDates.get(0).toString());
		assertEquals("20150702", exDates.get(1).toString());
	}

}
