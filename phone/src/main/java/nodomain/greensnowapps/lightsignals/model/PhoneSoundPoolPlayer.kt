package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import nodomain.greensnowapps.lightsignals.R
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_STREAMS = 3

@Singleton
class PhoneSoundPoolPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val lock = Any()
    private val soundResMap = mapOf(1 to R.raw.beep_1, 2 to R.raw.beep_2, 3 to R.raw.beep_3)
    private val soundIdMap = mutableMapOf<Int, Int>()
    private val loadedSoundIds = mutableSetOf<Int>()
    private val pendingPlays = mutableSetOf<Int>()
    private var soundPool: SoundPool? = null
    private var currentStreamId = 0
    private var lastPlayTime = 0L
    private val minGapMs = 120L

    init {
        initSoundPool()
    }

    fun initSoundPool() = synchronized(lock) {
        if (soundPool != null) return@synchronized
        val pool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        soundPool = pool
        pool.setOnLoadCompleteListener { completedPool, sampleId, status ->
            synchronized(lock) {
                if (completedPool === soundPool) {
                    val requested = pendingPlays.remove(sampleId)
                    if (status == 0) {
                        loadedSoundIds += sampleId
                        if (requested) playLoaded(completedPool, sampleId)
                    }
                }
            }
        }
        soundResMap.forEach { (key, resource) ->
            soundIdMap[key] = pool.load(context, resource, 1)
        }
    }

    fun playOnce(resId: Int) = synchronized(lock) {
        if (resId !in soundResMap) return@synchronized
        val now = SystemClock.uptimeMillis()
        if (lastPlayTime != 0L && now - lastPlayTime < minGapMs) return@synchronized
        lastPlayTime = now
        initSoundPool()
        val pool = soundPool ?: return@synchronized
        val sampleId = soundIdMap[resId] ?: return@synchronized
        if (sampleId in loadedSoundIds) playLoaded(pool, sampleId) else pendingPlays += sampleId
    }

    // All callers hold lock. Sample IDs never stand in for playback stream IDs.
    private fun playLoaded(pool: SoundPool, sampleId: Int) {
        if (currentStreamId != 0) pool.stop(currentStreamId)
        currentStreamId = pool.play(sampleId, 1f, 1f, 1, 0, 1f)
    }

    fun stopAll() = synchronized(lock) {
        if (currentStreamId != 0) soundPool?.stop(currentStreamId)
        currentStreamId = 0
        pendingPlays.clear()
    }

    fun release() = synchronized(lock) {
        soundPool?.setOnLoadCompleteListener(null)
        soundPool?.release()
        soundPool = null
        currentStreamId = 0
        lastPlayTime = 0L
        soundIdMap.clear()
        loadedSoundIds.clear()
        pendingPlays.clear()
    }
}

