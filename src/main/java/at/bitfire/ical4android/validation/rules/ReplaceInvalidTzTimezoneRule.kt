package at.bitfire.ical4android.validation.rules

import kotlin.reflect.KClass
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.transform.rfc5545.Rfc5545PropertyRule

abstract class ReplaceInvalidTzTimezoneRule<T : Property>(
    private val kClass: KClass<T>
) : Rfc5545PropertyRule<T> {
    companion object {
        protected val timeZoneReplacements = mapOf(
            "Europe/Dublin" to "Europe/London"
        )
    }

    protected abstract fun repair(property: T, currentTz: String, newTz: String)

    override fun applyTo(element: T?) {
        if (element == null) return
        for ((current, new) in timeZoneReplacements)
            repair(element, current, new)
    }

    override fun getSupportedType(): Class<T> = kClass.java
}