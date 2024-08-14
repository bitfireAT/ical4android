/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.os.Build
import at.bitfire.ical4android.validation.FixInvalidDayOffsetPreprocessor
import at.bitfire.ical4android.validation.FixInvalidUtcOffsetPreprocessor
import at.bitfire.ical4android.validation.ICalPreprocessor
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.verify
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStreamReader
import java.io.StringReader
import org.junit.Assume.assumeTrue

class ICalPreprocessorTest {

    @Test
    fun testPreprocessStream_appliesStreamProcessors() {
        assumeTrue(
            "Can only run on API Level 24 or newer because mockkObject doesn't support Android < P (SDK 28)",
            Build.VERSION.SDK_INT >= 28
        )

        mockkObject(FixInvalidDayOffsetPreprocessor, FixInvalidUtcOffsetPreprocessor) {
            ICalPreprocessor.preprocessStream(StringReader(""))

            // verify that the required stream processors have been called
            verify {
                FixInvalidDayOffsetPreprocessor.preprocess(any())
                FixInvalidUtcOffsetPreprocessor.preprocess(any())
            }
        }
    }


    @Test
    fun testPreprocessCalendar_MsTimeZones() {
        javaClass.classLoader!!.getResourceAsStream("events/outlook1.ics").use { stream ->
            val reader = InputStreamReader(stream, Charsets.UTF_8)
            val calendar = CalendarBuilder().build(reader)
            val vEvent = calendar.getComponent(Component.VEVENT) as VEvent

            assertEquals("W. Europe Standard Time", vEvent.startDate.timeZone.id)
            ICalPreprocessor.preprocessCalendar(calendar)
            assertEquals("Europe/Vienna", vEvent.startDate.timeZone.id)
        }
    }

}