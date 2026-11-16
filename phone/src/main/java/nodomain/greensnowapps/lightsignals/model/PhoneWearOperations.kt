package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.AppHelperResultCode.APP_HELPER_RESULT_SUCCESS
import com.google.android.horologist.data.apphelper.AppHelperNodeStatus
import com.google.android.horologist.data.apphelper.appInstalled
import com.google.android.horologist.datalayer.phone.PhoneDataLayerAppHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import nodomain.greensnowapps.lightsignals.APP_FAILED_TO_LAUNCH_ON_WATCH
import nodomain.greensnowapps.lightsignals.APP_IS_NOT_INSTALLED_ON_WATCH
import nodomain.greensnowapps.lightsignals.COMMAND_INSTALL_ON_WATCH
import nodomain.greensnowapps.lightsignals.COMMAND_START_ON_WATCH
import nodomain.greensnowapps.lightsignals.INSTALLATION_ON_WATCH_FAILED
import nodomain.greensnowapps.lightsignals.INSTALLATION_ON_WATCH_STARTED
import nodomain.greensnowapps.lightsignals.NO_CONNECTED_WATCHES
import nodomain.greensnowapps.lightsignals.TARGET_ROLE_WATCH
import nodomain.greensnowapps.lightsignals.TARGET_ROLE_WEAR
import nodomain.greensnowapps.lightsignals.WATCH_CONNECTION_UNCONFIRMED
import nodomain.greensnowapps.lightsignals.WEARABLE_API_UNAVAILABLE
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds


/**
 * Discovers, opens and installs the watch companion and initiates phone-led handoffs.
 *
 * Exposes operation progress and message events for the phone UI. Suspend entry points await
 * Wearable tasks without blocking; UI callers use viewModelScope on Main.
 *
 * @property context Application context used to access platform services without retaining an
 *     Activity.
 * @property phoneDataLayerAppHelper Horologist helper for phone-to-watch companion operations.
 * @property messageClient Wearable client used for request/reply messaging.
 * @property phoneControlRepository Owner of the phone signal settings and torch-service
 *     requests.
 * @property phoneSyncStateRepository Phone-side transport and authenticated watch-session
 *     repository.
 */
