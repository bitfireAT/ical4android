/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

/**
 * Indicates a problem with a calendar storage operation, like when a row can't be inserted or updated.
 *
 * Should not be used to wrap [android.os.RemoteException].
 */
class CalendarStorageException: Exception {

    constructor(message: String): super(message)
    constructor(message: String, ex: Throwable): super(message, ex)

}