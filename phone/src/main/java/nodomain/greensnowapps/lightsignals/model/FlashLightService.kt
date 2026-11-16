package nodomain.greensnowapps.lightsignals.model

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.greensnowapps.lightsignals.NOTIF_ID
import nodomain.greensnowapps.lightsignals.PhoneMainActivity
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.core.signal.SOS_UNIT_MS
import nodomain.greensnowapps.lightsignals.core.signal.runSosSignal
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs steady, blinking and SOS torch output with a foreground notification.
 *
 * Observes requested modes on Main and serializes camera writes through the IO-backed torch
 * driver. Timed sessions hold a partial wake lock through cancellation-safe torch cleanup.
 */
@AndroidEntryPoint
class FlashLightService : Service() {
    /**
     * Injected serialized torch driver; tests can replace it with a recording backend.
     */
    @Inject lateinit var torchDriver: PhoneTorchDriver
    /**
     * Injected owner of the phone signal requests observed by the service.
     */
    @Inject lateinit var phoneCtrl: PhoneControlRepository
    /**
     * Injected phone signal-sample player used by torch phases.
     */
    @Inject lateinit var soundPlayer: PhoneSoundPoolPlayer

    /**
     * Main scope for torch observation and notification refresh; cancelled in onDestroy.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /**
     * Per-service hardware token that prevents delayed cleanup from changing a replacement
     * signal.
     */
    private val torchOwner = Any()
    /**
     * Stable notification channel ID retained across flashlight sessions and app updates.
     */
    private val notificationChannelId = "light_signals_channel_id"
    /**
     * Whether initial foreground promotion succeeded, used when removing the notification on
     * destruction.
     */
    private var foregroundStarted = false
    /**
     * Lazily allocated, non-reference-counted partial wake lock reused by timed signals.
     */
    private var signalWakeLock: PowerManager.WakeLock? = null
    /**
     * Pairs timed-signal CPU ownership with cancellation-safe torch-off cleanup and lock
     * release.
     */
    private val signalSession = TorchSignalSession(
        acquireWakeLock = ::acquireSignalWakeLock,
        releaseWakeLock = ::releaseSignalWakeLock,
        switchOffTorch = { torchDriver.setEnabled(torchOwner, false) }
    )

    /**
     * Groups the requested torch output and timing settings for one service signal session.
     *
     * @property enabled Whether to enable the requested output or mode.
     * @property blink Whether Blink mode is requested.
     * @property sos Whether SOS mode is requested.
     * @property delayMs Blink half-period in milliseconds.
     */
    private data class TorchMode(
        val enabled: Boolean,
        val blink: Boolean,
        val sos: Boolean,
        val delayMs: Long
    )

    /**
     * Enters foreground mode and starts observing torch requests on Main.
     *
     * Posts the notification before starting signal work. If foreground startup throws a
     * runtime exception, logs the failure, clears torch selection through the repository and
     * stops the service. A service-owned coroutine also reposts the notification on refresh
     * requests while torch output remains selected.
     */
    override fun onCreate() {
        super.onCreate()
        try {
            postForegroundNotification()
            foregroundStarted = true
        } catch (error: RuntimeException) {
            Log.w("FlashLightService", "Unable to enter foreground", error)
            phoneCtrl.onTorchServiceUnavailable()
            stopSelf()
            return
        }
        observeTorchMode()
        serviceScope.launch {
            phoneCtrl.torchNotificationRefreshes.collect {
                if (phoneCtrl.flashOn.value) {
                    try {
                        postForegroundNotification()
                    } catch (error: RuntimeException) {
                        Log.w("FlashLightService", "Unable to refresh foreground notification", error)
                    }
                }
            }
        }
    }

