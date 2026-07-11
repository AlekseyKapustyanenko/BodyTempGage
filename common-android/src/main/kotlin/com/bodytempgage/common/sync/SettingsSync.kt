package com.bodytempgage.common.sync

import android.content.Context
import com.bodytempgage.common.data.SettingsRepository
import com.bodytempgage.core.SettingsSnapshot
import com.bodytempgage.core.SettingsSyncPolicy
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the local [SettingsRepository] and the paired device's settings in sync over the
 * Wearable Data Layer. Both the phone and the watch run one instance:
 *
 *  - **outbound:** collects local settings changes and publishes them to the shared `/settings`
 *    Data Layer item;
 *  - **inbound:** [applyIncoming] (called from [SettingsSyncService]) writes a newer remote
 *    snapshot into local settings.
 *
 * A single [lastSynced] snapshot is the loop guard shared by both directions: it records the
 * content most recently pushed or applied, so neither an echoed publish nor a re-applied item
 * bounces back (see [SettingsSyncPolicy]).
 */
class SettingsSync(
    context: Context,
    private val settings: SettingsRepository,
) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var lastSynced: SettingsSnapshot? = null

    fun start() {
        scope.launch {
            settings.flow.collect { current ->
                val snapshot = current.toSnapshot(System.currentTimeMillis())
                if (SettingsSyncPolicy.shouldPublish(snapshot, lastSynced)) {
                    publish(snapshot)
                }
            }
        }
    }

    private fun publish(snapshot: SettingsSnapshot) {
        val request = PutDataMapRequest.create(SyncContract.PATH).apply {
            dataMap.putAll(snapshot.toDataMap())
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request)
        lastSynced = snapshot
    }

    /** Applies a settings item received from the paired device, honouring last-write-wins. */
    suspend fun applyIncoming(dataMap: DataMap) {
        val currentSettings = settings.flow.first()
        // Baseline timestamp: the last value we synced; incoming must be at least as recent.
        val baseline = lastSynced?.updatedAt ?: 0L
        val current = currentSettings.toSnapshot(baseline)
        val incoming = dataMap.toSettingsSnapshot(current)
        if (!SettingsSyncPolicy.shouldApply(incoming, current)) return
        settings.applySyncedSnapshot(incoming)
        lastSynced = incoming
    }
}
