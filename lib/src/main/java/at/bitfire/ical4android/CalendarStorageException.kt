/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

class CalendarStorageException: Exception {

    constructor(message: String): super(message)
    constructor(message: String, ex: Throwable): super(message, ex)

}