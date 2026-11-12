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
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchControl
import nodomain.greensnowapps.lightsignals.core.sync.WatchControlCommand
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalHorologistApi::class)
class WearPhoneOperations @Inject constructor(
    @ApplicationContext context: Context,
    private val wearDataLayerAppHelper: WearDataLayerAppHelper,
    private val messageClient: MessageClient,
    private val controls: WearControlRepository
) {
    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val _isLaunchingOnPhone = MutableStateFlow(false)
    val isLaunchingOnPhone = _isLaunchingOnPhone.asStateFlow()

    private val _controlMessageFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val controlMessageFlow = _controlMessageFlow.asSharedFlow()

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

    private suspend fun isCompanionInstalled(node: AppHelperNodeStatus): Boolean? {
        if (node.appInstalled) return true
        // The connected-node lookup only includes currently reachable capabilities.
        return withTimeoutOrNull(4_000) {
            capabilityClient.getCapability(
                DataLayerAppHelper.PHONE_CAPABILITY,
                CapabilityClient.FILTER_ALL,
            ).await().nodes.any { it.id == node.id }
        }
    }

    private suspend fun openAndConnect(nodeId: String): String {
        if (wearDataLayerAppHelper.startRemoteOwnApp(nodeId) != APP_HELPER_RESULT_SUCCESS) {
            return WearConstants.APP_FAILED_TO_LAUNCH_ON_PHONE
        }
        val supportsHandoff = withTimeoutOrNull(4_000) {
            capabilityClient.getCapability(
                WatchConnectionRequest.PHONE_CAPABILITY,
                CapabilityClient.FILTER_ALL,
            ).await().nodes.any { it.id == nodeId }
        } ?: return WearConstants.PHONE_CONNECTION_UNCONFIRMED
        val connected = if (supportsHandoff) requestPhoneHandoff(nodeId) else controls.enableLegacyPhoneLink()
        return if (connected || controls.phoneConnection.value) WearConstants.APP_LAUNCHED_ON_PHONE
        else WearConstants.PHONE_CONNECTION_UNCONFIRMED
    }

    private suspend fun requestPhoneHandoff(nodeId: String): Boolean {
        val request = controls.preparePhoneHandoff(nodeId) ?: return false
        val payload = request.encode()
        var linked = false
        try {
            val acknowledged = withTimeoutOrNull(WatchConnectionRequest.LIFETIME_MS + 1_000) {
                while (controls.isPhoneHandoffPending(nodeId, request.id)) {
                    val response = try {
                        withTimeoutOrNull(2_000) {
                            messageClient.sendRequest(nodeId, WatchConnectionRequest.PHONE_PATH, payload).await()
                        }
                    } catch (error: ApiException) {
                        Log.d("WearPhoneOperations", "Phone handoff attempt failed: ${error.statusCode}")
                        null
                    }
                    when (WatchConnectionReply.decode(response)) {
                        WatchConnectionReply.ACCEPTED -> return@withTimeoutOrNull true
                        WatchConnectionReply.REJECTED -> return@withTimeoutOrNull false
                        WatchConnectionReply.RETRY -> delay(200)
                    }
                }
                false
            } == true
            linked = acknowledged && controls.finishPhoneHandoff(nodeId, request.id)
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

    private fun isNodeConnected(nodes: List<AppHelperNodeStatus>): AppHelperNodeStatus? =
        nodes.firstOrNull { it.appInstalled } ?: nodes.firstOrNull()
}
