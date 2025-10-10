package nodomain.greensnowapps.lightsignals.model

import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


private const val DATA_PATH = "/control_state"
private const val KEY_BLINK = "blinkEnabled"
private const val KEY_SOS = "sosEnabled"
private const val KEY_FREQ = "frequency"
private const val KEY_COLOR = "color"
private const val KEY_SCREEN = "screenIndex"
private const val KEY_TORCH = "torchOn"
private const val KEY_SOUND = "activeSound"
private const val KEY_ACTIVE_COLOR_BUTTON = "key_active_color_button"

@Singleton
class PhoneSyncStateRepository @Inject constructor(
    private val dataClient: DataClient,
) : DataClient.OnDataChangedListener {


    private lateinit var serviceScope: CoroutineScope
    private val _stateFlow = MutableStateFlow<PhoneControlState?>(null)
    val stateFlow: StateFlow<PhoneControlState?> = _stateFlow


 fun initFromViewModel() {
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataClient.addListener(this)
    }

    fun clear() {
        dataClient.removeListener(this)
        serviceScope.cancel()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                if (item.uri.path == DATA_PATH) {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    serviceScope.launch {
                        applyDataMap(dataMap)
                    }
                }
            }
        }
    }

    private fun applyDataMap(dataMap: DataMap) {
        val newState = PhoneControlState(
            blinkEnabled = dataMap.getBoolean(KEY_BLINK, false),
            sosEnabled = dataMap.getBoolean(KEY_SOS, false),
            frequency = dataMap.getLong(KEY_FREQ, 500L),
            color = dataMap.getFloat(KEY_COLOR, 0xFFFFFFFF.toFloat()),
            torchOn = dataMap.getBoolean(KEY_TORCH, false),
            activeSound = dataMap.getInt(KEY_SOUND, 0),
            screenIndex = dataMap.getInt(KEY_SCREEN, 0),
            activeColorButton = dataMap.getInt(KEY_ACTIVE_COLOR_BUTTON, 0)
        )
        _stateFlow.value = newState
    }

}