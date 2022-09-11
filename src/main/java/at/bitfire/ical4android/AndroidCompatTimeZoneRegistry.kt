package at.bitfire.ical4android

import net.fortuna.ical4j.model.DefaultTimeZoneRegistryFactory
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import java.time.ZoneId
import java.time.zone.ZoneRulesException

class AndroidCompatTimeZoneRegistry(
    private val base: TimeZoneRegistry
): TimeZoneRegistry by base {

    override fun getTimeZone(id: String): TimeZone? {
        // check whether time zone is available on Android
        val androidTzId =
            try {
                ZoneId.of(id).id
            } catch (e: ZoneRulesException) {
                /* Not available in Android, should return null in a later version.
                   However, we return the ical4j timezone to keep the changes caused by AndroidCompatTimeZoneRegistry introduction
                   as small as possible. */
                return base.getTimeZone(id)
            }

        /* Time zone known by Android. Unfortunately, we can't use the Android timezone database directly
           to generate ical4j timezone definitions (which are based on VTIMEZONE).
           So we have to use the timezone definition from ical4j (based on its own VTIMEZONE database),
           but we also need to use the Android TZ name (otherwise Android may not understand it later).

           Example: getTimeZone("Europe/Kiev") returns a TimeZone with TZID:Europe/Kyiv since ical4j/3.2.5,
           but most Android devices don't now Europe/Kyiv yet.
           */
        val tz = base.getTimeZone(id)
        if (tz.id != androidTzId) {
            Ical4Android.log.warning("Using Android TZID $androidTzId instead of ical4j ${tz.id}")
            tz.id = androidTzId                              // set TimeZone ID
            tz.vTimeZone.timeZoneId.value = androidTzId      // set VTIMEZONE TZID
        }

        return tz
    }


    class Factory : TimeZoneRegistryFactory() {

        private val ical4jRegistry = DefaultTimeZoneRegistryFactory().createRegistry()

        override fun createRegistry() = AndroidCompatTimeZoneRegistry(ical4jRegistry)

    }

}