    /**
     * Stops this start request when the repository no longer selects torch output.
     *
     * Android invokes this callback on Main. The observer started by [onCreate] owns signal
     * playback; this callback does not start a second signal job.
     *
     * @param intent Start intent, if supplied; signal settings come from the repository.
     * @param flags Platform service-start flags; this implementation does not use them.
     * @param startId Start request ID passed to stopSelf when torch output is not selected.
     * @return START_NOT_STICKY, so Android does not restart a killed service without another
     *     explicit start request.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!phoneCtrl.flashOn.value) {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /**
     * Launches service-owned observation of torch selection, timed modes and blink delay.
     *
     * Call on Main. Acquires serialized hardware ownership, bounds the blink half-period to
     * 100..1000 milliseconds and replaces the running signal when its settings change. Mode
     * replacement cancels and awaits the previous signal's cleanup. SOS takes precedence over
     * Blink, and only timed torch modes acquire the partial wake lock.
     *
     * The coroutine suspends between phases and dispatches hardware writes through the
     * IO-backed driver. Reports runtime failures during observation to the repository and
     * stops the service; cancellation propagates. Releases hardware ownership in
     * NonCancellable when observation ends, including on service destruction.
     */
    private fun observeTorchMode() {
        serviceScope.launch {
            try {
                torchDriver.acquire(torchOwner)
                combine(
                    phoneCtrl.flashOn,
                    phoneCtrl.blinkOn,
                    phoneCtrl.sosOn,
                    phoneCtrl.blinkDelay
                ) { enabled, blink, sos, delayMs ->
                    TorchMode(enabled, blink, sos, delayMs.coerceIn(100L, 1000L))
                }.distinctUntilChanged().collectLatest { mode ->
                    signalSession.run(keepCpuAwake = mode.enabled && (mode.blink || mode.sos)) {
                        when {
                            !mode.enabled -> stopSelf()
                            mode.sos -> runSos()
                            mode.blink -> {
                                while (currentCoroutineContext().isActive) {
                                    if (!driveTorch(true)) return@run
                                    delay(mode.delayMs.milliseconds)
                                    driveTorch(false)
                                    delay(mode.delayMs.milliseconds)
                                }
                            }
                            else -> {
                                if (driveTorch(true)) awaitCancellation()
                            }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: RuntimeException) {
                Log.w("FlashLightService", "Torch session failed", error)
                phoneCtrl.onTorchServiceUnavailable()
                stopSelf()
            } finally {
                withContext(NonCancellable) { torchDriver.release(torchOwner) }
            }
        }
    }

    /**
     * Writes one torch phase and requests the selected sound for a successful timed on-phase.
     *
     * Call from the service's Main coroutine. Suspends while the driver serializes the write
     * on IO. A failed enable clears torch selection and stops the service; a failed disable
     * only returns false. Plays a sample only when torch output and Blink or SOS are still
     * selected in the repository.
     *
     * @param enabled True for an on-phase, or false for an off-phase.
     * @return True when the driver accepts the write, including a superseded-owner no-op;
     *     false when the driver reports a hardware failure.
     */
    private suspend fun driveTorch(enabled: Boolean): Boolean {
        if (!torchDriver.setEnabled(torchOwner, enabled)) {
            if (enabled) {
                phoneCtrl.onTorchServiceUnavailable()
                stopSelf()
            }
            return false
        }
        if (enabled && phoneCtrl.flashOn.value &&
            (phoneCtrl.blinkOn.value || phoneCtrl.sosOn.value)
        ) {
            soundPlayer.playOnce(phoneCtrl.activeSound.value)
        }
        return true
    }

    /**
     * Repeats the shared SOS Morse pattern through [driveTorch].
     *
     * Call from the service's Main coroutine. Suspends between phases using SOS_UNIT_MS and
     * stops on cancellation or a failed on-phase. The enclosing [signalSession] owns final
     * torch-off cleanup and wake-lock release.
     */
    private suspend fun runSos() {
        runSosSignal(SOS_UNIT_MS, ::driveTorch)
    }

    /**
     * Acquires a non-reference-counted partial wake lock for a timed torch signal.
     *
     * Call on Main. Lazily creates the lock from the application context and reuses it without
     * a fixed timeout so a long signal can continue. [TorchSignalSession] pairs acquisition
     * with release after final torch-off cleanup, including on cancellation.
     */
    @SuppressLint("WakelockTimeout")
    private fun acquireSignalWakeLock() {
        val wakeLock = signalWakeLock ?: applicationContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:FlashLightService")
            .apply { setReferenceCounted(false) }
            .also { signalWakeLock = it }
        wakeLock.acquire()
    }

    /**
     * Releases the cached partial wake lock if it is held.
     *
     * Call on Main after torch cleanup. Does nothing before acquisition or after release, and
     * retains the lock object for the next timed signal.
     */
    private fun releaseSignalWakeLock() {
        signalWakeLock?.let { if (it.isHeld) it.release() }
    }

    /**
     * Returns no binder because torch control uses a started foreground service.
     *
     * Android invokes this callback on Main.
     *
     * @param intent Binding intent supplied by Android; this implementation does not use it.
     * @return Null; this service does not support bound clients.
     */
    override fun onBind(intent: Intent?) = null

    /**
     * Cancels service-owned coroutines and removes the foreground notification on Main.
     *
     * Does not wait for suspended hardware cleanup. Signal cleanup and hardware-owner release
     * continue in NonCancellable; the driver's owner token prevents an old service from
     * switching off a replacement service's torch session.
     */
    override fun onDestroy() {
        // The singleton driver's owner token prevents delayed cleanup from touching
        // a replacement service's hardware session.
        serviceScope.cancel()
        if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /**
     * Builds and posts or refreshes the notification through the foreground-service API.
     *
     * Call on Main. Specifies FOREGROUND_SERVICE_TYPE_SPECIAL_USE on Android 14 and later, and
     * uses the older startForeground overload on earlier versions. Platform runtime failures
     * propagate to the startup or notification-refresh handler.
     */
    private fun postForegroundNotification() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    /**
     * Builds the silent, ongoing flashlight notification that opens [PhoneMainActivity].
     *
     * Call on Main. Registers the low-importance notification channel on Android 8 and later.
     * Uses the round launcher icon on Android 13 and later and the traffic-light drawable on
     * earlier versions. Creates an immutable Activity pending intent; posting remains the
     * caller's responsibility.
     *
     * @return The configured notification for the active flashlight foreground service.
     */
    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    notificationChannelId,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_description)
                }
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, PhoneMainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val smallIcon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            R.mipmap.ic_launcher_round
        } else {
            R.drawable.outline_traffic_24
        }
        return NotificationCompat.Builder(this, notificationChannelId)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_text))
            )
            .setSmallIcon(smallIcon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
