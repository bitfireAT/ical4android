/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import java.util.logging.Logger

@Suppress("unused")
object Ical4Android {

    const val ical4jVersion = BuildConfig.version_ical4j

    @Deprecated("Use java.util.Logger.getLogger(javaClass.name) instead", ReplaceWith("Logger.getLogger(javaClass.name)", "java.util.logging.Logger"))
    val log: Logger = Logger.getLogger("at.bitfire.ical4android")


    fun checkThreadContextClassLoader() {
        if (Thread.currentThread().contextClassLoader == null)
            throw IllegalStateException("Thread.currentThread().contextClassLoader must be set for java.util.ServiceLoader (used by ical4j)")
    }

}
