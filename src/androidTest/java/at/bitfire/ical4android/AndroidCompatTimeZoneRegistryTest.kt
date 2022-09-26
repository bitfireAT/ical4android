/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import net.fortuna.ical4j.model.DefaultTimeZoneRegistryFactory
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistry
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.zone.ZoneRulesException

class AndroidCompatTimeZoneRegistryTest {

    lateinit var ical4jRegistry: TimeZoneRegistry
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
        ical4jRegistry = DefaultTimeZoneRegistryFactory.getInstance().createRegistry()
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
    fun getTimeZone_Existing_ButNotInIcal4j() {
        val reg = AndroidCompatTimeZoneRegistry(object: TimeZoneRegistry {
            override fun register(timezone: TimeZone?) = throw NotImplementedError()
            override fun register(timezone: TimeZone?, update: Boolean) = throw NotImplementedError()
            override fun clear() = throw NotImplementedError()
            override fun getTimeZone(id: String?) = null

        })
        assertNull(reg.getTimeZone("Europe/Berlin"))
    }

    @Test
    fun getTimeZone_Existing_Kiev() {
        Assume.assumeFalse(systemKnowsKyiv)
        val tz = registry.getTimeZone("Europe/Kiev")
        assertFalse(tz === ical4jRegistry.getTimeZone("Europe/Kiev"))      // we have made a copy
        assertEquals("Europe/Kiev", tz?.id)
        assertEquals("Europe/Kiev", tz?.vTimeZone?.timeZoneId?.value)
    }

    @Test
    fun getTimeZone_Existing_Kyiv() {
        Assume.assumeFalse(systemKnowsKyiv)

        /* Unfortunately, AndroidCompatTimeZoneRegistry can't rewrite to Europy/Kyiv to anything because
           it doesn't know a valid Android name for it. */
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