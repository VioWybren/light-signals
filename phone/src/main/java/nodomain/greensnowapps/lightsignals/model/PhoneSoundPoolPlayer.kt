package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import nodomain.greensnowapps.lightsignals.R
//import okio.AsyncTimeout.Companion.lock
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_STREAMS = 3

@Singleton
class PhoneSoundPoolPlayer @Inject constructor(
    @param: ApplicationContext private val context: Context
) {
    private val lock = Any()

    private val soundResMap = mapOf(
        1 to R.raw.beep_1,
        2 to R.raw.beep_2,
        3 to R.raw.beep_3,
    )
    private val soundIdMap = mutableMapOf<Int, Int>()
    private val loadedSoundIds = mutableSetOf<Int>()
    private val pendingPlays = mutableListOf<Int>()

    private lateinit var soundPool: SoundPool
    private var currentStreamId: Int = 0
    private var lastPlayTime = 0L
    private val minGapMs = 120L

    init {
        initSoundPool()
    }

    fun initSoundPool() {
        soundPool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()

        soundPool.setOnLoadCompleteListener { sp, sampleId, status ->
            if (status == 0) {
                loadedSoundIds += sampleId
                pendingPlays.filter { it == sampleId }.forEach { sid ->
                    sp.play(sid, 1f, 1f, 1, /*loop=*/0, 1f)
                    pendingPlays.remove(sid)
                }
            }
        }

        soundResMap.forEach { (key, res) ->
            val sid = soundPool.load(context, res, /*priority=*/1)
            soundIdMap[key] = sid
        }
    }

    fun playOnce(resId: Int) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastPlayTime < minGapMs) {
            return
        }
        lastPlayTime = currentTime
        playSound(resId)
    }

    private fun playSound(n: Int) {
        synchronized(lock) {
            if (soundIdMap[n] == null) {
                initSoundPool()
            } else {
                val soundId = soundIdMap[n] ?: return
                if (soundId in loadedSoundIds) {
                    stopCurrentStream()
                    currentStreamId = soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
                } else {
                    pendingPlays += soundId
                }
            }
        }
    }

    private fun stopCurrentStream() {
        synchronized(lock) {
            if (currentStreamId != 0) {
                try {
                    soundPool.stop(currentStreamId)
                } catch (_: Exception) {
                }
                currentStreamId = 0
            }
        }
    }

    fun stopAll() {
        soundIdMap.values.forEach { streamID -> soundPool.stop(streamID) }
        pendingPlays.clear()
    }

    fun release() {
        soundPool.release()
        soundIdMap.clear()
        loadedSoundIds.clear()
        pendingPlays.clear()
    }

}

