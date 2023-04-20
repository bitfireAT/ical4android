package at.bitfire.ical4android.validation.rules

import net.fortuna.ical4j.model.component.VTimeZone

object ReplaceInvalidTzVTimeZoneRule: ReplaceInvalidTzTimezoneRule<VTimeZone>(VTimeZone::class) {
    override fun repair(property: VTimeZone, oldTzId: String, newTz: VTimeZone) {
        if (property.timeZoneId?.value == oldTzId) {
            property.properties.clear()
            property.properties.addAll(newTz.properties)
            property.components.clear()
            property.components.addAll(newTz.components)
        }
    }
}
