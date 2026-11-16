@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package nodomain.greensnowapps.lightsignals.model

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
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
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.ACCEPTED
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.REJECTED
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.RETRY
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchControl
import nodomain.greensnowapps.lightsignals.core.sync.WatchControlCommand
import nodomain.greensnowapps.lightsignals.core.sync.WatchModeChange
import nodomain.greensnowapps.lightsignals.testing.FakePreferencesDataStore
import nodomain.greensnowapps.lightsignals.ui.viewmodels.PhoneMainViewModel.Companion.Active
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies phone state restoration, control transitions and watch-command application.
 */
class PhoneControlRepositoryTest {
    /**
     * Owns the fakes, observed events and coroutine test state used by this test suite.
     *
     * @param scope Coroutine test scope that owns the fixture's jobs and virtual-time
     *     scheduler.
     * @param saved Saved preferences used to seed the fixture.
     */
    private class Fixture(scope: TestScope, saved: Preferences = emptyPreferences()) {
        /**
         * In-memory preferences store used by the fixture.
         */
        val store = FakePreferencesDataStore(saved)
        /**
         * Mutable legacy peer state supplied to the repository under test.
         */
        val remote = MutableStateFlow<PhoneControlState?>(null)
        /**
         * Preference failures observed by this fixture.
         */
        val errors = mutableListOf<IOException>()
        /**
         * Number of foreground torch start requests observed by the fixture.
         */
        var serviceStarts = 0
        /**
         * Number of foreground torch stop requests observed by the fixture.
         */
        var serviceStops = 0
        /**
         * Whether the fake foreground service accepts startup.
         */
        var serviceAvailable = true
        /**
         * Session-state machine used by this fixture.
         */
        val sessions = WatchControlSessions()
        /**
         * Repository instance exercised by this fixture.
         */
        val repo = PhoneControlRepository(store, remote,
            { serviceStarts++; serviceAvailable }, { serviceStops++ },
            scope.backgroundScope, scope.backgroundScope, { errors += it })
    }

    /**
     * Verifies that initialization waits for preferences and is idempotent.
     */
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

    /**
     * Verifies that restoration gives SOS precedence without starting saved torch.
     */
    @Test fun restorationGivesSosPrecedenceWithoutStartingSavedTorch() = runTest {
        val f = Fixture(this, preferencesOf(KEY_SOS_ON to true, KEY_BLINK_ON to true))
        f.repo.initFromPrefs()
        runCurrent()
        assertTrue(f.repo.sosOn.value)
        assertFalse(f.repo.blinkOn.value)
        assertFalse(f.repo.flashOn.value)
        assertEquals(0, f.serviceStarts)
    }

    /**
     * Verifies that remote state is validated applied and persisted together.
     */
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

    /**
     * Verifies that starting torch is idempotent and stopping it does not clear screen mode.
     */
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

