package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.apphelper.DataLayerAppHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.ACCEPTED
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.REJECTED
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.RETRY
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionRequest

@Singleton
@OptIn(ExperimentalHorologistApi::class)
class WearPhoneConnectionRequests @Inject constructor(
    @ApplicationContext context: Context,
    private val messageClient: MessageClient,
    private val controls: WearControlRepository
) {
    private val nodeClient = Wearable.getNodeClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val foregroundOwners = mutableSetOf<Any>()
    private val accepted = mutableMapOf<Pair<String, UUID>, WatchConnectionRequest>()
    private val revoked = linkedMapOf<Pair<String, UUID>, Long>()
    private var activeSession: ForegroundSession? = null

    private inner class ForegroundSession {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val service = MessageClient.RpcService { nodeId, path, data ->
            handleRequest(this, nodeId, path, data)
        }
    }

    @MainThread
    fun start(owner: Any) {
        if (!foregroundOwners.add(owner) || activeSession != null) return
        val session = ForegroundSession()
        activeSession = session
        try {
            messageClient.addRpcService(session.service, WatchConnectionRequest.WATCH_PREFIX)
                .addOnSuccessListener {
                    // Registration can finish after this foreground session has already stopped.
                    if (activeSession !== session) unregister(session)
                    else Log.d(TAG, "Connection endpoint registered")
                }
                .addOnFailureListener { Log.w(TAG, "Could not register watch connection endpoint", it) }
        } catch (error: Exception) {
            Log.w(TAG, "Could not register watch connection endpoint", error)
        }
    }

    @MainThread
    fun stop(owner: Any) {
        if (!foregroundOwners.remove(owner) || foregroundOwners.isNotEmpty()) return
        val session = activeSession ?: return
        activeSession = null
        session.scope.cancel()
        unregister(session)
    }

    private fun unregister(session: ForegroundSession) {
        try {
            messageClient.removeRpcService(session.service)
                .addOnFailureListener { Log.w(TAG, "Could not remove watch connection endpoint", it) }
        } catch (error: Exception) {
            Log.w(TAG, "Could not remove watch connection endpoint", error)
        }
    }

    private fun handleRequest(
        session: ForegroundSession,
        nodeId: String,
        path: String,
        data: ByteArray
    ): Task<ByteArray> {
        if (activeSession !== session || nodeId.isBlank()) {
            return Tasks.forResult(REJECTED.encode())
        }
        if (path == WatchConnectionRequest.AVAILABLE_PATH || path == WatchConnectionRequest.PAUSE_PATH) {
            val id = WatchConnectionRequest.decodeId(data) ?: return Tasks.forResult(REJECTED.encode())
            val accepted = if (path == WatchConnectionRequest.AVAILABLE_PATH) {
                controls.phoneAvailable(nodeId, id)
            } else {
                controls.phonePaused(nodeId, id)
            }
            return Tasks.forResult(if (accepted) ACCEPTED.encode() else REJECTED.encode())
        }
        if (path == WatchConnectionRequest.REVOKE_PATH) {
            val id = WatchConnectionRequest.decodeId(data) ?: return Tasks.forResult(REJECTED.encode())
            controls.revokePhoneLink(nodeId, id)
            val now = System.currentTimeMillis()
            revoked.entries.removeAll { it.value < now }
            if (revoked.size >= MAX_RECENT_REQUESTS) revoked.remove(revoked.keys.first())
            revoked[nodeId to id] = now + 2 * WatchConnectionRequest.LIFETIME_MS
            return Tasks.forResult(ACCEPTED.encode())
        }
        if (path != WatchConnectionRequest.PATH) return Tasks.forResult(REJECTED.encode())
        val request = WatchConnectionRequest.decode(data)
            ?: return Tasks.forResult(REJECTED.encode())
        if (!request.isFresh()) return Tasks.forResult(REJECTED.encode())

        val reply = TaskCompletionSource<ByteArray>()
        val job = session.scope.launch {
            val result = try {
                withTimeoutOrNull(READY_TIMEOUT_MS) { acceptRequest(session, nodeId, request) }
                    ?: RETRY
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Could not verify phone connection request", error)
                RETRY
            }
            reply.trySetResult(result.encode())
        }
        // A stopped session may cancel the coroutine before it reaches the reply.
        job.invokeOnCompletion { cause ->
            if (cause != null) reply.trySetResult(RETRY.encode())
        }
        return reply.task
    }

    private suspend fun acceptRequest(
        session: ForegroundSession,
        nodeId: String,
        request: WatchConnectionRequest
    ): WatchConnectionReply {
        if (activeSession !== session) return RETRY
        if (!request.isFresh()) return REJECTED
        val key = nodeId to request.id
        val now = System.currentTimeMillis()
        if ((revoked[key] ?: 0) >= now) return REJECTED
        accepted.entries.removeAll { !it.value.isFresh(now) }
        if (key in accepted) return existingLinkReply(nodeId, request.id)

        if (nodeClient.localNode.await().id == nodeId) return REJECTED
        val phones = capabilityClient.getCapability(
            DataLayerAppHelper.PHONE_CAPABILITY,
            CapabilityClient.FILTER_ALL
        ).await().nodes
        if (phones.none { it.id == nodeId }) {
            Log.d(TAG, "Waiting for requesting phone's capability")
            return RETRY
        }

        controls.isInitialized.first { it }
        if (activeSession !== session) return RETRY
        if (!request.isFresh()) return REJECTED
        if ((revoked[key] ?: 0) >= System.currentTimeMillis()) return REJECTED

        // Recheck after suspend points. A concurrent retry may have completed first.
        if (key in accepted) return existingLinkReply(nodeId, request.id)
        if (accepted.size >= MAX_RECENT_REQUESTS) return REJECTED
        if (!controls.connectFromPhone(nodeId, request)) return RETRY

        accepted[key] = request
        Log.d(TAG, "Phone connection accepted")
        return ACCEPTED
    }

    private fun existingLinkReply(nodeId: String, requestId: UUID) =
        if (controls.hasPhoneLink(nodeId, requestId)) ACCEPTED else REJECTED

    private companion object {
        const val TAG = "WearPhoneConnection"
        const val READY_TIMEOUT_MS = 1_500L
        const val MAX_RECENT_REQUESTS = 32
    }
}
