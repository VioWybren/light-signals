package nodomain.greensnowapps.lightsignals.model

import android.util.Log
import androidx.core.net.toUri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val TAG = "WearSyncStateRepository"

private const val DATA_PATH = "/control_state"
private const val KEY_BLINK = "blinkEnabled"
private const val KEY_SOS = "sosEnabled"
private const val KEY_FREQ = "frequency"
private const val KEY_COLOR = "color"
private const val KEY_SCREEN = "screenIndex"
private const val KEY_TORCH = "torchOn"
private const val KEY_SOUND = "activeSound"
private const val KEY_COLOR_BLINK = "key_color_blink_on"
private const val KEY_ACTIVE_COLOR_BUTTON = "key_active_color_button"

class WearSyncStateRepository @Inject constructor(
    private val dataClient: DataClient
) : DataClient.OnDataChangedListener {

    // Exposed as StateFlow or LiveData for UI
    private val _stateFlow = MutableStateFlow(WearControlState())
    val stateFlow: StateFlow<WearControlState> = _stateFlow

    init {
        dataClient.addListener(this)
        fetchCurrentDataItem()
    }

    fun clear() {
        dataClient.removeListener(this)
    }

    private fun fetchCurrentDataItem() {
        // Query existing DataItem
        val uri = "wear://*/$DATA_PATH".toUri()
        dataClient.getDataItems(uri).addOnSuccessListener { buffer ->
            buffer.use {
                for (item in it) {
                    applyDataMap(DataMapItem.fromDataItem(item).dataMap)
                    break
                }
            }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                if (item.uri.path == DATA_PATH) {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    applyDataMap(dataMap)
                }
            }
        }
    }

    private fun applyDataMap(dataMap: DataMap) {
        val newState = WearControlState(
            blinkEnabled = dataMap.getBoolean(KEY_BLINK, false),
            sosEnabled = dataMap.getBoolean(KEY_SOS, false),
            frequency = dataMap.getLong(KEY_FREQ, 500L),
            color = dataMap.getFloat(KEY_COLOR, 0xFFFFFFFF.toFloat()),
            torchOn = dataMap.getBoolean(KEY_TORCH, false),
            activeSound = dataMap.getInt(KEY_SOUND, 0),
            colorBlinkEnabled = dataMap.getBoolean(KEY_COLOR_BLINK, false),
            screenIndex = dataMap.getInt(KEY_SCREEN, 0),
            activeColorButton = dataMap.getInt(KEY_ACTIVE_COLOR_BUTTON, 0)
        )
        _stateFlow.value = newState
    }

    // Call this when any field changes; you can debounce externally if needed.
    suspend fun sendState(update: WearControlState) {
        val putReq = PutDataMapRequest.create(DATA_PATH).run {
            dataMap.apply {
                putBoolean(KEY_BLINK, update.blinkEnabled)
                putBoolean(KEY_SOS, update.sosEnabled)
                putLong(KEY_FREQ, update.frequency)
                putFloat(KEY_COLOR, update.color)
                putBoolean(KEY_TORCH, update.torchOn)
                putInt(KEY_SOUND, update.activeSound)
                putBoolean(KEY_COLOR_BLINK, update.colorBlinkEnabled)
                putInt(KEY_SCREEN, update.screenIndex)
                putInt(KEY_ACTIVE_COLOR_BUTTON, update.activeColorButton)
            }
            asPutDataRequest().setUrgent()
        }
        try {
            dataClient.putDataItem(putReq).await() // requires kotlinx-coroutines-play-services
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send state", e)
        }
    }
}