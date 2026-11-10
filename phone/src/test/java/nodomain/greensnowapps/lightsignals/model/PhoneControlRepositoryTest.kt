@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package nodomain.greensnowapps.lightsignals.model

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nodomain.greensnowapps.lightsignals.DEFAULT_COLOR_PROGRESS
import nodomain.greensnowapps.lightsignals.core.sync.PhoneSignalState
import nodomain.greensnowapps.lightsignals.core.sync.WatchControl
import nodomain.greensnowapps.lightsignals.core.sync.WatchModeChange
import nodomain.greensnowapps.lightsignals.testing.FakePreferencesDataStore
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel.Companion.Active
import org.junit.Assert.*
import org.junit.Test

class PhoneControlRepositoryTest {
    private class Fixture(scope: TestScope, saved: Preferences = emptyPreferences()) {
        val store = FakePreferencesDataStore(saved)
        val remote = MutableStateFlow<PhoneControlState?>(null)
        val errors = mutableListOf<IOException>()
        var serviceStarts = 0
        var serviceStops = 0
        var serviceAvailable = true
        val sessions = WatchControlSessions()
        val repo = PhoneControlRepository(store, remote,
            { serviceStarts++; serviceAvailable }, { serviceStops++ },
            scope.backgroundScope, scope.backgroundScope, { errors += it })
    }

    @Test fun initializationWaitsForPreferencesAndIsIdempotent() = runTest {
        val f = Fixture(this, preferencesOf(KEY_ACTIVE_SOUND to 2, KEY_BLINK_DELAY to 325L))
        val gate = CompletableDeferred<Unit>()
        f.store.readGate = gate
        f.repo.initFromPrefs()
        f.repo.initFromPrefs()
        runCurrent()
        assertFalse(f.repo.initialized.value)
        gate.complete(Unit)
        runCurrent()
        assertTrue(f.repo.initialized.value)
        assertEquals(325L, f.repo.blinkDelay.value)
        assertEquals(2, f.repo.activeSound.value)
        assertEquals(1, f.remote.subscriptionCount.value)
        assertEquals(0, f.serviceStarts)
    }

    @Test fun restorationGivesSosPrecedenceWithoutStartingSavedTorch() = runTest {
        val f = Fixture(this, preferencesOf(KEY_SOS_ON to true, KEY_BLINK_ON to true))
        f.repo.initFromPrefs()
        runCurrent()
        assertTrue(f.repo.sosOn.value)
        assertFalse(f.repo.blinkOn.value)
        assertFalse(f.repo.flashOn.value)
        assertEquals(0, f.serviceStarts)
    }

    @Test fun remoteStateIsValidatedAppliedAndPersistedTogether() = runTest {
        val f = Fixture(this)
        f.repo.initFromPrefs()
        runCurrent()
        f.remote.value = PhoneControlState(true, true, false, -1L, 99, Float.NaN, 99, 99)
        runCurrent()
        assertTrue(f.repo.sosOn.value)
        assertFalse(f.repo.blinkOn.value)
        assertEquals(100L, f.repo.blinkDelay.value)
        assertEquals(3, f.repo.activeSound.value)
        assertEquals(1, f.repo.screenIndex.value)
        assertEquals(Active.FLASH, f.repo.activeButton.value)
        assertEquals(DEFAULT_COLOR_PROGRESS, f.store.snapshot[KEY_COLOR_PROGRESS])
        assertEquals(100f, f.store.snapshot[KEY_FREQUENCY_PROGRESS])
        assertEquals(false, f.store.snapshot[KEY_BLINK_ON])
        assertEquals(true, f.store.snapshot[KEY_SOS_ON])
    }

    @Test fun startingTorchIsIdempotentAndStoppingItDoesNotClearScreenMode() = runTest {
        val f = Fixture(this)
        f.repo.setBlinking(true, 300L)
        runCurrent()
        f.repo.setFlash(true)
        f.repo.setFlash(true)
        runCurrent()
        assertEquals(1, f.serviceStarts)
        assertTrue(f.repo.flashOn.value)
        assertFalse(f.repo.colorBlinkActive.value)
        f.repo.setFlash(false)
        runCurrent()
        assertTrue(f.repo.blinkOn.value)
        assertTrue(f.repo.colorBlinkActive.value)
    }

