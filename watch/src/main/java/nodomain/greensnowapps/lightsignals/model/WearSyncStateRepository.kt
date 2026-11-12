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
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply
import nodomain.greensnowapps.lightsignals.core.sync.WatchControlCommand
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeReply
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeRequest
import javax.inject.Inject

class WearSyncStateRepository internal constructor(
    private val publish: suspend (String, DataMap) -> Unit,
    private val nowMillis: () -> Long,
    private val onFailure: (Exception) -> Unit,
    private val sendRequest: suspend (String, String, ByteArray) -> ByteArray = { _, _, _ ->
        error("Message transport is unavailable")
    }
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
        sendRequest = { nodeId, path, payload -> messageClient.sendRequest(nodeId, path, payload).await() }
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

    internal suspend fun sendCommand(nodeId: String, command: WatchControlCommand): Boolean =
        request(nodeId, WatchControlCommand.PATH, command.encode())
            ?.contentEquals(byteArrayOf(WatchControlCommand.ACCEPTED)) == true

    internal suspend fun requestLink(nodeId: String, path: String, payload: ByteArray): WatchConnectionReply? {
        val reply = request(nodeId, path, payload)
        // Older v3 phones return an empty result for the added session-management paths.
        return if (reply?.isEmpty() == true) null else WatchConnectionReply.decode(reply)
    }

    private suspend fun request(nodeId: String, path: String, payload: ByteArray): ByteArray? {
        return try {
            withTimeoutOrNull(2_500) { sendRequest(nodeId, path, payload) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onFailure(error)
            null
        }
    }

    internal suspend fun resumeLink(nodeId: String, invitation: WatchResumeRequest): WatchResumeReply? {
        val payload = invitation.encode()
        val reply = request(nodeId, WatchConnectionRequest.RESUME_STATE_PATH, payload)
        if (reply?.isEmpty() == true) {
            // Older phones support the original connection-only resume response.
            return requestLink(nodeId, WatchConnectionRequest.RESUME_PATH, payload)?.let { WatchResumeReply(it) }
        }
        return WatchResumeReply.decode(reply, invitation.commandId)
    }
}
