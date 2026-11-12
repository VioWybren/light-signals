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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneSyncStateRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val dataClient: DataClient,
    private val messageClient: MessageClient,
    private val dataStore: DataStore<Preferences>
) {
    private class RpcRegistration(
        val token: Any,
        val scope: CoroutineScope,
        val service: MessageClient.RpcService,
        val added: Task<Void>
    ) {
        var availabilityAnnounced = false
    }

    private class ControlCallbacks(
        val isReady: () -> Boolean,
        val applyHandoff: (PhoneSignalState) -> Boolean,
        val applyControl: (WatchControl, Int) -> Boolean
    )

    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val listenerLock = Any()
    private var sessionToken: Any? = null
    private var listener: DataClient.OnDataChangedListener? = null
    private var callbacks: ControlCallbacks? = null
    private var rpcRegistration: RpcRegistration? = null
    private var hostResumed = false
    private val linkWrites = Channel<RememberedWatchLink?>(Channel.UNLIMITED)
    private val sessions = WatchControlSessions(onRememberedChanged = { linkWrites.trySend(it) })
    private var linkRestored = false
    private val blockedLegacyNodes = mutableSetOf<String>()
    private val _stateFlow = MutableStateFlow<PhoneControlState?>(null)
    val stateFlow: StateFlow<PhoneControlState?> = _stateFlow.asStateFlow()
    private val watchConnectedChannel = Channel<Unit>(Channel.CONFLATED)
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

    @MainThread
    internal fun initFromViewModel(
        isReady: () -> Boolean,
        applyHandoff: (PhoneSignalState) -> Boolean,
        applyControl: (WatchControl, Int) -> Boolean
    ) = synchronized(listenerLock) {
        callbacks = ControlCallbacks(isReady, applyHandoff, applyControl)
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

    @MainThread
    internal fun setHostResumed(resumed: Boolean) = synchronized(listenerLock) {
        hostResumed = resumed
        if (resumed && callbacks != null) ensureRpcRegistration()
        if (resumed) announceAvailability()
        Unit
    }

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
                withTimeoutOrNull(VERIFY_WATCH_TIMEOUT_MS) {
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

    @MainThread
    internal fun confirmWatchConnection(nodeId: String, requestId: UUID): Boolean = synchronized(listenerLock) {
        val current = sessions.active
        if (current?.matchesLink(nodeId, requestId) == true) return@synchronized true
        val pending = sessions.pending
        pending?.matches(nodeId, requestId) == true && pending.acceptsEarlyControl && confirmConnection(pending)
    }

    @MainThread
    internal fun isWatchConnectionConfirmed(nodeId: String, requestId: UUID): Boolean = synchronized(listenerLock) {
        sessions.active?.matchesLink(nodeId, requestId) == true
    }

    @MainThread
    internal fun cancelWatchConnection(requestId: UUID) {
        synchronized(listenerLock) { sessions.cancelPending(requestId) }?.let(::notifyWatchRevoked)
    }

    @MainThread
    internal fun allowLegacyWatch(nodeId: String) {
        synchronized(listenerLock) { blockedLegacyNodes.remove(nodeId) }
    }

    @MainThread
    fun clear() {
        val previousConnections = synchronized(listenerLock) {
            val connections = sessions.pause()
            callbacks = null
            hostResumed = false
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

    private fun confirmConnection(connection: LinkedWatchControl): Boolean {
        val alreadyConfirmed = sessions.active === connection
        if (!sessions.confirm(connection)) return false
        if (!alreadyConfirmed) watchConnectedChannel.trySend(Unit)
        return true
    }

    private fun ensureRpcRegistration(): RpcRegistration? {
        rpcRegistration?.let { return it }
        val token = Any()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        // MessageClient's default Wearable options deliver RPCs on the main thread.
        val service = MessageClient.RpcService { nodeId, path, payload ->
            when (path) {
                WatchControlCommand.PATH -> Tasks.forResult(handleWatchControl(token, nodeId, payload))
                WatchConnectionRequest.PHONE_PATH -> handleWatchHandoff(token, scope, nodeId, payload)
                WatchConnectionRequest.RESUME_PATH -> Tasks.forResult(handleWatchResume(token, nodeId, payload))
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

    private fun handleWatchControl(token: Any, nodeId: String, payload: ByteArray): ByteArray = synchronized(listenerLock) {
        if (rpcRegistration?.token !== token || !linkRestored) return@synchronized byteArrayOf()
        val command = WatchControlCommand.decode(payload) ?: return@synchronized byteArrayOf()
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
                callbacks?.applyControl?.invoke(command.control, command.value) == true
            }
            if (!applied || !confirmConnection(connection)) return@synchronized byteArrayOf()
            connection.recordAccepted(command.sequence)
        }
        byteArrayOf(WatchControlCommand.ACCEPTED)
    }

    private fun handleWatchResume(token: Any, nodeId: String, payload: ByteArray): ByteArray =
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
            reply.encode()
        }

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

    private fun handleWatchRelease(token: Any, nodeId: String, payload: ByteArray): ByteArray =
        synchronized(listenerLock) {
            if (rpcRegistration?.token !== token || !linkRestored) return@synchronized RETRY.encode()
            val id = WatchConnectionRequest.decodeId(payload) ?: return@synchronized REJECTED.encode()
            if (!sessions.forget(nodeId, id)) return@synchronized REJECTED.encode()
            blockedLegacyNodes.add(nodeId)
            ACCEPTED.encode()
        }

    private fun handleWatchHandoff(token: Any, scope: CoroutineScope, nodeId: String, payload: ByteArray): Task<ByteArray> {
        val request = WatchConnectionRequest.decode(payload) ?: return Tasks.forResult(REJECTED.encode())
        if (nodeId.isBlank() || !request.isFresh()) return Tasks.forResult(REJECTED.encode())
        val connection = synchronized(listenerLock) {
            if (rpcRegistration?.token !== token || !linkRestored || !hostResumed || callbacks?.isReady?.invoke() != true) {
                return Tasks.forResult(RETRY.encode())
            }
            if (sessions.active?.matchesLink(nodeId, request.id) == true) return Tasks.forResult(ACCEPTED.encode())
            if (sessions.pending != null) return Tasks.forResult(RETRY.encode())
            sessions.begin(nodeId, request.id, acceptsEarlyControl = false)?.also { _stateFlow.value = null }
        } ?: return Tasks.forResult(REJECTED.encode())

        val response = TaskCompletionSource<ByteArray>()
        val job = scope.launch {
            try {
                val verified = withTimeoutOrNull(VERIFY_WATCH_TIMEOUT_MS) {
                    capabilityClient.getCapability(
                        WatchConnectionRequest.CAPABILITY, CapabilityClient.FILTER_ALL
                    ).await().nodes.any { it.id == nodeId }
                } == true
                val result = synchronized(listenerLock) {
                    when {
                        rpcRegistration?.token !== token || sessions.pending !== connection || !request.isFresh() ->
                            REJECTED
                        !verified || !hostResumed || callbacks?.isReady?.invoke() != true -> RETRY
                        callbacks?.applyHandoff?.invoke(request.signal) == true && confirmConnection(connection) -> ACCEPTED
                        else -> REJECTED
                    }
                }
                response.trySetResult(result.encode())
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

    private fun notifyWatchRevoked(connection: LinkedWatchControl) {
        notifyWatch(connection, WatchConnectionRequest.REVOKE_PATH, connection.linkId)
    }

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

    private companion object {
        const val TAG = "PhoneSyncState"
        const val VERIFY_WATCH_TIMEOUT_MS = 1_500L
    }
}

internal data class RememberedWatchLink(val nodeId: String, val linkId: UUID, val resumedAt: Long = Long.MIN_VALUE) {
    fun matches(nodeId: String, linkId: UUID): Boolean = this.nodeId == nodeId && this.linkId == linkId

    companion object {
        private val NODE = stringPreferencesKey("linked_watch_node")
        private val ID = stringPreferencesKey("linked_watch_id")
        private val RESUMED_AT = longPreferencesKey("linked_watch_resumed_at")

        fun read(prefs: Preferences): RememberedWatchLink? {
            val nodeId = prefs[NODE]?.takeIf { it.isNotBlank() } ?: return null
            val id = prefs[ID] ?: return null
            return try {
                RememberedWatchLink(nodeId, UUID.fromString(id), prefs[RESUMED_AT] ?: Long.MIN_VALUE)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

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

internal class LinkedWatchControl(
    val nodeId: String,
    val requestId: UUID,
    val acceptsEarlyControl: Boolean,
    val linkId: UUID = requestId,
    val resumedAt: Long = Long.MIN_VALUE
) {
    private var lastAcceptedSequence = 0L

    fun matches(nodeId: String, requestId: UUID): Boolean =
        this.nodeId == nodeId && this.requestId == requestId

    fun matchesLink(nodeId: String, linkId: UUID): Boolean =
        this.nodeId == nodeId && this.linkId == linkId

    fun allows(nodeId: String, requestId: UUID, sequence: Long): Boolean =
        matches(nodeId, requestId) && sequence > lastAcceptedSequence

    fun recordAccepted(sequence: Long) {
        lastAcceptedSequence = sequence
    }
}

/** Accessed on Main under the same lock as the phone's Data Layer callbacks. */
internal class WatchControlSessions(
    private val onRememberedChanged: (RememberedWatchLink?) -> Unit = {},
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val retiredIds = mutableMapOf<UUID, Long>()
    var pending: LinkedWatchControl? = null
        private set
    var active: LinkedWatchControl? = null
        private set
    var remembered: RememberedWatchLink? = null
        private set

    fun restore(link: RememberedWatchLink?) {
        check(active == null && pending == null && remembered == null)
        remembered = link
    }

    private fun remember(link: RememberedWatchLink?) {
        if (remembered == link) return
        remembered = link
        onRememberedChanged(link)
    }

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

    fun confirm(connection: LinkedWatchControl): Boolean {
        if (active === connection) return true
        if (pending !== connection) return false
        active?.let(::retire)
        pending = null
        active = connection
        remember(RememberedWatchLink(connection.nodeId, connection.linkId, connection.resumedAt))
        return true
    }

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

    fun pause(): List<LinkedWatchControl> {
        val previous = listOfNotNull(pending, active)
        pending?.let(::end)
        active = null
        previous.forEach(::retire)
        return previous
    }

    fun cancelPending(requestId: UUID): LinkedWatchControl? =
        pending?.takeIf { it.requestId == requestId }?.also(::end)

    fun abandonUnconfirmed(connection: LinkedWatchControl) {
        if (pending === connection) pending = null
    }

    fun end(connection: LinkedWatchControl) {
        if (pending === connection) pending = null
        if (active === connection) {
            active = null
            remember(null)
        }
        retire(connection)
    }

    fun endAll(): List<LinkedWatchControl> {
        val previous = listOfNotNull(pending, active)
        previous.forEach(::end)
        remember(null)
        return previous
    }

    private fun retire(connection: LinkedWatchControl) {
        val expiry = nowMillis() + 2 * WatchConnectionRequest.LIFETIME_MS
        retiredIds[connection.linkId] = expiry
        retiredIds[connection.requestId] = expiry
    }
}
