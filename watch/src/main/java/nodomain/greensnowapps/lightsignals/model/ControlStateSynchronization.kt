package nodomain.greensnowapps.lightsignals.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull

internal fun statesToSynchronize(
    states: Flow<WearControlState>,
    connected: Flow<Boolean>,
    foreground: Flow<Boolean>,
    initialized: Flow<Boolean>
): Flow<WearControlState> = combine(states, connected, foreground, initialized) {
        state, isConnected, isForeground, isInitialized ->
    state.takeIf { isConnected && isForeground && isInitialized }
}.filterNotNull()
