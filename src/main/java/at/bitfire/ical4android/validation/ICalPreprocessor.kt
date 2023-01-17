/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import at.bitfire.ical4android.Ical4Android
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.transform.rfc5545.CreatedPropertyRule
import net.fortuna.ical4j.transform.rfc5545.DateListPropertyRule
import net.fortuna.ical4j.transform.rfc5545.DatePropertyRule
import net.fortuna.ical4j.transform.rfc5545.Rfc5545PropertyRule
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.util.*
import java.util.logging.Level

/**
 * Applies some rules to increase compatibility of parsed (incoming) iCalendars:
 *
 *   - [CreatedPropertyRule] to make sure CREATED is UTC
 *   - [DatePropertyRule], [DateListPropertyRule] to rename Outlook-specific TZID parameters
 * (like "W. Europe Standard Time" to an Android-friendly name like "Europe/Vienna")
 *
 */
object ICalPreprocessor {

    private val TZOFFSET_REGEXP = Regex("^(TZOFFSET(FROM|TO):[+\\-]?)((18|19|[2-6]\\d)\\d\\d)$", RegexOption.MULTILINE)

    private val INVALID_DAY_PERIOD_REGEX = Regex("-?PT-?\\d+D", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    private val propertyRules = arrayOf(
        CreatedPropertyRule(),      // make sure CREATED is UTC

        DatePropertyRule(),         // These two rules also replace VTIMEZONEs of the iCalendar ...
        DateListPropertyRule(),     // ... by the ical4j VTIMEZONE with the same TZID!
    )


    /**
     * Some servers modify UTC offsets in TZOFFSET(FROM,TO) like "+005730" to an invalid "+5730".
     *
     * Rewrites values of all TZOFFSETFROM and TZOFFSETTO properties which match [TZOFFSET_REGEXP]
     * so that an hour value of 00 is inserted.
     *
     * @param reader Reader that reads the potentially broken iCalendar (which for instance contains `TZOFFSETFROM:+5730`)
     * @return Reader that reads the fixed iCalendar (for instance `TZOFFSETFROM:+005730`)
     */
    fun fixInvalidUtcOffset(reader: Reader): Reader {
        fun fixStringFromReader() =
                IOUtils.toString(reader).replace(TZOFFSET_REGEXP) {
                    Ical4Android.log.log(Level.FINE, "Applying Synology WebDAV fix to invalid utc-offset", it.value)
                    "${it.groupValues[1]}00${it.groupValues[3]}"
                }

        var result: String? = null

        val resetSupported = try {
            reader.reset()
            true
        } catch(e: IOException) {
            false
        }

        if (resetSupported) {
            // reset is supported, no need to copy the whole stream to another String (unless we have to fix the TZOFFSET)
            if (Scanner(reader).findWithinHorizon(TZOFFSET_REGEXP.toPattern(), 0) != null) {
                reader.reset()
                result = fixStringFromReader()
            }
        } else
            result = fixStringFromReader()

        if (result != null)
            return StringReader(result)

        // not modified, return original iCalendar
        reader.reset()
        return reader
    }

    /**
     * Fixes durations with day offsets with the 'T' prefix.
     * @param reader Reader that reads the potentially broken iCalendar
     * @see <a href="https://github.com/bitfireAT/icsx5/issues/100">GitHub</a>
     */
    fun fixInvalidDayOffset(reader: Reader): Reader {
        fun fixStringFromReader(): String {
            // Convert the reader to a string
            var str = IOUtils.toString(reader)
            val found = INVALID_DAY_PERIOD_REGEX.findAll(str)
            // Find all matches for the expression
            for (match in found) {
                // Get the range of the match
                val range = match.range
                // Get the start position of the match
                val start = range.first
                // And the end position
                val end = range.last
                // Get the position of the number inside str (without the prefix)
                val numPos = str.indexOf("PT", start) + 2
                // And get the number, converting it to long
                val number = str.substring(numPos, end).toLongOrNull()
                // If the number has been converted to long correctly
                if (number != null) {
                    // Build a new string with the prefix given, and the number converted to hours
                    val newPiece = str.substring(start, numPos) + (number*24) + "H"
                    // Replace the range found with the new piece
                    str = str.replaceRange(IntRange(start, end), newPiece)
                }
            }
            return str
        }

        var result: String? = null

        val resetSupported = try {
            reader.reset()
            true
        } catch (e: IOException) {
            false
        }

        if (resetSupported) {
            // reset is supported, no need to copy the whole stream to another String (unless we have to fix the period)
            val horizonFind = Scanner(reader)
                .findWithinHorizon(INVALID_DAY_PERIOD_REGEX.toPattern(), 0)
            if (horizonFind != null) {
                reader.reset()
                result = fixStringFromReader()
            }
        } else
        // If reset is not supported, always try to fix the issue by copying the string
            result = fixStringFromReader()

        if (result != null)
            return StringReader(result)

        // not modified, return original iCalendar
        reader.reset()
        return reader
    }


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
                    val beforeStr = property.toString()
                    (it as Rfc5545PropertyRule<Property>).applyTo(property)
                    val afterStr = property.toString()
                    if (beforeStr != afterStr)
                        Ical4Android.log.log(Level.FINER, "$beforeStr -> $afterStr")
                }
    }

}