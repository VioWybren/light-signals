package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.AppHelperResultCode.APP_HELPER_RESULT_SUCCESS
import com.google.android.horologist.data.WearDataLayerRegistry
import com.google.android.horologist.data.apphelper.AppHelperNodeStatus
import com.google.android.horologist.data.apphelper.appInstalled
import com.google.android.horologist.datalayer.phone.PhoneDataLayerAppHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import nodomain.greensnowapps.lightsignals.APP_FAILED_TO_LAUNCH_ON_WATCH
import nodomain.greensnowapps.lightsignals.APP_IS_NOT_INSTALLED_ON_WATCH
import nodomain.greensnowapps.lightsignals.APP_LAUNCHED_ON_WATCH
import nodomain.greensnowapps.lightsignals.COMMAND_INSTALL_ON_WATCH
import nodomain.greensnowapps.lightsignals.COMMAND_START_ON_WATCH
import nodomain.greensnowapps.lightsignals.INSTALLATION_ON_WATCH_FAILED
import nodomain.greensnowapps.lightsignals.INSTALLATION_ON_WATCH_STARTED
import nodomain.greensnowapps.lightsignals.NO_CONNECTED_WATCHES
import nodomain.greensnowapps.lightsignals.TARGET_ROLE_WATCH
import nodomain.greensnowapps.lightsignals.TARGET_ROLE_WEAR
import nodomain.greensnowapps.lightsignals.WEARABLE_API_UNAVAILABLE
import javax.inject.Inject

private const val TAG = ""

class PhoneWearOperations
@OptIn(ExperimentalHorologistApi::class)
@Inject() constructor(
    @param: ApplicationContext private val context: Context,
    private val wearDataLayerRegistry: WearDataLayerRegistry,
    private val phoneDataLayerAppHelper: PhoneDataLayerAppHelper
)
{
    private val capabilityClient = Wearable.getCapabilityClient(context)

    // Flow for control messages / ACKs
    private val _controlMessageFlow = MutableSharedFlow<String>()
    val controlMessageFlow: SharedFlow<String> = _controlMessageFlow

    // Flow for showing a progress indicator
    private val _isLaunchingOnWear = MutableStateFlow(false)
    val isLaunchingOnWear: StateFlow<Boolean> = _isLaunchingOnWear.asStateFlow()

    @OptIn(ExperimentalHorologistApi::class)
    suspend fun startWearOperations(command: String) {
        Log.d(TAG,"phoneDataLayerAppHelper.isAvailable(): ${phoneDataLayerAppHelper.isAvailable()} ")
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
        val resultOfOperation: String
        // Get all connected nodes (devices)
        val nodes = phoneDataLayerAppHelper.connectedNodes()
        Log.d(TAG,"List of nodes: $nodes ")
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
                "stop" -> {
                    sendCommandToWatch(
                        wearDataLayerRegistry,
                        connectedNode.id,
                        "/command/stopTracking",
                        "test_command"
                    )
                }
                COMMAND_INSTALL_ON_WATCH -> {
                    installOnWatch(phoneDataLayerAppHelper, connectedNode)
                }
                else -> {
                    ""
                }
            }
        }
        emitMessage(resultOfOperation)
    }

    suspend fun isWearableApiActuallyAvailable(context: Context): Boolean {
        // Quick check for Google Play Services presence
        val gpa = GoogleApiAvailability.getInstance()
        val playServicesStatus = gpa.isGooglePlayServicesAvailable(context)
        Log.d(TAG,"GooglePlayServices code= $playServicesStatus)")
        Log.d(TAG,"is GooglePlayServices available: ${playServicesStatus != ConnectionResult.SUCCESS})")
        if (playServicesStatus != ConnectionResult.SUCCESS) {
            return false
        }

        return try {
            // This suspend await() is provided by kotlinx-coroutines-play-services
            val capabilities =
                capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE).await()
            true      // if the Task completes successfully -> available
        } catch (e: ApiException) {
            // Log the numeric code for debugging
            Log.d(TAG, "Wearable ApiException statusCode=${e.statusCode}")

            // Optional: explicit checks for known codes
            when (e.statusCode) {
                ConnectionResult.API_UNAVAILABLE -> false // ConnectionResult defines API_UNAVAILABLE (int 17)
                CommonStatusCodes.API_NOT_CONNECTED -> return false  // client not connected CommonStatusCodes.API_NOT_AVAILABLE // older / deprecated constant (if present)
                else -> return false  // Treat other ApiException codes as unavailable
            }
        } catch (t: Throwable) {
            // network / cancellation / other failures -> treat as unavailable
            false
        }
    }

    //    /**
    //     * Tries to launch the companion app on the target node (either Wear or Phone).
    //     * If the app isn’t installed, it triggers an installation request.
    //     *
    //     * @param dataLayerClient The instance of Horologist DataLayer client.
    //     * @param targetRole The role of the target device (e.g., Node.Role.WEAR or Node.Role.PHONE).
    //     * @param packageName The package name of the app to launch or install.
    //     */
    @OptIn(ExperimentalHorologistApi::class)
    suspend fun launchAppOnWatch(
        phoneDataLayerAppHelper: PhoneDataLayerAppHelper,
        targetNode: AppHelperNodeStatus,
    ): String {
        // Launch in a coroutine scope
        val strResult = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG,"App is installed on ${targetNode.displayName}")
                _isLaunchingOnWear.value = true
                // Launch the app remotely. This call might return a result or throw if it fails.
                val launchResult = phoneDataLayerAppHelper.startRemoteOwnApp(targetNode.id)
                Log.d(TAG,"Launching the app on node with result: $launchResult")
                if (launchResult == APP_HELPER_RESULT_SUCCESS) {
                    APP_LAUNCHED_ON_WATCH
                } else {
                    APP_FAILED_TO_LAUNCH_ON_WATCH
                }
            } catch (e: Exception) { // Handle exceptions appropriately (network issues, etc.)
                e.printStackTrace()
                APP_FAILED_TO_LAUNCH_ON_WATCH
            }
        }
        _isLaunchingOnWear.value = false
        return strResult
    }

    // If the app is not installed, trigger the install flow.
    @OptIn(ExperimentalHorologistApi::class)
    suspend fun installOnWatch(
        phoneDataLayerAppHelper: PhoneDataLayerAppHelper,
        targetNode: AppHelperNodeStatus?
    ): String {
        val resultOfInstall = phoneDataLayerAppHelper.installOnNode(targetNode?.id.toString())
        Log.d(TAG,"resultOfInstall $resultOfInstall")
        return if (resultOfInstall == APP_HELPER_RESULT_SUCCESS) {
            INSTALLATION_ON_WATCH_STARTED
        } else {
            INSTALLATION_ON_WATCH_FAILED
        }
    }

