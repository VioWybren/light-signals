package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import nodomain.greensnowapps.lightsignals.R
import okio.AsyncTimeout.Companion.lock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val TAG = "WearSoundPoolPlayer"

private const val MAX_STREAMS = 3

@Singleton
class WearSoundPoolPlayer @Inject constructor(
    @param: ApplicationContext private val context: Context
) {
    // Map logical IDs → resource IDs
    private val soundResMap = mapOf(
        1 to R.raw.beep_1,
        2 to R.raw.beep_2,
        3 to R.raw.beep_3,
//        4 to R.raw.beep_silence
    )
    private val soundIdMap = mutableMapOf<Int, Int>()
    private val loadedSoundIds = mutableSetOf<Int>()
    private val pendingPlays = mutableListOf<Int>()

    private lateinit var soundPool: SoundPool
    private var currentStreamId: Int = 0
    private var lastPlayTime = 0L
    private val minGapMs = 120L // min between sounds in Ms

    var volumeLevel: Float = 0.5f
        private set

    init {
        initSoundPool()
    }

    fun initSoundPool() {
        Log.d(TAG,"init")
        // Build and immediately load
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
            // kick off load immediately
            val sid = soundPool.load(context, res, /*priority=*/1)
            soundIdMap[key] = sid
        }
    }

    fun setVolumeLevel(volume: Float) {
        volumeLevel = volume.coerceIn(0f, 1f)
    }

    fun playOnce(resId: Int) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastPlayTime < minGapMs) {
            Log.d(TAG, "Suppressing duplicate play; gap=${currentTime - lastPlayTime}")
            return
        }
        lastPlayTime = currentTime
        playSound(resId)
    }

   private fun playSound(n: Int) {
        Log.d(TAG,"playOnce($n); soundIdMap[n]:  ${soundIdMap[n]}")
        if (soundIdMap[n] == null) {
            initSoundPool()
        } else {
            val sid = soundIdMap[n] ?: return
            if (sid in loadedSoundIds) {
                stopCurrentStream()
                // left/right volume 1f, priority 1, loop = 0 (play once), rate = 1f
                soundPool.play(sid, volumeLevel, volumeLevel, 1, 0, 1f)
            } else {
                pendingPlays += sid
            }
        }
    }

    private fun stopCurrentStream(){
        // stop previous stream if any (prevents overlap)
        synchronized(lock) {
            if (currentStreamId != 0) {
                Log.d(TAG, "Stopping previous streamId=$currentStreamId before new play")
                try {
                    soundPool.stop(currentStreamId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to stop stream $currentStreamId", e)
                }
                currentStreamId = 0
            }
        }
    }

    /** Immediately stop any currently playing streams (but keep them loaded) */
    fun stopAll() {
        Log.d(TAG,"stopAll()")
        soundIdMap.values.forEach { sid -> soundPool.stop(sid) }
        pendingPlays.clear()
    }

    /** Fully tear down the SoundPool (only call when you truly want to release) */
    fun release() {
        Log.d(TAG,"release()")
        soundPool.release()
        soundIdMap.clear()
        loadedSoundIds.clear()
        pendingPlays.clear()
    }

    fun setSystemStreamVolume(context: Context, relativeVolume: Float) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVol = (relativeVolume.coerceIn(0f, 1f) * max).roundToInt()
//        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        val flags = 0
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, flags)
    }

    /**
     * Convenience: update both app and system volumes.
     */
//    fun setVolumeBoth(context: Context?, v: Float, changeSystem: Boolean = false, showSystemUi: Boolean = false) {
    fun setVolumeBoth(context: Context?, v: Float) {
//        setVolumeLevel(v)
//        if (changeSystem && context != null) {
        if (context != null) {
            setSystemStreamVolume(context, v)
        }
    }

}