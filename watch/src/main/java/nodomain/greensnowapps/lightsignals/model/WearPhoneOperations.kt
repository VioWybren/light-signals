package nodomain.greensnowapps.lightsignals.model

import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.AppHelperResultCode.APP_HELPER_RESULT_SUCCESS
import com.google.android.horologist.data.apphelper.AppHelperNodeStatus
import com.google.android.horologist.data.apphelper.appInstalled
import com.google.android.horologist.datalayer.watch.WearDataLayerAppHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import nodomain.greensnowapps.lightsignals.WearConstants
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class WearPhoneOperations @OptIn(ExperimentalHorologistApi::class)
@Inject() constructor(
    private val wearDataLayerAppHelper: WearDataLayerAppHelper
) {

    private val _isLaunchingOnPhone = MutableStateFlow(false)
    val isLaunchingOnPhone: StateFlow<Boolean> = _isLaunchingOnPhone.asStateFlow()

    private val _controlMessageFlow = MutableSharedFlow<String>()
    val controlMessageFlow: SharedFlow<String> = _controlMessageFlow

    @OptIn(ExperimentalHorologistApi::class)
    suspend fun getAllConnectedNodes(): List<AppHelperNodeStatus> {
        return wearDataLayerAppHelper.connectedNodes()
    }

    @OptIn(ExperimentalHorologistApi::class)
    suspend fun performWearOperations(command: String) {
        val resultOfOperation: String
        val nodes = getAllConnectedNodes()

        val connectedNode = isNodeConnected(nodes)

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
                WearConstants.COMMAND_INSTALL_ON_PHONE -> {
                    installOnPhone(wearDataLayerAppHelper, connectedNode)
                }
                else -> {
                    ""
                }
            }
        }
        emitMessage(resultOfOperation)
    }

    @OptIn(ExperimentalHorologistApi::class)
    suspend fun launchAppOnPhone(
        wearDataLayerAppHelper: WearDataLayerAppHelper,
        targetNode: AppHelperNodeStatus,
    ): String {
        val strResult = withContext(Dispatchers.IO) {
            try {
                _isLaunchingOnPhone.value = true
                val launchResult = wearDataLayerAppHelper.startRemoteOwnApp(targetNode.id)
                if (launchResult == APP_HELPER_RESULT_SUCCESS) {
                    WearConstants.APP_LAUNCHED_ON_PHONE
                } else {
                    WearConstants.APP_FAILED_TO_LAUNCH_ON_PHONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                WearConstants.APP_FAILED_TO_LAUNCH_ON_PHONE
            }
        }
        _isLaunchingOnPhone.value = false
        return strResult
    }

    @OptIn(ExperimentalHorologistApi::class)
    suspend fun installOnPhone(
        wearDataLayerAppHelper: WearDataLayerAppHelper,
        targetNode: AppHelperNodeStatus?
    ): String {
        val resultOfInstall = wearDataLayerAppHelper.installOnNode(targetNode?.id.toString())
        return if (resultOfInstall == APP_HELPER_RESULT_SUCCESS) {
            WearConstants.INSTALLATION_HAS_BEEN_STARTED_ON_PHONE
        } else {
            WearConstants.INSTALLATION_ON_PHONE_FAILED
        }
    }

    @OptIn(ExperimentalHorologistApi::class)
    fun isNodeConnected(nodesList: List<AppHelperNodeStatus>): AppHelperNodeStatus? {
        val targetRole = WearConstants.TARGET_ROLE_PHONE
        val targetRole1 = WearConstants.TARGET_ROLE_TABLET

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

}