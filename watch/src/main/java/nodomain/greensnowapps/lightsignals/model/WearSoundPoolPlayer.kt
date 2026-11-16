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

/**
 * Loads watch signal samples and manages throttled, one-shot playback.
 *
 * Construction creates a one-stream pool and queues loading for logical samples 1..3.
 * Playback requests and asynchronous load callbacks share [lock], allowing playback
 * methods to be called from any thread after construction. [stopAll] clears playback
 * while retaining the pool and sample data for reuse.
 *
 * [setVolume] changes the device's shared media-volume stream synchronously through
 * AudioManager. Preference persistence and local-versus-phone sound routing remain the
 * callers' responsibility.
 *
 * @property context Application context used for raw sample resources and the audio
 *     service without retaining an Activity.
 */
@Singleton
class WearSoundPoolPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /**
     * Monitor guarding playback handles, pending requests, loaded IDs and retrigger
     * timing.
     *
     * Shared by playback methods and the asynchronous load-completion callback. Callers
     * hold it before invoking [playLoaded]; system volume changes use AudioManager
     * separately.
     */
    private val lock = Any()
    /**
     * Maps logical sample numbers 1..3 to SoundPool sample IDs returned during loading.
     *
     * Populated during construction and read under [lock] by [playOnce]. These sample
     * handles have readiness tracked separately by [loadedSoundIds]; they are not playback
     * streams or Android raw resource IDs.
     */
    private val soundIdMap = mutableMapOf<Int, Int>()
    /**
     * Sample IDs whose load-completion callback reported success.
     *
     * Guarded by [lock]. [playOnce] uses this set to choose immediate playback or a
     * pending request.
     */
    private val loadedSoundIds = mutableSetOf<Int>()
    /**
     * Latest sample ID awaiting load completion, or null when no request is pending.
     *
     * Guarded by [lock]. A new admitted request can replace the pending sample. A matching
     * load callback plays and clears it on success, or clears it on failure. [stopAll]
     * discards it so a late load cannot restart stopped playback.
     */
    private var pendingSampleId: Int? = null
    /**
     * Last playback-stream ID returned by SoundPool, or zero before playback, after a stop
     * or after a failed play.
     *
     * Guarded by [lock]. May remain nonzero after a one-shot finishes naturally; the
     * handle records the last requested stream rather than confirmed audible playback.
     */
    private var currentStreamId = 0
    /**
     * Uptime in milliseconds of the most recent admitted play request, including deferred
     * requests.
     *
     * Guarded by [lock]. Zero marks a reset retrigger gate. Invalid or throttled requests
     * leave it unchanged; it does not record actual sample start or completion time.
     */
    private var lastPlayTime = 0L
    /**
     * Fixed unity gain supplied to both SoundPool playback channels.
     *
     * System media volume determines the device output level. [setVolume] changes that
     * media stream while this per-channel gain remains unchanged.
     */
    private val volumeLevel = 1.0f
    /**
     * Singleton-owned pool configured for one media stream with sonification content.
     *
     * Loads raw samples asynchronously. Playback-state changes and completion callbacks
     * use [lock]. Retained with its sample data across stops for later signal playback.
     */
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

    /**
     * Requests one signal sample while throttling rapid retriggers under [lock].
     *
     * May be called from any thread after construction. Ignores unknown logical IDs and
     * valid requests arriving within 120 milliseconds of the previous admitted request. A
     * loaded sample replaces the tracked stream immediately; an unloaded sample becomes
     * the latest pending request for the load callback.
     *
     * Returns without waiting for decoding or playback completion. Sample number zero is
     * ignored and does not stop an existing stream; use [stopAll] to stop playback.
     *
     * @param sound Logical sample number 1..3; zero and other unknown values are ignored.
     */
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

    /**
     * Replaces the tracked stream with one playback of an already loaded sample.
     *
     * Call while holding [lock], either from [playOnce] or the asynchronous load callback.
     * Stops the previous stream handle and requests playback at unity channel gain, normal
     * rate and no looping. Stores the returned stream ID, including zero when SoundPool
     * cannot start playback.
     *
     * @param pool Pool containing the sample, supplied by the caller or completion
     *     callback.
     * @param sampleId Successfully loaded SoundPool sample ID, not a logical sample
     *     number.
     */
    private fun playLoaded(pool: SoundPool, sampleId: Int) {
        if (currentStreamId != 0) pool.stop(currentStreamId)
        currentStreamId = pool.play(sampleId, volumeLevel, volumeLevel, 1, 0, 1f)
    }

    /**
     * Stops the tracked stream and discards pending playback under [lock].
     *
     * May be called from any thread after construction. Clears the stream handle and
     * retrigger timestamp so a new valid request can play immediately. A late load
     * callback can mark its sample ready but cannot play the discarded request. Keeps the
     * pool and loaded samples available for reuse.
     */
    fun stopAll() {
        synchronized(lock) {
            pendingSampleId = null
            if (currentStreamId != 0) soundPool.stop(currentStreamId)
            currentStreamId = 0
            lastPlayTime = 0L
        }
    }

    /**
     * Sets the device's shared media-volume stream from a normalized fraction.
     *
     * Runs synchronously on the caller's thread through AudioManager, independently of the
     * playback lock. Bounds finite input to 0..1, substitutes 0.5 for non-finite input and
     * rounds the fraction of STREAM_MUSIC maximum to a volume index. The per-sample
     * SoundPool gain remains unchanged.
     *
     * Logs runtime failures from the platform audio service. Callers own preference
     * persistence; this method only requests the system media-volume change.
     *
     * @param value Requested media-volume fraction: 0 maps to volume index zero and 1 to
     *     the stream maximum; non-finite values use half volume.
     */
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
