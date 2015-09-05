/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU General Public License for more details.
 */

package at.bitfire.ical4android;

import android.content.res.AssetManager;
import android.test.InstrumentationTestCase;
import android.util.Log;

import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.Sequence;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.util.TimeZones;

import org.apache.commons.codec.Charsets;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import lombok.Cleanup;
import lombok.NonNull;

public class TaskTest extends InstrumentationTestCase {
    private static final String TAG = "ical4android.TaskTest";
    protected final TimeZone tzVienna = DateUtils.tzRegistry.getTimeZone("Europe/Vienna");

    AssetManager assetMgr;

    public void setUp() throws IOException, InvalidCalendarException {
        assetMgr = getInstrumentation().getContext().getResources().getAssets();
    }


    /* public interface tests */

    public void testCharsets() throws IOException, InvalidCalendarException {
        Task t = parseCalendar("latin1.ics", Charsets.ISO_8859_1);
        assertEquals("äöüß", t.summary);

        t = parseCalendar("utf8.ics", null);
        assertEquals("© äö — üß", t.summary);
        assertEquals("中华人民共和国", t.location);
    }

    public void testDueBeforeDtStart() throws IOException, InvalidCalendarException {
        Task t = parseCalendar("due-before-dtstart.ics", null);
        assertEquals(t.summary, "DUE before DTSTART");
        // TODO error handling
    }

    public void testAllFields() {
        // TODO
    }


    /* helpers */

    private Task parseCalendar(String fname, Charset charset) throws IOException, InvalidCalendarException {
        fname = "tasks/" + fname;
        Log.d(TAG, "Loading task file " + fname);
        @Cleanup InputStream is = assetMgr.open(fname, AssetManager.ACCESS_STREAMING);
        return Task.fromStream(is, charset)[0];
    }
    
}
