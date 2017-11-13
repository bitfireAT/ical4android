/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class TestDateUtils {

    @Test
    public void testTimeZoneRegistry() {
        assertNotNull(DateUtils.tzRegistry.getTimeZone("Europe/Vienna"));

        // https://github.com/ical4j/ical4j/issues/207
        // assertNotNull(DateUtils.tzRegistry.getTimeZone("EST"));
    }

}
