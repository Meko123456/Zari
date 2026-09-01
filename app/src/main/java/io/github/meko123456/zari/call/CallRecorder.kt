package io.github.meko123456.zari.call

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/** Which audio source actually worked, which is the single most useful diagnostic this app has. */
enum class AudioSourceUsed {
    VOICE_CALL,
    VOICE_COMMUNICATION,
    MIC,
    VOICE_RECOGNITION,
    CAMCORDER,
    UNPROCESSED,
    DEFAULT,
    NONE,
    ;

    /** The platform constant, kept next to the name so the two cannot drift apart. */
    val platformSource: Int?
        get() = when (this) {
            VOICE_CALL -> android.media.MediaRecorder.AudioSource.VOICE_CALL
            VOICE_COMMUNICATION -> android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION
            MIC -> android.media.MediaRecorder.AudioSource.MIC
            VOICE_RECOGNITION -> android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION
            CAMCORDER -> android.media.MediaRecorder.AudioSource.CAMCORDER
            UNPROCESSED -> android.media.MediaRecorder.AudioSource.UNPROCESSED
            DEFAULT -> android.media.MediaRecorder.AudioSource.DEFAULT
            NONE -> null
        }
}

/**
 * Wraps [MediaRecorder] and tries the audio sources in descending order of usefulness.
 *
 * `VOICE_CALL` is the only source that captures both sides of a phone call cleanly, and since
 * Android 10 it requires `CAPTURE_AUDIO_OUTPUT`, a `signature|privileged` permission a sideloaded
 * app cannot hold. It is tried anyway because the attempt is free and some OEM builds still allow
 * it — and because failing loudly here, once, is better than assuming.
 *
 * `VOICE_COMMUNICATION` is next: it is the VoIP source, so it applies echo cancellation, which is
 * the wrong processing when the remote party is coming out of the earpiece. `MIC` is the fallback
 * and needs speakerphone to hear the other side at all.
 */
class CallRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var peak = 0

    var sourceUsed: AudioSourceUsed = AudioSourceUsed.NONE
        private set

    val isRecording: Boolean get() = recorder != null

    /**
     * Starts recording into [target]. When [preferred] is given, only that source is used — the
     * probe has already established it is the one that hears anything, and falling back silently
     * would hide a change in device behaviour.
     */
    fun start(target: File, preferred: AudioSourceUsed? = null): Result<AudioSourceUsed> {
        var lastError: Throwable? = null
        val order = preferred?.platformSource?.let { listOf(it to preferred) } ?: SOURCES
        for ((source, label) in order) {
            val attempt = runCatching { begin(source, target) }
            if (attempt.isSuccess) {
                sourceUsed = label
                peak = 0
                return Result.success(label)
            }
            lastError = attempt.exceptionOrNull()
            Log.w(TAG, "audio source $label unavailable: ${lastError?.message}")
            releaseQuietly()
            target.delete()
        }
        sourceUsed = AudioSourceUsed.NONE
        return Result.failure(lastError ?: IllegalStateException("no audio source available"))
    }

    private fun begin(source: Int, target: File) {
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        created.apply {
            setAudioSource(source)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(SAMPLE_RATE)
            setAudioEncodingBitRate(BIT_RATE)
            setAudioChannels(1)
            setOutputFile(target.absolutePath)
            prepare()
            start()
        }
        recorder = created
    }

    /**
     * Samples the loudest level since the previous call. Called on a timer while recording, so
     * that a file which turns out to be digital silence can be reported as such rather than
     * handed over as a successful recording.
     */
    fun sampleLevel(): Int {
        val current = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        if (current > peak) peak = current
        return peak
    }

    /** Stops and returns the peak amplitude seen, 0..32767. */
    fun stop(): Int {
        val active = recorder ?: return peak
        runCatching { active.stop() }
        releaseQuietly()
        return peak
    }

    private fun releaseQuietly() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
    }

    private companion object {
        const val TAG = "ZariRecorder"
        const val SAMPLE_RATE = 44_100
        const val BIT_RATE = 96_000

        val SOURCES = listOf(
            MediaRecorder.AudioSource.VOICE_CALL to AudioSourceUsed.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to AudioSourceUsed.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC to AudioSourceUsed.MIC,
        )
    }
}
