package nodomain.greensnowapps.lightsignals.model

import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import nodomain.greensnowapps.lightsignals.core.sync.ControlStateWire
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply
import nodomain.greensnowapps.lightsignals.core.sync.WatchControlCommand
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeReply
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeRequest
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Publishes legacy watch snapshots and transports acknowledged phone-control requests.
 *
 * Suspend operations run in the caller's coroutine without switching dispatchers or
 * creating an independent scope. Each RPC uses [request]'s own 2.5-second deadline;
 * legacy DataItem publication has no added deadline. Callers own peer selection and
 * command ordering.
 *
 * Preserves cancellation and reports caught ordinary exceptions through onFailure. RPC
 * timeouts return a missing transport result without invoking that callback. Legacy
 * endpoint fallback requires an actual empty reply; explicit rejection or a timeout
 * never triggers that fallback.
 *
 * @property publish Suspending path/DataMap publisher. Completion reports publication
 *     success, not phone application acceptance.
 * @property nowMillis Clock providing epoch milliseconds for each legacy delivery
 *     timestamp. RPC snapshot decoders use their own clocks.
 * @property onFailure Synchronous exception-reporting callback used by sendState and
 *     request. Must return normally for those methods to provide fallback results.
 * @property sendRequest Suspending node/path/payload transport returning reply bytes
 *     unchanged. Its default throws IllegalStateException when invoked; request
 *     reports that failure through onFailure.
 */
