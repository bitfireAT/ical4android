/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import at.bitfire.ical4android.Ical4Android
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Property
import java.io.Reader
import java.util.*
import java.util.logging.Level
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.transform.property.DateListPropertyRule
import net.fortuna.ical4j.transform.property.DatePropertyRule
import net.fortuna.ical4j.transform.property.Rfc5545PropertyRule

/**
 * Applies some rules to increase compatibility of parsed (incoming) iCalendars:
 *
 *   - [CreatedPropertyRule] to make sure CREATED is UTC
 *   - [DatePropertyRule], [DateListPropertyRule] to rename Outlook-specific TZID parameters
 * (like "W. Europe Standard Time" to an Android-friendly name like "Europe/Vienna")
 *
 */
object ICalPreprocessor {

    private val propertyRules: Array<Rfc5545PropertyRule<out Property>> = arrayOf(
        // FIXME - CreatedPropertyRule has been removed
        // CreatedPropertyRule(),      // make sure CREATED is UTC

        DatePropertyRule(),         // These two rules also replace VTIMEZONEs of the iCalendar ...
        DateListPropertyRule()      // ... by the ical4j VTIMEZONE with the same TZID!
    )

    private val streamPreprocessors = arrayOf(
        FixInvalidUtcOffsetPreprocessor,    // fix things like TZOFFSET(FROM,TO):+5730
        FixInvalidDayOffsetPreprocessor     // fix things like DURATION:PT2D
    )

    /**
     * Applies [streamPreprocessors] to a given [Reader] that reads an iCalendar object
     * in order to repair some things that must be fixed before parsing.
     *
     * @param original    original iCalendar object
     * @return            the potentially repaired iCalendar object
     */
    fun preprocessStream(original: Reader): Reader {
        var reader = original
        for (preprocessor in streamPreprocessors)
            reader = preprocessor.preprocess(reader)
        return reader
    }


    /**
     * Applies the set of rules (see class definition) to a given calendar object.
     *
     * @param calendar the calendar object that is going to be modified
     */
    fun preprocessCalendar(calendar: Calendar) {
        for (component in calendar.getComponents<CalendarComponent>())
            for (property in component.getProperties<Property>())
                applyRules(property)
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyRules(property: Property) {
        propertyRules
            .filter { rule -> rule.supportedType.isAssignableFrom(property::class.java) }
            .forEach {
                val beforeStr = property.toString()
                (it as Rfc5545PropertyRule<Property>).applyTo(property)
                val afterStr = property.toString()
                if (beforeStr != afterStr)
                    Ical4Android.log.log(Level.FINER, "$beforeStr -> $afterStr")
            }
    }

}