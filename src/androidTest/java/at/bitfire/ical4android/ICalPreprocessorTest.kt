/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import at.bitfire.ical4android.validation.FixInvalidDayOffsetPreprocessor
import at.bitfire.ical4android.validation.FixInvalidUtcOffsetPreprocessor
import at.bitfire.ical4android.validation.ICalPreprocessor
import io.mockk.*
import java.io.InputStreamReader
import java.io.StringReader
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent
import org.apache.commons.io.IOUtils
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test

class ICalPreprocessorTest {

    @Test
    fun testPreprocesStream_appliesStreamProcessors() {
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