class WearSyncStateRepository internal constructor(
    private val publish: suspend (String, DataMap) -> Unit,
    private val nowMillis: () -> Long,
    private val onFailure: (Exception) -> Unit,
    private val sendRequest: suspend (String, String, ByteArray) -> ByteArray = { _, _, _ ->
        error("Message transport is unavailable")
    }
) {
    /**
     * Creates production Data Layer transports from the supplied Google Play services
     * clients.
     *
     * The publisher marks DataItem updates urgent and awaits putDataItem; the RPC
     * transport awaits MessageClient replies. Uses the system wall clock for legacy
     * delivery metadata and logs reported failures. Transport work starts in the calling
     * coroutine when a repository operation is invoked.
     *
     * @param dataClient Client used to publish legacy watch control DataItems.
     * @param messageClient Client used to send phone RPC requests and await reply bytes.
     */
    @Inject constructor(dataClient: DataClient, messageClient: MessageClient) : this(
        publish = { path, values ->
            val request = PutDataMapRequest.create(path).run {
                dataMap.putAll(values)
                asPutDataRequest().setUrgent()
            }
            dataClient.putDataItem(request).await()
        },
        nowMillis = System::currentTimeMillis,
        onFailure = { Log.w("WearSyncState", "Unable to synchronize controls", it) },
        sendRequest = { nodeId, path, payload -> messageClient.sendRequest(nodeId, path, payload).await() }
    )

    /**
     * Publishes a legacy watch snapshot with fresh delivery metadata.
     *
     * Suspends in the caller's coroutine until publication completes, with no added
     * timeout. Re-encodes the supplied settings using nowMillis on every invocation and
     * includes manual-link metadata only when supplied. Unchanged controls can therefore
     * carry a fresh delivery timestamp after reconnecting.
     *
     * Reports caught non-cancellation exceptions through onFailure and returns false.
     * Cancellation propagates. Success describes DataItem publication, not an
     * acknowledgment that the phone applied the settings.
     *
     * @param update Complete watch control configuration to encode for the legacy DataItem
     *     path.
     * @param manualLinkStartedAt Epoch milliseconds when the user enabled legacy phone
     *     control, or null to omit that metadata.
     * @return True when the publisher completes successfully; false after a caught
     *     exception is reported.
     */
    suspend fun sendState(update: WearControlState, manualLinkStartedAt: Long? = null): Boolean {
        return try {
            // Refresh metadata when reconnecting with unchanged controls.
            val snapshot = update.toDataMap(nowMillis())
            if (manualLinkStartedAt != null) {
                snapshot.putLong(ControlStateWire.MANUAL_LINK_STARTED_AT, manualLinkStartedAt)
            }
            publish(CONTROL_STATE_PATH, snapshot)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onFailure(error)
            false
        }
    }

    /**
     * Sends an ordered phone command and requires an exact command-acceptance reply.
     *
     * Suspends in the caller's coroutine. A command with a signal snapshot first uses
     * STATE_PATH. Only an empty reply retries PATH with the snapshot removed, retaining
     * the action, session, sequence and original command timestamp. Commands without
     * snapshots use PATH directly.
     *
     * Explicit rejection, malformed non-empty replies and missing transport results do not
     * trigger a different command attempt. Each endpoint request has its own deadline.
     * Callers own command freshness and ordering; cancellation propagates.
     *
     * @param nodeId Selected phone node ID that receives the command.
     * @param command Ordered watch command, with an optional complete per-action signal
     *     snapshot.
     * @return True only for the exact single-byte command ACCEPTED reply; false for other
     *     replies, a timeout or a reported transport failure.
     */
    internal suspend fun sendCommand(nodeId: String, command: WatchControlCommand): Boolean {
        if (command.signal != null) {
            val reply = request(nodeId, WatchControlCommand.STATE_PATH, command.encode())
            // An empty response means an older phone does not implement this endpoint.
            // Explicit rejection or transport failure must not retry as a different command.
            if (reply == null || reply.isNotEmpty()) {
                return reply?.contentEquals(byteArrayOf(WatchControlCommand.ACCEPTED)) == true
            }
        }
        return request(nodeId, WatchControlCommand.PATH, command.copy(signal = null).encode())
            ?.contentEquals(byteArrayOf(WatchControlCommand.ACCEPTED)) == true
    }

    /**
     * Requests a status-only phone session operation and decodes its compatibility reply.
     *
     * Suspends through [request] in the caller's coroutine. An actual empty reply returns
     * null to signal an unsupported endpoint. A timeout, reported transport failure or
     * unrecognized status decodes as RETRY. Cancellation propagates.
     *
     * @param nodeId Selected phone node ID that receives the session request.
     * @param path Phone session-management endpoint, such as status, release or legacy
     *     resume.
     * @param payload Encoded UUID or resume invitation expected by that endpoint.
     * @return The recognized peer status or RETRY for a missing or unrecognized result;
     *     null only when the endpoint returns empty bytes.
     */
    internal suspend fun requestLink(nodeId: String, path: String, payload: ByteArray): WatchConnectionReply? {
        val reply = request(nodeId, path, payload)
        // Older v3 phones return an empty result for the added session-management paths.
        return if (reply?.isEmpty() == true) null else WatchConnectionReply.decode(reply)
    }

    /**
     * Awaits one phone RPC reply with a 2.5-second deadline.
     *
     * Suspends in the caller's coroutine. Returns null when its own deadline expires
     * without reporting a failure. Other non-cancellation exceptions are passed to
     * onFailure before returning null. Caller or transport cancellation propagates.
     * Successful reply bytes are preserved, including an empty array.
     *
     * @param nodeId Phone node ID receiving the RPC.
     * @param path Phone-side RPC endpoint path.
     * @param payload Encoded request bytes to send unchanged.
     * @return Reply bytes, or null for this request's timeout or a reported transport
     *     exception.
     */
    private suspend fun request(nodeId: String, path: String, payload: ByteArray): ByteArray? {
        return try {
            withTimeoutOrNull(2_500.milliseconds) { sendRequest(nodeId, path, payload) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onFailure(error)
            null
        }
    }

    /**
     * Requests a fresh command session and validates the phone's returned settings.
     *
     * Suspends in the caller's coroutine using RESUME_STATE_PATH. Its accepted response
     * must include a fresh phone snapshot correlated with invitation.commandId. Missing,
     * malformed, stale or mismatched snapshot replies become RETRY.
     *
     * Only an actual empty response falls back to RESUME_PATH with the same encoded
     * invitation. That legacy endpoint can accept the session without returning settings.
     * Each request has its own deadline; a timeout or non-empty response never triggers
     * legacy fallback. Cancellation propagates.
     *
     * @param nodeId Selected phone node ID whose remembered relationship is being resumed.
     * @param invitation Resume invitation containing the stable link and new command IDs.
     * @return Decoded status and validated settings when provided. A legacy acceptance can
     *     have no settings; null means both queried endpoints returned empty replies.
     */
    internal suspend fun resumeLink(nodeId: String, invitation: WatchResumeRequest): WatchResumeReply? {
        val payload = invitation.encode()
        val reply = request(nodeId, WatchConnectionRequest.RESUME_STATE_PATH, payload)
        if (reply?.isEmpty() == true) {
            // Older phones support the original connection-only resume response.
            return requestLink(nodeId, WatchConnectionRequest.RESUME_PATH, payload)?.let { WatchResumeReply(it) }
        }
        return WatchResumeReply.decode(reply, invitation.commandId)
    }
}
