package at.bitfire.ical4android

import android.content.ContentResolver
import net.fortuna.ical4j.model.ParameterFactoryRegistry
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyFactoryRegistry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Helpers to (de)serialize unknown properties as JSON to store it in an Android ExtendedProperty row.
 *
 * Format: `{ propertyName, propertyValue, { param1Name: param1Value, ... } }`, with the third
 * array (parameters) being optional.
 */
object UnknownProperty {

    /**
     * Use this value for [android.provider.CalendarContract.ExtendedProperties.NAME] and
     * [org.dmfs.tasks.contract.TaskContract.Properties.MIMETYPE].
     */
    const val CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.ical4android.unknown-property"

    /**
     * Recommended maximum size of properties for serialization. Won't be enforced by this
     * class (should be checked by caller).
     */
    const val MAX_UNKNOWN_PROPERTY_SIZE = 25000


    private val parameterFactory = ParameterFactoryRegistry()
    private val propertyFactory = PropertyFactoryRegistry()

    /**
     * Deserializes a JSON string from an ExtendedProperty value to an ical4j property.
     *
     * @param jsonString JSON representation of an ical4j property
     * @return ical4j property, generated from [jsonString]
     * @throws org.json.JSONException when the input value can't be parsed
     */
    fun fromJsonString(jsonString: String): Property {
        val json = JSONArray(jsonString)
        val name = json.getString(0)
        val value = json.getString(1)

        val params = ParameterList()
        json.optJSONObject(2)?.let { jsonParams ->
            for (paramName in jsonParams.keys())
                params.add(parameterFactory.createParameter(
                        paramName,
                        jsonParams.getString(paramName)
                ))
        }

        return propertyFactory.createProperty(name, params, value)
    }

    /**
     * Serializes an ical4j property to a JSON string that can be stored in an ExtendedProperty.
     *
     * @param prop property to serialize as JSON
     * @return JSON representation of [prop]
     */
    fun toJsonString(prop: Property): String {
        val json = JSONArray()
        json.put(prop.name)
        json.put(prop.value)

        if (!prop.parameters.isEmpty) {
            val jsonParams = JSONObject()
            for (param in prop.parameters)
                jsonParams.put(param.name, param.value)
            json.put(jsonParams)
        }

        return json.toString()
    }

}