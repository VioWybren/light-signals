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
import kotlin.time.Duration.Companion.milliseconds

/**
 * Accepts verified phone-led handoffs while a watch foreground owner is active.
 *
 * Call start and stop on Main. Each listener owns a Main coroutine scope; stopping the final
 * owner cancels verification and unregisters that session's RPC service.
 *
 * @param context Application context used to access platform services without retaining an
 *     Activity.
 * @property messageClient Wearable client used for request/reply messaging.
 * @property controls Repository that owns the device's signal and companion control state.
 */
@Singleton
@OptIn(ExperimentalHorologistApi::class)
class WearPhoneConnectionRequests @Inject constructor(
    @ApplicationContext context: Context,
    private val messageClient: MessageClient,
    private val controls: WearControlRepository
) {
    /**
     * Application-context Wearable client used to reject a handoff falsely naming the local
     * watch as sender.
     */
    private val nodeClient = Wearable.getNodeClient(context)
    /**
     * Application-context Wearable client checking the requesting node's advertised phone
     * capability.
     */
    private val capabilityClient = Wearable.getCapabilityClient(context)
    /**
     * Main-confined owner tokens keeping the RPC listener alive until the final release.
     */
    private val foregroundOwners = mutableSetOf<Any>()
    /**
     * Recent accepted invitations keyed by node and relationship UUID; duplicates must still
     * match a remembered link.
     */
    private val accepted = mutableMapOf<Pair<String, UUID>, WatchConnectionRequest>()
    /**
     * Insertion-ordered node/UUID revocations with expiry times, preventing delayed invitations
     * from restoring revoked links.
     */
    private val revoked = linkedMapOf<Pair<String, UUID>, Long>()
    /**
     * Current foreground listener and Main verification scope, or null after its final owner
     * stops.
     */
    private var activeSession: ForegroundSession? = null

    /**
     * Owns the Main coroutine scope and RPC listener for one watch foreground session.
     */
    private inner class ForegroundSession {
        /**
         * Main-thread coroutine scope owned by this foreground RPC session.
         */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        /**
         * RPC listener bound to this foreground session.
         */
        val service = MessageClient.RpcService { nodeId, path, data ->
            handleRequest(this, nodeId, path, data)
        }
    }

    /**
     * Acquires foreground ownership and requests the phone-request listener for the first
     * owner.
     *
     * Call on Main with a token paired with [stop]. Reuses an active foreground session
     * and returns before registration completes. A registration that succeeds after its
     * session has stopped removes its obsolete listener. Registration failures are logged;
     * the active session remains, so repeated start calls do not automatically retry.
     *
     * @param owner Stable foreground-owner token to release through stop.
     */
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

    /**
     * Releases ownership and cancels request handling when the last foreground owner
     * leaves.
     *
     * Call on Main. Invalidates the active session before cancelling its Main coroutine
     * scope and requesting listener removal. Ignores unknown owners and keeps listener
     * work active while another owner remains. Keeps accepted and revoked invitation
     * caches; control-repository lifecycle ownership remains with the caller.
     *
     * @param owner Previously acquired foreground-owner token.
     */
    @MainThread
    fun stop(owner: Any) {
        if (!foregroundOwners.remove(owner) || foregroundOwners.isNotEmpty()) return
        val session = activeSession ?: return
        activeSession = null
        session.scope.cancel()
        unregister(session)
    }

    /**
     * Requests removal of the supplied foreground session's RPC listener on Main.
     *
     * Returns without waiting for removal. Logs both synchronous exceptions and
     * asynchronous task failures. The caller handles scope cancellation and active-session
     * state; this helper also removes obsolete listeners whose registration completed
     * late.
     *
     * @param session Foreground session whose exact listener instance should be removed.
     */
    private fun unregister(session: ForegroundSession) {
        try {
            messageClient.removeRpcService(session.service)
                .addOnFailureListener { Log.w(TAG, "Could not remove watch connection endpoint", it) }
        } catch (error: Exception) {
            Log.w(TAG, "Could not remove watch connection endpoint", error)
        }
    }

    /**
     * Routes phone handoffs and availability, pause or revocation notices to watch
     * controls.
     *
     * Call on Main. Rejects obsolete foreground sessions, blank senders, unknown paths and
     * malformed data. Availability uses the stable relationship ID; pause uses the
     * command-session ID. A well-formed revocation is cached and acknowledged even when
     * the repository no longer has that relationship.
     *
     * Fresh handoff invitations run verification in the session's Main scope under
     * READY_TIMEOUT_MS. Timeouts, verification exceptions and cancelled jobs complete the
     * response with RETRY. Other notice paths return completed tasks immediately.
     *
     * @param session Foreground registration that received this RPC; obsolete instances
     *     are rejected.
     * @param nodeId Wearable sender node ID supplied by MessageClient.
     * @param path Requested watch-side handoff or relationship-management endpoint.
     * @param data Encoded handoff invitation, or a UUID for availability, pause or
     *     revocation.
     * @return A task yielding encoded ACCEPTED, RETRY or REJECTED status for the request.
     */
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
                withTimeoutOrNull(READY_TIMEOUT_MS.milliseconds) { acceptRequest(session, nodeId, request) }
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

    /**
     * Verifies a fresh phone handoff and imports its settings when watch controls are
     * ready.
     *
     * Suspends in the caller's Main coroutine while querying node identity, checking the
     * advertised phone capability and awaiting control initialization. [handleRequest]
     * owns the overall timeout; cancellation and query exceptions propagate to that
     * handler.
     *
     * Rejects revoked or expired invitations and a sender matching the local watch node.
     * Cached accepted invitations use [existingLinkReply] without reimporting settings.
     * Rechecks session identity, freshness, revocation and duplicate acceptance after
     * suspension. Prunes expired acceptance records and rejects new invitations while the
     * acceptance cache is full. Caches a new invitation only after connectFromPhone
     * succeeds.
     *
     * @param session Main foreground session that owns verification and must remain
     *     active.
     * @param nodeId Requesting phone node ID to verify against its advertised capability.
     * @param request Decoded invitation containing the stable relationship ID and phone
     *     settings.
     * @return ACCEPTED for an accepted relationship, RETRY for transient session,
     *     capability or control readiness conditions, or REJECTED for invalid, revoked or
     *     unacceptable invitations.
     */
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

    /**
     * Checks whether an already accepted invitation still names a remembered phone
     * relationship.
     *
     * Call on Main after the request freshness and revocation checks. Compares the stable
     * relationship ID without reapplying settings or requiring the connection indicator to
     * be active.
     *
     * @param nodeId Phone node ID associated with the cached accepted invitation.
     * @param requestId Original invitation ID, retained as the stable relationship ID.
     * @return ACCEPTED when the remembered relationship still matches, including while
     *     paused; REJECTED otherwise.
     */
    private fun existingLinkReply(nodeId: String, requestId: UUID) =
        if (controls.hasPhoneLink(nodeId, requestId)) ACCEPTED else REJECTED

    /**
     * Defines the log tag, verification deadline and recent-invitation cache limit.
     */
    private companion object {
        /**
         * Log tag for this component.
         */
        const val TAG = "WearPhoneConnection"
        /**
         * Maximum wait for accepting a phone-led watch handoff, in milliseconds.
         */
        const val READY_TIMEOUT_MS = 1_500L
        /**
         * Bound on cached accepted or revoked handoff invitations.
         */
        const val MAX_RECENT_REQUESTS = 32
    }
}
