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
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE),
    )

    override fun fixString(original: String): String {
        var s: String = original

        // FIXME I have changed the regex (added ^, DURATION|TRIGGER and $)

        // Find all matches for the expression
        val found = regexpForProblem().findAll(s)
        for (match in found) {
            // Get the range of the match
            val range = match.range
            // Get the start position of the match
            val start = range.first
            // And the end position
            val end = range.last
            // Get the position of the number inside str (without the prefix)
            val numPos = s.indexOf("PT", start) + 2
            // And get the number, converting it to long
            val number = s.substring(numPos, end).toLongOrNull()
            // If the number has been converted to long correctly
            if (number != null) {
                // Build a new string with the prefix given, and the number converted to hours
                val newPiece = s.substring(start, numPos) + (number * 24) + "H"
                // Replace the range found with the new piece
                s = s.replaceRange(IntRange(start, end), newPiece)
            }
        }
        return s
    }

}