package at.bitfire.ical4android.util

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import net.fortuna.ical4j.model.FluentProperty
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.util.TimeZones

object Ical4jUtils {
    /**
     * Removes all properties from the PropertyList that match the given predicate.
     *
     * @param predicate The predicate used to determine whether a property should be removed.
     *                  The predicate takes a Property as its input and should return true if the property should be
     *                  removed, and false otherwise.
     */
    fun PropertyList.removeAll(predicate: (Property) -> Boolean) =
        all.forEach { if (predicate(it)) remove(it) }

    /**
     * Converts the LocalDateTime to a ZonedDateTime object in the specified time zone.
     *
     * If no time zone is given, the system's default will be used.
     *
     * @param tz The time zone to convert to. Can be null.
     *
     * @return Returns the Temporal object representing the LocalDateTime in the specified time zone.
     */
    fun LocalDateTime.atZone(tz: TimeZone?): ZonedDateTime =
        if (tz != null)
            if (TimeZones.isUtc(tz))
                atZone(ZoneOffset.UTC)
            else
                atZone(tz.toZoneId())
        else
            atZone(ZoneId.systemDefault())

    /**
     * Applies an optional parameter to a FluentProperty object.
     *
     * @param parameter The optional parameter to apply
     *
     * @return The FluentProperty object with the optional parameter applied
     */
    @Suppress("UNCHECKED_CAST")
    fun <P : FluentProperty> P.withOptionalParameter(parameter: Parameter?): P =
        if (parameter != null)
            withParameter(parameter) as P
        else
            this
}
