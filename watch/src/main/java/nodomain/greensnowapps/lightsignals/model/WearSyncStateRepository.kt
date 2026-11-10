package nodomain.greensnowapps.lightsignals.model

import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import nodomain.greensnowapps.lightsignals.core.sync.ControlStateWire
import nodomain.greensnowapps.lightsignals.core.sync.WatchControlCommand
import javax.inject.Inject

class WearSyncStateRepository internal constructor(
    private val publish: suspend (String, DataMap) -> Unit,
    private val nowMillis: () -> Long,
    private val onFailure: (Exception) -> Unit,
    private val messageClient: MessageClient? = null
) {
    @Inject constructor(dataClient: DataClient, messageClient: MessageClient) : this(
        publish = { path, values ->
            val request = PutDataMapRequest.create(path).run {
                dataMap.putAll(values)
                asPutDataRequest().setUrgent()
            }
            dataClient.putDataItem(request).await()
        },
        nowMillis = System::currentTimeMillis,
        onFailure = { Log.w("WearSyncState", "Unable to synchronize controls", it) },
        messageClient = messageClient
    )

    suspend fun sendState(update: WearControlState, manualLinkStartedAt: Long? = null): Boolean {
        return try {
            // Refresh metadata when reconnecting with unchanged controls.
            val snapshot = update.toDataMap(nowMillis())
            if (manualLinkStartedAt != null) {
                snapshot.putLong(ControlStateWire.MANUAL_LINK_STARTED_AT, manualLinkStartedAt)
            }
            publish(CONTROL_STATE_PATH, snapshot)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onFailure(error)
            false
        }
    }

    internal suspend fun sendCommand(nodeId: String, command: WatchControlCommand): Boolean {
        val client = checkNotNull(messageClient)
        return try {
            val reply = withTimeoutOrNull(2_500) {
                client.sendRequest(nodeId, WatchControlCommand.PATH, command.encode()).await()
            }
            reply?.contentEquals(byteArrayOf(WatchControlCommand.ACCEPTED)) == true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onFailure(error)
            false
        }
    }
}