//    @OptIn(ExperimentalHorologistApi::class)
//    suspend fun isNodeConnected(nodesList: List<AppHelperNodeStatus>): AppHelperNodeStatus? {
//        val targetRole = TARGET_ROLE_WATCH // Find the target node by its role (e.g., PHONE or WATCH)
//        val targetRole1 = TARGET_ROLE_WEAR
//        val targetNode =
//            nodesList.firstOrNull {
//                it.displayName.contains(
//                    targetRole,
//                    ignoreCase = true
//                ) || it.displayName.contains(targetRole1, ignoreCase = true)
//            }
//        if (targetNode == null) {
//            Log.d(TAG,"No connected node found with role $targetRole or $targetRole1")
//            return null
//        }
//        return targetNode
//    }

    @OptIn(ExperimentalHorologistApi::class)
    fun isNodeConnected(nodesList: List<AppHelperNodeStatus>): AppHelperNodeStatus? {
        val targetRole = TARGET_ROLE_WATCH// Find the target node by its role (e.g., PHONE or WATCH)
        val targetRole1 = TARGET_ROLE_WEAR // Find the target node by its role (e.g., TABLET or WEAR)

        if (nodesList.isEmpty()) {
            Log.d(TAG,"list of connected nodes is empty")
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

    // Check if the app is installed on the target node.
    fun isInstalledOnWear(targetNode: AppHelperNodeStatus?): Boolean {
        return targetNode?.appInstalled == true
    }


    @OptIn(ExperimentalHorologistApi::class)
    suspend fun sendCommandToWatch(
        registry: WearDataLayerRegistry,
        targetNodeId: String,
        commandPath: String,
        payload: String? = null
    ): String {
        // Convert payload to ByteArray if not null.
        val data = payload?.toByteArray(Charsets.UTF_8)
        // Send a message to the target node.
        val result = registry.messageClient.sendMessage(targetNodeId, commandPath, data).await()
        Log.d(TAG,"Sent command $commandPath to node $targetNodeId with result: $result")
        return result.toString()
    }

    // Emit a message to Flow (e.g., in PhoneWearListenerService)
    suspend fun emitMessage(message: String) {
        Log.d(TAG,"emitMessage($message)")
        if (message.isEmpty()) _controlMessageFlow.emit(message)
        else _controlMessageFlow.emit(message + System.currentTimeMillis())
    }

}