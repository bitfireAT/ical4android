package at.bitfire.ical4android.util

import android.Manifest
import android.accounts.Account
import android.provider.CalendarContract
import android.provider.CalendarContract.Instances
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.Ical4Android
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.impl.TestEvent
import at.bitfire.ical4android.util.InitCalendarProviderRule.Companion.withPermissions
import at.bitfire.ical4android.util.MiscUtils.CursorHelper.toValues
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.property.DtStart
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * When the Android Calendar Provider is accessed the first time, it performs some asynchronous initialization.
 * This initialization changes the [Instances], so there may be a short time where the [Instances] are reset,
 * which may cause [Instances]-related tests to fail.
 *
 * You can simulate a first-time access (as it happens on a fresh emulator image in CI) with
 * `adb shell pm clear com.android.providers.calendar`.
 *
 * This rule opens the calendar provider, inserts an event and waits until the instance for this event is ready.
 * So when you apply this rule to a test class, it should be safe to test [Instances].
 *
 * Usually you will want to use [withPermissions], which also grants calendar permissions.
 */
class InitCalendarProviderRule: TestRule {

    companion object {
        /**
         * To make sure the time-intensive [initCalendarProvider] is called only once.
         */
        var initialized = false

        /**
         * Rule chain that first grants calendar permissions and then applies [InitCalendarProviderRule].
         */
        val withPermissions: RuleChain = RuleChain
            .outerRule(GrantPermissionRule.grant(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            ))
            .around(InitCalendarProviderRule())
    }

    private val testAccount = Account(javaClass.canonicalName, CalendarContract.ACCOUNT_TYPE_LOCAL)

    override fun apply(base: Statement, description: Description?) = object: Statement() {
        override fun evaluate() {
            initCalendarProvider()
            base.evaluate()
        }
    }

    @Synchronized
    private fun initCalendarProvider() {
        if (initialized)
            return

        val context = InstrumentationRegistry.getInstrumentation().context
        context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.use { calendarProvider ->
            val calendar = TestCalendar.findOrCreate(testAccount, calendarProvider)
            try {
                // wait until instance of this event is available
                var foundInstances = 0
                do {
                    // add event
                    val time = 1681379449000
                    val event = TestEvent(calendar, Event().apply {
                        dtStart = DtStart(Date(time))
                        summary = "initCalendarProvider"
                    })
                    event.add()
                    val eventId = event.id

                    calendarProvider.query(
                        Instances.CONTENT_URI.buildUpon()
                            .appendPath("1600000000000")        // BEGIN
                            .appendPath("1700000000000")        // END
                            .build(), null, null, null, null
                    )!!.use { cursor ->
                        Ical4Android.log.info("Found ${cursor.count} instances (expected: 1)")
                        while (cursor.moveToNext()) {
                            val values = cursor.toValues()
                            if (values.getAsLong(Instances.EVENT_ID) == eventId)
                                foundInstances++
                        }
                    }

                    event.delete()

                    // wait a bit if calendar provider is not ready yet
                    if (foundInstances == 0)
                        Thread.sleep(100)

                } while (foundInstances == 0)

                initialized = true
            } finally {
                calendar.delete()
            }
        }
    }

}