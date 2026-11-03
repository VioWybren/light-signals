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
import com.google.android.horologist.data.apphelper.AppHelperNodeStatus
import com.google.android.horologist.data.apphelper.appInstalled
import com.google.android.horologist.datalayer.phone.PhoneDataLayerAppHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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


class PhoneWearOperations
@OptIn(ExperimentalHorologistApi::class)
@Inject() constructor(
    @param: ApplicationContext private val context: Context,
    private val phoneDataLayerAppHelper: PhoneDataLayerAppHelper
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
        val resultOfOperation: String
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
                else -> {
                    ""
                }
            }
        }
        emitMessage(resultOfOperation)
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
    ): String {
        _isLaunchingOnWear.value = true
        return try {
            withContext(Dispatchers.IO) {
                val result = phoneDataLayerAppHelper.startRemoteOwnApp(targetNode.id)
                if (result == APP_HELPER_RESULT_SUCCESS) APP_LAUNCHED_ON_WATCH
                else APP_FAILED_TO_LAUNCH_ON_WATCH
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

}