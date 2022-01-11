/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import java.util.logging.Level
import java.util.logging.Logger

object Ical4Android {

    val log: Logger = Logger.getLogger("ical4android")

    const val ical4jVersion = BuildConfig.version_ical4j


    init {
        if (BuildConfig.DEBUG)
            log.level = Level.ALL
    }

    fun checkThreadContextClassLoader() {
        if (Thread.currentThread().contextClassLoader == null)
            throw IllegalStateException("Thread.currentThread().contextClassLoader must be set")
    }

}
