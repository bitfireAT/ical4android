/*
 * This file is part of ical4android which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.content.ContentValues

interface DmfsTaskFactory<out T: DmfsTask> {

    fun fromProvider(taskList: DmfsTaskList<DmfsTask>, values: ContentValues): T

}
