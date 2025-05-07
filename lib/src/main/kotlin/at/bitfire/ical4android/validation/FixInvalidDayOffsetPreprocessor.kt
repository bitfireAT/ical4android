/*
 * This file is part of ical4android which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

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
        val found = regexpForProblem().findAll(iCal).toList()

        // ... and repair them. Use reversed order so that already replaced occurrences don't interfere with the following matches.
        for (match in found.reversed()) {
            match.groups[1]?.let { duration ->      // first capturing group is the duration value, for instance: "-PT1D"
                val fixed = duration.value     // fixed is then for instance: "-P1D"
                    .replace("PT", "P")
                    .replace("DT", "D")
                iCal = iCal.replaceRange(duration.range, fixed)
            }
        }
        return iCal
    }

}