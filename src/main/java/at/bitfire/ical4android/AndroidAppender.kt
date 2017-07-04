/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android

import org.apache.log4j.Appender
import org.apache.log4j.Layout
import org.apache.log4j.Priority
import org.apache.log4j.spi.ErrorHandler
import org.apache.log4j.spi.Filter
import org.apache.log4j.spi.LoggingEvent
import java.util.logging.Level

class AndroidAppender: Appender {

    override fun setName(name: String?) {}
    override fun getName() = null

    override fun setErrorHandler(errorHandler: ErrorHandler?) {}
    override fun getErrorHandler() = null

    override fun requiresLayout() = false
    override fun getLayout() = null
    override fun setLayout(layout: Layout?) {}

    override fun clearFilters() {}
    override fun addFilter(filter: Filter?) {}
    override fun getFilter() = null

    override fun close() {}

    override fun doAppend(event: LoggingEvent) {
        val level = when(event.getLevel().toInt()) {
            in Priority.ERROR_INT .. Priority.OFF_INT  -> Level.SEVERE
            in Priority.WARN_INT .. Priority.ERROR_INT -> Level.WARNING
            in Priority.INFO_INT .. Priority.WARN_INT  -> Level.INFO
            else -> Level.FINE
        }
        Constants.log.log(level, "[${event.locationInformation.className}:${event.locationInformation.lineNumber}] ${event.renderedMessage}")
    }

}