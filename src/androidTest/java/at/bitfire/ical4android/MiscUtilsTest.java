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

public class MiscUtilsTest extends InstrumentationTestCase {

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

	public void testReflectionToString() {
        String s = MiscUtils.reflectionToString(new TestClass());
        assertTrue(s.startsWith("TestClass=["));
        assertTrue(s.contains("s=test"));
        assertTrue(s.contains("i=2"));
    }


    private static class TestClass {
        final private String s = "test";
        public int i = 2;
    }

}