    @Test fun linkedWatchSnapshotAndUnrelatedControlsPreserveTheRunningSos() = runTest {
        val f = Fixture(this)
        f.repo.initFromPrefs()
        runCurrent()
        f.repo.setBlinking(false, 425L)
        f.repo.switchSOS(true)
        f.repo.setFlash(true)
        val running = PhoneSignalState(torchOn = true, blinkEnabled = false, sosEnabled = true, frequency = 425L)
        assertEquals(running, f.repo.connectionSignal())

        assertTrue(f.repo.applyWatchControl(WatchControl.COLOR, 651f.toBits()))
        assertTrue(f.repo.applyWatchControl(WatchControl.SOUND, 2))
        assertTrue(f.repo.applyWatchControl(WatchControl.SCREEN, 1))
        runCurrent()
        assertEquals(running.copy(color = 651f, activeSound = 2, screenIndex = 1), f.repo.connectionSignal())
        assertEquals(1, f.serviceStarts)
        assertEquals(0, f.serviceStops)
        assertEquals(651f, f.store.snapshot[KEY_COLOR_PROGRESS])
        assertEquals(2, f.repo.activeSound.value)
        assertEquals(1, f.repo.screenIndex.value)

        assertTrue(f.repo.applyWatchControl(WatchControl.TORCH, 0))
        assertTrue(f.repo.applyWatchControl(WatchControl.SOUND, 0))
        assertFalse(f.repo.flashOn.value)
        assertTrue(f.repo.sosOn.value)
        assertEquals(1, f.serviceStarts)
        assertEquals(1, f.serviceStops)
    }

    @Test fun linkedWatchFrequencyPreservesModesAndColorSelectionStopsTorch() = runTest {
        val f = Fixture(this)
        f.repo.initFromPrefs()
        runCurrent()
        assertTrue(f.repo.applyWatchControl(WatchControl.TORCH, 1))
        assertTrue(f.repo.applyWatchControl(WatchControl.SOS, WatchModeChange.ENABLE_ON_TORCH.wireValue))
        assertTrue(f.repo.applyWatchControl(WatchControl.FREQUENCY, 620))
        assertTrue(f.repo.sosOn.value)
        assertFalse(f.repo.blinkOn.value)
        assertEquals(620L, f.repo.blinkDelay.value)
        assertTrue(f.repo.applyWatchControl(WatchControl.BLINK, WatchModeChange.ENABLE_ON_TORCH.wireValue))
        assertTrue(f.repo.applyWatchControl(WatchControl.FREQUENCY, 300))
        assertTrue(f.repo.blinkOn.value)
        assertFalse(f.repo.sosOn.value)
        assertEquals(300L, f.repo.blinkDelay.value)
        assertTrue(f.repo.applyWatchControl(WatchControl.COLOR_BUTTON, Active.GREEN.ordinal))
        runCurrent()
        assertFalse(f.repo.flashOn.value)
        assertEquals(Active.GREEN, f.repo.activeButton.value)
        assertEquals(300f, f.store.snapshot[KEY_FREQUENCY_PROGRESS])
    }

    @Test fun linkedWatchCommandsRejectUnavailableOrInvalidChanges() = runTest {
        val f = Fixture(this)
        assertFalse(f.repo.applyWatchControl(WatchControl.TORCH, 1))
        f.repo.initFromPrefs()
        runCurrent()
        assertFalse(f.repo.applyWatchControl(WatchControl.TORCH, 2))
        assertFalse(f.repo.applyWatchControl(WatchControl.COLOR_BUTTON, Active.FLASH.ordinal))
        assertFalse(f.repo.applyWatchControl(WatchControl.COLOR, Float.NaN.toBits()))
        assertEquals(0, f.serviceStarts)
        f.serviceAvailable = false
        assertFalse(f.repo.applyWatchControl(WatchControl.TORCH, 1))
        assertFalse(f.repo.flashOn.value)
    }

    @Test fun watchSosUsesTorchAfterItWasTurnedOffOnPhone() = runTest {
        val f = Fixture(this)
        f.repo.initFromPrefs()
        runCurrent()
        assertTrue(f.repo.applyWatchControl(WatchControl.TORCH, 1))
        f.repo.setFlash(false)
        assertFalse(f.repo.flashOn.value)

        assertTrue(f.repo.applyWatchControl(WatchControl.SOS, WatchModeChange.ENABLE_ON_TORCH.wireValue))
        runCurrent()
        assertTrue(f.repo.flashOn.value)
        assertTrue(f.repo.sosOn.value)
        assertFalse(f.repo.colorBlinkActive.value)
        assertEquals(2, f.serviceStarts)

        f.repo.setFlash(false)
        assertTrue(f.repo.applyWatchControl(WatchControl.SOS, WatchModeChange.DISABLE.wireValue))
        assertFalse(f.repo.sosOn.value)
        assertFalse(f.repo.flashOn.value)
        assertEquals(2, f.serviceStarts)
    }

