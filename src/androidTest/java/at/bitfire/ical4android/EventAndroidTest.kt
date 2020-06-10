package at.bitfire.ical4android

import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.TzId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Duration
import kotlin.concurrent.thread

class EventAndroidTest {

    @Test
    fun testGenerateEtcUTC() {
        val vtz = VTimeZone()
        vtz.properties += TzId("Etc/UTC")
        DateUtils.tzRegistry.register(TimeZone(vtz))

        val tzUTC = DateUtils.tzRegistry.getTimeZone("Etc/UTC")

        val e = Event()
        e.uid = "etc-utc-test@example.com"
        e.dtStart = DtStart("20200926T080000", tzUTC)
        e.dtEnd = DtEnd("20200926T100000", tzUTC)
        e.alarms += VAlarm(Duration.ofMinutes(-30))
        e.attendees += Attendee("mailto:test@example.com")
        val baos = ByteArrayOutputStream()
        e.write(baos)
        val ical = baos.toString(Charsets.UTF_8.toString())
        assertEquals("a", ical)
    }

}