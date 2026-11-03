package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import nodomain.greensnowapps.lightsignals.R
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class WearSoundPoolPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val lock = Any()
    private val soundIdMap = mutableMapOf<Int, Int>()
    private val loadedSoundIds = mutableSetOf<Int>()
    private var pendingSampleId: Int? = null
    private var currentStreamId = 0
    private var lastPlayTime = 0L
    private val volumeLevel = 0.5f
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    init {
        soundPool.setOnLoadCompleteListener { pool, sampleId, status ->
            synchronized(lock) {
                if (status == 0) {
                    loadedSoundIds += sampleId
                    if (pendingSampleId == sampleId) {
                        pendingSampleId = null
                        playLoaded(pool, sampleId)
                    }
                } else {
                    if (pendingSampleId == sampleId) pendingSampleId = null
                    Log.w("WearSoundPool", "Sound sample failed to load: $sampleId ($status)")
                }
            }
        }
        mapOf(1 to R.raw.beep_1, 2 to R.raw.beep_2, 3 to R.raw.beep_3)
            .forEach { (key, resource) ->
                soundIdMap[key] = soundPool.load(context, resource, 1)
            }
    }

    fun playOnce(sound: Int) {
        synchronized(lock) {
            val sampleId = soundIdMap[sound] ?: return
            val now = SystemClock.uptimeMillis()
            if (lastPlayTime != 0L && now - lastPlayTime < 120L) return
            lastPlayTime = now
            if (sampleId in loadedSoundIds) {
                pendingSampleId = null
                playLoaded(soundPool, sampleId)
            } else {
                pendingSampleId = sampleId
            }
        }
    }

    // Called while holding lock, including from the asynchronous load callback.
    private fun playLoaded(pool: SoundPool, sampleId: Int) {
        if (currentStreamId != 0) pool.stop(currentStreamId)
        currentStreamId = pool.play(sampleId, volumeLevel, volumeLevel, 1, 0, 1f)
    }

    fun stopAll() {
        synchronized(lock) {
            pendingSampleId = null
            if (currentStreamId != 0) soundPool.stop(currentStreamId)
            currentStreamId = 0
            lastPlayTime = 0L
        }
    }

    fun setVolume(value: Float) {
        val volume = value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC, (volume * maximum).roundToInt(), 0
            )
        } catch (error: RuntimeException) {
            Log.w("WearSoundPool", "Unable to adjust media volume", error)
        }
    }
}
