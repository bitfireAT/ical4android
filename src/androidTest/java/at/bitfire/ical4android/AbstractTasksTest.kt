/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)

abstract class AbstractTasksTest(
    val providerName: TaskProvider.ProviderName
) {

    companion object {
        @Parameterized.Parameters(name="{0}")
        @JvmStatic
        fun taskProviders() = listOf(TaskProvider.ProviderName.OpenTasks,TaskProvider.ProviderName.TasksOrg)
    }

    @JvmField
    @Rule
    val permissionRule = GrantPermissionRule.grant(*providerName.permissions)

    var providerOrNull: TaskProvider? = null
    lateinit var provider: TaskProvider

    @Before
    open fun prepare() {
        providerOrNull = TaskProvider.acquire(InstrumentationRegistry.getInstrumentation().context, providerName)
        Assume.assumeNotNull(providerOrNull)      // will halt here if providerOrNull is null

        provider = providerOrNull!!
        Ical4Android.log.fine("Using task provider: $provider")
    }

    @After
    open fun shutdown() {
        providerOrNull?.close()
    }

}