package at.bitfire.ical4android

import net.fortuna.ical4j.model.DefaultTimeZoneRegistryFactory
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.TimeZoneRegistryImpl
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.TzId
import java.time.ZoneId
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.ContentCollection

/**
 * Wrapper around default [TimeZoneRegistry] that uses the Android name if a time zone has a
 * different name in ical4j and Android.
 *
 * **This time zone registry is set as default registry for ical4android projects in
 * resources/ical4j.properties.**
 *
 * For instance, if a time zone is known as "Europe/Kyiv" (with alias "Europe/Kiev") in ical4j
 * and only "Europe/Kiev" in Android, this registry behaves like the default [TimeZoneRegistryImpl],
 * but the returned time zone for `getTimeZone("Europe/Kiev")` has an ID of "Europe/Kiev" and not
 * "Europe/Kyiv".
 */
class AndroidCompatTimeZoneRegistry(
    private val base: TimeZoneRegistry
): TimeZoneRegistry by base {

    /**
     * Gets the time zone for a given ID.
     *
     * If a time zone with the given ID exists in Android, the icalj timezone for this ID
     * is returned, but the TZID is set to the Android name (and not the ical4j name, which
     * may not be known to Android).
     *
     * If a time zone with the given ID doesn't exist in Android, this method returns the
     * result of its [base] method.
     *
     * @param id
     * @return time zone
     */
    override fun getTimeZone(id: String): TimeZone? {
        val tz: TimeZone = base.getTimeZone(id)
            ?: return null      // ical4j doesn't know time zone, return null

        // check whether time zone is available on Android, too
        val androidTzId =
            try {
                ZoneId.of(id).id
            } catch (e: Exception) {
                /* Not available in Android, should return null in a later version.
                   However, we return the ical4j timezone to keep the changes caused by AndroidCompatTimeZoneRegistry introduction
                   as small as possible. */
                return tz
            }

        /* Time zone known by Android. Unfortunately, we can't use the Android timezone database directly
           to generate ical4j timezone definitions (which are based on VTIMEZONE).
           So we have to use the timezone definition from ical4j (based on its own VTIMEZONE database),
           but we also need to use the Android TZ name (otherwise Android may not understand it later).

           Example: getTimeZone("Europe/Kiev") returns a TimeZone with TZID:Europe/Kyiv since ical4j/3.2.5,
           but most Android devices don't now Europe/Kyiv yet.
           */
        if (tz.id != androidTzId) {
            Ical4Android.log.warning("Using Android TZID $androidTzId instead of ical4j ${tz.id}")

            // create a copy of the VTIMEZONE so that we don't modify the original registry values (which are not immutable)
            val vTimeZone = tz.vTimeZone
            val newVTimeZoneProperties = PropertyList(vTimeZone.getProperties())
            newVTimeZoneProperties.all.forEach { property ->
                if (property is TzId)
                    newVTimeZoneProperties.remove(property)
            }
            newVTimeZoneProperties.add(TzId(androidTzId))
            return TimeZone(
                VTimeZone(
                    newVTimeZoneProperties,
                    ComponentList(vTimeZone.observances)
                )
            )
        } else
            return tz
    }


    class Factory : TimeZoneRegistryFactory() {

        override fun createRegistry(): AndroidCompatTimeZoneRegistry {
            val ical4jRegistry = DefaultTimeZoneRegistryFactory().createRegistry()
            return AndroidCompatTimeZoneRegistry(ical4jRegistry)
        }

    }

}