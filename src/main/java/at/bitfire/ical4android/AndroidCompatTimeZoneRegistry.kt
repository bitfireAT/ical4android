package at.bitfire.ical4android

import net.fortuna.ical4j.model.DefaultTimeZoneRegistryFactory
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.util.TimeZones

class AndroidCompatTimeZoneRegistry(
    val base: TimeZoneRegistry
): TimeZoneRegistry by base {

    private val utcZone = TimeZones.getUtcTimeZone()

    override fun getTimeZone(id: String): TimeZone? {
        // check whether time zone is available on Android
        val androidTz = java.util.TimeZone.getTimeZone(id)
        if (androidTz == utcZone) {
            // UTC or not available in Android, return ical4j guess (may be null).
            return base.getTimeZone(id)

        } else {
            /* Time zone known by Android. Unfortunately, we can't use the Android timezone database directly
               to generate ical4j timezone definitions (which are based on VTIMEZONE).
               So we use the timezone definition from ical4j (based on its own VTIMEZONE database),
               but we have to make sure to use the Android TZ name (otherwise Android may not understand it later).

               Example: getTimeZone("Europe/Kiev") returns a TimeZone with TZID:Europe/Kyiv since ical4j/3.2.5,
               but most Android devices don't now Europe/Kyiv yet.
               */
            val tz = base.getTimeZone(id)
            if (tz.id != id) {
                Ical4Android.log.warning("ical4j uses TZ name ${tz.id} but Android uses $id; using Android name")
                tz.id = id                              // set TimeZone ID
                tz.vTimeZone.timeZoneId.value = id      // set VTIMEZONE TZID
            }

            return tz
        }

    }


    class Factory : TimeZoneRegistryFactory() {

        private val ical4jRegistry = DefaultTimeZoneRegistryFactory().createRegistry()

        override fun createRegistry() = AndroidCompatTimeZoneRegistry(ical4jRegistry)

    }

}