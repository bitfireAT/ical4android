/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import android.content.ContentValues

interface AndroidTaskFactory<out T: AndroidTask> {

    fun newInstance(calendar: AndroidTaskList<AndroidTask>, id: Long, baseInfo: ContentValues?): T
    fun newInstance(calendar: AndroidTaskList<AndroidTask>, task: Task): T

}
