package at.bitfire.ical4android.validation.rules

import net.fortuna.ical4j.model.property.TzId

object ReplaceInvalidTzTzIdRule: ReplaceInvalidTzTimezoneRule<TzId>(TzId::class) {
    override fun repair(property: TzId, currentTz: String, newTz: String) {
        if (property.value == currentTz) property.value = newTz
    }
}
