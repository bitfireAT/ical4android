/*
 * This file is part of ical4android which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assume
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
abstract class AbstractTaskProvidersTest(
    val providerName: TaskProvider.ProviderName
) {

    companion object {
        @Parameterized.Parameters(name="{0}")
        @JvmStatic
        fun taskProviders() = listOf(TaskProvider.ProviderName.OpenTasks,TaskProvider.ProviderName.TasksOrg)
    }

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(*providerName.permissions)


    fun acquireTasksProvider(): TaskProvider {
        val providerOrNull = TaskProvider.acquire(InstrumentationRegistry.getInstrumentation().context, providerName)
        Assume.assumeNotNull(providerOrNull)      // will throw here if providerOrNull is null

        return providerOrNull!!
    }

}