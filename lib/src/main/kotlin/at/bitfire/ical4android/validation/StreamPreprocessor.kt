/*
 * This file is part of ical4android which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.validation

import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.util.Scanner

abstract class StreamPreprocessor {

    abstract fun regexpForProblem(): Regex?

    /**
     * Fixes an iCalendar string.
     *
     * @param original The complete iCalendar string
     * @return The complete iCalendar string, but fixed
     */
    abstract fun fixString(original: String): String

    fun preprocess(reader: Reader): Reader {
        var result: String? = null

        val resetSupported = try {
            reader.reset()
            true
        } catch(_: IOException) {
            false
        }

        if (resetSupported) {
            val regex = regexpForProblem()
            // reset is supported, no need to copy the whole stream to another String (unless we have to fix the TZOFFSET)
            if (regex == null || Scanner(reader).findWithinHorizon(regex.toPattern(), 0) != null) {
                reader.reset()
                result = fixString(reader.readText())
            }
        } else
            // reset not supported, always generate a new String that will be returned
            result = fixString(reader.readText())

        if (result != null)
            // modified or reset not supported, return new stream
            return StringReader(result)

        // not modified, return original iCalendar
        reader.reset()
        return reader
    }

}