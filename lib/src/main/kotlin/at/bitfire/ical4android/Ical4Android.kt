/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

@Suppress("unused")
object Ical4Android {

    const val ical4jVersion = BuildConfig.version_ical4j


    fun checkThreadContextClassLoader() {
        if (Thread.currentThread().contextClassLoader == null)
            throw IllegalStateException("Thread.currentThread().contextClassLoader must be set for java.util.ServiceLoader (used by ical4j)")
    }

}