class PhoneWearOperations
@OptIn(ExperimentalHorologistApi::class)
@Inject() constructor(
    @param: ApplicationContext private val context: Context,
    private val phoneDataLayerAppHelper: PhoneDataLayerAppHelper,
    private val messageClient: MessageClient,
    private val phoneControlRepository: PhoneControlRepository,
    private val phoneSyncStateRepository: PhoneSyncStateRepository,
) {
    /**
     * Serializes operations submitted through startWearOperations, including suspension during
     * discovery and launch.
     */
    private val operationMutex = Mutex()
    /**
     * Application-context Wearable client for API availability and watch handoff-capability
     * queries.
     */
    private val capabilityClient = Wearable.getCapabilityClient(context)

    /**
     * Non-replaying phone feedback events; emission can suspend for active collectors and does
     * not retain events without collectors.
     */
    private val _controlMessageFlow = MutableSharedFlow<String>()
    /**
     * Companion-operation message events translated into user feedback by the UI.
     */
    val controlMessageFlow: SharedFlow<String> = _controlMessageFlow

    /**
     * Mutable progress flag for remote app startup and handoff, reset in launchAppOnWatch
     * finally.
     */
    private val _isLaunchingOnWear = MutableStateFlow(false)
    /**
     * Read-only progress state while opening the watch companion and confirming its
     * connection.
     */
    val isLaunchingOnWear: StateFlow<Boolean> = _isLaunchingOnWear.asStateFlow()

    /**
     * Serializes watch launch and installation requests after checking Wearable API
     * availability.
     *
     * Call on Main. Suspends while waiting for the operation mutex and companion tasks. Emits
     * WEARABLE_API_UNAVAILABLE when neither availability check succeeds; coroutine
     * cancellation propagates to the caller.
     *
     * @param command COMMAND_START_ON_WATCH to open and connect the companion, or
     *     COMMAND_INSTALL_ON_WATCH to request installation.
     */
    @OptIn(ExperimentalHorologistApi::class)
    suspend fun startWearOperations(command: String) = operationMutex.withLock {
        if (phoneDataLayerAppHelper.isAvailable()) {
            performWearOperations(command)
        } else {
            if (isWearableApiActuallyAvailable(context)) {
                performWearOperations(command)
            } else {
                emitMessage(WEARABLE_API_UNAVAILABLE)
            }
        }
    }

    /**
     * Discovers a watch, dispatches the requested companion operation and emits its feedback.
     *
     * Call on Main. Suspends during discovery, launch or installation and feedback emission.
     * This function does not acquire the operation mutex; use [startWearOperations] to
     * serialize requests. Reports a missing watch or companion app through
     * [controlMessageFlow]. Unknown commands produce no operation feedback once a node is
     * selected.
     *
     * @param command COMMAND_START_ON_WATCH or COMMAND_INSTALL_ON_WATCH.
     */
    @OptIn(ExperimentalHorologistApi::class)
    suspend fun performWearOperations(command: String) {
        val resultOfOperation: String?
        val nodes = phoneDataLayerAppHelper.connectedNodes()
        val connectedNode = isNodeConnected(nodes)
        if (connectedNode == null) {
            resultOfOperation = NO_CONNECTED_WATCHES
        } else {
            resultOfOperation = when (command) {
                COMMAND_START_ON_WATCH -> {
                    if (!isInstalledOnWear(connectedNode)) {
                        APP_IS_NOT_INSTALLED_ON_WATCH
                    } else {
                        launchAppOnWatch(
                            phoneDataLayerAppHelper,
                            connectedNode,
                        )
                    }
                }
                COMMAND_INSTALL_ON_WATCH -> {
                    installOnWatch(phoneDataLayerAppHelper, connectedNode)
                }
                else -> null
            }
        }
        if (resultOfOperation != null) emitMessage(resultOfOperation)
    }

    /**
     * Checks Google Play services availability, then queries the Wearable capability API.
     *
     * Suspends while awaiting the capability query in the caller's coroutine. An empty
     * capability map still counts as success; this check does not require a connected watch.
     * Returns false for availability or query failures and propagates coroutine cancellation.
     *
     * @param context Context used to check Google Play services availability.
     * @return True when Google Play services is available and the capability query succeeds.
     */
    suspend fun isWearableApiActuallyAvailable(context: Context): Boolean {
        val gpa = GoogleApiAvailability.getInstance()
        val playServicesStatus = gpa.isGooglePlayServicesAvailable(context)
        if (playServicesStatus != ConnectionResult.SUCCESS) {
            return false
        }

        return try {
            capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE).await()
            true
        } catch (e: ApiException) {

            when (e.statusCode) {
                ConnectionResult.API_UNAVAILABLE -> false
                CommonStatusCodes.API_NOT_CONNECTED -> return false
                else -> return false
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens the watch companion and negotiates a handoff of the phone's complete signal
     * settings.
     *
     * Call on Main. Suspends while launching the app on IO and confirming its connection.
     * Keeps [isLaunchingOnWear] true for the attempt and resets it in finally, including on
     * cancellation. Returns a feedback identifier for the caller to emit; this function does
     * not emit the result itself.
     *
     * @param phoneDataLayerAppHelper Horologist helper used to open this app on the watch.
     * @param targetNode Selected watch node on which to open the installed companion.
     * @return Null after a confirmed handoff, APP_FAILED_TO_LAUNCH_ON_WATCH on launch failure,
     *     or WATCH_CONNECTION_UNCONFIRMED when the handoff is not confirmed.
     */
    @OptIn(ExperimentalHorologistApi::class)
    suspend fun launchAppOnWatch(
        phoneDataLayerAppHelper: PhoneDataLayerAppHelper,
        targetNode: AppHelperNodeStatus,
    ): String? {
        _isLaunchingOnWear.value = true
        return try {
            val result = withContext(Dispatchers.IO) {
                phoneDataLayerAppHelper.startRemoteOwnApp(targetNode.id)
            }
            when {
                result != APP_HELPER_RESULT_SUCCESS -> APP_FAILED_TO_LAUNCH_ON_WATCH
                requestWatchConnection(targetNode.id) -> null
                else -> WATCH_CONNECTION_UNCONFIRMED
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("PhoneWearOperations", "Unable to launch watch app", error)
            APP_FAILED_TO_LAUNCH_ON_WATCH
        } finally {
            _isLaunchingOnWear.value = false
        }
    }

    /**
     * Verifies watch handoff support and sends the phone's current settings in a fresh
     * session.
     *
     * Call on Main. Suspends during capability lookup, RPC listener registration and bounded
     * handoff retries. Enables the legacy control route when the watch lacks the handoff
     * capability, but reports that attempt as unconfirmed. If the reply is lost, also checks
     * whether the session was already confirmed by an early watch command. Cancels the pending
     * handoff on failure or cancellation; coroutine cancellation propagates to the caller.
     *
     * @param nodeId Wearable node ID of the watch selected for the phone-led handoff.
     * @return True when this handoff is confirmed; false when setup or confirmation fails.
     */
    private suspend fun requestWatchConnection(nodeId: String): Boolean {
        val requestId = UUID.randomUUID()
        var connected = false
        try {
            val ready = withTimeoutOrNull(CONNECT_SETUP_TIMEOUT_MS.milliseconds) {
                val supportsConnection = capabilityClient.getCapability(
                    WatchConnectionRequest.CAPABILITY,
                    CapabilityClient.FILTER_ALL,
                ).await().nodes.any { it.id == nodeId }
                if (!supportsConnection) {
                    Log.d("PhoneWearOperations", "Watch did not advertise automatic connection")
                    phoneSyncStateRepository.allowLegacyWatch(nodeId)
                    return@withTimeoutOrNull false
                }
                phoneSyncStateRepository.beginWatchConnection(nodeId, requestId)
            } == true
            if (!ready) {
                Log.d("PhoneWearOperations", "Watch connection setup did not complete")
                return false
            }

            val request = WatchConnectionRequest.create(phoneControlRepository.connectionSignal(), id = requestId)
            val acknowledged = withTimeoutOrNull(CONNECT_TIMEOUT_MS.milliseconds) {
                sendConnectionRequestWithRetry(nodeId, request.encode())
            } == true
            connected = if (acknowledged) {
                phoneSyncStateRepository.confirmWatchConnection(nodeId, requestId)
            } else {
                phoneSyncStateRepository.isWatchConnectionConfirmed(nodeId, requestId)
            }
            Log.d("PhoneWearOperations", "Watch connection confirmed: $connected")
            return connected
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("PhoneWearOperations", "Unable to connect to watch", error)
            return false
        } finally {
            if (!connected) phoneSyncStateRepository.cancelWatchConnection(requestId)
        }
    }

    /**
     * Retries the same encoded handoff until the watch explicitly accepts or rejects it.
     *
     * Suspends in the caller's coroutine. Bounds each RPC attempt and delays before retrying
     * after an API failure, timeout, RETRY status or missing or unrecognized reply. This
     * function has no overall deadline; [requestWatchConnection] supplies one. Cancellation
     * and non-API transport exceptions propagate to the caller.
     *
     * @param nodeId Wearable node ID that receives each handoff attempt.
     * @param payload Encoded invitation reused across retries, preserving its ID and
     *     timestamp.
     * @return True for an ACCEPTED reply, or false for an explicit REJECTED reply.
     */
    private suspend fun sendConnectionRequestWithRetry(nodeId: String, payload: ByteArray): Boolean {
        while (true) {
            val reply = try {
                withTimeoutOrNull(CONNECT_ATTEMPT_TIMEOUT_MS.milliseconds) {
                    messageClient.sendRequest(nodeId, WatchConnectionRequest.PATH, payload).await()
                }
            } catch (error: ApiException) {
                // The watch may advertise support before its foreground receiver is ready.
                Log.d("PhoneWearOperations", "Watch connection attempt failed: ${error.statusCode}")
                null
            }
            when (WatchConnectionReply.decode(reply)) {
                WatchConnectionReply.ACCEPTED -> return true
                WatchConnectionReply.REJECTED -> return false
                WatchConnectionReply.RETRY -> delay(CONNECT_RETRY_DELAY_MS.milliseconds)
            }
        }
    }

    /**
     * Requests companion installation on the selected watch and returns operation feedback.
     *
     * Suspends while awaiting the Horologist request in the caller's coroutine. A successful
     * result means installation was requested, not that the app is installed. A missing node
     * returns failure without sending a request; transport exceptions propagate to the caller.
     *
     * @param phoneDataLayerAppHelper Horologist helper used to request companion installation.
     * @param targetNode Selected watch node, or null when discovery found no target.
     * @return INSTALLATION_ON_WATCH_STARTED when the request succeeds, or
     *     INSTALLATION_ON_WATCH_FAILED when the node is absent or the helper reports failure.
     */
    @OptIn(ExperimentalHorologistApi::class)
    suspend fun installOnWatch(
        phoneDataLayerAppHelper: PhoneDataLayerAppHelper,
        targetNode: AppHelperNodeStatus?
    ): String {
        val node = targetNode ?: return INSTALLATION_ON_WATCH_FAILED
        val resultOfInstall = phoneDataLayerAppHelper.installOnNode(node.id)
        return if (resultOfInstall == APP_HELPER_RESULT_SUCCESS) {
            INSTALLATION_ON_WATCH_STARTED
        } else {
            INSTALLATION_ON_WATCH_FAILED
        }
    }

    /**
     * Selects a candidate watch from the supplied discovery results.
     *
     * Prefers the first display name containing Watch or Wear, ignoring case. Next checks each
     * node's string representation, then falls back to the first node. This synchronous
     * selection does not query connectivity or verify that the companion is installed.
     *
     * @param nodesList Discovered companion nodes, in discovery order.
     * @return The preferred candidate, or null when the list is empty.
     */
    fun isNodeConnected(nodesList: List<AppHelperNodeStatus>): AppHelperNodeStatus? {
        val targetRole = TARGET_ROLE_WATCH
        val targetRole1 = TARGET_ROLE_WEAR

        if (nodesList.isEmpty()) {
            return null
        }
        for (node in nodesList) {
            if (node.displayName.contains(targetRole, ignoreCase = true)
                || node.displayName.contains(targetRole1, ignoreCase = true)
            )
                return node
        }
        for (node in nodesList) {
            if (node.toString().contains(targetRole, ignoreCase = true)
                || node.toString().contains(targetRole1, ignoreCase = true)
            ) {
                return node
            }
        }
        val targetNode = nodesList.first()

        return targetNode
    }

    /**
     * Reads the companion installation status already reported for a discovered node.
     *
     * This synchronous check performs no fresh discovery or connectivity request.
     *
     * @param targetNode Discovered watch node to inspect, or null when no target was found.
     * @return True when the node reports this app installed; false for an absent node or app.
     */
    fun isInstalledOnWear(targetNode: AppHelperNodeStatus?): Boolean {
        return targetNode?.appInstalled == true
    }

    /**
     * Emits companion-operation feedback to active [controlMessageFlow] collectors.
     *
     * Suspends in the caller's coroutine while emitting. Appends epoch milliseconds to
     * non-empty identifiers so repeated results can trigger UI feedback; empty messages remain
     * unchanged. The flow does not replay messages to later collectors.
     *
     * @param message Companion feedback identifier, or an empty string to emit empty feedback.
     */
    suspend fun emitMessage(message: String) {
        if (message.isEmpty()) _controlMessageFlow.emit(message)
        else _controlMessageFlow.emit(message + System.currentTimeMillis())
    }

    /**
     * Defines watch setup, handoff and retry deadlines.
     */
    private companion object {
        /**
         * Maximum peer capability/discovery wait before a handoff, in milliseconds.
         */
        const val CONNECT_SETUP_TIMEOUT_MS = 4_000L
        /**
         * Overall handoff acknowledgment window, in milliseconds.
         */
        const val CONNECT_TIMEOUT_MS = WatchConnectionRequest.LIFETIME_MS + 1_000L
        /**
         * Maximum wait for one handoff request attempt, in milliseconds.
         */
        const val CONNECT_ATTEMPT_TIMEOUT_MS = 2_000L
        /**
         * Delay between unconfirmed handoff attempts, in milliseconds.
         */
        const val CONNECT_RETRY_DELAY_MS = 200L
    }
}
