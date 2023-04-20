package at.bitfire.ical4android.validation.rules

import kotlin.reflect.KClass
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.Daylight
import net.fortuna.ical4j.model.component.Standard
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.TzId
import net.fortuna.ical4j.model.property.TzName
import net.fortuna.ical4j.model.property.TzOffsetFrom
import net.fortuna.ical4j.model.property.TzOffsetTo
import net.fortuna.ical4j.transform.rfc5545.Rfc5545Rule

abstract class ReplaceInvalidTzTimezoneRule<T: Any>(
    private val kClass: KClass<T>
) : Rfc5545Rule<T> {
    companion object {
        /**
         * BEGIN:VTIMEZONE
         * TZID:Europe/London
         * X-LIC-LOCATION:Europe/London
         * BEGIN:DAYLIGHT
         * TZOFFSETFROM:+0000
         * TZOFFSETTO:+0100
         * TZNAME:BST
         * DTSTART:19700329T010000
         * END:DAYLIGHT
         * BEGIN:STANDARD
         * TZOFFSETFROM:+0100
         * TZOFFSETTO:+0000
         * TZNAME:GMT
         * DTSTART:19701025T020000
         * END:STANDARD
         * END:VTIMEZONE
         */

        val timeZoneReplacements = mapOf(
            "Europe/Dublin" to VTimeZone(
                PropertyList<Property>(1).apply {
                    add(TzId("Europe/London"))
                },
                ComponentList(
                    listOf(
                        Daylight(
                            PropertyList<Property>(4).apply {
                                add(TzOffsetFrom("+0000"))
                                add(TzOffsetTo("+0100"))
                                add(TzName("BST"))
                                add(DtStart("19700329T010000"))
                            }
                        ),
                        Standard(
                            PropertyList<Property>(4).apply {
                                add(TzOffsetFrom("+0100"))
                                add(TzOffsetTo("+0000"))
                                add(TzName("GMT"))
                                add(DtStart("19701025T020000"))
                            }
                        )
                    )
                )
            )
        )
    }

    /**
     * Repairs the passed [property] if matching an internal criteria.
     * @param property The property to be updated.
     * @param oldTzId The timezone to be replaced. [repair] should evaluate based on this parameter
     * whether to perform the repair or not.
     * @param newTz The timezone to set instead of [oldTzId].
     */
    protected abstract fun repair(property: T, oldTzId: String, newTz: VTimeZone)

    override fun applyTo(element: T?) {
        if (element == null) return
        for ((current, new) in timeZoneReplacements)
            repair(element, current, new)
    }

    override fun getSupportedType(): Class<T> = kClass.java
}