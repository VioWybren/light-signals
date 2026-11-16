package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.AppHelperResultCode.APP_HELPER_RESULT_SUCCESS
import com.google.android.horologist.data.apphelper.DataLayerAppHelper
import com.google.android.horologist.data.apphelper.AppHelperNodeStatus
import com.google.android.horologist.data.apphelper.appInstalled
import com.google.android.horologist.datalayer.watch.WearDataLayerAppHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import nodomain.greensnowapps.lightsignals.WearConstants
import nodomain.greensnowapps.lightsignals.core.sync.PhoneSignalState
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchControl
import nodomain.greensnowapps.lightsignals.core.sync.WatchControlCommand
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeReply
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Requests phone companion launch or installation and negotiates watch control.
 *
 * Call [performWearOperations] from a Main-owned coroutine. [isLaunchingOnPhone]
 * exposes progress and guards against overlapping operations on Main;
 * [controlMessageFlow] emits feedback identifiers for the watch UI. Work shares the
 * caller's coroutine and cancellation lifetime. Wearable tasks suspend without
 * blocking, and repository handoff changes run on Main.
 *
 * @param context Application context used to construct the Wearable capability client.
 * @property wearDataLayerAppHelper Horologist helper for phone discovery, remote
 *     startup and installation requests.
 * @property messageClient Wearable RPC client for handoffs and best-effort
 *     failed-attempt cleanup.
 * @property controls Main-confined watch state owner for handoffs, imported settings
 *     and legacy linking.
 */
