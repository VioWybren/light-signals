package nodomain.greensnowapps.lightsignals.model

import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class WearSyncStateRepository internal constructor(
    private val publish: suspend (String, DataMap) -> Unit,
    private val nowMillis: () -> Long,
    private val onFailure: (Exception) -> Unit
) {
    @Inject constructor(dataClient: DataClient) : this(
        publish = { path, values ->
            val request = PutDataMapRequest.create(path).run {
                dataMap.putAll(values)
                asPutDataRequest().setUrgent()
            }
            dataClient.putDataItem(request).await()
        },
        nowMillis = System::currentTimeMillis,
        onFailure = { Log.w("WearSyncState", "Unable to synchronize controls", it) }
    )

    suspend fun sendState(update: WearControlState): Boolean {
        return try {
            // Refresh metadata when reconnecting with unchanged controls.
            publish(CONTROL_STATE_PATH, update.toDataMap(nowMillis()))
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onFailure(error)
            false
        }
    }
}
