/*
 * This file is part of ical4android which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.util

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract

object MiscUtils {

    // various extension methods

    fun ContentProviderClient.closeCompat() {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            close()
        else
            release()
    }

    /**
     * Removes blank (empty or only white-space) [String] values from [ContentValues].
     *
     * @return the modified object (which is the same object as passed in; for chaining)
     */
    fun ContentValues.removeBlankStrings(): ContentValues {
        val iter = keySet().iterator()
        while (iter.hasNext()) {
            val obj = this[iter.next()]
            if (obj is CharSequence && obj.isBlank())
                iter.remove()
        }
        return this
    }

    /**
     * Returns the entire contents of the current row as a [ContentValues] object.
     *
     * @param  removeBlankRows  whether rows with blank values should be removed
     * @return entire contents of the current row
     */
    fun Cursor.toValues(removeBlankRows: Boolean = false): ContentValues {
        val values = ContentValues(columnCount)
        DatabaseUtils.cursorRowToContentValues(this, values)

        if (removeBlankRows)
            values.removeBlankStrings()

        return values
    }

    fun Uri.asSyncAdapter(account: Account): Uri = buildUpon()
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .build()

}