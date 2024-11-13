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
        // REFRESH-INTERVAL;VALUE=DURATION:-PT1D
        "(?:^|^(?:DURATION|REFRESH-INTERVAL|RELATED-TO|TRIGGER);VALUE=)(?:DURATION|TRIGGER):(-?P((T-?\\d+D)|(-?\\d+DT)))$",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )

    override fun fixString(original: String): String {
        var iCal: String = original

        // Find all instances matching the defined expression
        val found = regexpForProblem().findAll(iCal)

        // ..and repair them
        for (match in found) {
            // Fix the duration string
            val faultyDuration = match.groupValues[1]       // IE: "-PT1D" (faulty)
            val fixedDuration = faultyDuration              // IE: "-P1D" (fixed)
                .replace("PT", "P")
                .replace("DT", "D")

            // Replace the faulty duration with the fixed one in the captured line
            val faultyCapture = match.value                 // IE: "REFRESH-INTERVAL;VALUE=DURATION:-PT1D" (faulty)
            val fixedCapture = faultyCapture                // IE: "REFRESH-INTERVAL;VALUE=DURATION:-P1D" (fixed)
                .replace(faultyDuration, fixedDuration)

            // Replace complete faulty line in the iCal string with the fixed one
            iCal = iCal.replace(faultyCapture, fixedCapture)
        }
        return iCal
    }

}