@Singleton
@OptIn(ExperimentalHorologistApi::class)
class WearPhoneOperations @Inject constructor(
    @ApplicationContext context: Context,
    private val wearDataLayerAppHelper: WearDataLayerAppHelper,
    private val messageClient: MessageClient,
    private val controls: WearControlRepository
) {
    /**
     * Application-scoped client for companion-presence and modern handoff-capability
     * queries.
     */
    private val capabilityClient = Wearable.getCapabilityClient(context)
    /**
     * Mutable operation progress and guard whose check-and-set is confined to Main.
     *
     * Set before discovery and reset in the operation's finally block, including on
     * failure or cancellation. Its read-only view is [isLaunchingOnPhone].
     */
    private val _isLaunchingOnPhone = MutableStateFlow(false)
    /**
     * Read-only busy state for phone discovery, launch, handoff and installation requests.
     *
     * Remains true through feedback emission until the operation exits. The watch UI can
     * use this state to show progress and disable repeated requests.
     */
    val isLaunchingOnPhone = _isLaunchingOnPhone.asStateFlow()

    /**
     * Non-replaying feedback source with room for four buffered events for active
     * collectors.
     *
     * Emission suspends when slow collectors fill the buffer. Events emitted without
     * collectors are not retained for later subscribers.
     */
    private val _controlMessageFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /**
     * Read-only stream of WearConstants feedback identifiers for the watch UI.
     *
     * Repeated identifiers are separate flow events. New collectors receive only
     * subsequent emissions because this flow has no replay.
     */
    val controlMessageFlow = _controlMessageFlow.asSharedFlow()

    /**
     * Discovers a phone and requests companion startup with control handoff, or
     * installation.
     *
     * Call from a Main-owned coroutine. Returns immediately while another operation is
     * busy. An explicit startup request first forgets a suspended relationship while
     * retaining the last-phone routing hint. Discovery, installation and connection
     * results are emitted through [controlMessageFlow].
     *
     * Suspends during Wearable tasks and feedback emission. Logs ordinary exceptions and
     * emits launch or installation failure feedback. Propagates cancellation and resets
     * progress in finally for every exit path.
     *
     * @param command COMMAND_START_ON_PHONE to open and connect the companion, or
     *     COMMAND_INSTALL_ON_PHONE to request installation.
     */
    suspend fun performWearOperations(command: String) {
        if (_isLaunchingOnPhone.value) return
        _isLaunchingOnPhone.value = true
        try {
            if (command == WearConstants.COMMAND_START_ON_PHONE) controls.forgetSuspendedPhoneLink()
            val node = isNodeConnected(wearDataLayerAppHelper.connectedNodes())
            val message = when {
                node == null -> WearConstants.NO_CONNECTED_PHONES
                command == WearConstants.COMMAND_START_ON_PHONE -> when (isCompanionInstalled(node)) {
                    true -> openAndConnect(node.id)
                    false -> WearConstants.APP_IS_NOT_INSTALLED_ON_PHONE
                    null -> WearConstants.PHONE_CONNECTION_UNCONFIRMED
                }
                command == WearConstants.COMMAND_INSTALL_ON_PHONE -> {
                    if (wearDataLayerAppHelper.installOnNode(node.id) == APP_HELPER_RESULT_SUCCESS) {
                        WearConstants.INSTALLATION_HAS_BEEN_STARTED_ON_PHONE
                    } else {
                        WearConstants.INSTALLATION_ON_PHONE_FAILED
                    }
                }
                else -> return
            }
            _controlMessageFlow.emit(message)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("WearPhoneOperations", "Remote operation failed", error)
            _controlMessageFlow.emit(
                if (command == WearConstants.COMMAND_INSTALL_ON_PHONE)
                    WearConstants.INSTALLATION_ON_PHONE_FAILED
                else
                    WearConstants.APP_FAILED_TO_LAUNCH_ON_PHONE
            )
        } finally {
            _isLaunchingOnPhone.value = false
        }
    }

    /**
     * Resolves companion installation status from discovery metadata or advertised
     * capabilities.
     *
     * Returns true immediately when the discovered node reports installation. Otherwise
     * suspends in the caller's coroutine for an all-node phone-capability query bounded to
     * four seconds. This includes capability records omitted by reachable-only discovery.
     * Query exceptions and caller cancellation propagate to the operation handler.
     *
     * @param node Discovered phone node whose installation status needs confirmation.
     * @return True when metadata or the capability query reports the companion; false when
     *     a completed query finds no matching node; null when this query times out.
     */
    private suspend fun isCompanionInstalled(node: AppHelperNodeStatus): Boolean? {
        if (node.appInstalled) return true
        // The connected-node lookup only includes currently reachable capabilities.
        return withTimeoutOrNull(4_000.milliseconds) {
            capabilityClient.getCapability(
                DataLayerAppHelper.PHONE_CAPABILITY,
                CapabilityClient.FILTER_ALL,
            ).await().nodes.any { it.id == node.id }
        }
    }

    /**
     * Requests phone app startup, then negotiates modern handoff or legacy watch control.
     *
     * Call from the Main coroutine. Suspends for remote startup and a four-second
     * modern-capability query. A query timeout reports an unconfirmed connection; a
     * completed query without handoff support enables local legacy control. A supported
     * peer uses [requestPhoneHandoff].
     *
     * After the lookup, success also accepts an already selected repository connection.
     * Exceptions propagate to [performWearOperations] for feedback handling.
     *
     * @param nodeId Discovered phone node ID on which to open the companion and request
     *     control.
     * @return APP_FAILED_TO_LAUNCH_ON_PHONE when startup fails,
     *     PHONE_CONNECTION_UNCONFIRMED when negotiation is unresolved, or
     *     APP_LAUNCHED_ON_PHONE when control is selected.
     */
    private suspend fun openAndConnect(nodeId: String): String {
        if (wearDataLayerAppHelper.startRemoteOwnApp(nodeId) != APP_HELPER_RESULT_SUCCESS) {
            return WearConstants.APP_FAILED_TO_LAUNCH_ON_PHONE
        }
        val supportsHandoff = withTimeoutOrNull(4000L.milliseconds) {
            capabilityClient.getCapability(
                WatchConnectionRequest.PHONE_CAPABILITY,
                CapabilityClient.FILTER_ALL,
            ).await().nodes.any { it.id == nodeId }
        } ?: return WearConstants.PHONE_CONNECTION_UNCONFIRMED
        val connected = if (supportsHandoff) requestPhoneHandoff(nodeId) else controls.enableLegacyPhoneLink()
        return if (connected || controls.phoneConnection.value) WearConstants.APP_LAUNCHED_ON_PHONE
        else WearConstants.PHONE_CONNECTION_UNCONFIRMED
    }

    /**
     * Negotiates a fresh control session using watch settings or the last phone's current
     * state.
     *
     * Call from the Main coroutine. Prepares a pending invitation and chooses the
     * phone-state refresh path for the last connected phone. Reuses the encoded invitation
     * across two-second RPC attempts, delaying 200 milliseconds after RETRY, API errors or
     * missing replies. The overall exchange is bounded to the invitation lifetime plus one
     * second.
     *
     * A state-refresh reply must include a valid phone snapshot correlated with the
     * invitation. Stops on explicit rejection or replacement of the pending attempt. After
     * acknowledgment, asks the repository to finish the handoff and process pending user
     * edits.
     *
     * An unsuccessful or cancelled prepared attempt clears its local pending state and
     * sends a best-effort DISCONNECT, covering a lost acknowledgment after phone
     * acceptance. Cleanup does not await delivery or send a restoration snapshot.
     * Cancellation and other uncaught transport errors propagate after cleanup.
     *
     * @param nodeId Verified phone node ID selected for this handoff.
     * @return True when the phone acknowledges and the repository finishes this handoff;
     *     false when preparation, confirmation or finalization cannot complete.
     */
    private suspend fun requestPhoneHandoff(nodeId: String): Boolean {
        val refreshFromPhone = controls.shouldRefreshFromPhone(nodeId)
        val request = controls.preparePhoneHandoff(nodeId) ?: return false
        val path = if (refreshFromPhone) WatchConnectionRequest.PHONE_STATE_PATH else WatchConnectionRequest.PHONE_PATH
        val payload = request.encode()
        var phoneSignal: PhoneSignalState? = null
        var linked = false
        try {
            val acknowledged = withTimeoutOrNull((WatchConnectionRequest.LIFETIME_MS + 1_000).milliseconds) {
                while (controls.isPhoneHandoffPending(nodeId, request.id)) {
                    val response = try {
                        withTimeoutOrNull(2_000.milliseconds) {
                            messageClient.sendRequest(nodeId, path, payload).await()
                        }
                    } catch (error: ApiException) {
                        Log.d("WearPhoneOperations", "Phone handoff attempt failed: ${error.statusCode}")
                        null
                    }
                    val status = if (refreshFromPhone) {
                        val reply = WatchResumeReply.decode(response, request.id)
                        phoneSignal = reply.signal
                        reply.status
                    } else WatchConnectionReply.decode(response)
                    when (status) {
                        WatchConnectionReply.ACCEPTED -> return@withTimeoutOrNull true
                        WatchConnectionReply.REJECTED -> return@withTimeoutOrNull false
                        WatchConnectionReply.RETRY -> delay(200.milliseconds)
                    }
                }
                false
            } == true
            linked = acknowledged && controls.finishPhoneHandoff(nodeId, request.id, phoneSignal)
            return linked
        } finally {
            if (!linked) {
                controls.cancelPhoneHandoff(nodeId, request.id)
                // The phone may have applied the snapshot before its acknowledgment was lost.
                val release = WatchControlCommand(request.id, 1, WatchControl.DISCONNECT, 0)
                try {
                    messageClient.sendRequest(nodeId, WatchControlCommand.PATH, release.encode())
                        .addOnFailureListener { Log.d("WearPhoneOperations", "Phone handoff release was not confirmed", it) }
                } catch (error: RuntimeException) {
                    Log.d("WearPhoneOperations", "Unable to release unconfirmed phone handoff", error)
                }
            }
        }
    }

    /**
     * Selects the first discovered node reporting the companion installed, then falls back
     * to the first node.
     *
     * Uses supplied discovery metadata synchronously; capability verification belongs to
     * [isCompanionInstalled] and [openAndConnect].
     *
     * @param nodes Discovered phone nodes in discovery order.
     * @return The preferred node, or null when discovery returned no nodes.
     */
    private fun isNodeConnected(nodes: List<AppHelperNodeStatus>): AppHelperNodeStatus? =
        nodes.firstOrNull { it.appInstalled } ?: nodes.firstOrNull()
}
