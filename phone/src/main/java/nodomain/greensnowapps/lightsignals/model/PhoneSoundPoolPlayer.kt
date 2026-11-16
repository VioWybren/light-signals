package nodomain.greensnowapps.lightsignals.model

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import nodomain.greensnowapps.lightsignals.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SoundPool capacity; normal playback still replaces the player's previously tracked stream.
 */
private const val MAX_STREAMS = 3

/**
 * Lazily loads phone signal samples and manages one tracked stream with throttled requests.
 *
 * A monitor serializes public playback methods and load callbacks. The pool allows multiple
 * native streams, but each normal play request stops the tracked previous stream. Pending
 * unloaded samples are coalesced by sample ID. Stop retains loaded resources; release frees the
 * pool and allows later lazy initialization.
 *
 * @property context Application context used to access platform services without retaining an
 *     Activity.
 */
@Singleton
class PhoneSoundPoolPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /**
     * Monitor protecting pool ownership, sample loading, pending playback and stream handles.
     */
    private val lock = Any()
    /**
     * Maps logical sample numbers 1..3 to this app's raw audio resources.
     */
    private val soundResMap = mapOf(1 to R.raw.beep_1, 2 to R.raw.beep_2, 3 to R.raw.beep_3)
    /**
     * Maps logical sample numbers to SoundPool load handles; readiness is tracked separately.
     */
    private val soundIdMap = mutableMapOf<Int, Int>()
    /**
     * Sample handles whose current-pool load callback reported success, guarded by lock.
     */
    private val loadedSoundIds = mutableSetOf<Int>()
    /**
     * Set of requested sample handles awaiting loading; duplicate requests for one sample are
     * coalesced.
     */
    private val pendingPlays = mutableSetOf<Int>()
    /**
     * Lazily created pool, or null before initialization and after release; guarded by lock.
     */
    private var soundPool: SoundPool? = null
    /**
     * Last playback-stream handle, or zero after stop or failed play; a finished one-shot can
     * leave a stale nonzero handle.
     */
    private var currentStreamId = 0
    /**
     * Uptime of the last admitted request, including deferred playback; stopAll retains it and
     * release resets it.
     */
    private var lastPlayTime = 0L
    /**
     * Minimum uptime interval between admitted play requests, in milliseconds.
     */
    private val minGapMs = 120L

    init {
        initSoundPool()
    }

    /**
     * Creates and loads the phone pool once under its reentrant playback lock.
     *
     * May be called from any thread. Queues asynchronous raw-sample loading and handles only
     * callbacks belonging to the current pool. A successful pending load can play once;
     * callbacks from a released or replaced pool are ignored.
     */
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

    /**
     * Requests one logical sample, throttling admitted requests by uptime under the lock.
     *
     * May be called from any thread. Valid IDs are 1..3; zero and unknown values are ignored.
     * Lazily initializes resources, plays loaded samples or adds an unloaded sample to the
     * pending set. Repeated pending requests for one sample are coalesced. Returns before
     * asynchronous loading or one-shot playback completes.
     *
     * @param resId Logical sample number 1..3, distinct from an Android raw resource ID.
     */
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

    /**
     * Stops the tracked stream and requests one normal-rate playback of a loaded sample.
     *
     * Call while holding lock, including from a load-completion callback. Stores the returned
     * stream handle, including zero when SoundPool cannot start playback.
     *
     * @param pool Current pool containing the loaded sample.
     * @param sampleId SoundPool sample handle, distinct from logical sample and playback-stream
     *     IDs.
     */
    // All callers hold lock. Sample IDs never stand in for playback stream IDs.
    private fun playLoaded(pool: SoundPool, sampleId: Int) {
        if (currentStreamId != 0) pool.stop(currentStreamId)
        currentStreamId = pool.play(sampleId, 1f, 1f, 1, 0, 1f)
    }

    /**
     * Stops the tracked stream and clears pending sample requests under the playback lock.
     *
     * May be called from any thread. Retains the pool, loaded samples and retrigger timestamp.
     * Late callbacks can record successful loading but cannot play a request discarded by this
     * stop.
     */
    fun stopAll() = synchronized(lock) {
        if (currentStreamId != 0) soundPool?.stop(currentStreamId)
        currentStreamId = 0
        pendingPlays.clear()
    }

    /**
     * Releases the current pool and clears all loading, playback and retrigger state under the
     * lock.
     *
     * May be called from any thread. Removes the load listener before release. A later play
     * request can initialize a new pool; old completion callbacks cannot restart it.
     */
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

