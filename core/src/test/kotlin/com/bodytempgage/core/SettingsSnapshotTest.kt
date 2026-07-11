package com.bodytempgage.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsSnapshotTest {

    private fun snapshot(
        mac: String? = "AA:BB:CC:DD:EE:FF",
        name: String? = "MMC-T201",
        displayMode: DisplayMode = DisplayMode.BODY,
        useFahrenheit: Boolean = false,
        alertEnabled: Boolean = true,
        alertHighC: Double = 37.5,
        warnHighC: Double = 37.0,
        warnLowC: Double = 35.5,
        alertLowC: Double = 35.0,
        updatedAt: Long = 1_000L,
    ) = SettingsSnapshot(
        selectedMac = mac,
        selectedName = name,
        displayMode = displayMode,
        useFahrenheit = useFahrenheit,
        alertEnabled = alertEnabled,
        alertHighC = alertHighC,
        warnHighC = warnHighC,
        warnLowC = warnLowC,
        alertLowC = alertLowC,
        updatedAt = updatedAt,
    )

    @Test
    fun sameContent_ignoresTimestamp() {
        assertTrue(snapshot(updatedAt = 1).sameContentAs(snapshot(updatedAt = 999)))
    }

    @Test
    fun sameContent_falseWhenAnyFieldDiffers() {
        assertFalse(snapshot().sameContentAs(snapshot(alertHighC = 38.0)))
        assertFalse(snapshot().sameContentAs(snapshot(mac = "11:22:33:44:55:66")))
        assertFalse(snapshot().sameContentAs(snapshot(displayMode = DisplayMode.GAUGE)))
        assertFalse(snapshot().sameContentAs(snapshot(useFahrenheit = true)))
    }

    @Test
    fun shouldPublish_whenNoPriorSyncOrContentChanged() {
        assertTrue(SettingsSyncPolicy.shouldPublish(snapshot(), lastSynced = null))
        assertTrue(
            SettingsSyncPolicy.shouldPublish(
                local = snapshot(alertHighC = 38.0, updatedAt = 2),
                lastSynced = snapshot(updatedAt = 1),
            ),
        )
    }

    @Test
    fun shouldPublish_falseWhenContentUnchanged_evenIfTimestampMoved() {
        // Echo suppression: re-emitting identical content must not re-publish.
        assertFalse(
            SettingsSyncPolicy.shouldPublish(
                local = snapshot(updatedAt = 5),
                lastSynced = snapshot(updatedAt = 1),
            ),
        )
    }

    @Test
    fun shouldApply_lastWriteWins() {
        val current = snapshot(alertHighC = 37.5, updatedAt = 10)
        assertTrue(
            SettingsSyncPolicy.shouldApply(snapshot(alertHighC = 39.0, updatedAt = 11), current),
        )
        assertFalse(
            SettingsSyncPolicy.shouldApply(snapshot(alertHighC = 39.0, updatedAt = 9), current),
        )
    }

    @Test
    fun shouldApply_falseWhenIncomingSameContent() {
        // Echo suppression on receive: identical content is never re-applied.
        val current = snapshot(updatedAt = 10)
        assertFalse(SettingsSyncPolicy.shouldApply(snapshot(updatedAt = 20), current))
    }

    @Test
    fun shouldApply_tieBreakFavoursIncoming() {
        val current = snapshot(alertHighC = 37.5, updatedAt = 10)
        assertTrue(
            SettingsSyncPolicy.shouldApply(snapshot(alertHighC = 39.0, updatedAt = 10), current),
        )
    }
}
