/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import android.content.ContentValues
import java.lang.reflect.Modifier
import java.util.*

object MiscUtils {

    /**
     * Removes empty [String] values from [values].
     * @param values set of values to be processed
     */
    @JvmStatic
    fun removeEmptyStrings(values: ContentValues) {
        val it = values.keySet().iterator()
        while (it.hasNext()) {
            val obj = values[it.next()]
            if (obj is String && obj.isEmpty())
                it.remove()
        }
    }

    @JvmStatic
    fun reflectionToString(obj: Any): String {
        val s = LinkedList<String>()
        var clazz: Class<in Any>? = obj.javaClass
        while (clazz != null) {
            for (prop in clazz.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }) {
                prop.isAccessible = true
                s += "${prop.name}=" + prop.get(obj)?.toString()?.trim()
            }
            clazz = clazz.superclass
        }
        return "${obj.javaClass.simpleName}=[${s.joinToString(", ")}]"
    }

}