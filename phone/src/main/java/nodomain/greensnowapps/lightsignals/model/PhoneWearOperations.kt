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


class PhoneWearOperations
@OptIn(ExperimentalHorologistApi::class)
@Inject() constructor(
    @param: ApplicationContext private val context: Context,
    private val phoneDataLayerAppHelper: PhoneDataLayerAppHelper,
    private val messageClient: MessageClient,
    private val phoneControlRepository: PhoneControlRepository,
    private val phoneSyncStateRepository: PhoneSyncStateRepository,
) {
    private val operationMutex = Mutex()
    private val capabilityClient = Wearable.getCapabilityClient(context)

    private val _controlMessageFlow = MutableSharedFlow<String>()
    val controlMessageFlow: SharedFlow<String> = _controlMessageFlow

    private val _isLaunchingOnWear = MutableStateFlow(false)
    val isLaunchingOnWear: StateFlow<Boolean> = _isLaunchingOnWear.asStateFlow()

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

    private suspend fun requestWatchConnection(nodeId: String): Boolean {
        val requestId = UUID.randomUUID()
        var connected = false
        try {
            val ready = withTimeoutOrNull(CONNECT_SETUP_TIMEOUT_MS) {
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
            val acknowledged = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
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

    private suspend fun sendConnectionRequestWithRetry(nodeId: String, payload: ByteArray): Boolean {
        while (true) {
            val reply = try {
                withTimeoutOrNull(CONNECT_ATTEMPT_TIMEOUT_MS) {
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
                WatchConnectionReply.RETRY -> delay(CONNECT_RETRY_DELAY_MS)
            }
        }
    }

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

    @OptIn(ExperimentalHorologistApi::class)
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

    fun isInstalledOnWear(targetNode: AppHelperNodeStatus?): Boolean {
        return targetNode?.appInstalled == true
    }

    suspend fun emitMessage(message: String) {
        if (message.isEmpty()) _controlMessageFlow.emit(message)
        else _controlMessageFlow.emit(message + System.currentTimeMillis())
    }

    private companion object {
        const val CONNECT_SETUP_TIMEOUT_MS = 4_000L
        const val CONNECT_TIMEOUT_MS = WatchConnectionRequest.LIFETIME_MS + 1_000L
        const val CONNECT_ATTEMPT_TIMEOUT_MS = 2_000L
        const val CONNECT_RETRY_DELAY_MS = 200L
    }
}
