package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import nodomain.greensnowapps.lightsignals.core.sync.PhoneSignalState
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.ACCEPTED
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.REJECTED
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.RETRY
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchControl
import nodomain.greensnowapps.lightsignals.core.sync.WatchControlCommand
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeReply
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Receives legacy watch state and manages verified watch-control RPC sessions.
 *
 * Exposes legacy state through stateFlow and confirmed connection events through
 * watchConnectedEvents. Call lifecycle and session APIs on Main; callbacks serialize session
 * transitions under a lock. Readiness and verified peer identity authorize background control
 * independently of Activity visibility.
 *
 * @param context Application context used to access platform services without retaining an
 *     Activity.
 * @property dataClient Wearable client used for legacy DataItem transport.
 * @property messageClient Wearable client used for request/reply messaging.
 * @property dataStore Preferences store used to read and persist settings.
 */
@Singleton
class PhoneSyncStateRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val dataClient: DataClient,
    private val messageClient: MessageClient,
    private val dataStore: DataStore<Preferences>
) {
    /**
     * Groups an RPC listener, its identity token and the coroutine scope that owns it.
     *
     * @property token Registration identity used to reject callbacks from an old listener.
     * @property scope Coroutine scope that owns the operation.
     * @property service RPC listener registered for this session.
     * @property added Task that completes when the RPC listener is registered.
     */
    private class RpcRegistration(
        val token: Any,
        val scope: CoroutineScope,
        val service: MessageClient.RpcService,
        val added: Task<Void>
    ) {
        /**
         * Whether this RPC registration has scheduled its one availability announcement
         * attempt.
         */
        var availabilityAnnounced = false
    }

    /**
     * Connects verified RPC requests to the phone repository's readiness and signal
     * operations.
     *
     * @property isReady Reports whether phone control initialization has completed.
     * @property currentSignal Returns a fresh snapshot of the current phone settings.
     * @property applyHandoff Applies a complete peer snapshot and reports whether it was
     *     accepted.
     * @property applyControl Applies an authorized user command and reports whether it was
     *     accepted.
     */
    private class ControlCallbacks(
        val isReady: () -> Boolean,
        val currentSignal: () -> PhoneSignalState,
        val applyHandoff: (PhoneSignalState) -> Boolean,
        val applyControl: (WatchControlCommand) -> Boolean
    )

    /**
     * Application-context Wearable client used to verify watch handoff capability.
     */
    private val capabilityClient = Wearable.getCapabilityClient(context)
    /**
     * Monitor guarding listener identity, callbacks and watch-session transitions.
     */
    private val listenerLock = Any()
    /**
     * Identity of the current legacy Data Layer listener; obsolete callbacks are ignored.
     */
    private var sessionToken: Any? = null
    /**
     * Current legacy DataItem listener, or null while unregistered or after registration
     * failure.
     */
    private var listener: DataClient.OnDataChangedListener? = null
    /**
     * Main-thread bridge to ready phone controls, or null after transport cleanup.
     */
    private var callbacks: ControlCallbacks? = null
    /**
     * Current RPC listener, token, registration task and Main scope; registration may still be
     * pending.
     */
    private var rpcRegistration: RpcRegistration? = null
    /**
     * Ordered relationship updates consumed by the independent IO persistence coroutine; null
     * removes the saved link.
     */
    private val linkWrites = Channel<RememberedWatchLink?>(Channel.UNLIMITED)
    /**
     * Main-confined watch-session state machine, accessed under listenerLock.
     */
    private val sessions = WatchControlSessions(onRememberedChanged = { linkWrites.trySend(it) })
    /**
     * Whether initial remembered-watch restoration has completed, including restoration of no
     * link.
     */
    private var linkRestored = false
    /**
     * Nodes barred from legacy DataItem control after explicit disconnect or release.
     */
    private val blockedLegacyNodes = mutableSetOf<String>()
    /**
     * Mutable latest legacy snapshot; cleared on modern handoff preparation or transport
     * cleanup.
     */
    private val _stateFlow = MutableStateFlow<PhoneControlState?>(null)
    /**
     * Latest decoded legacy watch state, or null when no legacy update is active.
     */
    val stateFlow: StateFlow<PhoneControlState?> = _stateFlow.asStateFlow()
    /**
     * Conflated connection notifications consumed by phone feedback; repeated pending events
     * collapse to one.
     */
    private val watchConnectedChannel = Channel<Unit>(Channel.CONFLATED)
    /**
     * One-shot flow announcing a newly confirmed watch connection for phone feedback.
     */
    internal val watchConnectedEvents = watchConnectedChannel.receiveAsFlow()

    init {
        // Persist the relationship independently of an Activity's lifetime. Commands stay in memory.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val saved = RememberedWatchLink.read(dataStore.safePreferences().first())
            withContext(Dispatchers.Main.immediate) {
                synchronized(listenerLock) {
                    sessions.restore(saved)
                    linkRestored = true
                    announceAvailability()
                }
            }
            for (link in linkWrites) dataStore.editSafely { prefs -> RememberedWatchLink.write(prefs, link) }
        }
    }

    /**
     * Attaches phone-control callbacks and requests RPC and legacy Data Layer listeners.
     *
     * Call on Main. Replaces the callbacks but reuses existing registrations. Registration
     * completes asynchronously; an obsolete Data Layer listener removes itself if
     * registration finishes after cleanup. Saved-link restoration starts during repository
     * construction and continues independently of this call.
     *
     * @param isReady Main-thread callback reporting whether phone control restoration is
     *     complete.
     * @param currentSignal Main-thread callback returning the complete current phone
     *     settings.
     * @param applyHandoff Main-thread callback applying a watch snapshot and reporting
     *     acceptance.
     * @param applyControl Main-thread callback applying an authorized watch action and
     *     reporting acceptance.
     */
    @MainThread
    internal fun initFromViewModel(
        isReady: () -> Boolean,
        currentSignal: () -> PhoneSignalState,
        applyHandoff: (PhoneSignalState) -> Boolean,
        applyControl: (WatchControlCommand) -> Boolean
    ) = synchronized(listenerLock) {
        callbacks = ControlCallbacks(isReady, currentSignal, applyHandoff, applyControl)
        ensureRpcRegistration()
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
                Log.w(TAG, "Unable to register Data Layer listener", error)
            }
        Unit
    }

    /**
     * Reuses or recreates the RPC listener and announces phone availability when the host
     * resumes.
     *
     * Call on Main. Only requests registration while control callbacks are attached;
     * [announceAvailability] applies readiness and per-registration announcement limits.
     */
    @MainThread
    internal fun onHostResumed() = synchronized(listenerLock) {
        if (callbacks != null) ensureRpcRegistration()
        announceAvailability()
    }

    /**
     * Schedules one availability announcement to the remembered watch for this RPC
     * registration.
     *
     * Call on Main. Requires restored link state, ready controls and a remembered
     * relationship. The registration coroutine awaits listener registration, rechecks the
     * relationship and bounds the send attempt. Returns immediately without examining
     * acknowledgment bytes. Logs delivery failures; another call with the same
     * registration does not retry the attempt.
     */
    @MainThread
    internal fun announceAvailability() = synchronized(listenerLock) {
        val registration = rpcRegistration ?: return@synchronized
        val link = sessions.remembered ?: return@synchronized
        if (!linkRestored || callbacks?.isReady?.invoke() != true || registration.availabilityAnnounced) {
            return@synchronized
        }
        registration.availabilityAnnounced = true
        registration.scope.launch {
            try {
                registration.added.await()
                if (rpcRegistration !== registration || sessions.remembered?.matches(link.nodeId, link.linkId) != true) {
                    return@launch
                }
                withTimeoutOrNull(VERIFY_WATCH_TIMEOUT_MS.milliseconds) {
                    messageClient.sendRequest(
                        link.nodeId, WatchConnectionRequest.AVAILABLE_PATH, WatchConnectionRequest.encodeId(link.linkId)
                    ).await()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.d(TAG, "Watch availability announcement could not be delivered", error)
            }
        }
        Unit
    }

    /**
     * Prepares a phone-led handoff and suspends until its RPC listener is registered.
     *
     * Call on Main after verifying the watch capability. Requires restored link state and
     * ready controls. Creates a pending session that accepts early commands and clears the
     * legacy [stateFlow]. After registration, checks that both the listener and pending
     * session are still current.
     *
     * Returns false for setup or registration failure and propagates coroutine
     * cancellation. The caller owns [cancelWatchConnection] cleanup after a failed or
     * cancelled attempt.
     *
     * @param nodeId Wearable node ID of the watch selected for this handoff.
     * @param requestId Fresh invitation ID, which also identifies the new relationship.
     * @return True when registration succeeds and this pending handoff remains current;
     *     false otherwise.
     */
    @MainThread
    internal suspend fun beginWatchConnection(nodeId: String, requestId: UUID): Boolean {
        val (registration, connection) = synchronized(listenerLock) {
            if (!linkRestored || callbacks?.isReady?.invoke() != true) return false
            val registration = ensureRpcRegistration() ?: return false
            val connection = sessions.begin(nodeId, requestId, acceptsEarlyControl = true) ?: return false
            _stateFlow.value = null
            registration to connection
        }
        try {
            registration.added.await()
            return synchronized(listenerLock) {
                rpcRegistration === registration && sessions.pending === connection
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Unable to register watch controls", error)
            return false
        }
    }

    /**
     * Confirms a matching phone-led handoff or recognizes its already active relationship.
     *
     * Call on Main after the watch acknowledgment. A new confirmation requires the
     * matching node, invitation ID and pending session that permits early control. A newly
     * confirmed session queues a [watchConnectedEvents] notification.
     *
     * @param nodeId Wearable node ID of the watch acknowledging the handoff.
     * @param requestId Original handoff invitation ID, retained as the relationship ID.
     * @return True when the relationship is already active or its pending handoff is
     *     confirmed.
     */
    @MainThread
    internal fun confirmWatchConnection(nodeId: String, requestId: UUID): Boolean = synchronized(listenerLock) {
        val current = sessions.active
        if (current?.matchesLink(nodeId, requestId) == true) return@synchronized true
        val pending = sessions.pending
        pending?.matches(nodeId, requestId) == true && pending.acceptsEarlyControl && confirmConnection(pending)
    }

    /**
     * Checks whether the active watch relationship matches a handoff on Main.
     *
     * Compares the stable relationship ID, which remains unchanged when a resume replaces
     * the command-session ID.
     *
     * @param nodeId Wearable node ID of the watch to check.
     * @param requestId Original handoff ID identifying the relationship, not a resumed
     *     command ID.
     * @return True when the active relationship matches both the node and handoff ID.
     */
    @MainThread
    internal fun isWatchConnectionConfirmed(nodeId: String, requestId: UUID): Boolean = synchronized(listenerLock) {
        sessions.active?.matchesLink(nodeId, requestId) == true
    }

    /**
     * Retires a matching pending handoff and sends a best-effort revocation to its watch.
     *
     * Call on Main. A missing or different pending invitation is left unchanged. This
     * method does not disconnect an already confirmed active session.
     *
     * @param requestId Invitation ID of the pending handoff to cancel.
     */
    @MainThread
    internal fun cancelWatchConnection(requestId: UUID) {
        synchronized(listenerLock) { sessions.cancelPending(requestId) }?.let(::notifyWatchRevoked)
    }

    /**
     * Removes a node from the legacy DataItem block list after an explicit legacy launch.
     *
     * Call on Main. Other [consumeEvents] gates still apply, including listener identity,
     * completed link restoration and the absence of remembered or pending modern
     * relationships.
     *
     * @param nodeId Wearable node ID whose legacy control updates may be accepted again.
     */
    @MainThread
    internal fun allowLegacyWatch(nodeId: String) {
        synchronized(listenerLock) { blockedLegacyNodes.remove(nodeId) }
    }

    /**
     * Detaches control callbacks and listeners while retaining the remembered watch
     * relationship.
     *
     * Call on Main. Retires current command sessions, cancels the RPC scope, clears legacy
     * state and block entries, and discards a pending connection event. Requests listener
     * removal without waiting; an RPC listener whose registration is still pending is
     * removed when registration succeeds.
     *
     * Sends a pause notification for a retained relationship and revokes other previous
     * sessions. Independent relationship persistence continues. This transport cleanup
     * does not directly stop the phone signal or erase the saved relationship.
     */
    @MainThread
    fun clear() {
        val previousConnections = synchronized(listenerLock) {
            val connections = sessions.pause()
            callbacks = null
            blockedLegacyNodes.clear()
            watchConnectedChannel.tryReceive()
            val previousRpc = rpcRegistration
            rpcRegistration = null
            if (previousRpc != null) {
                previousRpc.scope.cancel()
                // A still-pending registration must remove its own listener once added.
                previousRpc.added.addOnSuccessListener {
                    messageClient.removeRpcService(previousRpc.service).addOnFailureListener { error ->
                        Log.w(TAG, "Unable to remove watch controls", error)
                    }
                }
            }
            val previousListener = listener
            listener = null
            sessionToken = null
            _stateFlow.value = null
            if (previousListener != null) {
                dataClient.removeListener(previousListener)
                    .addOnFailureListener { error -> Log.w(TAG, "Unable to remove Data Layer listener", error) }
            }
            connections
        }
        previousConnections.forEach { connection ->
            if (sessions.remembered?.matches(connection.nodeId, connection.linkId) == true) {
                notifyWatch(connection, WatchConnectionRequest.PAUSE_PATH, connection.requestId)
            } else {
                notifyWatchRevoked(connection)
            }
        }
    }

    /**
     * Promotes the exact pending session and queues feedback only for a new confirmation.
     *
     * Call on Main while holding listenerLock, after request authorization. An already
     * active session succeeds without duplicate feedback. A new confirmation retires the
     * previous authorization and queues remembered-link persistence through the session
     * owner.
     *
     * @param connection Pending or already active session object to confirm.
     * @return True when this exact session is active after confirmation; false for a
     *     superseded session.
     */
    private fun confirmConnection(connection: LinkedWatchControl): Boolean {
        val alreadyConfirmed = sessions.active === connection
        if (!sessions.confirm(connection)) return false
        if (!alreadyConfirmed) watchConnectedChannel.trySend(Unit)
        return true
    }

    /**
     * Reuses the current RPC registration or requests a new phone-endpoint listener.
     *
     * Call on Main while holding listenerLock. Creates a Main coroutine scope and identity
     * token for handlers. Returns before asynchronous registration completes; callers that
     * need a ready listener must await the registration's added task.
     *
     * A synchronous registration exception is logged and returns null. Asynchronous
     * failure clears this registration if it is still current and cancels its scope. The
     * snapshot control endpoint converts empty handler replies into explicit command
     * rejections.
     *
     * @return The existing or newly requested registration, or null on synchronous setup
     *     failure.
     */
    private fun ensureRpcRegistration(): RpcRegistration? {
        rpcRegistration?.let { return it }
        val token = Any()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        // MessageClient's default Wearable options deliver RPCs on the main thread.
        val service = MessageClient.RpcService { nodeId, path, payload ->
            when (path) {
                WatchControlCommand.PATH -> Tasks.forResult(handleWatchControl(token, nodeId, payload))
                WatchControlCommand.STATE_PATH -> {
                    val reply = handleWatchControl(token, nodeId, payload, requireSignal = true)
                    Tasks.forResult(if (reply.isEmpty()) byteArrayOf(WatchControlCommand.REJECTED) else reply)
                }
                WatchConnectionRequest.PHONE_PATH -> handleWatchHandoff(token, scope, nodeId, payload)
                WatchConnectionRequest.PHONE_STATE_PATH -> handleWatchStateHandoff(token, scope, nodeId, payload)
                WatchConnectionRequest.RESUME_PATH -> Tasks.forResult(handleWatchResume(token, nodeId, payload))
                WatchConnectionRequest.RESUME_STATE_PATH ->
                    Tasks.forResult(handleWatchResume(token, nodeId, payload, includeSignal = true))
                WatchConnectionRequest.STATUS_PATH -> Tasks.forResult(handleWatchStatus(token, nodeId, payload))
                WatchConnectionRequest.RELEASE_PATH -> Tasks.forResult(handleWatchRelease(token, nodeId, payload))
                else -> Tasks.forResult(byteArrayOf())
            }
        }
        return try {
            val added = messageClient.addRpcService(service, WatchConnectionRequest.PHONE_PREFIX)
            val registration = RpcRegistration(token, scope, service, added)
            rpcRegistration = registration
            added.addOnFailureListener { error ->
                synchronized(listenerLock) {
                    if (rpcRegistration === registration) rpcRegistration = null
                }
                scope.cancel()
                Log.w(TAG, "Unable to register watch controls", error)
            }
            registration
        } catch (error: Exception) {
            scope.cancel()
            Log.w(TAG, "Unable to register watch controls", error)
            null
        }
    }

    /**
     * Validates and applies an ordered command from the active or eligible pending watch
     * session.
     *
     * Runs on Main under listenerLock. Checks listener identity, restored link state,
     * payload freshness, peer/session IDs and an increasing sequence number. A zero-valued
     * CHECK confirms liveness without changing signal settings. Other signal actions use
     * the control callback; accepted checks and actions confirm the session and record the
     * sequence.
     *
     * A zero-valued DISCONNECT ends the matching session and blocks its legacy updates
     * without replacing the running signal. The snapshot RPC endpoint translates empty
     * results from this handler into explicit rejections.
     *
     * @param token Identity of the RPC registration that received the request.
     * @param nodeId Wearable sender node ID supplied by MessageClient.
     * @param payload Encoded watch command, optionally including a complete signal
     *     snapshot.
     * @param requireSignal Whether commands without a complete signal snapshot must be
     *     rejected.
     * @return The command ACCEPTED byte for an acknowledged operation, or empty bytes on
     *     rejection.
     */
    private fun handleWatchControl(token: Any, nodeId: String, payload: ByteArray, requireSignal: Boolean = false): ByteArray = synchronized(listenerLock) {
        if (rpcRegistration?.token !== token || !linkRestored) return@synchronized byteArrayOf()
        val command = WatchControlCommand.decode(payload) ?: return@synchronized byteArrayOf()
        if (requireSignal && command.signal == null) return@synchronized byteArrayOf()
        if (!command.isFresh()) return@synchronized byteArrayOf()
        val connection = sessions.active?.takeIf { it.matches(nodeId, command.id) } ?: sessions.pending?.takeIf {
            it.acceptsEarlyControl || command.control == WatchControl.DISCONNECT
        }
        if (connection?.allows(nodeId, command.id, command.sequence) != true) return@synchronized byteArrayOf()
        if (command.control == WatchControl.DISCONNECT) {
            if (command.value != 0) return@synchronized byteArrayOf()
            sessions.end(connection)
            blockedLegacyNodes.add(connection.nodeId)
        } else {
            val applied = if (command.control == WatchControl.CHECK) {
                command.value == 0
            } else {
                callbacks?.applyControl?.invoke(command) == true
            }
            if (!applied || !confirmConnection(connection)) return@synchronized byteArrayOf()
            connection.recordAccepted(command.sequence)
        }
        byteArrayOf(WatchControlCommand.ACCEPTED)
    }

    /**
     * Resumes a remembered watch relationship and optionally returns current phone
     * settings.
     *
     * Runs on Main under listenerLock. Requires the current listener, restored link state
     * and ready controls. The session owner validates the relationship, fresh invitation,
     * command ID and resume timestamp. Queues connection feedback only when the active
     * session object changes; repeated acknowledgments do not duplicate the event.
     * Preserves phone settings rather than importing old watch settings.
     *
     * @param token Identity of the RPC registration receiving the resume request.
     * @param nodeId Wearable sender node ID supplied by MessageClient.
     * @param payload Encoded WatchResumeRequest carrying the relationship and fresh
     *     command IDs.
     * @param includeSignal Whether an accepted reply includes current settings bound to
     *     the new command ID.
     * @return Encoded ACCEPTED, RETRY or REJECTED status, with a correlated phone snapshot
     *     when requested and accepted.
     */
    private fun handleWatchResume(
        token: Any,
        nodeId: String,
        payload: ByteArray,
        includeSignal: Boolean = false
    ): ByteArray =
        synchronized(listenerLock) {
            if (rpcRegistration?.token !== token || !linkRestored || callbacks?.isReady?.invoke() != true) {
                return@synchronized RETRY.encode()
            }
            val request = WatchResumeRequest.decode(payload) ?: return@synchronized REJECTED.encode()
            val previousConnection = sessions.active
            val reply = sessions.resume(nodeId, request)
            if (reply == ACCEPTED && sessions.active !== previousConnection) {
                watchConnectedChannel.trySend(Unit)
            }
            if (includeSignal) {
                val signal = if (reply == ACCEPTED) callbacks?.currentSignal?.invoke() else null
                WatchResumeReply(reply, signal).encode(request.commandId)
            } else {
                reply.encode()
            }
        }

    /**
     * Reports whether a watch command session is active without changing signal settings.
     *
     * Runs on Main under listenerLock. Returns RETRY while registration, restoration or
     * controls are unavailable, and for pending sessions or a remembered sender awaiting
     * resume. An exact active node/session match returns ACCEPTED.
     *
     * @param token Identity of the RPC registration receiving the status request.
     * @param nodeId Wearable sender node ID supplied by MessageClient.
     * @param payload Encoded command-session UUID to check, not the stable relationship
     *     ID.
     * @return Encoded ACCEPTED for an active match, RETRY while waiting or resuming, or
     *     REJECTED for a malformed ID or unrelated sender.
     */
    private fun handleWatchStatus(token: Any, nodeId: String, payload: ByteArray): ByteArray =
        synchronized(listenerLock) {
            if (rpcRegistration?.token !== token || !linkRestored || callbacks?.isReady?.invoke() != true) {
                return@synchronized RETRY.encode()
            }
            val id = WatchConnectionRequest.decodeId(payload) ?: return@synchronized REJECTED.encode()
            when {
                sessions.active?.matches(nodeId, id) == true -> ACCEPTED
                sessions.pending?.matches(nodeId, id) == true -> RETRY
                sessions.remembered?.nodeId == nodeId -> RETRY
                else -> REJECTED
            }.encode()
        }

    /**
     * Revokes a matching watch relationship while preserving the current phone signal.
     *
     * Runs on Main under listenerLock. Requires current registration and restored link
     * state, but not ready control callbacks. The session owner forgets only the matching
     * relationship, protecting a newer link from a delayed release. Blocks subsequent
     * legacy DataItem control from the sender.
     *
     * @param token Identity of the RPC registration receiving the release request.
     * @param nodeId Wearable sender node ID supplied by MessageClient.
     * @param payload Encoded stable relationship UUID to revoke.
     * @return Encoded ACCEPTED when the relationship is removed, RETRY before transport
     *     readiness, or REJECTED for a malformed ID or unknown relationship.
     */
    private fun handleWatchRelease(token: Any, nodeId: String, payload: ByteArray): ByteArray =
        synchronized(listenerLock) {
            if (rpcRegistration?.token !== token || !linkRestored) return@synchronized RETRY.encode()
            val id = WatchConnectionRequest.decodeId(payload) ?: return@synchronized REJECTED.encode()
            if (!sessions.forget(nodeId, id)) return@synchronized REJECTED.encode()
            blockedLegacyNodes.add(nodeId)
            ACCEPTED.encode()
        }

    /**
     * Handles a watch-led handoff that applies the watch's complete signal settings.
     *
     * Call on Main. Delegates asynchronous verification and confirmation to the shared
     * handoff handler with readPhoneState disabled.
     *
     * @param token Identity of the RPC registration receiving the handoff.
     * @param scope Registration-owned Main coroutine scope for asynchronous watch
     *     verification.
     * @param nodeId Wearable sender node ID supplied by MessageClient.
     * @param payload Encoded WatchConnectionRequest carrying a fresh invitation and signal
     *     settings.
     * @return A task yielding encoded ACCEPTED, RETRY or REJECTED status.
     */
    private fun handleWatchHandoff(token: Any, scope: CoroutineScope, nodeId: String, payload: ByteArray): Task<ByteArray> =
        handleWatchHandoff(token, scope, nodeId, payload, readPhoneState = false)

    /**
     * Handles a fresh watch session that reads phone settings instead of replacing them.
     *
     * Call on Main. Delegates verification to the shared handoff handler with
     * readPhoneState enabled. The invitation's signal snapshot is not applied to the
     * phone.
     *
     * @param token Identity of the RPC registration receiving the handoff.
     * @param scope Registration-owned Main coroutine scope for asynchronous watch
     *     verification.
     * @param nodeId Wearable sender node ID supplied by MessageClient.
     * @param payload Encoded WatchConnectionRequest carrying a fresh invitation and signal
     *     settings.
     * @return A task yielding connection status and, on acceptance, current settings
     *     correlated with the invitation ID.
     */
    private fun handleWatchStateHandoff(token: Any, scope: CoroutineScope, nodeId: String, payload: ByteArray): Task<ByteArray> =
        handleWatchHandoff(token, scope, nodeId, payload, readPhoneState = true)

    /**
     * Validates a handoff, verifies watch capability and confirms a fresh control session.
     *
     * Call on Main. Immediately rejects malformed, blank-sender or expired invitations.
     * Reports RETRY while controls or restoration are unavailable or another handoff is
     * pending. Repeated invitations for the active relationship are acknowledged without
     * reapplying watch settings.
     *
     * For a new attempt, returns a task while scope performs a bounded capability query.
     * Rechecks registration, pending identity, freshness and readiness before applying
     * watch settings or returning phone settings. Verification exceptions and job
     * cancellation complete the response with RETRY. The verification coroutine abandons
     * its unconfirmed attempt in finally so a still-fresh invitation can be retried.
     *
     * @param token Identity of the RPC registration receiving the handoff.
     * @param scope Registration-owned Main coroutine scope for asynchronous watch
     *     verification.
     * @param nodeId Wearable sender node ID supplied by MessageClient.
     * @param payload Encoded WatchConnectionRequest carrying a fresh invitation and signal
     *     settings.
     * @param readPhoneState True to preserve and return phone settings; false to apply the
     *     watch snapshot.
     * @return A task yielding encoded status and, for an accepted state-reading handoff,
     *     the current phone snapshot bound to the invitation ID.
     */
    private fun handleWatchHandoff(
        token: Any, scope: CoroutineScope, nodeId: String, payload: ByteArray, readPhoneState: Boolean
    ): Task<ByteArray> {
        val request = WatchConnectionRequest.decode(payload) ?: return Tasks.forResult(REJECTED.encode())
        if (nodeId.isBlank() || !request.isFresh()) return Tasks.forResult(REJECTED.encode())
        /**
         * Encodes this handoff's status and optional current-phone snapshot.
         *
         * Call on Main while holding listenerLock. When readPhoneState is enabled, only an
         * accepted response reads the control callback and attaches settings correlated with
         * the enclosing invitation ID.
         *
         * @param status Connection result to encode for this handoff response.
         * @return Status bytes, optionally followed by the accepted phone snapshot.
         */
        fun reply(status: WatchConnectionReply): ByteArray = if (readPhoneState) {
            val signal = if (status == ACCEPTED) callbacks?.currentSignal?.invoke() else null
            WatchResumeReply(status, signal).encode(request.id)
        } else status.encode()
        val connection = synchronized(listenerLock) {
            // A verified watch can control a ready phone without revealing its UI over keyguard.
            if (rpcRegistration?.token !== token || !linkRestored || callbacks?.isReady?.invoke() != true) {
                return Tasks.forResult(RETRY.encode())
            }
            if (sessions.active?.matchesLink(nodeId, request.id) == true) return Tasks.forResult(reply(ACCEPTED))
            if (sessions.pending != null) return Tasks.forResult(RETRY.encode())
            sessions.begin(nodeId, request.id, acceptsEarlyControl = false)?.also { _stateFlow.value = null }
        } ?: return Tasks.forResult(REJECTED.encode())

        val response = TaskCompletionSource<ByteArray>()
        val job = scope.launch {
            try {
                val verified = withTimeoutOrNull(VERIFY_WATCH_TIMEOUT_MS.milliseconds) {
                    capabilityClient.getCapability(
                        WatchConnectionRequest.CAPABILITY, CapabilityClient.FILTER_ALL
                    ).await().nodes.any { it.id == nodeId }
                } == true
                val result = synchronized(listenerLock) {
                    val status = when {
                        rpcRegistration?.token !== token || sessions.pending !== connection || !request.isFresh() ->
                            REJECTED
                        !verified || callbacks?.isReady?.invoke() != true -> RETRY
                        (readPhoneState || callbacks?.applyHandoff?.invoke(request.signal) == true) &&
                            confirmConnection(connection) -> ACCEPTED
                        else -> REJECTED
                    }
                    reply(status)
                }
                response.trySetResult(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Unable to verify watch handoff", error)
                response.trySetResult(RETRY.encode())
            } finally {
                // A retry can use the same invitation while it is fresh. Local revocation retires it.
                synchronized(listenerLock) { sessions.abandonUnconfirmed(connection) }
            }
        }
        job.invokeOnCompletion { response.trySetResult(RETRY.encode()) }
        return response.task
    }

    /**
     * Queues best-effort revocation of a watch's stable relationship.
     *
     * Called on Main during handoff cancellation or transport cleanup. Uses the
     * relationship ID rather than a resumed command-session ID and returns without waiting
     * for delivery.
     *
     * @param connection Session identifying the watch and relationship to revoke.
     */
    private fun notifyWatchRevoked(connection: LinkedWatchControl) {
        notifyWatch(connection, WatchConnectionRequest.REVOKE_PATH, connection.linkId)
    }

    /**
     * Queues a watch pause or revocation request without awaiting acknowledgment.
     *
     * Called on Main during session cleanup. Encodes the supplied UUID, logs synchronous
     * runtime failures and asynchronous request failures, and leaves response bytes
     * unused.
     *
     * @param connection Session identifying the destination watch node.
     * @param path Watch RPC endpoint for pause or relationship revocation.
     * @param id Command-session ID for pause, or stable relationship ID for revocation.
     */
    private fun notifyWatch(connection: LinkedWatchControl, path: String, id: UUID) {
        try {
            messageClient.sendRequest(
                connection.nodeId,
                path,
                WatchConnectionRequest.encodeId(id)
            ).addOnFailureListener { Log.d(TAG, "Watch disconnect was not confirmed", it) }
        } catch (error: RuntimeException) {
            Log.d(TAG, "Watch disconnect could not be sent", error)
        }
    }

    /**
     * Decodes permitted legacy control updates while their DataEventBuffer is valid.
     *
     * Runs synchronously in the DataClient callback under listenerLock. Requires the
     * current listener token, restored link state and no remembered or pending modern
     * relationship. Processes changed items on CONTROL_STATE_PATH only when their sender
     * is not blocked.
     *
     * Publishes decoded snapshots through [stateFlow] and logs runtime decoding failures
     * per item. Does not retain events or the callback-owned buffer for later coroutine
     * work.
     *
     * @param events Callback-owned Data Layer events to inspect before the callback
     *     returns.
     * @param token Identity of the legacy Data Layer listener that received these events.
     */
    private fun consumeEvents(events: DataEventBuffer, token: Any) {
        synchronized(listenerLock) {
            if (sessionToken !== token || !linkRestored || sessions.remembered != null || sessions.pending != null) return
            for (event in events) {
                val uri = event.dataItem.uri
                if (event.type == DataEvent.TYPE_CHANGED && uri.path == CONTROL_STATE_PATH && uri.host !in blockedLegacyNodes) {
                    try {
                        // Decode while the callback still owns its DataEventBuffer.
                        val state = decodeControlState(DataMapItem.fromDataItem(event.dataItem).dataMap)
                        _stateFlow.value = state
                    } catch (error: RuntimeException) {
                        Log.w(TAG, "Ignoring malformed control state", error)
                    }
                }
            }
        }
    }

    /**
     * Holds the transport log tag and bounded watch-verification interval.
     */
    private companion object {
        /**
         * Log tag for this component.
         */
        const val TAG = "PhoneSyncState"
        /**
         * Maximum wait for watch capability verification, in milliseconds.
         */
        const val VERIFY_WATCH_TIMEOUT_MS = 1_500L
    }
}

/**
 * Persists a verified watch relationship separately from its current command session.
 *
 * @property nodeId Wearable node ID of the peer.
 * @property linkId Stable identifier of the remembered relationship.
 * @property resumedAt Creation timestamp of the last accepted resume, or the unset sentinel.
 */
internal data class RememberedWatchLink(val nodeId: String, val linkId: UUID, val resumedAt: Long = Long.MIN_VALUE) {
    /**
     * Compares the peer node and relationship or request identifier.
     *
     * @param nodeId Wearable node ID of the peer.
     * @param linkId Stable identifier of the remembered relationship.
     * @return True when both fields match this record.
     */
    fun matches(nodeId: String, linkId: UUID): Boolean = this.nodeId == nodeId && this.linkId == linkId

    /**
     * Defines stored-link keys and preference read/write adapters.
     */
    companion object {
        /**
         * Preference key storing the remembered watch node ID.
         */
        private val NODE = stringPreferencesKey("linked_watch_node")
        /**
         * Preference key storing the stable remembered-watch relationship UUID.
         */
        private val ID = stringPreferencesKey("linked_watch_id")
        /**
         * Preference key storing the latest accepted resume timestamp for replay checks.
         */
        private val RESUMED_AT = longPreferencesKey("linked_watch_resumed_at")

        /**
         * Restores a remembered relationship from preferences if both identifiers are valid.
         *
         * @param prefs Preferences snapshot used by this operation.
         * @return The remembered relationship, or null for absent or malformed values.
         */
        fun read(prefs: Preferences): RememberedWatchLink? {
            val nodeId = prefs[NODE]?.takeIf { it.isNotBlank() } ?: return null
            val id = prefs[ID] ?: return null
            return try {
                RememberedWatchLink(nodeId, UUID.fromString(id), prefs[RESUMED_AT] ?: Long.MIN_VALUE)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        /**
         * Updates or removes relationship fields in the supplied mutable preference transaction.
         *
         * The caller owns DataStore editing and persistence; this helper only mutates prefs.
         *
         * @param prefs Mutable preferences transaction receiving the remembered-link fields.
         * @param link Remembered relationship, or null to clear it.
         */
        fun write(prefs: MutablePreferences, link: RememberedWatchLink?) {
            if (link == null) {
                prefs.remove(NODE)
                prefs.remove(ID)
                prefs.remove(RESUMED_AT)
            } else {
                prefs[NODE] = link.nodeId
                prefs[ID] = link.linkId.toString()
                prefs[RESUMED_AT] = link.resumedAt
            }
        }
    }
}

/**
 * Tracks one watch command session and its last accepted sequence number.
 *
 * @property nodeId Wearable node ID of the peer.
 * @property requestId Identifier of the handoff or command session being checked.
 * @property acceptsEarlyControl Whether matching commands may confirm a still-pending handoff.
 * @property linkId Stable identifier of the remembered relationship.
 * @property resumedAt Creation timestamp of the last accepted resume, or the unset sentinel.
 */
internal class LinkedWatchControl(
    val nodeId: String,
    val requestId: UUID,
    val acceptsEarlyControl: Boolean,
    val linkId: UUID = requestId,
    val resumedAt: Long = Long.MIN_VALUE
) {
    /**
     * Largest accepted command sequence for this in-memory session, initially zero.
     */
    private var lastAcceptedSequence = 0L

    /**
     * Compares the peer node and relationship or request identifier.
     *
     * @param nodeId Wearable node ID of the peer.
     * @param requestId Identifier of the handoff or command session being checked.
     * @return True when both fields match this record.
     */
    fun matches(nodeId: String, requestId: UUID): Boolean =
        this.nodeId == nodeId && this.requestId == requestId

    /**
     * Compares the peer node and stable relationship identifier.
     *
     * @param nodeId Wearable node ID of the peer.
     * @param linkId Stable identifier of the remembered relationship.
     * @return True when both identify this relationship.
     */
    fun matchesLink(nodeId: String, linkId: UUID): Boolean =
        this.nodeId == nodeId && this.linkId == linkId

    /**
     * Checks the peer, command-session ID and increasing sequence number.
     *
     * @param nodeId Wearable node ID of the peer.
     * @param requestId Identifier of the handoff or command session being checked.
     * @param sequence Positive, increasing command number within this session.
     * @return True when this session may accept the command.
     */
    fun allows(nodeId: String, requestId: UUID, sequence: Long): Boolean =
        matches(nodeId, requestId) && sequence > lastAcceptedSequence

    /**
     * Stores an accepted command sequence for later replay and ordering checks.
     *
     * Call under the session owner's Main-thread lock after allows has validated the sequence;
     * this setter performs no independent bound check.
     *
     * @param sequence Positive, increasing command number within this session.
     */
    fun recordAccepted(sequence: Long) {
        lastAcceptedSequence = sequence
    }
}

/**
 * Tracks pending, active, remembered and retired watch sessions.
 *
 * The phone accesses this state on Main under its listener lock. Sequence and timestamp checks
 * reject stale commands and prevent superseded sessions from regaining control.
 *
 * @property onRememberedChanged Synchronous notification of changed remembered identity; the
 *     owner queues persistence.
 * @property nowMillis Clock that returns epoch milliseconds for freshness checks and timestamps.
 */
internal class WatchControlSessions(
    private val onRememberedChanged: (RememberedWatchLink?) -> Unit = {},
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    /**
     * Retired relationship and command UUIDs mapped to expiry times in epoch milliseconds.
     */
    private val retiredIds = mutableMapOf<UUID, Long>()
    /**
     * Handoff awaiting verification or acknowledgment, if any.
     */
    var pending: LinkedWatchControl? = null
        private set
    /**
     * Currently confirmed watch command session, if any.
     */
    var active: LinkedWatchControl? = null
        private set
    /**
     * Persisted relationship retained for authenticated resume, if any.
     */
    var remembered: RememberedWatchLink? = null
        private set

    /**
     * Loads remembered identity before any active, pending or remembered session exists.
     *
     * Call on Main under the phone listener lock. Initial restoration does not emit the
     * persistence callback because the value already came from storage.
     *
     * @param link Saved relationship, or null when storage contains no authorized watch.
     * @throws IllegalStateException If an active, pending or remembered session already exists.
     */
    fun restore(link: RememberedWatchLink?) {
        check(active == null && pending == null && remembered == null)
        remembered = link
    }

    /**
     * Updates remembered identity only when it changes and notifies the persistence owner.
     *
     * Call on Main while holding the phone listener lock. The callback queues storage work; this
     * method does not wait for it.
     *
     * @param link New stable relationship, or null to remove the remembered relationship.
     */
    private fun remember(link: RememberedWatchLink?) {
        if (remembered == link) return
        remembered = link
        onRememberedChanged(link)
    }

    /**
     * Creates a fresh pending session unless another peer, pending request or retired ID
     * prevents it.
     *
     * Call on Main, where the owner serializes control and session changes.
     *
     * @param nodeId Wearable node ID of the peer.
     * @param requestId Identifier of the handoff or command session being checked.
     * @param acceptsEarlyControl Whether matching commands may confirm a still-pending
     *     handoff.
     * @return The pending session, or null when the request cannot begin.
     */
    fun begin(nodeId: String, requestId: UUID, acceptsEarlyControl: Boolean): LinkedWatchControl? {
        val now = nowMillis()
        retiredIds.entries.removeAll { it.value < now }
        if (pending != null || requestId in retiredIds || requestId == remembered?.linkId) return null
        val current = active
        // A restarted watch can request a fresh connection while its old session is still active.
        if (current != null && (current.nodeId != nodeId || current.linkId == requestId ||
                current.requestId == requestId)
        ) return null
        return LinkedWatchControl(nodeId, requestId, acceptsEarlyControl).also { pending = it }
    }

    /**
     * Promotes the matching pending session and retires the previous command authorization.
     *
     * Call on Main, where the owner serializes control and session changes.
     *
     * @param connection Session record whose state should change.
     * @return True when this session is now active.
     */
    fun confirm(connection: LinkedWatchControl): Boolean {
        if (active === connection) return true
        if (pending !== connection) return false
        active?.let(::retire)
        pending = null
        active = connection
        remember(RememberedWatchLink(connection.nodeId, connection.linkId, connection.resumedAt))
        return true
    }

    /**
     * Resumes a matching remembered relationship with a current invitation and eligible command
     * ID.
     *
     * Call on Main under the phone listener lock. Pending handoffs defer resume. An already
     * active matching command ID is acknowledged idempotently. Otherwise requires a newer resume
     * timestamp and a command ID different from the stable link and currently retired IDs, then
     * replaces command authorization and records the timestamp.
     *
     * @param nodeId Wearable node ID of the peer.
     * @param request Resume invitation carrying the stable link, replacement command ID and
     *     creation time.
     * @return ACCEPTED, RETRY or REJECTED according to the relationship and freshness checks.
     */
    fun resume(nodeId: String, request: WatchResumeRequest): WatchConnectionReply {
        if (!request.isFresh(nowMillis())) return RETRY
        if (pending?.matchesLink(nodeId, request.linkId) == true) return RETRY
        val link = remembered?.takeIf { it.matches(nodeId, request.linkId) } ?: return REJECTED
        if (pending != null) return RETRY
        if (active?.matches(nodeId, request.commandId) == true) return ACCEPTED
        if (request.sentAt <= link.resumedAt ||
            request.commandId == link.linkId || request.commandId in retiredIds
        ) return RETRY
        // A new command ID also makes any in-flight commands from the old link unusable.
        active = LinkedWatchControl(nodeId, request.commandId, false, link.linkId, request.sentAt)
        remember(link.copy(resumedAt = request.sentAt))
        return ACCEPTED
    }

    /**
     * Revokes the matching relationship without letting a delayed old release revoke a new
     * link.
     *
     * Call on Main, where the owner serializes control and session changes.
     *
     * @param nodeId Wearable node ID of the peer.
     * @param linkId Stable identifier of the remembered relationship.
     * @return True when a known or pending relationship was removed.
     */
    fun forget(nodeId: String, linkId: UUID): Boolean {
        val known = remembered?.matches(nodeId, linkId) == true
        val pendingLink = pending?.takeIf { it.matchesLink(nodeId, linkId) }
        if (!known && pendingLink == null) return false
        pendingLink?.let(::end)
        active?.takeIf { it.matchesLink(nodeId, linkId) }?.let(::end)
        if (known) remember(null)
        retiredIds[linkId] = nowMillis() + 2 * WatchConnectionRequest.LIFETIME_MS
        return true
    }

    /**
     * Retires current command sessions while retaining the confirmed relationship for later
     * resume.
     *
     * Call on Main, where the owner serializes control and session changes.
     *
     * @return Sessions that were active or pending before pausing.
     */
    fun pause(): List<LinkedWatchControl> {
        val previous = listOfNotNull(pending, active)
        pending?.let(::end)
        active = null
        previous.forEach(::retire)
        return previous
    }

    /**
     * Cancels and retires the pending request with the given ID.
     *
     * Call on Main, where the owner serializes control and session changes.
     *
     * @param requestId Identifier of the handoff or command session being checked.
     * @return The cancelled session, or null when the ID does not match.
     */
    fun cancelPending(requestId: UUID): LinkedWatchControl? =
        pending?.takeIf { it.requestId == requestId }?.also(::end)

    /**
     * Drops an unconfirmed attempt while leaving its still-fresh invitation eligible for
     * retry.
     *
     * Call on Main, where the owner serializes control and session changes.
     *
     * @param connection Session record whose state should change.
     */
    fun abandonUnconfirmed(connection: LinkedWatchControl) {
        if (pending === connection) pending = null
    }

    /**
     * Removes fields referring to this session and retires its IDs on Main under the listener
     * lock.
     *
     * Ending the active session also clears remembered identity. Ending only a pending
     * replacement leaves the existing active relationship intact.
     *
     * @param connection Session record whose state should change.
     */
    fun end(connection: LinkedWatchControl) {
        if (pending === connection) pending = null
        if (active === connection) {
            active = null
            remember(null)
        }
        retire(connection)
    }

    /**
     * Ends all command sessions and clears the remembered relationship.
     *
     * Call on Main, where the owner serializes control and session changes.
     *
     * @return The sessions that were pending or active before shutdown.
     */
    fun endAll(): List<LinkedWatchControl> {
        val previous = listOfNotNull(pending, active)
        previous.forEach(::end)
        remember(null)
        return previous
    }

    /**
     * Marks both relationship and command IDs as retired for twice the invitation lifetime.
     *
     * Call on Main under the phone listener lock. Expiry uses the injected epoch clock; begin
     * removes expired entries before checking a new invitation.
     *
     * @param connection Session whose IDs must not authorize a delayed replacement or replay.
     */
    private fun retire(connection: LinkedWatchControl) {
        val expiry = nowMillis() + 2 * WatchConnectionRequest.LIFETIME_MS
        retiredIds[connection.linkId] = expiry
        retiredIds[connection.requestId] = expiry
    }
}
