package at.bitfire.ical4android.validation.rules

import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.DateProperty

object ReplaceInvalidTzDatetimeRule: ReplaceInvalidTzTimezoneRule<DateProperty>(DateProperty::class) {
    override fun repair(property: DateProperty, oldTzId: String, newTz: VTimeZone) {
        val timeZone: TimeZone = property.timeZone ?: return
        if (timeZone.id == oldTzId)
            property.timeZone = TimeZone(newTz)
    }
}
