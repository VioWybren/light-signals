package nodomain.greensnowapps.lightsignals.model

import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneSyncStateRepository @Inject constructor(
    private val dataClient: DataClient
) {
    private val listenerLock = Any()
    private var sessionToken: Any? = null
    private var listener: DataClient.OnDataChangedListener? = null
    private val _stateFlow = MutableStateFlow<PhoneControlState?>(null)
    val stateFlow: StateFlow<PhoneControlState?> = _stateFlow.asStateFlow()

    fun initFromViewModel() = synchronized(listenerLock) {
        if (listener != null) return@synchronized
        val token = Any()
        val sessionListener = DataClient.OnDataChangedListener { events ->
            consumeEvents(events, token)
        }
        sessionToken = token
        listener = sessionListener
        dataClient.addListener(sessionListener)
            .addOnSuccessListener {
                synchronized(listenerLock) {
                    // Late completion may remove only its own session's listener.
                    if (sessionToken !== token) dataClient.removeListener(sessionListener)
                }
            }
            .addOnFailureListener { error ->
                synchronized(listenerLock) {
                    if (sessionToken === token) {
                        sessionToken = null
                        listener = null
                    }
                }
                Log.w("PhoneSyncState", "Unable to register Data Layer listener", error)
            }
        Unit
    }

    fun clear() = synchronized(listenerLock) {
        val previousListener = listener
        listener = null
        sessionToken = null
        _stateFlow.value = null
        if (previousListener != null) {
            dataClient.removeListener(previousListener)
                .addOnFailureListener { error ->
                    Log.w("PhoneSyncState", "Unable to remove Data Layer listener", error)
                }
        }
        Unit
    }

    private fun consumeEvents(events: DataEventBuffer, token: Any) {
        synchronized(listenerLock) {
            if (sessionToken !== token) return
            for (event in events) {
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == CONTROL_STATE_PATH) {
                    try {
                        // Decode while the callback still owns its DataEventBuffer.
                        _stateFlow.value = decodeControlState(DataMapItem.fromDataItem(event.dataItem).dataMap)
                    } catch (error: RuntimeException) {
                        Log.w("PhoneSyncState", "Ignoring malformed control state", error)
                    }
                }
            }
        }
    }

}
