package nodomain.greensnowapps.lightsignals.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull

/**
 * Builds a cold flow of distinct watch settings while initialized, foreground and explicitly
 * connected.
 *
 * Collection suspends in the caller's coroutine; disabled gates stop state publication.
 *
 * @param states Flow of complete watch control snapshots.
 * @param connected Flow indicating an explicitly enabled legacy phone connection.
 * @param foreground Flow indicating active watch foreground ownership.
 * @param initialized Flow indicating completed preference restoration.
 * @return The gated complete-control snapshots, excluding transient blink phases.
 */
internal fun statesToSynchronize(
    states: Flow<WearControlState>,
    connected: Flow<Boolean>,
    foreground: Flow<Boolean>,
    initialized: Flow<Boolean>
): Flow<WearControlState> = combine(states, connected, foreground, initialized) {
        state, isConnected, isForeground, isInitialized ->
    state.takeIf { isConnected && isForeground && isInitialized }
}.filterNotNull()
