package nodomain.greensnowapps.lightsignals.model

import android.util.Log
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.AppHelperResultCode.APP_HELPER_RESULT_SUCCESS
import com.google.android.horologist.data.WearDataLayerRegistry
import com.google.android.horologist.data.apphelper.AppHelperNodeStatus
import com.google.android.horologist.data.apphelper.appInstalled
import com.google.android.horologist.datalayer.watch.WearDataLayerAppHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import nodomain.greensnowapps.lightsignals.WearConstants
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG =  "WearPhoneOperations"

@Singleton
class WearPhoneOperations @OptIn(ExperimentalHorologistApi::class)
@Inject() constructor(
    private val wearDataLayerRegistry: WearDataLayerRegistry,
    private val wearDataLayerAppHelper: WearDataLayerAppHelper
) {

    private val _isLaunchingOnPhone = MutableStateFlow(false)
    val isLaunchingOnPhone: StateFlow<Boolean> = _isLaunchingOnPhone.asStateFlow()

    // Flow for control messages / ACKs
    private val _controlMessageFlow = MutableSharedFlow<String>()
    val controlMessageFlow: SharedFlow<String> = _controlMessageFlow

//    //    Flow<Set<Node>>
//    @OptIn(ExperimentalHorologistApi::class)
//    val installedPhoneNodes = wearDataLayerAppHelper.connectedAndInstalledNodes
//
////    private val repoScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
//
//
////    private val localJob = SupervisorJob()
////    private val repoScope = CoroutineScope(localJob + Dispatchers.Default)
//
//    /**
//     * A simple boolean you can use to enable/disable UI.
//     */
//    val isPhoneAppAvailable: StateFlow<Boolean> =
//        installedPhoneNodes
//            .map { it.isNotEmpty() }
//            .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), false)

//    fun checkNodes() {
//        repoScope.launch {
//            installedPhoneNodes.collect { set ->
//                Log.d(TAG,"set of Nodes: $set")
//            }
//        }
//    }


    @OptIn(ExperimentalHorologistApi::class)
    suspend fun getAllConnectedNodes(): List<AppHelperNodeStatus> {
        return wearDataLayerAppHelper.connectedNodes()
    }

    @OptIn(ExperimentalHorologistApi::class)
    suspend fun performWearOperations(command: String) {
        val resultOfOperation: String
        // Get all connected nodes (devices)
        val nodes = getAllConnectedNodes()
        Log.d(TAG,"List of nodes: $nodes")

        val connectedNode = isNodeConnected(nodes)
        Log.d(TAG,"connectedNode: $connectedNode")

        if (connectedNode == null) {
            resultOfOperation = WearConstants.NO_CONNECTED_PHONES
        } else {
            resultOfOperation = when (command) {
                WearConstants.COMMAND_START_ON_PHONE -> {
                    if (!isInstalledOnWear(connectedNode)) {
                        WearConstants.APP_IS_NOT_INSTALLED_ON_PHONE
                    } else {
                        launchAppOnPhone(
                            wearDataLayerAppHelper,
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
                WearConstants.COMMAND_INSTALL_ON_PHONE -> {
                    installOnPhone(wearDataLayerAppHelper, connectedNode)
                }
                "testCommandToPhone" -> {
                    sendCommandToWatch(
                        wearDataLayerRegistry,
                        connectedNode.id,
                        "/command/startTracking",
                        "test_command"
                    )
                }
                else -> {
                    ""
                }
            }
        }
        emitMessage(resultOfOperation)
    }

    //    /**
    //     * Tries to launch the companion app on the target node (either Wear or Phone).
    //     * If the app isn’t installed, it triggers an installation request.
    //     */
    @OptIn(ExperimentalHorologistApi::class)
    suspend fun launchAppOnPhone(
        wearDataLayerAppHelper: WearDataLayerAppHelper,
        targetNode: AppHelperNodeStatus,
    ): String {
        // Launch in a coroutine scope
        val strResult = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG,"App is installed on ${targetNode.displayName}. Launching the app...")
                _isLaunchingOnPhone.value = true
                // Launch the app remotely. This call might return a result or throw if it fails.
                val launchResult = wearDataLayerAppHelper.startRemoteOwnApp(targetNode.id)
                Log.d(TAG,"Launching the app on node with result: $launchResult")
                if (launchResult == APP_HELPER_RESULT_SUCCESS) {
                    WearConstants.APP_LAUNCHED_ON_PHONE
                } else {
                    WearConstants.APP_FAILED_TO_LAUNCH_ON_PHONE
                }
            } catch (e: Exception) { // Handle exceptions appropriately (network issues, etc.)
                e.printStackTrace()
                WearConstants.APP_FAILED_TO_LAUNCH_ON_PHONE
            }
        }
        _isLaunchingOnPhone.value = false
        return strResult
    }

    // If the app is not installed, trigger the install flow.
    @OptIn(ExperimentalHorologistApi::class)
    suspend fun installOnPhone(
        wearDataLayerAppHelper: WearDataLayerAppHelper,
        targetNode: AppHelperNodeStatus?
    ): String {
        val resultOfInstall = wearDataLayerAppHelper.installOnNode(targetNode?.id.toString())
        Log.d(TAG,"resultOfInstall $resultOfInstall")
        return if (resultOfInstall == APP_HELPER_RESULT_SUCCESS) {
            WearConstants.INSTALLATION_HAS_BEEN_STARTED_ON_PHONE
        } else {
            WearConstants.INSTALLATION_ON_PHONE_FAILED
        }
    }

    @OptIn(ExperimentalHorologistApi::class)
    fun isNodeConnected(nodesList: List<AppHelperNodeStatus>): AppHelperNodeStatus? {
        val targetRole = WearConstants.TARGET_ROLE_PHONE // Find the target node by its role (e.g., PHONE or WATCH)
        val targetRole1 = WearConstants.TARGET_ROLE_TABLET // Find the target node by its role (e.g., PHONE or WATCH)

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
        Log.d("MY_TAG", "Sent command $commandPath to node $targetNodeId with result: $result")
        return result.toString()
    }

    // Emit a message to Flow (e.g., in PhoneWearListenerService)
    suspend fun emitMessage(message: String) {
        Log.d(TAG,"emitMessage($message)")
        if (message.isEmpty()) _controlMessageFlow.emit(message)
        else _controlMessageFlow.emit(message + System.currentTimeMillis())
    }

    fun clearAll() {
        // cancel everything this repo ever launched
//        localJob.cancelChildren()
//        repoScope.cancel()

        // Optional: If you need an explicit stop exposed:
        // Cancel children so the root appScope is still reusable later
//        appScope.coroutineContext[Job]?.cancelChildren()
    }

}