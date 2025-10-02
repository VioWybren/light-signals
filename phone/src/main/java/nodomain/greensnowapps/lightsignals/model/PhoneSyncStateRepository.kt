package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val TAG = ""

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
    @param: ApplicationContext private val context: Context,
    private val dataClient: DataClient,
    private val dataStore: DataStore<Preferences>,
) : DataClient.OnDataChangedListener {

    // Read the saved index *synchronously* so we can seed our StateFlow
    private val initialPhoneControlState: PhoneControlState = runBlocking {
        PhoneControlState.fromDataStore(dataStore)
    }
    //cancel it in onDestroy()
    private lateinit var serviceScope: CoroutineScope
    // Exposed as StateFlow
    private val _stateFlow = MutableStateFlow<PhoneControlState?>(null)
    val stateFlow: StateFlow<PhoneControlState?> = _stateFlow

    init {
        Log.d(TAG,"has started")
        Log.d(TAG,"initialPhoneControlState = $initialPhoneControlState")
    }

    suspend fun initFromViewModel() {
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataClient.addListener(this)
    }

    fun clear() {
        dataClient.removeListener(this)
        serviceScope.cancel()
    }

    private fun fetchCurrentDataItem() {
        // Query existing DataItem
        val uri = "wear://*/$DATA_PATH".toUri()
        dataClient.getDataItems(uri).addOnSuccessListener { buffer ->
            buffer.use {
                for (item in it) {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    serviceScope.launch {
                        applyDataMap(dataMap )
                    }
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
                    // launch suspend applyDataMap
                    serviceScope.launch {
                        applyDataMap(dataMap)
                    }
                }
            }
        }
    }

    private suspend fun applyDataMap(dataMap: DataMap) {
        val newState = PhoneControlState(
            blinkEnabled = dataMap.getBoolean(KEY_BLINK, false),
            sosEnabled = dataMap.getBoolean(KEY_SOS, false),
            frequency = dataMap.getLong(KEY_FREQ, 500L),
            color = dataMap.getFloat(KEY_COLOR, 0xFFFFFFFF.toFloat()),
            torchOn = dataMap.getBoolean(KEY_TORCH, false),
            activeSound = dataMap.getInt(KEY_SOUND, 0),
//            colorBlinkEnabled = dataMap.getBoolean(KEY_COLOR_BLINK, false),
            screenIndex = dataMap.getInt(KEY_SCREEN, 0),
            activeColorButton = dataMap.getInt(KEY_ACTIVE_COLOR_BUTTON, 0)
        )
        _stateFlow.value = newState
    }

    // Call this when any field changes; you can debounce externally if needed.
    suspend fun sendState(update: PhoneControlState) {
        val putReq = PutDataMapRequest.create(DATA_PATH).run {
            dataMap.apply {
                putBoolean(KEY_BLINK, update.blinkEnabled)
                putBoolean(KEY_SOS, update.sosEnabled)
                putLong(KEY_FREQ, update.frequency)
                putFloat(KEY_COLOR, update.color)
                putInt(KEY_SCREEN, update.screenIndex)
                putBoolean(KEY_TORCH, update.torchOn)
                putInt(KEY_SOUND, update.activeSound)
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