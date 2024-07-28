/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import at.bitfire.ical4android.validation.FixInvalidUtcOffsetPreprocessor.TZOFFSET_REGEXP
import java.util.logging.Level
import java.util.logging.Logger


/**
 * Some servers modify UTC offsets in TZOFFSET(FROM,TO) like "+005730" to an invalid "+5730".
 *
 * Rewrites values of all TZOFFSETFROM and TZOFFSETTO properties which match [TZOFFSET_REGEXP]
 * so that an hour value of 00 is inserted.
 */
object FixInvalidUtcOffsetPreprocessor: StreamPreprocessor() {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val TZOFFSET_REGEXP = Regex("^(TZOFFSET(FROM|TO):[+\\-]?)((18|19|[2-6]\\d)\\d\\d)$",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    override fun regexpForProblem() = TZOFFSET_REGEXP

    override fun fixString(original: String) =
        original.replace(TZOFFSET_REGEXP) {
            logger.log(Level.FINE, "Applying Synology WebDAV fix to invalid utc-offset", it.value)
            "${it.groupValues[1]}00${it.groupValues[3]}"
        }

}