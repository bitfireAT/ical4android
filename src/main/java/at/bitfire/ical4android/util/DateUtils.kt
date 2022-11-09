/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.util

import at.bitfire.ical4android.Ical4Android
import at.bitfire.ical4android.UsesThreadContextClassLoader
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.DateProperty
import java.io.StringReader
import java.time.ZoneId

/**
 * Date/time utilities
 *
 * Before this object is accessed the first time, the accessing thread's contextClassLoader
 * must be set to an Android Context.classLoader!
 */
object DateUtils {

    /**
     * Global ical4j time zone registry used for event/task processing. Do not
     * modify this registry or its entries!
     */
    @UsesThreadContextClassLoader
    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()

    init {
        Ical4Android.checkThreadContextClassLoader()
    }


    // time zones

    /**
     * Find the best matching Android (= available in system and Java timezone registry)
     * time zone ID for a given arbitrary time zone ID:
     *
     * 1. Use a case-insensitive match ("EUROPE/VIENNA" will return "Europe/Vienna",
     *    assuming "Europe/Vienna") is available in Android.
     * 2. Find partial matches (case-sensitive) in both directions, so both "Vienna"
     *    and "MyClient: Europe/Vienna" will return "Europe/Vienna". This shouln't be
     *    case-insensitive, because that would for instance return "EST" for "Westeuropäische Sommerzeit".
     * 3. If nothing can be found or [tzId] is `null`, return the system default time zone.
     *
     * @param tzID time zone ID to be converted into Android time zone ID
     *
     * @return best matching Android time zone ID
     */
    fun findAndroidTimezoneID(tzID: String?): String {
        val availableTZs = ZoneId.getAvailableZoneIds()
        var result: String? = null

        if (tzID != null) {
            // first, try to find an exact match (case insensitive)
            result = availableTZs.firstOrNull { it.equals(tzID, true) }

            // if that doesn't work, try to find something else that matches
            if (result == null)
                for (availableTZ in availableTZs)
                    if (availableTZ.contains(tzID) || tzID.contains(availableTZ)) {
                        result = availableTZ
                        Ical4Android.log.warning("Couldn't find system time zone \"$tzID\", assuming $result")
                        break
                    }
        }

        // if that doesn't work, use device default as fallback
        return result ?: TimeZone.getDefault().id
    }

    /**
     * Gets a [ZoneId] from a given ID string. In opposite to [ZoneId.of],
     * this methods returns null when the zone is not available.
     *
     * @param id    zone ID, like "Europe/Berlin" (may be null)
     *
     * @return      ZoneId or null if the argument was null or no zone with this ID could be found
     */
    fun getZoneId(id: String?): ZoneId? =
            id?.let {
                try {
                    val zone = ZoneId.of(id)
                    zone
                } catch (e: Exception) {
                    null
                }
            }

    @Suppress("DEPRECATION")
    @UsesThreadContextClassLoader
    /**
     * Loads a time zone from the ical4j time zone registry (which contains the
     * VTIMEZONE definitions).
     *
     * All Android time zone IDs plus some other time zones should be available.
     * However, the possibility that the time zone is not available in ical4j should
     * be handled.
     *
     * @param id    time zone ID (like `Europe/Vienna`)
     * @return the ical4j time zone (VTIMEZONE), or `null` if no VTIMEZONE is available
     */
    fun ical4jTimeZone(id: String): TimeZone? = tzRegistry.getTimeZone(id)

    /**
     * Determines whether a given date represents a DATE value.
     * @param date date property to check
     * @return *true* if the date is a DATE value; *false* otherwise (for instance, when the argument is a DATE-TIME value or null)
     */
    fun isDate(date: DateProperty?) = date != null && date.date is Date && date.date !is DateTime

    /**
     * Determines whether a given date represents a DATE-TIME value.
     * @param date date property to check
     * @return *true* if the date is a DATE-TIME value; *false* otherwise (for instance, when the argument is a DATE value or null)
     */
    fun isDateTime(date: DateProperty?) = date != null && date.date is DateTime

    /**
     * Parses a VTIMEZONE definition to a VTimeZone object.
     * @param timezoneDef VTIMEZONE definition
     * @return parsed VTimeZone
     * @throws IllegalArgumentException when the timezone definition can't be parsed
     */
    @UsesThreadContextClassLoader
    fun parseVTimeZone(timezoneDef: String): VTimeZone {
        Ical4Android.checkThreadContextClassLoader()
        val builder = CalendarBuilder(tzRegistry)
        try {
            val cal = builder.build(StringReader(timezoneDef))
            return cal.getComponent(VTimeZone.VTIMEZONE) as VTimeZone
        } catch (e: Exception) {
            throw IllegalArgumentException("Couldn't parse timezone definition")
        }
    }

}
