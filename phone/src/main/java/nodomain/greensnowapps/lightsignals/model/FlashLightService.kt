package nodomain.greensnowapps.lightsignals.model

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nodomain.greensnowapps.lightsignals.ACTION_PERMISSIONS_MISSING
import nodomain.greensnowapps.lightsignals.EXTRA_MISSING_CAMERA
import nodomain.greensnowapps.lightsignals.EXTRA_MISSING_NOTIFICATIONS
import nodomain.greensnowapps.lightsignals.NOTIF_ID
import nodomain.greensnowapps.lightsignals.PhoneMainActivity
import nodomain.greensnowapps.lightsignals.R
import nodomain.greensnowapps.lightsignals.SOS_UNIT_MS
import nodomain.greensnowapps.lightsignals.utils.PermissionUtils
import javax.inject.Inject


@AndroidEntryPoint
class FlashLightService : Service() {
    @Inject
    lateinit var cameraManager: CameraManager

    @Inject
    lateinit var phoneCtrl: PhoneControlRepository

    @Inject
    lateinit var soundPlayer: PhoneSoundPoolPlayer

    private val notificationChannelId = "light_signals_channel_id"
    lateinit var notificationTitle: String
    lateinit var notificationText: String
    private var notificationManager: NotificationManagerCompat? = null
    private var cameraId: String? = null
    private var blinkJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var sosJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationTitle = getString(R.string.notification_title)
        notificationText = getString(R.string.notification_text)

        if (!PermissionUtils.hasPostNotificationsPermission(this)) {
            notifyUiPermissionsMissing()
            stopSelf()
            return
        }

        if (!PermissionUtils.hasCameraPermission(this)) {
            notifyUiPermissionsMissing()
            stopSelf()
            return
        } else {
            enableCamera()
        }
        notificationManager = NotificationManagerCompat.from(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, NOTIF_ID, addNotification(),
                FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            this.startForeground(NOTIF_ID, addNotification())
        }

        serviceScope.launch {
            phoneCtrl.blinkOn.collect { blink ->
                blinkJob?.cancel()
                if (!blink) {
                    stopBlinkFlash()
                    if (phoneCtrl.flashOn.value) {
                        switchFlashOn(true)
                    }
                }
            }
        }
        serviceScope.launch {
            phoneCtrl.flashOn.collect { on ->
                if (on) {
                    switchFlashOn(true)
                } else {
                    switchFlashOn(false)
                }
            }
        }