    /**
     * Verifies that legacy watch commands without a snapshot preserve the running SOS.
     */
    @Test fun legacyWatchCommandsWithoutASnapshotPreserveTheRunningSos() = runTest {
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

    /**
     * Verifies that watch actions restore blink and every watch setting after phone selects
     * SOS.
     */
    @Test fun watchActionsRestoreBlinkAndEveryWatchSettingAfterPhoneSelectsSos() = runTest {
        val watch = PhoneSignalState(true, true, false, 350L, 3000f, 2, 1, Active.MIDDLE.ordinal)
        val actions = listOf(
            Triple(WatchControl.COLOR, 3400f.toBits(), watch.copy(color = 3400f)),
            Triple(WatchControl.TORCH, 0, watch.copy(torchOn = false)),
            Triple(WatchControl.TORCH, 1, watch),
            Triple(WatchControl.SOUND, 3, watch.copy(activeSound = 3)),
            Triple(WatchControl.FREQUENCY, 600, watch.copy(frequency = 600L))
        )
        for ((control, value, expected) in actions) {
            val f = Fixture(this)
            f.repo.initFromPrefs()
            runCurrent()
            assertTrue(f.repo.applyWatchHandoff(watch))
            f.repo.switchSOS(true)
            f.repo.setBlinking(false, 800L)
            f.repo.updateColorProgress(70f)
            f.repo.setActiveSound(1)
            f.repo.updateScreenIndex(0)
            assertTrue(f.repo.sosOn.value)
            val wire = WatchControlCommand(UUID.randomUUID(), 1, control, value, signal = expected).encode()
            assertTrue(f.repo.applyWatchControl(checkNotNull(WatchControlCommand.decode(wire))))
            runCurrent()
            assertEquals("Watch action $control must replace the conflicting phone mode", expected, f.repo.connectionSignal())
            assertEquals(false, f.store.snapshot[KEY_SOS_ON])
            assertEquals(true, f.store.snapshot[KEY_BLINK_ON])
        }
    }

    /**
     * Verifies that watch SOS and steady settings replace locally selected blink.
     */
    @Test fun watchSosAndSteadySettingsReplaceLocallySelectedBlink() = runTest {
        for (sos in listOf(true, false)) {
            val f = Fixture(this)
            f.repo.initFromPrefs()
            runCurrent()
            f.repo.setBlinking(true, 300L)
            val watch = PhoneSignalState(false, false, sos, 600L, 3400f, 2, 1, Active.MIDDLE.ordinal)
            val command = WatchControlCommand(UUID.randomUUID(), 1, WatchControl.COLOR, watch.color.toBits(), signal = watch)
            assertTrue(f.repo.applyWatchControl(checkNotNull(WatchControlCommand.decode(command.encode()))))
            assertEquals(watch, f.repo.connectionSignal())
        }
    }

    /**
     * Verifies that rejected snapshot and unavailable torch leave phone state unchanged.
     */
    @Test fun rejectedSnapshotAndUnavailableTorchLeavePhoneStateUnchanged() = runTest {
        val f = Fixture(this)
        val watch = PhoneSignalState(true, true, false, 350L, 3400f)
        val command = WatchControlCommand(UUID.randomUUID(), 1, WatchControl.TORCH, 1, signal = watch)
        assertFalse(f.repo.applyWatchControl(command))
        f.repo.initFromPrefs()
        runCurrent()
        f.repo.switchSOS(true)
        val before = f.repo.connectionSignal()
        assertFalse(f.repo.applyWatchControl(command.copy(value = 0)))
        assertEquals(before, f.repo.connectionSignal())
        f.serviceAvailable = false
        assertFalse(f.repo.applyWatchControl(command))
        assertEquals(before, f.repo.connectionSignal())
    }

    /**
     * Verifies that linked watch frequency preserves modes and color selection stops torch.
     */
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

    /**
     * Verifies that linked watch commands reject unavailable or invalid changes.
     */
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

    /**
     * Verifies that watch SOS uses torch after it was turned off on phone.
     */
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

    /**
     * Verifies that watch blink uses selected output and rejected torch start leaves state
     * intact.
     */
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

    /**
     * Verifies that linked watch authorization rejects wrong watch wrong session and replayed
     * commands.
     */
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

    /**
     * Verifies that local phone controls remain available after watch handoff.
     */
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

    /**
     * Verifies that explicit resync replaces the session for the same watch.
     */
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

    /**
     * Verifies that reopened watch can replace its previous session.
     */
    @Test fun reopenedWatchCanReplaceItsPreviousSession() {
        val sessions = WatchControlSessions()
        val previous = checkNotNull(sessions.begin("watch", UUID.randomUUID(), acceptsEarlyControl = false))
        assertTrue(sessions.confirm(previous))
        assertNull(sessions.begin("other", UUID.randomUUID(), acceptsEarlyControl = false))

        val reconnect = checkNotNull(sessions.begin("watch", UUID.randomUUID(), acceptsEarlyControl = false))
        assertSame(previous, sessions.active)
        assertFalse(reconnect.acceptsEarlyControl)
        assertTrue(sessions.confirm(reconnect))
        assertSame(reconnect, sessions.active)
        assertFalse(sessions.confirm(previous))
        assertNull(sessions.begin("watch", previous.requestId, acceptsEarlyControl = false))
    }

    /**
     * Verifies that only the confirmed watch can resume and old commands stay invalid.
     */
    @Test fun onlyTheConfirmedWatchCanResumeAndOldCommandsStayInvalid() {
        var now = 100_000L
        val sessions = WatchControlSessions { now }
        val initial = checkNotNull(sessions.begin("watch", UUID.randomUUID(), acceptsEarlyControl = true))
        val first = WatchResumeRequest.create(initial.linkId, now)
        assertEquals(RETRY, sessions.resume("watch", first))
        assertTrue(sessions.confirm(initial))
        assertEquals(REJECTED, sessions.resume("other-watch", first))
        assertEquals(REJECTED, sessions.resume("watch", WatchResumeRequest.create(UUID.randomUUID(), now)))
        assertEquals(ACCEPTED, sessions.resume("watch", first))
        val resumed = checkNotNull(sessions.active)
        assertFalse(resumed.allows("watch", initial.requestId, Long.MAX_VALUE))
        assertTrue(resumed.allows("watch", first.commandId, 1))
        resumed.recordAccepted(1)
        assertEquals(ACCEPTED, sessions.resume("watch", first))
        assertFalse(resumed.allows("watch", first.commandId, 1))

        val newer = WatchResumeRequest.create(initial.linkId, ++now)
        assertEquals(ACCEPTED, sessions.resume("watch", newer))
        assertEquals(RETRY, sessions.resume("watch", first))
        assertEquals(newer.commandId, sessions.active?.requestId)
        sessions.endAll()
        assertEquals(REJECTED, sessions.resume("watch", newer))
    }

    /**
     * Verifies that closing the phone preserves only its confirmed relationship.
     */
    @Test fun closingThePhonePreservesOnlyItsConfirmedRelationship() {
        val sessions = WatchControlSessions()
        val link = checkNotNull(sessions.begin("watch", UUID.randomUUID(), acceptsEarlyControl = true))
        assertNull(sessions.remembered)
        assertTrue(sessions.confirm(link))
        val confirmed = sessions.remembered
        val pending = checkNotNull(sessions.begin("watch", UUID.randomUUID(), acceptsEarlyControl = false))

        assertEquals(listOf(pending, link), sessions.pause())
        assertNull(sessions.active)
        assertNull(sessions.pending)
        assertEquals(confirmed, sessions.remembered)
        assertEquals(REJECTED, sessions.resume("watch", WatchResumeRequest.create(pending.linkId)))
        assertEquals(ACCEPTED, sessions.resume("watch", WatchResumeRequest.create(link.linkId)))
        assertFalse(checkNotNull(sessions.active).allows("watch", link.requestId, Long.MAX_VALUE))
    }

    /**
     * Verifies that a new phone process restores the relationship and requires a fresh
     * handshake.
     */
    @Test fun aNewPhoneProcessRestoresTheRelationshipAndRequiresAFreshHandshake() {
        var now = 100_000L
        val prefs = mutablePreferencesOf()
        val original = WatchControlSessions({ RememberedWatchLink.write(prefs, it) }) { now }
        val initial = checkNotNull(original.begin("watch", UUID.randomUUID(), acceptsEarlyControl = false))
        assertTrue(original.confirm(initial))
        val oldResume = WatchResumeRequest.create(initial.linkId, now)
        assertEquals(ACCEPTED, original.resume("watch", oldResume))
        original.pause()

        val reopened = WatchControlSessions({ RememberedWatchLink.write(prefs, it) }) { now }
        reopened.restore(RememberedWatchLink.read(prefs))
        assertNull(reopened.active)
        assertEquals(RETRY, reopened.resume("watch", oldResume))
        val freshResume = WatchResumeRequest.create(initial.linkId, ++now)
        assertEquals(REJECTED, reopened.resume("other", freshResume))
        assertEquals(ACCEPTED, reopened.resume("watch", freshResume))
        assertEquals(freshResume.commandId, reopened.active?.requestId)
        assertFalse(checkNotNull(reopened.active).allows("watch", oldResume.commandId, 1))

        reopened.pause()
        assertTrue(reopened.forget("watch", initial.linkId))
        assertNull(RememberedWatchLink.read(prefs))
        val afterDisconnect = WatchControlSessions { now }
        afterDisconnect.restore(RememberedWatchLink.read(prefs))
        assertEquals(REJECTED, afterDisconnect.resume("watch", WatchResumeRequest.create(initial.linkId, ++now)))
    }

    /**
     * Verifies that malformed saved watch identity cannot restore control.
     */
    @Test fun malformedSavedWatchIdentityCannotRestoreControl() {
        val prefs = preferencesOf(
            stringPreferencesKey("linked_watch_node") to "watch",
            stringPreferencesKey("linked_watch_id") to "invalid-id"
        )
        assertNull(RememberedWatchLink.read(prefs))
    }

    /**
     * Verifies that local change during pending handoff and failed start preserve state.
     */
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

    /**
     * Verifies that failed service start returns to safe screen state.
     */
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

    /**
     * Verifies that mode switch cancels SOS and uses only the new blink period.
     */
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

    /**
     * Verifies that clearing cancels signals and remote collection but keeps queued writes.
     */
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

    /**
     * Verifies that invalid UI values are clamped and latest write wins.
     */
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

    /**
     * Verifies that read failure initializes defaults and reports the error.
     */
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

    /**
     * Verifies that failed write does not prevent later writes.
     */
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
