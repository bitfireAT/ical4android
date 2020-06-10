package at.bitfire.ical4android

import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Duration

class EventAndroidTest {

    @Test
    fun testGenerateEtcUTC() {
        val tzUTC = DateUtils.tzRegistry.getTimeZone("Etc/UTC")

        val e = Event()
        e.uid = "etc-utc-test@example.com"
        e.dtStart = DtStart("20200926T080000", tzUTC)
        e.dtEnd = DtEnd("20200926T100000", tzUTC)
        e.alarms += VAlarm(Duration.ofMinutes(-30))
        e.attendees += Attendee("mailto:test@example.com")
        val baos = ByteArrayOutputStream()
        e.write(baos)
        val ical = baos.toString()

        assertTrue("BEGIN:VTIMEZONE.+BEGIN:STANDARD.+END:STANDARD.+END:VTIMEZONE".toRegex(RegexOption.DOT_MATCHES_ALL).containsMatchIn(ical))
    }

}