/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient

interface JtxCollectionFactory<out T: Jtx5Collection<JtxICalObject>> {

    fun newInstance(account: Account, client: ContentProviderClient, id: Long): T

}