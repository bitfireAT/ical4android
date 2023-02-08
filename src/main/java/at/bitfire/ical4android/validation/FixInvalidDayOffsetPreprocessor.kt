/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

/**
 * Fixes durations with day offsets with the 'T' prefix.
 * See also https://github.com/bitfireAT/ical4android/issues/77
 */
object FixInvalidDayOffsetPreprocessor : StreamPreprocessor() {

    override fun regexpForProblem() = Regex(
        // Examples:
        // TRIGGER:-P2DT
        // TRIGGER:-PT2D
        "^(DURATION|TRIGGER):-?P((T-?\\d+D)|(-?\\d+DT))\$",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )

    override fun fixString(original: String): String {
        var s: String = original

        // Find all matches for the expression
        val found = regexpForProblem().find(s) ?: return s
        for (match in found.groupValues) {
            val fixed = match
                .replace("PT", "P")
                .replace("DT", "D")
            s = s.replace(match, fixed)
        }
        return s
    }

}