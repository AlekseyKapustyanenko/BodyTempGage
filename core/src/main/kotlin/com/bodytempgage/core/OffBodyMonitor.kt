package com.bodytempgage.core

/**
 * Raises a single "gauge came off the body" event per wear session.
 *
 * A reading with a body estimate marks the gauge as worn. Once worn, an unbroken run of
 * estimate-less readings lasting [confirmMillis] fires the event once; producing a body
 * estimate again re-arms it. The confirmation window keeps short dips around the
 * predictor's engage gate from notifying.
 */
class OffBodyMonitor(private val confirmMillis: Long = DEFAULT_CONFIRM_MILLIS) {

    private var worn = false
    private var offSinceMillis: Long? = null

    /** Feed one reading; true when the off-body notification should fire now. */
    fun check(hasBodyEstimate: Boolean, nowMillis: Long): Boolean {
        if (hasBodyEstimate) {
            worn = true
            offSinceMillis = null
            return false
        }
        if (!worn) return false
        val since = offSinceMillis ?: nowMillis.also { offSinceMillis = it }
        if (nowMillis - since < confirmMillis) return false
        worn = false
        offSinceMillis = null
        return true
    }

    companion object {
        const val DEFAULT_CONFIRM_MILLIS = 60_000L
    }
}
