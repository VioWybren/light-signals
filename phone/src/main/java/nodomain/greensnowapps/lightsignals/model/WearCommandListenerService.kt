package nodomain.greensnowapps.lightsignals.model

import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
//class WearCommandListenerService(override val appHelper: DataLayerAppHelper) : DataLayerAppHelperService() {
class WearCommandListenerService : WearableListenerService() {

    //    @Inject
//    lateinit var wearRepository: WearRepository
    @Inject
    lateinit var messageClient: MessageClient

    // Create a CoroutineScope for the service using Dispatchers.IO
//    private val serviceJob = SupervisorJob()

    //    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    // Create a CoroutineScope for the service using Dispatchers.IO
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d("MY_TAG", "WearCommandListenerService onCreate()")
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        when (messageEvent.path) {
            "/command/startTracking" -> {
                Log.d("MY_TAG", "PHONE. Received command: /command/startTracking")
                // Send an ACK to the phone
                sendAckMessage(messageEvent.sourceNodeId,
                    "/ack/start",
                    "Start command has received on the phone")
            }
            "/command/stopTracking" -> {
                Log.d("MY_TAG", "PHONE. Received command: /command/stopTracking")
                // Add logic for stopping.
                // Send an ACK to the phone
                sendAckMessage(messageEvent.sourceNodeId,
                    "/ack/stop",
                    "Stop command has received on the phone")
            }
        }
    }

    private fun sendAckMessage(nodeId: String, ackPath: String, ackPayload: String) {
        // Send an ACK message to the phone
        val ackPayloadByteArray = ackPayload.toByteArray(Charsets.UTF_8)

        // Launch the message sending operation
        // You can use a coroutine here for async operations
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Send the message using MessageClient API
//                    Wearable.getMessageClient(this)
                messageClient.sendMessage(nodeId, ackPath, ackPayloadByteArray)
                    .await() // Wait for the message to be sent
                Log.d("MY_TAG", "ACK message sent to watch")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("MY_TAG", "Failed to send ACK message.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MY_TAG", "WearCommandListenerService onDestroy()")
        // Cancel the scope to avoid leaks.
//        serviceJob.cancel()
        serviceScope.cancel()
    }
}