/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

/**
 * Fixes durations with day offsets with the 'T' prefix.
 * See also https://github.com/bitfireAT/icsx5/issues/100
 */
object FixInvalidDayOffsetPreprocessor : StreamPreprocessor() {

    override fun regexpForProblem() = Regex(
        "^(DURATION|TRIGGER):-?PT-?\\d+D$",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )

    override fun fixString(original: String): String {
        var s: String = original

        // Find all matches for the expression
        val found = regexpForProblem().find(s) ?: return s
        for (match in found.groupValues) {
            val fixed = match.replace("PT", "P")
            s = s.replace(match, fixed)
        }
        return s
    }

}