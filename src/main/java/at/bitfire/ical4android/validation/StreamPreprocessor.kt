/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import org.apache.commons.io.IOUtils
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.util.*

abstract class StreamPreprocessor {

    abstract fun regexpForProblem(): Regex?

    abstract fun fixString(original: String): String

    fun preprocess(reader: Reader): Reader {
        var result: String? = null

        val resetSupported = try {
            reader.reset()
            true
        } catch(e: IOException) {
            false
        }

        if (resetSupported) {
            val regex = regexpForProblem()
            // reset is supported, no need to copy the whole stream to another String (unless we have to fix the TZOFFSET)
            if (regex == null || Scanner(reader).findWithinHorizon(regex.toPattern(), 0) != null) {
                reader.reset()
                result = fixString(IOUtils.toString(reader))
            }
        } else
            // reset not supported, always generate a new String that will be returned
            result = fixString(IOUtils.toString(reader))

        if (result != null)
            // modified or reset not supported, return new stream
            return StringReader(result)

        // not modified, return original iCalendar
        reader.reset()
        return reader
    }

}