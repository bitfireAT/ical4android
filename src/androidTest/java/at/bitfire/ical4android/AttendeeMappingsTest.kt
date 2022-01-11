/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract.Attendees
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.model.property.Attendee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttendeeMappingsTest {

    companion object {
        const val DEFAULT_ORGANIZER = "organizer@example.com"

        val CuTypeFancy = CuType("X-FANCY")
        val RoleFancy = Role("X-FANCY")
    }


    @Test
    fun testAndroidToICalendar_TypeRequired_RelationshipAttendee() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
        }) {
            assertNull(getParameter(Parameter.CUTYPE))
            assertNull(getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeRequired_RelationshipOrganizer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ORGANIZER)
        }) {
            assertNull(getParameter(Parameter.CUTYPE))
            assertNull(getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeRequired_RelationshipPerformer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_PERFORMER)
        }) {
            assertEquals(CuType.GROUP, getParameter(Parameter.CUTYPE))
            assertNull(getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeRequired_RelationshipSpeaker() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_SPEAKER)
        }) {
            assertNull(getParameter(Parameter.CUTYPE))
            assertEquals(Role.CHAIR, getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeRequired_RelationshipNone() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_NONE)
        }) {
            assertEquals(CuType.UNKNOWN, getParameter(Parameter.CUTYPE))
            assertNull(getParameter(Parameter.ROLE))
        }
    }


    @Test
    fun testAndroidToICalendar_TypeOptional_RelationshipAttendee() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_OPTIONAL)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
        }) {
            assertNull(getParameter(Parameter.CUTYPE))
            assertEquals(Role.OPT_PARTICIPANT, getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeOptional_RelationshipOrganizer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_OPTIONAL)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ORGANIZER)
        }) {
            assertNull(getParameter(Parameter.CUTYPE))
            assertEquals(Role.OPT_PARTICIPANT, getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeOptional_RelationshipPerformer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_OPTIONAL)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_PERFORMER)
        }) {
            assertEquals(CuType.GROUP, getParameter(Parameter.CUTYPE))
            assertEquals(Role.OPT_PARTICIPANT, getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeOptional_RelationshipSpeaker() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_OPTIONAL)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_SPEAKER)
        }) {
            assertNull(getParameter(Parameter.CUTYPE))
            assertEquals(Role.CHAIR, getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeOptional_RelationshipNone() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_OPTIONAL)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_NONE)
        }) {
            assertEquals(CuType.UNKNOWN, getParameter(Parameter.CUTYPE))
            assertEquals(Role.OPT_PARTICIPANT, getParameter(Parameter.ROLE))
        }
    }


    @Test
    fun testAndroidToICalendar_TypeNone_RelationshipAttendee() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
        }) {
            assertNull(getParameter(Parameter.CUTYPE))
            assertNull(getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeNone_RelationshipOrganizer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ORGANIZER)
        }) {
            assertNull(getParameter(Parameter.CUTYPE))
            assertNull(getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeNone_RelationshipPerformer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_PERFORMER)
        }) {
            assertEquals(CuType.GROUP, getParameter(Parameter.CUTYPE))
            assertNull(getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeNone_RelationshipSpeaker() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_SPEAKER)
        }) {
            assertNull(getParameter(Parameter.CUTYPE))
            assertEquals(Role.CHAIR, getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeNone_RelationshipNone() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_NONE)
        }) {
            assertEquals(CuType.UNKNOWN, getParameter(Parameter.CUTYPE))
            assertNull(getParameter(Parameter.ROLE))
        }
    }


    @Test
    fun testAndroidToICalendar_TypeResource_RelationshipAttendee() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_RESOURCE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
        }) {
            assertEquals(CuType.RESOURCE, getParameter(Parameter.CUTYPE))
            assertNull(getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeResource_RelationshipOrganizer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_RESOURCE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ORGANIZER)
        }) {
            assertEquals(CuType.RESOURCE, getParameter(Parameter.CUTYPE))
            assertNull(getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeResource_RelationshipPerformer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_RESOURCE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_PERFORMER)
        }) {
            assertEquals(CuType.ROOM, getParameter(Parameter.CUTYPE))
            assertNull(getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeResource_RelationshipSpeaker() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_RESOURCE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_SPEAKER)
        }) {
            assertEquals(CuType.RESOURCE, getParameter(Parameter.CUTYPE))
            assertEquals(Role.CHAIR, getParameter(Parameter.ROLE))
        }
    }

    @Test
    fun testAndroidToICalendar_TypeResource_RelationshipNone() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_RESOURCE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_NONE)
        }) {
            assertEquals(CuType.RESOURCE, getParameter(Parameter.CUTYPE))
            assertNull(getParameter(Parameter.ROLE))
        }
    }



    @Test
    fun testICalendarToAndroid_CuTypeNone_RoleNone() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com")) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeNone_RoleChair() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(Role.CHAIR)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_SPEAKER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeNone_RoleReqParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(Role.REQ_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeNone_RoleOptParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(Role.OPT_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_OPTIONAL, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeNone_RoleNonParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(Role.NON_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_NONE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeNone_RoleXValue() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(RoleFancy)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }


    @Test
    fun testICalendarToAndroid_CuTypeIndividual_RoleNone() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.INDIVIDUAL)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeIndividual_RoleChair() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.INDIVIDUAL)
            parameters.add(Role.CHAIR)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_SPEAKER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeIndividual_RoleReqParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.INDIVIDUAL)
            parameters.add(Role.REQ_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeIndividual_RoleOptParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.INDIVIDUAL)
            parameters.add(Role.OPT_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_OPTIONAL, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeIndividual_RoleNonParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.INDIVIDUAL)
            parameters.add(Role.NON_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_NONE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeIndividual_RoleXValue() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.INDIVIDUAL)
            parameters.add(RoleFancy)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }


    @Test
    fun testICalendarToAndroid_CuTypeUnknown_RoleNone() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.UNKNOWN)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_NONE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeUnknown_RoleChair() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.UNKNOWN)
            parameters.add(Role.CHAIR)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_SPEAKER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeUnknown_RoleReqParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.UNKNOWN)
            parameters.add(Role.REQ_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_NONE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeUnknown_RoleOptParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.UNKNOWN)
            parameters.add(Role.OPT_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_OPTIONAL, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_NONE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeUnknown_RoleNonParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.UNKNOWN)
            parameters.add(Role.NON_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_NONE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_NONE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeUnknown_RoleXValue() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.UNKNOWN)
            parameters.add(RoleFancy)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_NONE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }


    @Test
    fun testICalendarToAndroid_CuTypeGroup_RoleNone() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.GROUP)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_PERFORMER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeGroup_RoleChair() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.GROUP)
            parameters.add(Role.CHAIR)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_SPEAKER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeGroup_RoleReqParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.GROUP)
            parameters.add(Role.REQ_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_PERFORMER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeGroup_RoleOptParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.GROUP)
            parameters.add(Role.OPT_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_OPTIONAL, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_PERFORMER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeGroup_RoleNonParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.GROUP)
            parameters.add(Role.NON_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_NONE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_PERFORMER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeGroup_RoleXValue() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.GROUP)
            parameters.add(RoleFancy)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_PERFORMER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }


    @Test
    fun testICalendarToAndroid_CuTypeResource_RoleNone() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.RESOURCE)
        }) {
            assertEquals(Attendees.TYPE_RESOURCE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_NONE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeResource_RoleChair() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.RESOURCE)
            parameters.add(Role.CHAIR)
        }) {
            assertEquals(Attendees.TYPE_RESOURCE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_SPEAKER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeResource_RoleReqParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.RESOURCE)
            parameters.add(Role.REQ_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_RESOURCE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_NONE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeResource_RoleOptParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.RESOURCE)
            parameters.add(Role.OPT_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_RESOURCE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_NONE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeResource_RoleNonParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.RESOURCE)
            parameters.add(Role.NON_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_RESOURCE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_NONE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeResource_RoleXValue() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.RESOURCE)
            parameters.add(RoleFancy)
        }) {
            assertEquals(Attendees.TYPE_RESOURCE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_NONE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }


    @Test
    fun testICalendarToAndroid_CuTypeRoom_RoleNone() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.ROOM)
        }) {
            assertEquals(Attendees.TYPE_RESOURCE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_PERFORMER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeRoom_RoleChair() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.ROOM)
            parameters.add(Role.CHAIR)
        }) {
            assertEquals(Attendees.TYPE_RESOURCE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_PERFORMER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeRoom_RoleReqParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.ROOM)
            parameters.add(Role.REQ_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_RESOURCE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_PERFORMER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeRoom_RoleOptParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.ROOM)
            parameters.add(Role.OPT_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_RESOURCE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_PERFORMER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeRoom_RoleNonParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.ROOM)
            parameters.add(Role.NON_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_RESOURCE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_PERFORMER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeRoom_RoleXValue() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuType.ROOM)
            parameters.add(RoleFancy)
        }) {
            assertEquals(Attendees.TYPE_RESOURCE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_PERFORMER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }


    @Test
    fun testICalendarToAndroid_CuTypeXValue_RoleNone() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuTypeFancy)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeXValue_RoleChair() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuTypeFancy)
            parameters.add(Role.CHAIR)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_SPEAKER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeXValue_RoleReqParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuTypeFancy)
            parameters.add(Role.REQ_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeXValue_RoleOptParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuTypeFancy)
            parameters.add(Role.OPT_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_OPTIONAL, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeXValue_RoleNonParticipant() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuTypeFancy)
            parameters.add(Role.NON_PARTICIPANT)
        }) {
            assertEquals(Attendees.TYPE_NONE, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeXValue_RoleXValue() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com").apply {
            parameters.add(CuTypeFancy)
            parameters.add(RoleFancy)
        }) {
            assertEquals(Attendees.TYPE_REQUIRED, values[Attendees.ATTENDEE_TYPE])
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }


    @Test
    fun testICalendarToAndroid_Organizer() {
        testICalendarToAndroid(Attendee("mailto:$DEFAULT_ORGANIZER")) {
            assertEquals(Attendees.RELATIONSHIP_ORGANIZER, values[Attendees.ATTENDEE_RELATIONSHIP])
        }
    }



    // helpers

    private fun testICalendarToAndroid(attendee: Attendee, organizer: String = DEFAULT_ORGANIZER, test: (BatchOperation.CpoBuilder).() -> Unit) {
        val row = BatchOperation.CpoBuilder.newInsert(Uri.EMPTY)
        AttendeeMappings.iCalendarToAndroid(attendee, row, organizer)
        test(row)
    }

    private fun testAndroidToICalendar(values: ContentValues, test: (Attendee).() -> Unit) {
        val attendee = Attendee()
        AttendeeMappings.androidToICalendar(values, attendee)
        test(attendee)
    }

}