    @Test fun watchBlinkUsesSelectedOutputAndRejectedTorchStartLeavesStateIntact() = runTest {
        val f = Fixture(this)
        f.repo.initFromPrefs()
        runCurrent()
        f.repo.setFlash(true)
        assertTrue(f.repo.applyWatchControl(WatchControl.BLINK, WatchModeChange.ENABLE_ON_SCREEN.wireValue))
        runCurrent()
        assertFalse(f.repo.flashOn.value)
        assertTrue(f.repo.blinkOn.value)
        assertTrue(f.repo.colorBlinkActive.value)

        val screenSignal = f.repo.connectionSignal()
        f.serviceAvailable = false
        assertFalse(f.repo.applyWatchControl(WatchControl.SOS, WatchModeChange.ENABLE_ON_TORCH.wireValue))
        assertFalse(f.repo.applyWatchControl(WatchControl.BLINK, -1))
        assertEquals(screenSignal, f.repo.connectionSignal())

        f.serviceAvailable = true
        assertTrue(f.repo.applyWatchControl(WatchControl.BLINK, WatchModeChange.ENABLE_ON_TORCH.wireValue))
        assertTrue(f.repo.flashOn.value)
        assertTrue(f.repo.blinkOn.value)
        assertFalse(f.repo.colorBlinkActive.value)
    }

    @Test fun linkedWatchAuthorizationRejectsWrongWatchWrongSessionAndReplayedCommands() {
        val id = UUID.randomUUID()
        val connection = LinkedWatchControl("watch", id, acceptsEarlyControl = true)
        assertFalse(connection.allows("other", id, 1L))
        assertFalse(connection.allows("watch", UUID.randomUUID(), 1L))
        assertFalse(connection.allows("watch", id, 0L))
        assertTrue(connection.allows("watch", id, 2L))
        connection.recordAccepted(2L)
        assertFalse(connection.allows("watch", id, 1L))
        assertFalse(connection.allows("watch", id, 2L))
        assertTrue(connection.allows("watch", id, 3L))
    }

    @Test fun localPhoneControlsRemainAvailableAfterWatchHandoff() = runTest {
        val f = Fixture(this)
        f.repo.initFromPrefs()
        runCurrent()
        val state = PhoneSignalState(true, true, false, 325L, 660f, 2, 1, Active.MIDDLE.ordinal)
        val session = checkNotNull(f.sessions.begin("watch", UUID.randomUUID(), acceptsEarlyControl = false))
        assertTrue(f.repo.applyWatchHandoff(state))
        assertTrue(f.sessions.confirm(session))
        assertEquals(state, f.repo.connectionSignal())

        f.repo.setFlash(false)
        f.repo.setBlinking(false, 800L)
        f.repo.switchSOS(true)
        f.repo.select(Active.RED)
        f.repo.updateColorProgress(3f)
        f.repo.updateScreenIndex(0)
        f.repo.setActiveSound(1)
        runCurrent()
        assertEquals(
            PhoneSignalState(false, false, true, 800L, 3f, 1, 0, Active.RED.ordinal),
            f.repo.connectionSignal()
        )

        assertTrue(f.repo.applyWatchControl(WatchControl.SOUND, 3))
        assertEquals(3, f.repo.activeSound.value)
        assertSame(session, f.sessions.active)
    }

    @Test fun explicitResyncReplacesTheSessionForTheSameWatch() {
        val sessions = WatchControlSessions()
        val initial = checkNotNull(sessions.begin("watch", UUID.randomUUID(), acceptsEarlyControl = false))
        assertTrue(sessions.confirm(initial))
        assertNull(sessions.begin("other", UUID.randomUUID(), acceptsEarlyControl = true))
        val refresh = checkNotNull(sessions.begin("watch", UUID.randomUUID(), acceptsEarlyControl = true))
        assertSame(initial, sessions.active)
        assertTrue(sessions.confirm(refresh))
        assertFalse(sessions.confirm(initial))
        sessions.end(initial)
        assertSame(refresh, sessions.active)
        assertEquals(listOf(refresh), sessions.endAll())
        assertNull(sessions.begin("watch", refresh.requestId, acceptsEarlyControl = true))
    }

