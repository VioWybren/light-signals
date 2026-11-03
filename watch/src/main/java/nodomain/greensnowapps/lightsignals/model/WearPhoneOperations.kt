package nodomain.greensnowapps.lightsignals.model

import android.util.Log
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.AppHelperResultCode.APP_HELPER_RESULT_SUCCESS
import com.google.android.horologist.data.apphelper.AppHelperNodeStatus
import com.google.android.horologist.data.apphelper.appInstalled
import com.google.android.horologist.datalayer.watch.WearDataLayerAppHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import nodomain.greensnowapps.lightsignals.WearConstants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalHorologistApi::class)
class WearPhoneOperations @Inject constructor(
    private val wearDataLayerAppHelper: WearDataLayerAppHelper
) {
    private val _isLaunchingOnPhone = MutableStateFlow(false)
    val isLaunchingOnPhone = _isLaunchingOnPhone.asStateFlow()

    private val _controlMessageFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val controlMessageFlow = _controlMessageFlow.asSharedFlow()

    suspend fun performWearOperations(command: String) {
        if (_isLaunchingOnPhone.value) return
        _isLaunchingOnPhone.value = true
        try {
            val node = isNodeConnected(wearDataLayerAppHelper.connectedNodes())
            val message = when {
                node == null -> WearConstants.NO_CONNECTED_PHONES
                command == WearConstants.COMMAND_START_ON_PHONE -> {
                    if (!node.appInstalled) {
                        WearConstants.APP_IS_NOT_INSTALLED_ON_PHONE
                    } else if (wearDataLayerAppHelper.startRemoteOwnApp(node.id) == APP_HELPER_RESULT_SUCCESS) {
                        WearConstants.APP_LAUNCHED_ON_PHONE
                    } else {
                        WearConstants.APP_FAILED_TO_LAUNCH_ON_PHONE
                    }
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

    private fun isNodeConnected(nodes: List<AppHelperNodeStatus>): AppHelperNodeStatus? =
        nodes.firstOrNull { it.appInstalled } ?: nodes.firstOrNull()
}
