package at.bitfire.ical4android.validation

import at.bitfire.ical4android.Ical4Android
import java.util.logging.Level

/**
 * Replaces all the "Europe/Dublin" timezone references to "Europe/London". May be removed when
 * ical4j fixes the issue.
 * @see <a href="https://github.com/ical4j/ical4j/issues/493">GitHub Issue</a>
 */
object FixTimezonePreprocessor: StreamPreprocessor() {
    private val TZOFFSET_REGEXP = Regex("Europe/Dublin",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    override fun regexpForProblem(): Regex = TZOFFSET_REGEXP

    override fun fixString(original: String): String =
        original.replace(TZOFFSET_REGEXP) {
            Ical4Android.log.log(Level.FINE, "Changing problematic Dublin timezone to London timezone", it.value)
            "Europe/London"
        }
}
