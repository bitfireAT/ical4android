/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import at.bitfire.ical4android.validation.FixInvalidDayOffsetPreprocessor
import at.bitfire.ical4android.validation.FixInvalidUtcOffsetPreprocessor
import at.bitfire.ical4android.validation.ICalPreprocessor
import io.mockk.mockkObject
import io.mockk.verify
import java.io.File
import java.io.InputStreamReader
import java.io.StringReader
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun testPreprocessStream_files() {
        val preprocessorDir = File("preprocessor")
        val files = listOf(
            "Collingwood_Magpies_Football_Club.ics"
        )
        files.forEach { fileName ->
            val stream = javaClass.classLoader!!.getResourceAsStream("$preprocessorDir/$fileName")
            assertNotNull(stream)
            stream!!.use { inputStream ->
                val reader = ICalPreprocessor.preprocessStream(inputStream.reader())
                CalendarBuilder().build(reader)
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