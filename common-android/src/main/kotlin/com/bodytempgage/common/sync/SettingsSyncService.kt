package com.bodytempgage.common.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/**
 * Delivers settings updates from the paired device into local storage, even when the app's UI
 * and services are not running (Google Play services starts this component on a data change).
 *
 * Registered once in the `:common-android` manifest, so both `:app` and `:wear` inherit it.
 */
class SettingsSyncService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        val sync = (application as? SettingsSyncProvider)?.settingsSync ?: return
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != SyncContract.PATH) continue
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            // onDataChanged runs on a background binder thread; bridging the suspend apply is fine.
            runBlocking { sync.applyIncoming(dataMap) }
        }
    }
}
