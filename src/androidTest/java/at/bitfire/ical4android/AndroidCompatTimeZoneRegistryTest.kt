/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import net.fortuna.ical4j.model.DefaultTimeZoneRegistryFactory
import net.fortuna.ical4j.model.TimeZoneRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.zone.ZoneRulesException

class AndroidCompatTimeZoneRegistryTest {

    val ical4jRegistry = DefaultTimeZoneRegistryFactory.getInstance().createRegistry()
    lateinit var registry: TimeZoneRegistry

    val systemKnowsKyiv =
        try {
            ZoneId.of("Europe/Kyiv")
            true
        } catch (e: ZoneRulesException) {
            false
        }

    @Before
    fun createRegistry() {
        registry = AndroidCompatTimeZoneRegistry.Factory().createRegistry()
    }


    @Test
    fun getTimeZone_Existing() {
        assertEquals(
            ical4jRegistry.getTimeZone("Europe/Vienna"),
            registry.getTimeZone("Europe/Vienna")
        )
    }

    @Test
    fun getTimeZone_Existing_Kiev() {
        Assume.assumeFalse(systemKnowsKyiv)
        val tz = registry.getTimeZone("Europe/Kiev")
        assertEquals("Europe/Kiev", tz?.id)
        assertEquals("Europe/Kiev", tz?.vTimeZone?.timeZoneId?.value)
    }

    @Test
    fun getTimeZone_Existing_Kyiv() {
        Assume.assumeFalse(systemKnowsKyiv)

        // See ical4jRegistry.getTimeZone code
        // assertNull(ical4jRegistry.getTimeZone("Europe/Kyiv"))

        assertEquals(
            ical4jRegistry.getTimeZone("Europe/Kyiv"),
            registry.getTimeZone("Europe/Kyiv")
        )
    }

    @Test
    fun getTimeZone_NotExisting() {
        assertNull(registry.getTimeZone("Test/NotExisting"))
    }

}