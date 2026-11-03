package nodomain.greensnowapps.lightsignals.model

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
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
import nodomain.greensnowapps.lightsignals.ACTION_PERMISSIONS_MISSING
import nodomain.greensnowapps.lightsignals.EXTRA_MISSING_CAMERA
import nodomain.greensnowapps.lightsignals.NOTIF_ID
import nodomain.greensnowapps.lightsignals.PhoneMainActivity
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.SOS_UNIT_MS
import nodomain.greensnowapps.lightsignals.utils.PermissionUtils
import javax.inject.Inject

@AndroidEntryPoint
class FlashLightService : Service() {
    @Inject lateinit var torchDriver: PhoneTorchDriver
    @Inject lateinit var phoneCtrl: PhoneControlRepository
    @Inject lateinit var soundPlayer: PhoneSoundPoolPlayer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val torchOwner = Any()
    private val notificationChannelId = "light_signals_channel_id"
    private var foregroundStarted = false

    private data class TorchMode(
        val enabled: Boolean,
        val blink: Boolean,
        val sos: Boolean,
        val delayMs: Long
    )

    override fun onCreate() {
        super.onCreate()
        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this, NOTIF_ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
            foregroundStarted = true
        } catch (error: RuntimeException) {
            Log.w("FlashLightService", "Unable to enter foreground", error)
            phoneCtrl.onTorchServiceUnavailable()
            stopSelf()
            return
        }
        // Satisfy startForegroundService's platform contract before an early permission exit.
        // This special-use service can post its notification without camera permission.
        if (!PermissionUtils.hasCameraPermission(this)) {
            notifyUiPermissionsMissing()
            phoneCtrl.onTorchServiceUnavailable()
            stopSelf()
            return
        }
        observeTorchMode()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!PermissionUtils.hasCameraPermission(this)) {
            notifyUiPermissionsMissing()
            phoneCtrl.onTorchServiceUnavailable()
            stopSelf(startId)
        } else if (!phoneCtrl.flashOn.value) {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

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
                    try {
                        when {
                            !mode.enabled -> stopSelf()
                            mode.sos -> runSos()
                            mode.blink -> {
                                while (currentCoroutineContext().isActive) {
                                    if (!driveTorch(true)) return@collectLatest
                                    delay(mode.delayMs)
                                    driveTorch(false)
                                    delay(mode.delayMs)
                                }
                            }
                            else -> {
                                if (driveTorch(true)) awaitCancellation()
                            }
                        }
                    } finally {
                        withContext(NonCancellable) { torchDriver.setEnabled(torchOwner, false) }
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

    private suspend fun runSos() {
        runSosSignal(SOS_UNIT_MS, ::driveTorch)
    }

    private fun notifyUiPermissionsMissing() {
        sendBroadcast(
            Intent(ACTION_PERMISSIONS_MISSING)
                .setPackage(packageName)
                .putExtra(EXTRA_MISSING_CAMERA, true)
        )
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        // The singleton driver's owner token prevents delayed cleanup from touching
        // a replacement service's hardware session.
        serviceScope.cancel()
        if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

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
        return NotificationCompat.Builder(this, notificationChannelId)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_text))
            )
            .setSmallIcon(R.drawable.outline_traffic_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
