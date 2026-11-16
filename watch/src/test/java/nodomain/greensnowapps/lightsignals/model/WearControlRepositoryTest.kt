@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package nodomain.greensnowapps.lightsignals.model

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nodomain.greensnowapps.lightsignals.WearConstants
import nodomain.greensnowapps.lightsignals.core.sync.PhoneSignalState
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.ACCEPTED
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.REJECTED
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionReply.RETRY
import nodomain.greensnowapps.lightsignals.core.sync.WatchConnectionRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchControl
import nodomain.greensnowapps.lightsignals.core.sync.WatchControlCommand
import nodomain.greensnowapps.lightsignals.core.sync.WatchModeChange
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeRequest
import nodomain.greensnowapps.lightsignals.core.sync.WatchResumeReply
import nodomain.greensnowapps.lightsignals.testing.FakePreferencesDataStore
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies watch state, sound routing and phone connection/reconnection contracts.
 */
class WearControlRepositoryTest {
    /**
     * Independent legacy Blink preference key used to seed repository tests.
     */
    private val blink = booleanPreferencesKey(WearConstants.PREFERENCE_BLINK_DON)
    /**
     * Independent legacy SOS preference key used to seed repository tests.
     */
    private val sos = booleanPreferencesKey(WearConstants.PREFERENCE_SOS_ON)
    /**
     * Independent legacy torch preference key used to verify safe restoration.
     */
    private val flash = booleanPreferencesKey(WearConstants.PREFERENCE_FLASH_ON)
    /**
     * Independent legacy sound-selection key used by repository tests.
     */
    private val sound = intPreferencesKey(WearConstants.PREFERENCE_ACTIVE_SOUND)
    /**
     * Independent legacy blink-delay key used by timing and restoration tests.
     */
    private val delay = longPreferencesKey(WearConstants.PREFERENCE_BLINK_DELAY)

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
         * Preference failures observed by this fixture.
         */
        val errors = mutableListOf<IOException>()
        /**
         * Authenticated commands observed by the fake phone transport.
         */
        val linkedCommands = mutableListOf<Pair<String, WatchControlCommand>>()
        /**
         * Mutable discovery results used to simulate phone reachability.
         */
        val reachablePhones = MutableStateFlow(setOf("phone"))
        /**
         * Node, path and payload triples observed by the fake transport.
         */
        val requests = mutableListOf<Triple<String, String, ByteArray>>()
        /**
         * Configurable session-management response returned by the fake transport.
         */
        var reply: suspend (String) -> WatchConnectionReply? = { ACCEPTED }
        /**
         * Optional phone snapshot returned by the fake resume transport.
         */
        var resumeSignal: PhoneSignalState? = null
        /**
         * Whether the fake phone accepts linked user commands.
         */
        var commandAccepted = true
        /**
         * Number of local sound-stop calls observed by the fixture.
         */
        var soundStops = 0
        /**
         * Repository instance exercised by this fixture.
         */
        val repo = WearControlRepository(store, { soundStops++ },
            { CoroutineScope(scope.backgroundScope.coroutineContext + SupervisorJob(scope.backgroundScope.coroutineContext[Job])) },
            scope.backgroundScope, { errors += it },
            { phone, command -> linkedCommands += phone to command; commandAccepted },
            reachablePhones,
            { node, path, payload ->
                requests += Triple(node, path, payload)
                reply(path)
            },
            { node, request ->
                val path = WatchConnectionRequest.RESUME_STATE_PATH
                requests += Triple(node, path, request.encode())
                reply(path)?.let { WatchResumeReply(it, resumeSignal) }
            })
    }

    /**
     * Verifies that phone control suppresses watch playback until an explicit disconnect.
     */
    @Test fun phoneControlSuppressesWatchPlaybackUntilAnExplicitDisconnect() = runTest {
        val f = Fixture(this, preferencesOf(sound to 1))
        f.repo.start(Any())
        runCurrent()
        assertTrue(f.repo.canPlayLocalSound)
        val beforeConnection = f.soundStops
        val signal = PhoneSignalState(false, true, false, 350L, activeSound = 3)
        assertTrue(f.repo.connectFromPhone("phone", WatchConnectionRequest.create(signal)))
        runCurrent()
        assertFalse(f.repo.canPlayLocalSound)
        assertTrue(f.soundStops > beforeConnection)
        assertEquals(3, f.repo.activeSound.value)
        assertEquals(1, f.store.snapshot[sound])

        f.repo.setActiveSound(2)
        runCurrent()
        assertEquals(WatchControl.SOUND, f.linkedCommands.single().second.control)
        assertEquals(2, f.linkedCommands.single().second.value)
        assertFalse(f.repo.canPlayLocalSound)

        f.reachablePhones.value = emptySet()
        runCurrent()
        assertFalse(f.repo.phoneConnection.value)
        assertFalse(f.repo.canPlayLocalSound)
        f.resumeSignal = signal.copy(activeSound = 2)
        f.reachablePhones.value = setOf("phone")
        runCurrent()
        assertTrue(f.repo.phoneConnection.value)
        assertFalse(f.repo.canPlayLocalSound)
        assertEquals(2, f.repo.activeSound.value)

        f.repo.togglePhoneConnection(false)
        runCurrent()
        assertTrue(f.repo.canPlayLocalSound)
        assertEquals(1, f.repo.activeSound.value)
    }

    /**
     * Verifies that enabling watch sound offline runs a local signal and reconnection returns
     * audio to the phone.
     */
    @Test fun enablingWatchSoundOfflineRunsALocalSignalAndReconnectionReturnsAudioToThePhone() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        val signal = PhoneSignalState(true, true, false, 350L, activeSound = 3)
        val request = WatchConnectionRequest.create(signal)
        assertTrue(f.repo.connectFromPhone("phone", request))
        runCurrent()
        assertFalse(f.repo.canPlayLocalSound)
        assertFalse(f.repo.colorBlinkActive.value)

        f.reply = { RETRY }
        assertTrue(f.repo.phonePaused("phone", request.id))
        runCurrent()
        f.repo.setActiveSound(3) // Reselecting the same sound must enable local playback too.
        runCurrent()
        assertTrue(f.repo.canPlayLocalSound)
        assertFalse(f.repo.localSoundSuppressed.value)
        assertFalse(f.repo.flashOn.value)
        assertTrue(f.repo.colorBlinkActive.value)
        assertEquals(3, f.repo.activeSound.value)
        assertFalse(f.repo.phoneConnection.value)
        assertTrue(f.linkedCommands.isEmpty())

        val beforeReconnect = f.soundStops
        f.reply = { ACCEPTED }
        f.resumeSignal = signal.copy(activeSound = 2)
        assertTrue(f.repo.phoneAvailable("phone", request.id))
        runCurrent()
        assertTrue(f.repo.phoneConnection.value)
        assertFalse(f.repo.canPlayLocalSound)
        assertTrue(f.repo.localSoundSuppressed.value)
        assertTrue(f.repo.flashOn.value)
        assertEquals(2, f.repo.activeSound.value)
        assertTrue(f.soundStops > beforeReconnect)
        assertTrue(f.linkedCommands.isEmpty())
    }

    /**
     * Verifies that watch sound remains usable when the phone stays closed.
     */
    @Test fun watchSoundRemainsUsableWhenThePhoneStaysClosed() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        val request = WatchConnectionRequest.create(PhoneSignalState(false, true, false, 400L, activeSound = 3))
        assertTrue(f.repo.connectFromPhone("phone", request))
        f.reply = { RETRY }
        assertTrue(f.repo.phonePaused("phone", request.id))
        runCurrent()
        f.repo.setActiveSound(2)
        advanceTimeBy(60_000)
        runCurrent()
        assertFalse(f.repo.phoneConnection.value)
        assertTrue(f.repo.canPlayLocalSound)
        assertFalse(f.repo.localSoundSuppressed.value)
        assertEquals(2, f.repo.activeSound.value)
        assertTrue(f.linkedCommands.isEmpty())

        val beforeOff = f.soundStops
        f.repo.setActiveSound(0)
        assertEquals(0, f.repo.activeSound.value)
        assertTrue(f.soundStops > beforeOff)
        assertTrue(f.repo.phoneAvailable("phone", request.id))
    }

    /**
     * Verifies that watch initiated and legacy phone links suppress only local playback.
     */
    @Test fun watchInitiatedAndLegacyPhoneLinksSuppressOnlyLocalPlayback() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        f.repo.setActiveSound(2)
        val request = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        assertEquals(2, request.signal.activeSound)
        val beforeConnection = f.soundStops
        assertTrue(f.repo.finishPhoneHandoff("phone", request.id))
        assertFalse(f.repo.canPlayLocalSound)
        assertTrue(f.soundStops > beforeConnection)
        assertEquals(2, f.repo.activeSound.value)

        f.repo.togglePhoneConnection(false)
        assertTrue(f.repo.canPlayLocalSound)
        assertTrue(f.repo.enableLegacyPhoneLink())
        assertFalse(f.repo.canPlayLocalSound)
        assertEquals(2, f.repo.activeSound.value)
        f.repo.togglePhoneConnection(false)
        assertTrue(f.repo.canPlayLocalSound)
    }

    /**
     * Verifies that restoring a saved phone link keeps watch audio suppressed while waiting
     * for the phone.
     */
    @Test fun restoringASavedPhoneLinkKeepsWatchAudioSuppressedWhileWaitingForThePhone() = runTest {
        val original = Fixture(this, preferencesOf(sound to 1))
        val owner = Any()
        original.repo.start(owner)
        runCurrent()
        val request = WatchConnectionRequest.create(PhoneSignalState(false, true, false, 400L, activeSound = 3))
        assertTrue(original.repo.connectFromPhone("phone", request))
        runCurrent()
        original.repo.stop(owner)
        assertFalse(original.repo.canPlayLocalSound)

        val reopened = Fixture(this, original.store.snapshot)
        val preferencesReady = CompletableDeferred<Unit>()
        reopened.store.readGate = preferencesReady
        reopened.reply = { RETRY }
        reopened.repo.start(Any())
        runCurrent()
        assertFalse(reopened.repo.canPlayLocalSound)
        preferencesReady.complete(Unit)
        runCurrent()
        assertTrue(reopened.repo.isInitialized.value)
        assertFalse(reopened.repo.phoneConnection.value)
        assertFalse(reopened.repo.canPlayLocalSound)
        assertTrue(reopened.soundStops > 0)

        reopened.repo.togglePhoneConnection(false)
        assertTrue(reopened.repo.canPlayLocalSound)
        assertEquals(1, reopened.repo.activeSound.value)
    }

    /**
     * Verifies that phone initiated connect seeds all controls without sending commands.
     */
    @Test fun phoneInitiatedConnectSeedsAllControlsWithoutSendingCommands() = runTest {
        val f = Fixture(this, preferencesOf(KEY_COLOR_PROGRESS to 99f, sound to 1))
        val owner = Any()
        val request = WatchConnectionRequest.create(PhoneSignalState(true, true, false, 350L, 789f, 3, 1, 2))
        assertFalse(f.repo.connectFromPhone("phone", request))
        f.repo.start(owner)
        runCurrent()
        assertFalse(f.repo.phoneConnection.value)

        assertTrue(f.repo.connectFromPhone("phone", request))
        runCurrent()
        assertTrue(f.repo.phoneConnection.value)
        assertFalse(f.repo.manualPhoneConnection.value)
        assertTrue(f.repo.flashOn.value)
        assertTrue(f.repo.blinkOn.value)
        assertEquals(350L, f.repo.blinkDelay.value)
        assertEquals(789f, f.repo.colorProgress.value)
        assertEquals(3, f.repo.activeSound.value)
        assertEquals(1, f.repo.screenIndex.value)
        assertEquals(2, f.repo.activeColorButton.value)
        assertTrue(f.linkedCommands.isEmpty())

        f.repo.commitColor(3f)
        f.repo.setFlash(false)
        runCurrent()
        assertEquals(listOf("phone", "phone"), f.linkedCommands.map { it.first })
        assertEquals(listOf(WatchControl.COLOR, WatchControl.TORCH),
            f.linkedCommands.map { it.second.control })
        assertEquals(listOf(1L, 2L), f.linkedCommands.map { it.second.sequence })
        assertEquals(0, f.linkedCommands.last().second.value)
        assertTrue(f.repo.connectFromPhone("phone", request))
        assertFalse(f.repo.flashOn.value)

        f.repo.togglePhoneConnection(false)
        runCurrent()
        assertFalse(f.repo.phoneConnection.value)
        val release = f.requests.single()
        assertEquals(WatchConnectionRequest.RELEASE_PATH, release.second)
        assertEquals(request.id, WatchConnectionRequest.decodeId(release.third))
        assertEquals(99f, f.repo.colorProgress.value)
        assertEquals(1, f.repo.activeSound.value)
        assertEquals(99f, f.store.snapshot[KEY_COLOR_PROGRESS])
        f.repo.togglePhoneConnection(true)
        assertTrue(f.repo.manualPhoneConnection.value)
    }

    /**
     * Verifies that watch initiated handoff sends current configuration and waits for ack.
     */
    @Test fun watchInitiatedHandoffSendsCurrentConfigurationAndWaitsForAck() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        f.repo.setBlinking(true, 330L)
        f.repo.setActiveSound(2)
        f.repo.selectColorButton(1)
        f.repo.commitColor(1200f)
        f.repo.updateScreenIndex(1)
        val request = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        assertEquals(PhoneSignalState(false, true, false, 330L, 1200f, 2, 1, 1), request.signal)
        assertFalse(f.repo.phoneConnection.value)
        f.repo.setActiveSound(3)
        assertTrue(f.repo.finishPhoneHandoff("phone", request.id))
        runCurrent()
        assertTrue(f.repo.phoneConnection.value)
        assertFalse(f.repo.manualPhoneConnection.value)
        assertEquals(WatchControl.SOUND, f.linkedCommands.single().second.control)
        assertEquals(3, f.linkedCommands.single().second.value)
        assertFalse(f.repo.revokePhoneLink("other", request.id))
        assertTrue(f.repo.revokePhoneLink("phone", request.id))
        assertFalse(f.repo.phoneConnection.value)
        assertFalse(f.repo.finishPhoneHandoff("phone", request.id))
    }

    /**
     * Verifies that same process explicit reconnect imports phone state without sending
     * standalone settings.
     */
    @Test fun sameProcessExplicitReconnectImportsPhoneStateWithoutSendingStandaloneSettings() = runTest {
        val f = Fixture(this, preferencesOf(KEY_COLOR_PROGRESS to 99f, sound to 1))
        f.repo.start(Any())
        runCurrent()
        assertFalse(f.repo.shouldRefreshFromPhone("phone"))
        val initial = PhoneSignalState(true, true, false, 350L, 789f, 2, 1, 2)
        assertTrue(f.repo.connectFromPhone("phone", WatchConnectionRequest.create(initial)))
        f.repo.togglePhoneConnection(false)
        runCurrent()
        assertFalse(f.repo.phoneConnection.value)
        assertFalse(f.repo.blinkOn.value)
        assertEquals(99f, f.repo.colorProgress.value)
        assertTrue(f.repo.shouldRefreshFromPhone("phone"))
        assertFalse(f.repo.shouldRefreshFromPhone("other"))

        val reconnect = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        assertFalse(reconnect.signal.torchOn)
        assertFalse(f.repo.finishPhoneHandoff("phone", reconnect.id)) // A bare acknowledgment is insufficient.
        val currentPhone = initial.copy(frequency = 550L, color = 1200f, activeSound = 3)
        assertTrue(f.repo.finishPhoneHandoff("phone", reconnect.id, currentPhone))
        runCurrent()
        assertTrue(f.repo.phoneConnection.value)
        assertTrue(f.repo.flashOn.value)
        assertTrue(f.repo.blinkOn.value)
        assertFalse(f.repo.sosOn.value)
        assertEquals(550L, f.repo.blinkDelay.value)
        assertEquals(1200f, f.repo.colorProgress.value)
        assertEquals(3, f.repo.activeSound.value)
        assertEquals(1, f.repo.screenIndex.value)
        assertEquals(2, f.repo.activeColorButton.value)
        assertTrue(f.linkedCommands.isEmpty())
        assertFalse(f.repo.canPlayLocalSound)

        f.repo.togglePhoneConnection(false)
        assertEquals(99f, f.repo.colorProgress.value)
        assertEquals(1, f.repo.activeSound.value)
    }

    /**
     * Verifies that explicit disconnect persists only a node hint and never automatically
     * reconnects.
     */
    @Test fun explicitDisconnectPersistsOnlyANodeHintAndNeverAutomaticallyReconnects() = runTest {
        val original = Fixture(this)
        val owner = Any()
        original.repo.start(owner)
        runCurrent()
        assertTrue(original.repo.connectFromPhone("phone",
            WatchConnectionRequest.create(PhoneSignalState(true, true, false, 350L))))
        original.repo.togglePhoneConnection(false)
        runCurrent()
        original.repo.stop(owner)

        val reopened = Fixture(this, original.store.snapshot)
        reopened.repo.start(Any())
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(reopened.repo.shouldRefreshFromPhone("phone"))
        assertFalse(reopened.repo.phoneConnection.value)
        assertTrue(reopened.requests.isEmpty())
        assertTrue(reopened.linkedCommands.isEmpty())
        val reconnect = checkNotNull(reopened.repo.preparePhoneHandoff("phone"))
        assertTrue(reopened.repo.finishPhoneHandoff("phone", reconnect.id,
            PhoneSignalState(true, false, true, 450L)))
        assertTrue(reopened.repo.flashOn.value)
        assertTrue(reopened.repo.sosOn.value)
    }

    /**
     * Verifies that a different phone still receives the watch configuration.
     */
    @Test fun aDifferentPhoneStillReceivesTheWatchConfiguration() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        assertTrue(f.repo.connectFromPhone("phone",
            WatchConnectionRequest.create(PhoneSignalState(true, true, false, 350L))))
        f.repo.togglePhoneConnection(false)
        f.repo.switchSOS(true)
        val request = checkNotNull(f.repo.preparePhoneHandoff("other"))
        assertFalse(f.repo.shouldRefreshFromPhone("other"))
        assertTrue(request.signal.sosEnabled)
        assertTrue(f.repo.finishPhoneHandoff("other", request.id))
        assertTrue(f.repo.sosOn.value)
    }

    /**
     * Verifies that pending reconnect edits are rebased on the phone signal in tap order.
     */
    @Test fun pendingReconnectEditsAreRebasedOnThePhoneSignalInTapOrder() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        val phone = PhoneSignalState(true, true, false, 550L, 789f, 2, 1, 2)
        assertTrue(f.repo.connectFromPhone("phone", WatchConnectionRequest.create(phone)))
        f.repo.togglePhoneConnection(false)
        val request = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        f.repo.commitColor(1200f)
        f.repo.setActiveSound(3)
        assertTrue(f.repo.finishPhoneHandoff("phone", request.id, phone))
        runCurrent()
        val commands = f.linkedCommands.map { it.second }
        assertEquals(listOf(WatchControl.COLOR, WatchControl.SOUND), commands.map { it.control })
        assertEquals(listOf(phone.copy(color = 1200f), phone.copy(color = 1200f, activeSound = 3)),
            commands.map { it.signal })
        assertEquals(listOf(1L, 2L), commands.map { it.sequence })
        commands.forEach { assertEquals(it, WatchControlCommand.decode(it.encode())) }
    }

    /**
     * Verifies that pending reconnect mode and output choices replace only their intended
     * settings.
     */
    @Test fun pendingReconnectModeAndOutputChoicesReplaceOnlyTheirIntendedSettings() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        val phone = PhoneSignalState(true, true, false, 550L, 789f, 2, 1, 2)
        assertTrue(f.repo.connectFromPhone("phone", WatchConnectionRequest.create(phone)))
        f.repo.togglePhoneConnection(false)
        val request = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        f.repo.setFlash(true)
        f.repo.switchSOS(true)
        f.repo.selectColorButton(1)
        f.repo.setBlinkDelay(250L)
        f.repo.setBlinking(true, 250L)
        f.repo.updateScreenIndex(1)
        assertTrue(f.repo.finishPhoneHandoff("phone", request.id, phone.copy(screenIndex = 0)))
        runCurrent()
        val commands = f.linkedCommands.map { it.second }
        assertEquals(listOf(WatchControl.TORCH, WatchControl.SOS, WatchControl.COLOR_BUTTON,
            WatchControl.FREQUENCY, WatchControl.BLINK, WatchControl.SCREEN), commands.map { it.control })
        assertEquals(phone.copy(torchOn = false, frequency = 250L, activeColorButton = 1), commands.last().signal)
        commands.forEach { assertEquals(it, WatchControlCommand.decode(it.encode())) }
    }

    /**
     * Verifies that cancelled or mismatched reconnect cannot import phone state.
     */
    @Test fun cancelledOrMismatchedReconnectCannotImportPhoneState() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        val phone = PhoneSignalState(true, true, false, 350L)
        assertTrue(f.repo.connectFromPhone("phone", WatchConnectionRequest.create(phone)))
        f.repo.togglePhoneConnection(false)
        val request = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        assertFalse(f.repo.finishPhoneHandoff("other", request.id, phone))
        f.repo.cancelPhoneHandoff("phone", request.id)
        assertFalse(f.repo.finishPhoneHandoff("phone", request.id, phone))
        assertFalse(f.repo.phoneConnection.value)
        assertFalse(f.repo.flashOn.value)
    }

    /**
     * Verifies that explicit resync imports new phone settings and retains standalone
     * settings.
     */
    @Test fun explicitResyncImportsNewPhoneSettingsAndRetainsStandaloneSettings() = runTest {
        val f = Fixture(this, preferencesOf(KEY_COLOR_PROGRESS to 99f))
        f.repo.start(Any())
        runCurrent()
        val state = PhoneSignalState(true, true, false, 350L, 789f)
        val first = WatchConnectionRequest.create(state)
        val refresh = WatchConnectionRequest.create(state.copy(color = 690f, activeSound = 2))
        assertTrue(f.repo.connectFromPhone("phone", first))
        assertFalse(f.repo.connectFromPhone("other", refresh))
        assertTrue(f.repo.connectFromPhone("phone", refresh))
        runCurrent()
        assertEquals(690f, f.repo.colorProgress.value)
        assertEquals(2, f.repo.activeSound.value)
        assertTrue(f.linkedCommands.isEmpty())
        assertFalse(f.repo.revokePhoneLink("phone", first.id))
        assertTrue(f.repo.revokePhoneLink("phone", refresh.id))
        assertEquals(99f, f.repo.colorProgress.value)
    }

    /**
     * Verifies that queued actions carry the entire configuration at each tap.
     */
    @Test fun queuedActionsCarryTheEntireConfigurationAtEachTap() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        val initial = PhoneSignalState(true, true, false, 350L, 3000f, 2, 1, 1)
        assertTrue(f.repo.connectFromPhone("phone", WatchConnectionRequest.create(initial)))
        f.repo.commitColor(3400f)
        f.repo.setFlash(false)
        f.repo.switchSOS(true)
        runCurrent()
        val commands = f.linkedCommands.map { it.second }
        assertEquals(listOf(
            initial.copy(color = 3400f),
            initial.copy(color = 3400f, torchOn = false),
            initial.copy(color = 3400f, torchOn = false, blinkEnabled = false, sosEnabled = true)
        ), commands.map { it.signal })
        commands.forEach { assertEquals(it, WatchControlCommand.decode(it.encode())) }
    }

    /**
     * Verifies that changes during handoff keep their original snapshots in order.
     */
    @Test fun changesDuringHandoffKeepTheirOriginalSnapshotsInOrder() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        val invitation = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        f.repo.setBlinking(true, 350L)
        f.repo.setFlash(true)
        f.repo.switchSOS(true)
        assertTrue(f.linkedCommands.isEmpty())
        assertTrue(f.repo.finishPhoneHandoff("phone", invitation.id))
        runCurrent()
        val base = invitation.signal.copy(blinkEnabled = true, frequency = 350L)
        val commands = f.linkedCommands.map { it.second }
        assertEquals(listOf(
            base,
            base.copy(torchOn = true),
            base.copy(torchOn = true, blinkEnabled = false, sosEnabled = true)
        ), commands.map { it.signal })
        assertEquals(listOf(1L, 2L, 3L), commands.map { it.sequence })
        commands.forEach { assertEquals(it, WatchControlCommand.decode(it.encode())) }
    }

    /**
     * Verifies that timed mode commands carry the selected watch output.
     */
    @Test fun timedModeCommandsCarryTheSelectedWatchOutput() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        val request = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        assertTrue(f.repo.finishPhoneHandoff("phone", request.id))
        f.repo.setFlash(true)
        f.repo.switchSOS(true)
        f.repo.switchSOS(false)
        f.repo.selectColorButton(1)
        f.repo.setBlinking(true, 400L)
        runCurrent()

        val modeCommands = f.linkedCommands.map { it.second }
            .filter { it.control == WatchControl.SOS || it.control == WatchControl.BLINK }
        assertEquals(
            listOf(
                WatchControl.SOS to WatchModeChange.ENABLE_ON_TORCH.wireValue,
                WatchControl.SOS to WatchModeChange.DISABLE.wireValue,
                WatchControl.BLINK to WatchModeChange.ENABLE_ON_SCREEN.wireValue
            ),
            modeCommands.map { it.control to it.value }
        )
    }

    /**
     * Verifies that a reopened watch resumes its saved link only after phone acknowledges.
     */
    @Test fun aReopenedWatchResumesItsSavedLinkOnlyAfterPhoneAcknowledges() = runTest {
        val original = Fixture(this, preferencesOf(KEY_COLOR_PROGRESS to 99f))
        val owner = Any()
        original.repo.start(owner)
        runCurrent()
        val request = WatchConnectionRequest.create(PhoneSignalState(true, true, false, 350L, 789f))
        assertTrue(original.repo.connectFromPhone("phone", request))
        original.repo.commitColor(678f)
        runCurrent()
        original.repo.stop(owner)

        val reopened = Fixture(this, original.store.snapshot)
        val response = CompletableDeferred<WatchConnectionReply>()
        reopened.reply = { response.await() }
        reopened.repo.start(Any())
        runCurrent()
        val (phone, path, payload) = reopened.requests.single()
        assertEquals("phone", phone)
        assertEquals(WatchConnectionRequest.RESUME_STATE_PATH, path)
        val resume = checkNotNull(WatchResumeRequest.decode(payload))
        assertEquals(request.id, resume.linkId)
        assertFalse(reopened.repo.phoneConnection.value)
        assertFalse(reopened.repo.flashOn.value)
        assertEquals(99f, reopened.repo.colorProgress.value)
        response.complete(ACCEPTED)
        runCurrent()
        assertTrue(reopened.repo.phoneConnection.value)
        assertTrue(reopened.repo.flashOn.value)
        assertTrue(reopened.repo.blinkOn.value)
        assertEquals(678f, reopened.repo.colorProgress.value)
        assertTrue(reopened.linkedCommands.isEmpty())
        reopened.repo.setFlash(false)
        runCurrent()
        assertEquals(resume.commandId, reopened.linkedCommands.single().second.id)
        assertTrue(reopened.repo.revokePhoneLink("phone", request.id))
        assertEquals(99f, reopened.repo.colorProgress.value)
    }

    /**
     * Verifies that reachability loss pauses the link and its return resumes without replaying
     * controls.
     */
    @Test fun reachabilityLossPausesTheLinkAndItsReturnResumesWithoutReplayingControls() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        val request = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        assertTrue(f.repo.finishPhoneHandoff("phone", request.id))
        runCurrent()

        f.reachablePhones.value = setOf("other-phone")
        runCurrent()
        assertFalse(f.repo.phoneConnection.value)
        f.repo.setFlash(true)
        assertTrue(f.requests.isEmpty())
        f.reachablePhones.value = setOf("phone")
        runCurrent()
        assertTrue(f.repo.phoneConnection.value)
        assertEquals(WatchConnectionRequest.RESUME_STATE_PATH, f.requests.single().second)
        assertTrue(f.linkedCommands.isEmpty())
    }

    /**
     * Verifies that reconnect adopts every shared phone setting before enabling remote
     * controls.
     */
    @Test fun reconnectAdoptsEverySharedPhoneSettingBeforeEnablingRemoteControls() = runTest {
        val f = Fixture(this, preferencesOf(KEY_COLOR_PROGRESS to 99f, sound to 1))
        f.repo.start(Any())
        runCurrent()
        val initial = WatchConnectionRequest.create(PhoneSignalState(false, true, false, 350L, 789f, 2, 0, 1))
        assertTrue(f.repo.connectFromPhone("phone", initial))
        runCurrent()
        f.reachablePhones.value = emptySet()
        runCurrent()
        f.repo.switchSOS(true)
        f.repo.commitColor(17f)

        f.resumeSignal = PhoneSignalState(true, false, false, 725L, 1450f, 3, 1, 2)
        val acknowledgment = CompletableDeferred<WatchConnectionReply>()
        f.reply = { acknowledgment.await() }
        f.reachablePhones.value = setOf("phone")
        runCurrent()
        assertFalse(f.repo.phoneConnection.value)
        acknowledgment.complete(ACCEPTED)
        runCurrent()

        assertTrue(f.repo.phoneConnection.value)
        assertTrue(f.repo.flashOn.value)
        assertFalse(f.repo.blinkOn.value)
        assertFalse(f.repo.sosOn.value)
        assertEquals(725L, f.repo.blinkDelay.value)
        assertEquals(1450f, f.repo.colorProgress.value)
        assertEquals(3, f.repo.activeSound.value)
        assertEquals(1, f.repo.screenIndex.value)
        assertEquals(2, f.repo.activeColorButton.value)
        assertTrue(f.linkedCommands.isEmpty())

        f.repo.switchSOS(true)
        runCurrent()
        assertEquals(WatchModeChange.ENABLE_ON_TORCH.wireValue, f.linkedCommands.single().second.value)
        f.repo.togglePhoneConnection(false)
        runCurrent()
        assertEquals(99f, f.repo.colorProgress.value)
        assertEquals(1, f.repo.activeSound.value)
    }

    /**
     * Verifies that reopening the watch uses the current phone state instead of its saved
     * torch selection.
     */
    @Test fun reopeningTheWatchUsesTheCurrentPhoneStateInsteadOfItsSavedTorchSelection() = runTest {
        val original = Fixture(this)
        val owner = Any()
        original.repo.start(owner)
        runCurrent()
        val handoff = WatchConnectionRequest.create(PhoneSignalState(true, true, false, 350L, 789f, 2, 1, 2))
        assertTrue(original.repo.connectFromPhone("phone", handoff))
        runCurrent()
        original.repo.stop(owner)

        val reopened = Fixture(this, original.store.snapshot)
        reopened.resumeSignal = PhoneSignalState(false, false, true, 900L, 123f, 1, 0, 0)
        reopened.repo.start(Any())
        runCurrent()
        assertTrue(reopened.repo.phoneConnection.value)
        assertFalse(reopened.repo.flashOn.value)
        assertFalse(reopened.repo.blinkOn.value)
        assertTrue(reopened.repo.sosOn.value)
        assertEquals(900L, reopened.repo.blinkDelay.value)
        assertEquals(123f, reopened.repo.colorProgress.value)
        assertEquals(1, reopened.repo.activeSound.value)
        assertEquals(0, reopened.repo.screenIndex.value)
        assertEquals(0, reopened.repo.activeColorButton.value)
        assertTrue(reopened.linkedCommands.isEmpty())
    }

    /**
     * Verifies that explicit phone rejection clears the saved link.
     */
    @Test fun explicitPhoneRejectionClearsTheSavedLink() = runTest {
        val f = Fixture(this)
        val owner = Any()
        f.repo.start(owner)
        runCurrent()
        val request = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        assertTrue(f.repo.finishPhoneHandoff("phone", request.id))
        f.reply = { REJECTED }

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(WatchConnectionRequest.STATUS_PATH, f.requests.single().second)
        assertFalse(f.repo.phoneConnection.value)
        val reopened = Fixture(this, f.store.snapshot)
        reopened.repo.start(Any())
        runCurrent()
        assertTrue(reopened.requests.isEmpty())
        assertFalse(reopened.repo.phoneConnection.value)
    }

    /**
     * Verifies that an unresponsive phone clears the icon and recovery attempts back off.
     */
    @Test fun anUnresponsivePhoneClearsTheIconAndRecoveryAttemptsBackOff() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        val request = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        assertTrue(f.repo.finishPhoneHandoff("phone", request.id))
        f.reply = { RETRY }

        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(listOf(WatchConnectionRequest.STATUS_PATH, WatchConnectionRequest.RESUME_STATE_PATH,
            WatchConnectionRequest.RESUME_STATE_PATH, WatchConnectionRequest.RESUME_STATE_PATH,
            WatchConnectionRequest.RESUME_STATE_PATH), f.requests.map { it.second })
        assertFalse(f.repo.phoneConnection.value)
        assertTrue(f.linkedCommands.isEmpty())
    }

    /**
     * Verifies that a reopened phone announces availability only for the saved link.
     */
    @Test fun aReopenedPhoneAnnouncesAvailabilityOnlyForTheSavedLink() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        val handoff = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        assertTrue(f.repo.finishPhoneHandoff("phone", handoff.id))
        f.reply = { RETRY }
        assertFalse(f.repo.phonePaused("other", handoff.id))
        assertTrue(f.repo.phonePaused("phone", handoff.id))
        runCurrent()
        assertFalse(f.repo.phoneConnection.value)
        val response = CompletableDeferred<WatchConnectionReply>()
        f.reply = { response.await() }
        assertFalse(f.repo.phoneAvailable("other", handoff.id))
        assertFalse(f.repo.phoneAvailable("phone", java.util.UUID.randomUUID()))
        assertTrue(f.repo.phoneAvailable("phone", handoff.id))
        runCurrent()
        assertFalse(f.repo.phoneConnection.value)
        response.complete(ACCEPTED)
        runCurrent()
        assertTrue(f.repo.phoneConnection.value)
        assertTrue(f.linkedCommands.isEmpty())
        assertFalse(f.repo.phonePaused("phone", handoff.id)) // Delayed close from the older command session.

        f.repo.togglePhoneConnection(false)
        runCurrent()
        val before = f.requests.size
        assertFalse(f.repo.phoneAvailable("phone", handoff.id))
        runCurrent()
        assertEquals(before, f.requests.size)
    }

    /**
     * Verifies that the phone can reopen without a notice or a reachability change.
     */
    @Test fun thePhoneCanReopenWithoutANoticeOrAReachabilityChange() = runTest {
        val f = Fixture(this)
        val owner = Any()
        f.repo.start(owner)
        runCurrent()
        val handoff = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        assertTrue(f.repo.finishPhoneHandoff("phone", handoff.id))
        f.reply = { RETRY }
        assertTrue(f.repo.phonePaused("phone", handoff.id))
        advanceTimeBy(2_000)
        runCurrent()
        assertFalse(f.repo.phoneConnection.value)

        f.reply = { ACCEPTED }
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(f.repo.phoneConnection.value)
        assertTrue(f.linkedCommands.isEmpty())
        f.repo.stop(owner)
        val attempts = f.requests.size
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(attempts, f.requests.size)
    }

    /**
     * Verifies that empty status requires an acknowledged legacy check.
     */
    @Test fun emptyStatusRequiresAnAcknowledgedLegacyCheck() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        val request = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        assertTrue(f.repo.finishPhoneHandoff("phone", request.id))
        f.reply = { null }

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(WatchControl.CHECK, f.linkedCommands.single().second.control)
        assertTrue(f.repo.phoneConnection.value)
        f.commandAccepted = false
        advanceTimeBy(5_000)
        runCurrent()
        assertFalse(f.repo.phoneConnection.value)
    }

    /**
     * Verifies that explicit disconnect prevents resume and checks stop in the background.
     */
    @Test fun explicitDisconnectPreventsResumeAndChecksStopInTheBackground() = runTest {
        val f = Fixture(this)
        val owner = Any()
        f.repo.start(owner)
        runCurrent()
        val request = checkNotNull(f.repo.preparePhoneHandoff("phone"))
        assertTrue(f.repo.finishPhoneHandoff("phone", request.id))
        f.repo.stop(owner)
        advanceTimeBy(15_000)
        runCurrent()
        assertTrue(f.requests.isEmpty())

        f.repo.start(owner)
        runCurrent()
        assertTrue(f.repo.phoneConnection.value)
        f.repo.togglePhoneConnection(false)
        runCurrent()
        f.reachablePhones.value = emptySet()
        runCurrent()
        f.reachablePhones.value = setOf("phone")
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(listOf(WatchConnectionRequest.RESUME_STATE_PATH, WatchConnectionRequest.RELEASE_PATH),
            f.requests.map { it.second })
        assertFalse(f.repo.phoneConnection.value)
    }

    /**
     * Verifies that the implementation restores validated preferences and never restarts saved
     * remote torch.
     */
    @Test fun restoresValidatedPreferencesAndNeverRestartsSavedRemoteTorch() = runTest {
        val f = Fixture(this, preferencesOf(flash to true, blink to true, sos to true,
            sound to 99, KEY_ACTIVE_SCREEN to 99, KEY_FREQUENCY_PROGRESS to 2000f))
        f.repo.start(Any())
        runCurrent()
        assertTrue(f.repo.isInitialized.value)
        assertFalse(f.repo.flashOn.value)
        assertTrue(f.repo.sosOn.value)
        assertFalse(f.repo.blinkOn.value)
        assertEquals(1000L, f.repo.blinkDelay.value)
        assertEquals(3, f.repo.activeSound.value)
        assertEquals(1, f.repo.screenIndex.value)
    }

    /**
     * Verifies that non finite frequency uses the saved legacy delay.
     */
    @Test fun nonFiniteFrequencyUsesTheSavedLegacyDelay() = runTest {
        val f = Fixture(this, preferencesOf(KEY_FREQUENCY_PROGRESS to Float.NaN, delay to 350L))
        f.repo.start(Any())
        runCurrent()
        assertEquals(350L, f.repo.blinkDelay.value)
    }

    /**
     * Verifies that only the last owner can stop an active session.
     */
    @Test fun onlyTheLastOwnerCanStopAnActiveSession() = runTest {
        val f = Fixture(this)
        val first = Any()
        val replacement = Any()
        f.repo.start(first)
        f.repo.start(first)
        f.repo.start(replacement)
        runCurrent()
        f.repo.switchSOS(true)
        runCurrent()
        f.repo.stop(first)
        f.repo.stop(Any())
        assertTrue(f.repo.isForeground.value)
        assertTrue(f.repo.colorBlinkActive.value)
        assertEquals(0, f.soundStops)
        f.repo.stop(replacement)
        assertFalse(f.repo.isForeground.value)
        assertFalse(f.repo.colorBlinkActive.value)
        assertEquals(1, f.soundStops)
        advanceTimeBy(20_000L)
        runCurrent()
        assertFalse(f.repo.colorBlinkActive.value)
    }

    /**
     * Verifies that stop during initialization cannot resume an obsolete session.
     */
    @Test fun stopDuringInitializationCannotResumeAnObsoleteSession() = runTest {
        val f = Fixture(this, preferencesOf(sos to true))
        val gate = CompletableDeferred<Unit>()
        f.store.readGate = gate
        val owner = Any()
        f.repo.start(owner)
        runCurrent()
        f.repo.stop(owner)
        gate.complete(Unit)
        runCurrent()
        assertFalse(f.repo.isInitialized.value)
        assertFalse(f.repo.colorBlinkActive.value)
        f.repo.start(Any())
        runCurrent()
        assertTrue(f.repo.isInitialized.value)
        assertTrue(f.repo.colorBlinkActive.value)
    }

    /**
     * Verifies that switching to blink cancels the old SOS edges.
     */
    @Test fun switchingToBlinkCancelsTheOldSosEdges() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        f.repo.switchSOS(true)
        runCurrent()
        advanceTimeBy(100L)
        f.repo.setBlinking(true, 350L)
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()
        assertTrue(f.repo.colorBlinkActive.value)
        assertFalse(f.repo.sosOn.value)
        advanceTimeBy(250L)
        runCurrent()
        assertFalse(f.repo.colorBlinkActive.value)
    }

    /**
     * Verifies that disconnecting remote torch resumes the selected screen signal.
     */
    @Test fun disconnectingRemoteTorchResumesTheSelectedScreenSignal() = runTest {
        val f = Fixture(this)
        f.repo.start(Any())
        runCurrent()
        f.repo.setBlinking(true, 300L)
        f.repo.togglePhoneConnection(true)
        f.repo.setFlash(true)
        runCurrent()
        assertFalse(f.repo.colorBlinkActive.value)
        f.repo.togglePhoneConnection(false)
        runCurrent()
        assertFalse(f.repo.flashOn.value)
        assertTrue(f.repo.blinkOn.value)
        assertTrue(f.repo.colorBlinkActive.value)
    }

    /**
     * Verifies that queued preference writes survive stop and keep user action order.
     */
    @Test fun queuedPreferenceWritesSurviveStopAndKeepUserActionOrder() = runTest {
        val f = Fixture(this)
        val owner = Any()
        f.repo.start(owner)
        runCurrent()
        f.repo.setActiveSound(1)
        f.repo.setActiveSound(2)
        f.repo.setActiveSound(3)
        f.repo.setBlinking(true, 900L)
        f.repo.switchSOS(true)
        f.repo.stop(owner)
        runCurrent()
        assertEquals(listOf(1, 2, 3), f.store.writes.mapNotNull { it[sound] }.distinct())
        assertEquals(3, f.store.snapshot[sound])
        assertEquals(false, f.store.snapshot[blink])
        assertEquals(true, f.store.snapshot[sos])
        assertEquals(900L, f.store.snapshot[delay])
    }

    /**
     * Verifies that read failure uses defaults and reports the error.
     */
    @Test fun readFailureUsesDefaultsAndReportsTheError() = runTest {
        val f = Fixture(this)
        val error = IOException("cannot read")
        f.store.readFailure = error
        f.repo.start(Any())
        runCurrent()
        assertTrue(f.repo.isInitialized.value)
        assertEquals(400L, f.repo.blinkDelay.value)
        assertEquals(listOf(error), f.errors)
    }

    /**
     * Verifies that failed write does not kill the preference writer.
     */
    @Test fun failedWriteDoesNotKillThePreferenceWriter() = runTest {
        val f = Fixture(this)
        val error = IOException("cannot save")
        f.store.nextWriteFailure = error
        f.repo.setActiveSound(1)
        runCurrent()
        f.repo.setActiveSound(2)
        runCurrent()
        assertEquals(listOf(error), f.errors)
        assertEquals(2, f.store.snapshot[sound])
    }

    /**
     * Verifies that restart retains modes and starts a fresh signal job.
     */
    @Test fun restartRetainsModesAndStartsAFreshSignalJob() = runTest {
        val f = Fixture(this)
        val owner = Any()
        f.repo.start(owner)
        runCurrent()
        f.repo.switchSOS(true)
        runCurrent()
        f.repo.stop(owner)
        advanceTimeBy(100L)
        f.repo.start(Any())
        runCurrent()
        assertTrue(f.repo.colorBlinkActive.value)
        advanceTimeBy(100L)
        runCurrent()
        assertTrue("An old session's edge must not stop the fresh pulse", f.repo.colorBlinkActive.value)
        advanceTimeBy(100L)
        runCurrent()
        assertFalse(f.repo.colorBlinkActive.value)
    }
}
