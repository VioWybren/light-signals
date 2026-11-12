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

class WearControlRepositoryTest {
    private val blink = booleanPreferencesKey(WearConstants.PREFERENCE_BLINK_DON)
    private val sos = booleanPreferencesKey(WearConstants.PREFERENCE_SOS_ON)
    private val flash = booleanPreferencesKey(WearConstants.PREFERENCE_FLASH_ON)
    private val sound = intPreferencesKey(WearConstants.PREFERENCE_ACTIVE_SOUND)
    private val delay = longPreferencesKey(WearConstants.PREFERENCE_BLINK_DELAY)

    private class Fixture(scope: TestScope, saved: Preferences = emptyPreferences()) {
        val store = FakePreferencesDataStore(saved)
        val errors = mutableListOf<IOException>()
        val linkedCommands = mutableListOf<Pair<String, WatchControlCommand>>()
        val reachablePhones = MutableStateFlow(setOf("phone"))
        val requests = mutableListOf<Triple<String, String, ByteArray>>()
        var reply: suspend (String) -> WatchConnectionReply? = { ACCEPTED }
        var resumeSignal: PhoneSignalState? = null
        var commandAccepted = true
        var soundStops = 0
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

    @Test fun nonFiniteFrequencyUsesTheSavedLegacyDelay() = runTest {
        val f = Fixture(this, preferencesOf(KEY_FREQUENCY_PROGRESS to Float.NaN, delay to 350L))
        f.repo.start(Any())
        runCurrent()
        assertEquals(350L, f.repo.blinkDelay.value)
    }

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
