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
        // REFRESH-INTERVAL;VALUE=DURATION:PT1D
        "(?:^|(DURATION|REFRESH-INTERVAL|RELATED-TO|TRIGGER);VALUE=)" +
            "(DURATION|TRIGGER):-?P((T-?\\d+D)|(-?\\d+DT))$",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )

    override fun fixString(original: String): String {
        var s: String = original

        // Find all instances matching the defined expression
        val found = regexpForProblem().findAll(s)

        // ..and repair them
        for (match in found) {
            val matchStr = match.value
            val fixed = matchStr
                .replace("PT", "P")
                .replace("DT", "D")
            s = s.replace(matchStr, fixed)
        }
        return s
    }

}