/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android.validation

import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.transform.rfc5545.Rfc5545ComponentRule

class RruleUntilAfterStartRule: Rfc5545ComponentRule<VEvent> {

    companion object {

        fun hasUntilBeforeDtStart(dtStart: DtStart, rRule: RRule): Boolean {
            val until = rRule.recur.until ?: return false
            return until < dtStart.date
        }

        fun removeRRulesWithUntilBeforeDtStart(dtStart: DtStart, rRules: MutableList<RRule>) {
            val iter = rRules.iterator()
            while (iter.hasNext()) {
                val rRule = iter.next()

                // drop invalid RRULEs
                if (hasUntilBeforeDtStart(dtStart, rRule))
                    iter.remove()
            }
        }

    }


    override fun applyTo(event: VEvent) {
        val dtStart = event.startDate ?: return
        val rRules: MutableList<RRule> = event.getProperties<RRule>(Property.RRULE)
        removeRRulesWithUntilBeforeDtStart(dtStart, rRules)
    }

    override fun getSupportedType() = VEvent::class.java

}