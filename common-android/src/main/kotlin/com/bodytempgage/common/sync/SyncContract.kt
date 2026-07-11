package com.bodytempgage.common.sync

/**
 * Wire contract for phone ↔ watch settings sync over the Wearable Data Layer. The field keys
 * deliberately match the on-disk DataStore preference names so the mapping is 1:1.
 */
object SyncContract {

    /** Data Layer item path holding the shared settings snapshot. */
    const val PATH = "/settings"

    const val KEY_SELECTED_MAC = "selected_mac"
    const val KEY_SELECTED_NAME = "selected_name"
    const val KEY_DISPLAY_MODE = "display_mode"
    const val KEY_USE_FAHRENHEIT = "use_fahrenheit"
    const val KEY_ALERT_ENABLED = "alert_enabled"

    // The high-alert key predates the other thresholds, hence the generic name (matches DataStore).
    const val KEY_ALERT_HIGH_C = "alert_threshold_c"
    const val KEY_WARN_HIGH_C = "warn_high_c"
    const val KEY_WARN_LOW_C = "warn_low_c"
    const val KEY_ALERT_LOW_C = "alert_low_c"

    const val KEY_UPDATED_AT = "updatedAt"
}