        serviceScope.launch {
            phoneCtrl.flashOn
                .combine(phoneCtrl.blinkOn) { a, b -> a && b }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    startBlinkFlash()
                }
        }

        serviceScope.launch {
            phoneCtrl.flashOn.combine(phoneCtrl.sosOn) { a, b -> a && b }
                .distinctUntilChanged()
                .collect {
                    if (it) setSos(true) else setSos(false)
                }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!PermissionUtils.hasPostNotificationsPermission(this)) {
                 notifyUiPermissionsMissing()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!PermissionUtils.hasCameraPermission(this)) {
            notifyUiPermissionsMissing()
            stopSelf()
            return START_NOT_STICKY
        } else {
            enableCamera()
        }
        return START_NOT_STICKY
    }

    private fun startBlinkFlash() {
        blinkJob?.cancel()
        blinkJob = serviceScope.launch {
              while (isActive && phoneCtrl.flashOn.value && phoneCtrl.blinkOn.value) {
                switchFlashOn(true)
                delay(phoneCtrl.blinkDelay.value)
                switchFlashOn(false)
                delay(phoneCtrl.blinkDelay.value)
            }
        }
    }

    private fun stopBlinkFlash() {
        blinkJob?.cancel()
    }

    private fun switchFlashOn(status: Boolean) {
        cameraId?.let { cameraManager.setTorchMode(it, status) }
        if (status && phoneCtrl.activeSound.value != 0 &&
            (phoneCtrl.flashOn.value && (phoneCtrl.blinkOn.value || phoneCtrl.sosOn.value))
        ) {
            soundPlayer.playOnce(phoneCtrl.activeSound.value)
        }
    }

    private fun setSos(on: Boolean) {
        if (on) {
            stopBlinkFlash()
            startTorchSos()
        } else {
            stopTorchSos()
        }
    }

    private fun stopTorchSos() {
        sosJob?.cancel()
    }

    private fun startTorchSos() {
        sosJob?.cancel()
        sosJob = CoroutineScope(Dispatchers.Default).launch {
            val dot = SOS_UNIT_MS
            val dash = 3 * SOS_UNIT_MS
            val gap = SOS_UNIT_MS
            val letter = 3 * SOS_UNIT_MS
            val word = 7 * SOS_UNIT_MS

            suspend fun flash(duration: Long) {
                switchFlashOn(true)
                delay(duration)
                switchFlashOn(false)
                delay(gap)
            }

            while (isActive && phoneCtrl.sosOn.value) {
                repeat(3) { flash(dot) }
                delay(letter - gap)
                repeat(3) { flash(dash) }
                delay(letter - gap)
                repeat(3) { flash(dot) }
                delay(word - gap)
            }
        }
    }

    private fun enableCamera() {
        cameraId = cameraManager.cameraIdList.firstOrNull {
            cameraManager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    private fun notifyUiPermissionsMissing() {
        val intent = Intent(ACTION_PERMISSIONS_MISSING)
        intent.putExtra(EXTRA_MISSING_CAMERA, !PermissionUtils.hasCameraPermission(this))
        intent.putExtra(
            EXTRA_MISSING_NOTIFICATIONS,
            !PermissionUtils.hasPostNotificationsPermission(this)
        )
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        blinkJob?.cancel()
        sosJob?.cancel()
        switchFlashOn(false)
        phoneCtrl.setFlashBlinkMode(false)
        soundPlayer.release()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun addNotification(): Notification {
        val notificationIntent = Intent(this, PhoneMainActivity::class.java)
        val pendingIntent: PendingIntent?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_MUTABLE)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val notificationBuilder: NotificationCompat.Builder =
                NotificationCompat.Builder(this, notificationChannelId)
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
                    .setColor(Color.BLUE)
                    .setSmallIcon(R.drawable.outline_traffic_24)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setSound(null)
                    .setDeleteIntent(createDeleteIntent())
                    .setOngoing(true)
                    .setContentIntent(pendingIntent)
            return notificationBuilder.build()
        } else {
            createNotificationChannel()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val notificationBuilder: NotificationCompat.Builder =
                    NotificationCompat.Builder(this, notificationChannelId)
                        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                        .setContentTitle(notificationTitle)
                        .setContentText(notificationText)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
                        .setColor(Color.BLUE)
                        .setSmallIcon(R.drawable.outline_traffic_24)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setSound(null)
                        .setDeleteIntent(createDeleteIntent())
                        .setSilent(true)
                        .setOngoing(true)
                        .setContentIntent(pendingIntent)
                return notificationBuilder.build()
            } else {
                val notificationBuilder: NotificationCompat.Builder =
                    NotificationCompat.Builder(this, notificationChannelId)
                        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                        .setContentTitle(notificationTitle)
                        .setContentText(notificationText)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
                        .setColor(Color.BLUE)
                        .setSmallIcon(R.mipmap.ic_launcher_round)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setSound(null)
                        .setDeleteIntent(createDeleteIntent())
                        .setSilent(true)
                        .setOngoing(true)
                        .setContentIntent(pendingIntent)
                return notificationBuilder.build()
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channelName = getString(R.string.notification_channel_name)
        val channelDescription = getString(R.string.notification_channel_description)
        val channelImportance = NotificationManager.IMPORTANCE_HIGH

        val channel = NotificationChannel(notificationChannelId, channelName, channelImportance)
        channel.description = channelDescription
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

        notificationManager?.createNotificationChannel(channel)
    }

    private fun createDeleteIntent(): PendingIntent? {
        val intent = Intent()
        intent.setAction("nodomain.greensnowapps.lightsignals.action_notification_deleted")

        return PendingIntent.getBroadcast(
            this,
            0,
            intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
    }

}