    @Test fun localChangeDuringPendingHandoffAndFailedStartPreserveState() = runTest {
        val f = Fixture(this)
        f.repo.initFromPrefs()
        runCurrent()
        val session = checkNotNull(f.sessions.begin("watch", UUID.randomUUID(), acceptsEarlyControl = true))
        f.repo.updateColorProgress(777f)
        assertSame(session, f.sessions.pending)
        val current = f.repo.connectionSignal()
        f.serviceAvailable = false
        assertFalse(f.repo.applyWatchHandoff(current.copy(torchOn = true, activeSound = 3)))
        assertEquals(current, f.repo.connectionSignal())
    }

    @Test fun failedServiceStartReturnsToSafeScreenState() = runTest {
        val f = Fixture(this)
        f.serviceAvailable = false
        f.repo.setBlinking(true, 300L)
        f.repo.setFlash(true)
        runCurrent()
        assertFalse(f.repo.flashOn.value)
        assertEquals(false, f.store.snapshot[KEY_FLASH_ON])
        assertTrue(f.repo.colorBlinkActive.value)
        assertTrue(f.serviceStops > 0)
    }

    @Test fun modeSwitchCancelsSosAndUsesOnlyTheNewBlinkPeriod() = runTest {
        val f = Fixture(this)
        f.repo.switchSOS(true)
        runCurrent()
        advanceTimeBy(100L)
        f.repo.setBlinking(true, 350L)
        runCurrent()
        assertFalse(f.repo.sosOn.value)
        assertTrue(f.repo.blinkOn.value)
        advanceTimeBy(100L)
        runCurrent()
        assertTrue("The old SOS edge at 200 ms must be cancelled", f.repo.colorBlinkActive.value)
        advanceTimeBy(250L)
        runCurrent()
        assertFalse(f.repo.colorBlinkActive.value)
    }

    @Test fun clearingCancelsSignalsAndRemoteCollectionButKeepsQueuedWrites() = runTest {
        val f = Fixture(this)
        f.repo.initFromPrefs()
        runCurrent()
        f.repo.switchSOS(true)
        runCurrent()
        f.repo.setActiveSound(3)
        f.repo.clearAll()
        runCurrent()
        assertFalse(f.repo.initialized.value)
        assertFalse(f.repo.flashOn.value)
        assertFalse(f.repo.colorBlinkActive.value)
        assertEquals(0, f.remote.subscriptionCount.value)
        assertEquals(3, f.store.snapshot[KEY_ACTIVE_SOUND])
        f.remote.value = PhoneControlState(torchOn = true)
        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(0, f.serviceStarts)
        assertFalse(f.repo.colorBlinkActive.value)
    }

    @Test fun invalidUiValuesAreClampedAndLatestWriteWins() = runTest {
        val f = Fixture(this)
        f.repo.setActiveSound(-9)
        f.repo.setActiveSound(99)
        f.repo.updateScreenIndex(99)
        f.repo.updateColorProgress(Float.NaN)
        f.repo.updateFrequencyProgress(Float.POSITIVE_INFINITY)
        f.repo.setBlinking(true, Long.MIN_VALUE)
        runCurrent()
        assertEquals(3, f.store.snapshot[KEY_ACTIVE_SOUND])
        assertEquals(1, f.store.snapshot[KEY_ACTIVE_SCREEN])
        assertEquals(DEFAULT_COLOR_PROGRESS, f.store.snapshot[KEY_COLOR_PROGRESS])
        assertEquals(500f, f.store.snapshot[KEY_FREQUENCY_PROGRESS])
        assertEquals(100L, f.repo.blinkDelay.value)
    }

    @Test fun readFailureInitializesDefaultsAndReportsTheError() = runTest {
        val f = Fixture(this)
        val failure = IOException("unreadable preferences")
        f.store.readFailure = failure
        f.repo.initFromPrefs()
        runCurrent()
        assertTrue(f.repo.initialized.value)
        assertEquals(500L, f.repo.blinkDelay.value)
        assertEquals(listOf(failure), f.errors)
        assertEquals(0, f.serviceStarts)
    }

    @Test fun failedWriteDoesNotPreventLaterWrites() = runTest {
        val f = Fixture(this)
        val failure = IOException("disk unavailable")
        f.store.nextWriteFailure = failure
        f.repo.setActiveSound(1)
        runCurrent()
        f.repo.setActiveSound(2)
        runCurrent()
        assertEquals(listOf(failure), f.errors)
        assertEquals(2, f.store.snapshot[KEY_ACTIVE_SOUND])
        assertEquals(2, f.repo.activeSound.value)
    }
}
