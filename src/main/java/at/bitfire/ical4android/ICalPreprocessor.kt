package at.bitfire.ical4android

import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.transform.rfc5545.DateListPropertyRule
import net.fortuna.ical4j.transform.rfc5545.DatePropertyRule
import net.fortuna.ical4j.transform.rfc5545.Rfc5545PropertyRule
import java.util.logging.Level

/**
 * Applies some rules to increase compatibility or parsed iCalendars:
 *
 * - [DatePropertyRule], [DateListPropertyRule]: to rename Outlook-specific TZID parameters
 * (like "W. Europe Standard Time" to an Android-friendly name like "Europe/Vienna")
 *
 */
object ICalPreprocessor {

    private val propertyRules = arrayOf(
            DatePropertyRule(),
            DateListPropertyRule()
    )

    /**
     * Applies the set of rules (see class definition) to a given calendar object.
     *
     * @param calendar the calendar object that is going to be modified
     */
    fun preProcess(calendar: Calendar) {
        for (component in calendar.components) {
            for (property in component.properties)
                applyRules(property)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyRules(property: Property) {
        propertyRules
                .filter { rule -> rule.supportedType.isAssignableFrom(property::class.java) }
                .forEach {
                    Constants.log.log(Level.FINER, "Applying rules to ${property.toString()}")
                    (it as Rfc5545PropertyRule<Property>).applyTo(property)
                    Constants.log.log(Level.FINER, "-> ${property.toString()}")
                }
    }

}