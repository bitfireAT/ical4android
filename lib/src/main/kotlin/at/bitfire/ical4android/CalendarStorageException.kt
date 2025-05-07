/*
 * This file is part of ical4android which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

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