package at.bitfire.ical4android.validation

/**
 * Some providers do not add the `T` prefix for seconds, minutes and hours, as stated by the
 * [Java Documentation](https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-):
 * > The ASCII letter "T" must occur before the first occurrence, if any, of an hour, minute or
 * second section.
 *
 * This preprocessor simply adds the `T` in this cases.
 *
 * See [matching GitHub issue](https://github.com/bitfireAT/ical4android/issues/117).
 */
object FixMissingTPrefixPreprocessor: StreamPreprocessor() {
    override fun regexpForProblem(): Regex = Regex(
        // Examples:
        // TRIGGER:-P5S
        "^(DURATION|TRIGGER):-?P[^T]?\\d+[SMH]$",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )

    override fun fixString(original: String): String {
        var s: String = original

        // Find all instances matching the defined expression
        val found = FixInvalidDayOffsetPreprocessor.regexpForProblem().findAll(s)

        // ..and repair them
        for (match in found) {
            val matchStr = match.value
            println("MatchStr: $matchStr")
            val fixed = matchStr
                .replace("P", "PT")
            s = s.replace(matchStr, fixed)
        }
        return s
    }

}
