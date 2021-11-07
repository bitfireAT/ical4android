package at.bitfire.ical4android

import android.accounts.Account
import android.content.Context
import android.test.mock.MockContentProvider
import android.test.mock.MockContentResolver
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.impl.TestJtxCollection
import at.bitfire.jtx.SyncContentProviderContract
import junit.framework.TestCase.assertEquals
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.Property.*
import net.fortuna.ical4j.model.property.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader

class JtxICalObjectTest {

    private val testAccount = Account("test", "test")
    private lateinit var mContentResolver: MockContentResolver
    lateinit var collection: TestJtxCollection
    lateinit var context: Context

    @Before
    fun setUp() {
        mContentResolver = MockContentResolver()
        context = InstrumentationRegistry.getInstrumentation().targetContext

        val mContentProvider = MockContentProvider(context)
        mContentResolver.addProvider(SyncContentProviderContract.AUTHORITY, mContentProvider)
        val provider = mContentResolver.acquireContentProviderClient(SyncContentProviderContract.AUTHORITY)
        collection = TestJtxCollection.create(testAccount, provider!!)
    }

    @After
    fun tearDown() {
    }

    // VTODO

    @Test fun check_input_equals_output_vtodo_most_fields1() = compare_properties("jtx/vtodo/most-fields1.ics", listOf("EXDATE", "RDATE"))
    @Test fun check_input_equals_output_vtodo_most_fields2() = compare_properties("jtx/vtodo/most-fields2.ics", null)
    @Test fun check_input_equals_output_vtodo_utf8() = compare_properties("jtx/vtodo/utf8.ics", null)
    @Test fun check_input_equals_output_vtodo_rfc5545_sample() = compare_properties("jtx/vtodo/rfc5545-sample1.ics", null)
    @Test fun check_input_equals_output_vtodo_empty_priority() = compare_properties("jtx/vtodo/empty-priority.ics", null)
    @Test fun check_input_equals_output_vtodo_latin1() = compare_properties("jtx/vtodo/latin1.ics", null)

    // VJOURNAL
    @Test fun check_input_equals_output_vjournal_default_example() = compare_properties("jtx/vjournal/default-example.ics", null)
    @Test fun check_input_equals_output_vjournal_default_example_note() = compare_properties("jtx/vjournal/default-example-note.ics", null)
    @Test fun check_input_equals_output_vjournal_utf8() = compare_properties("jtx/vjournal/utf8.ics", null)
    @Test fun check_input_equals_output_vjournal_two_line() = compare_properties("jtx/vjournal/two-line-description-without-crlf.ics", listOf("CREATED", "LAST-MODIFIED", "DTSTART"))   // expected:<CREATED;VALUE=DATE-TIME:20131008T205713Z > but was:<CREATED:20131008T205713Z  is ignored here; expected:<LAST-MODIFIED;VALUE=DATE-TIME:20131008T205740> but was:<LAST-MODIFIED:20131008T205740Z  is ignored here as the actual is the correct result
    //@Test fun check_input_equals_output_vjournal_two_journals() = compare_properties("jtx/vjournal/two-events-without-exceptions.ics", null) // this file contains two events, the direct comparison through the given method would not work
    @Test fun check_input_equals_output_vjournal_recurring() = compare_properties("jtx/vjournal/recurring.ics", null)
    //@Test fun check_input_equals_output_vjournal_outlook_theoretical() = compare_properties("jtx/vjournal/outlook-theoretical.ics", null)    // includes custom timezones, ignored for now
    @Test fun check_input_equals_output_vjournal_outlook_theoretical2() = compare_properties("jtx/vjournal/outlook-theoretical2.ics", null)
    @Test fun check_input_equals_output_vjournal_latin1() = compare_properties("jtx/vjournal/latin1.ics", null)
    @Test fun check_input_equals_output_vjournal_journalonthatday() = compare_properties("jtx/vjournal/journal-on-that-day.ics", null)
    //@Test fun check_input_equals_output_vjournal_dst_only_vtimezone() = compare_properties("jtx/vjournal/dst-only-vtimezone.ics", null)    // includes custom timezones, ignored for now
    @Test fun check_input_equals_output_vjournal_all_day() = compare_properties("jtx/vjournal/all-day.ics", null)



    /**
     * This function takes a file asserts if the ICalendar is the same before and after processing with getIncomingIcal and getOutgoingIcal
     * @param filename the filename to be processed
     * @param exceptions a list of property names that should not cause the assertion to fail (DTSTAMP is taken in any case)
     */
    private fun compare_properties(filename: String, exceptions: List<String>?) {

        val iCalIn = getIncomingIcal(filename)
        val iCalOut = getOutgoingIcal(filename)

        //assertEquals(iCalIn.components[0].getProperty(Component.VTODO), iCalOut.components[0].getProperty(Component.VTODO))

        // there should only be one component for VJOURNAL and VTODO!
        for(i in 0 until iCalIn.components.size)  {

            iCalIn.components[i].properties.forEach { inProp ->

                if(inProp.name == "DTSTAMP" || exceptions?.contains(inProp.name) == true)
                    return@forEach
                val outProp = iCalOut.components[i].properties.getProperty<Property>(inProp.name)
                assertEquals(inProp, outProp)
            }
        }

    }


    /**
     * This function takes a file and returns the parsed ical4j Calendar object
     * @param filename: The filename of the ics-file
     * @return the ICalendar with the parsed information from the file
     */
    private fun getIncomingIcal(filename: String): Calendar {

        val stream = javaClass.classLoader!!.getResourceAsStream(filename)
        val reader = InputStreamReader(stream, Charsets.UTF_8)

        val iCalIn = ICalendar.fromReader(reader)

        stream.close()
        reader.close()

        return iCalIn
    }

    /**
     * This function takes a filename and creates a JtxICalObject.
     * Then it uses the object to create an ical4j Calendar again.
     * @param filename: The filename of the ics-file
     * @return The ICalendar after applying all functionalities of JtxICalObject.fromReader(...)
     */
    private fun getOutgoingIcal(filename: String): Calendar {

        val stream = javaClass.classLoader!!.getResourceAsStream(filename)
        val reader = InputStreamReader(stream, Charsets.UTF_8)
        val iCalObject = JtxICalObject.fromReader(reader, collection)

        val os = ByteArrayOutputStream()

        iCalObject[0].write(os, context)

        val iCalOut = ICalendar.fromReader(os.toByteArray().inputStream().reader())

        stream.close()
        reader.close()

        return iCalOut
    }
}
