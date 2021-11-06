package at.bitfire.ical4android

import android.accounts.Account
import android.content.Context
import android.test.mock.MockContentProvider
import android.test.mock.MockContentResolver
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.impl.TestJtxCollection
import at.bitfire.jtx.SyncContentProviderContract
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.Property.*
import net.fortuna.ical4j.model.property.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

class JtxICalObjectTest {

    private val testAccount = Account("test", "test")
    lateinit var mContentResolver: MockContentResolver
    lateinit var collection: TestJtxCollection
    lateinit var context: Context

    @Before
    fun setUp() {
        mContentResolver = MockContentResolver()
        context = InstrumentationRegistry.getInstrumentation().targetContext

        val mContentProvider = MockContentProvider(context)
        mContentResolver.addProvider(SyncContentProviderContract.AUTHORITY, mContentProvider)
        val provider = mContentResolver.acquireContentProviderClient(SyncContentProviderContract.AUTHORITY)

        //val provider = mContentResolver.acquireContentProviderClient(SyncContentProviderContract.AUTHORITY)
        assertNotNull(provider)

        /*
        val collectionValues = ContentValues().apply {
            put(SyncContentProviderContract.JtxCollection.ACCOUNT_NAME, testAccount.name)
            put(SyncContentProviderContract.JtxCollection.ACCOUNT_TYPE, testAccount.type)
            put(SyncContentProviderContract.JtxCollection.DESCRIPTION, "Test")
            put(SyncContentProviderContract.JtxCollection.DISPLAYNAME, "Testcollection")
        }

         */


        collection = TestJtxCollection.create(testAccount, provider!!)

    }

    @After
    fun tearDown() {
    }

    @Test
    fun check_input_equals_output_most_fields1() {

        val filename = "jtx/most-fields1.ics"

        val iCalIn = getIncomingIcal(filename, null)
        val iCalOut = getOutgoingIcal(filename, null)

        assertEquals(iCalIn.components[0].getProperty(Component.VTODO), iCalOut.components[0].getProperty(Component.VTODO))

        iCalIn.components[0].properties.forEach { inProp ->

            if(inProp.name == "EXDATE" || inProp.name == "RDATE")
                return@forEach
            val outProp = iCalOut.components[0].properties.getProperty<Property>(inProp.name)
            assertEquals(inProp, outProp)
        }
    }

    @Test
    fun check_input_equals_output_most_fields2() {

        val filename = "jtx/most-fields2.ics"

        val iCalIn = getIncomingIcal(filename, null)
        val iCalOut = getOutgoingIcal(filename, null)

        assertEquals(iCalIn.components[0].getProperty(Component.VTODO), iCalOut.components[0].getProperty(Component.VTODO))

        iCalIn.components[0].properties.forEach { inProp ->
            val outProp = iCalOut.components[0].properties.getProperty<Property>(inProp.name)
            assertEquals(inProp, outProp)
        }
    }

    @Test
    fun check_input_equals_output_utf8() {

        val filename = "jtx/utf8.ics"

        val iCalIn = getIncomingIcal(filename, null)
        val iCalOut = getOutgoingIcal(filename, null)

        assertEquals(iCalIn.components[0].getProperty(Component.VTODO), iCalOut.components[0].getProperty(Component.VTODO))

        iCalIn.components[0].properties.forEach { inProp ->

            if(inProp.name == "DTSTAMP")
                return@forEach

            val outProp = iCalOut.components[0].properties.getProperty<Property>(inProp.name)
            assertEquals(inProp, outProp)
        }
    }

    @Test
    fun check_input_equals_output_rfc5545_sample() {

        val filename = "jtx/rfc5545-sample1.ics"

        val iCalIn = getIncomingIcal(filename, null)
        val iCalOut = getOutgoingIcal(filename, null)

        assertEquals(iCalIn.components[0].getProperty(Component.VTODO), iCalOut.components[0].getProperty(Component.VTODO))

        iCalIn.components[0].properties.forEach { inProp ->

            if(inProp.name == "DTSTAMP")
                return@forEach
            val outProp = iCalOut.components[0].properties.getProperty<Property>(inProp.name)
            assertEquals(inProp, outProp)
        }
    }

    @Test
    fun check_input_equals_output_empty_priority() {

        val filename = "jtx/empty-priority.ics"

        val iCalIn = getIncomingIcal(filename, null)
        val iCalOut = getOutgoingIcal(filename, null)

        assertEquals(iCalIn.components[0].getProperty(Component.VTODO), iCalOut.components[0].getProperty(Component.VTODO))

        iCalIn.components[0].properties.forEach { inProp ->

            if(inProp.name == "DTSTAMP")
                return@forEach
            val outProp = iCalOut.components[0].properties.getProperty<Property>(inProp.name)
            assertEquals(inProp, outProp)
        }
    }


    @Test
    fun check_input_equals_output_latin1() {

        val filename = "jtx/latin1.ics"

        val iCalIn = getIncomingIcal(filename, Charsets.ISO_8859_1)
        val iCalOut = getOutgoingIcal(filename, Charsets.ISO_8859_1)

        assertEquals(iCalIn.components[0].getProperty(Component.VTODO), iCalOut.components[0].getProperty(Component.VTODO))

        iCalIn.components[0].properties.forEach { inProp ->

            if(inProp.name == "DTSTAMP")
                return@forEach
            val outProp = iCalOut.components[0].properties.getProperty<Property>(inProp.name)
            assertEquals(inProp, outProp)
        }
    }



    /**
     * This function takes a file and returns the parsed ical4j Calendar object
     * @param file: The filename of the ics-file
     * @param cs: The desired Charset to read the file, if null, then Charsets.UTF_8 is taken
     * @return the ICalendar with the parsed information from the file
     */
    private fun getIncomingIcal(file: String, cs: Charset?): Calendar {

        val charset = cs ?: Charsets.UTF_8

        val stream = javaClass.classLoader!!.getResourceAsStream(file)
        val reader = InputStreamReader(stream, charset)

        val iCalIn = ICalendar.fromReader(reader)

        stream.close()
        reader.close()

        return iCalIn
    }

    /**
     * This function takes a filename and creates a JtxICalObject.
     * Then it uses the object to create an ical4j Calendar again.
     * @param file: The filename of the ics-file
     * @param cs: The desired Charset to read the file, if null, then Charsets.UTF_8 is taken
     * @return The ICalendar after applying all functionalities of JtxICalObject.fromReader(...)
     */
    private fun getOutgoingIcal(file: String, cs: Charset?): Calendar {

        val charset = cs ?: Charsets.UTF_8

        val stream = javaClass.classLoader!!.getResourceAsStream(file)
        val reader = InputStreamReader(stream, charset)
        val iCalObject = JtxICalObject.fromReader(reader, collection)

        val os = ByteArrayOutputStream()
        iCalObject[0].write(os, context)

        val iCalOut = ICalendar.fromReader(os.toByteArray().inputStream().reader())

        stream.close()
        reader.close()

        return iCalOut
    }


}
