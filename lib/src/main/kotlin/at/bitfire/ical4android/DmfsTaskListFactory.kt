/*
 * This file is part of ical4android which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient

interface DmfsTaskListFactory<out T: DmfsTaskList<DmfsTask>> {

    fun newInstance(account: Account, provider: ContentProviderClient, providerName: TaskProvider.ProviderName, id: Long): T

}