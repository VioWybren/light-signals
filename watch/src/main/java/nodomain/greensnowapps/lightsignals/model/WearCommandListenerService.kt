package nodomain.greensnowapps.lightsignals.model

import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val TAG = "WearCommandListenerService"

@AndroidEntryPoint
class WearCommandListenerService : WearableListenerService() {

//    @Inject
//    lateinit var wearControlRepository: WearControlRepository
    @Inject
    lateinit var wearPhoneOperations: WearPhoneOperations
    @Inject
    lateinit var messageClient: MessageClient

    // Create a CoroutineScope for the service using Dispatchers.IO
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG,"onCreate()")
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        when (messageEvent.path) {
            "/command/startTracking" -> {
                Log.d(TAG,"WEAR. Received command: /command/startTracking")
                // Send an ACK to the phone
                sendAckMessage(messageEvent.sourceNodeId,
                               "/ack/start",
                               "Tracking data on the watch has started")
            }
            "/command/stopTracking" -> {
                Log.d(TAG,"WEAR. Received command: /command/stopTracking")
                // Send an ACK to the phone
                sendAckMessage(messageEvent.sourceNodeId,
                               "/ack/stop",
                               "Tracking data on the watch has been stopped")
            }
        }

        var ackMessage = ""
        when (messageEvent.path) {
            "/ack/start" -> {
                ackMessage = String(messageEvent.data, Charsets.UTF_8)
                Log.d(TAG,"WEAR Received ACK for start: $ackMessage")
            }
            "/ack/stop" -> {
                ackMessage = String(messageEvent.data, Charsets.UTF_8)
                Log.d(TAG,"WEAR Received ACK for stop: $ackMessage")
            }
        }
        // Handle the acknowledgment (e.g., update UI, trigger next action)
        serviceScope.launch {
            wearPhoneOperations.emitMessage(ackMessage)
        }

    }

    private fun sendAckMessage(nodeId: String, ackPath: String, ackPayload: String) {
        // Send an ACK message to the phone
        val ackPayloadByteArray = ackPayload.toByteArray(Charsets.UTF_8)

        // Launch the message sending operation
        // You can use a coroutine here for async operations
            serviceScope.launch (Dispatchers.IO) {
                try {
                    // Send the message using MessageClient API
                    messageClient.sendMessage(nodeId, ackPath, ackPayloadByteArray)
                        .await() // Wait for the message to be sent
                    Log.d(TAG,"ACK message sent to phone.")
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.d(TAG,"Failed to send ACK message.")
                }
        }
    }
    // Phone (or other node) just became reachable
    override fun onPeerConnected(node: Node) {
//        super.onPeerConnected(node)
//        broadcastConnectionChange(connected = true)
        Log.d(TAG,"onPeerConnected(node: $node)")
    }

    // Phone (or other node) just went out of range
    override fun onPeerDisconnected(node: Node) {
//        super.onPeerDisconnected(node)
//        broadcastConnectionChange(connected = false)
        Log.d(TAG,"onPeerDisconnected(node: $node)")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG,"onDestroy()")
        // Cancel the scope to avoid leaks.
        serviceScope.cancel()
    }
}