package io.github.meko123456.zari.call

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.abs

/**
 * Opens one audio source briefly and measures how loud it actually is.
 *
 * The point is a question `MediaRecorder` cannot answer: a source that *opens* is not necessarily
 * a source that *hears anything*. During a call, Android hands a non-privileged app a microphone
 * that reads as an unbroken run of zeroes — no error, no exception, a perfectly valid recording of
 * nothing. `AudioRecord` is used rather than `MediaRecorder` because it hands over raw samples, so
 * "is anything arriving" is answered in a few hundred milliseconds and without writing a file.
 */
class MicProbe {

    /** Peak absolute sample seen, 0..32767; or null when the source could not be opened at all. */
    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked before the service ever starts.
    fun peakOf(source: Int, durationMillis: Long): Int? {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) return null
        val bufferSize = minBuffer * 2
        var record: AudioRecord? = null
        return try {
            record = AudioRecord(source, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize)
            if (record.state != AudioRecord.STATE_INITIALIZED) return null
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) return null

            val samples = ShortArray(bufferSize / 2)
            val deadline = System.currentTimeMillis() + durationMillis
            var peak = 0
            while (System.currentTimeMillis() < deadline) {
                val read = record.read(samples, 0, samples.size)
                if (read <= 0) continue
                for (index in 0 until read) {
                    val magnitude = abs(samples[index].toInt())
                    if (magnitude > peak) peak = magnitude
                }
            }
            peak
        } catch (error: Exception) {
            Log.w(TAG, "probe of source $source failed: ${error.message}")
            null
        } finally {
            runCatching { record?.stop() }
            runCatching { record?.release() }
        }
    }

    companion object {
        private const val TAG = "ZariProbe"
        private const val SAMPLE_RATE = 44_100
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /**
         * Above this, something is genuinely arriving. A muted source reads exactly 0; a live one
         * in a quiet room still shows hundreds.
         */
        const val AUDIBLE_THRESHOLD = 150

        /** Long enough for a few buffers, short enough to lose almost none of the call. */
        const val PROBE_MILLIS = 400L

        /**
         * Every source a non-privileged app is allowed to name, best first. VOICE_CALL needs
         * CAPTURE_AUDIO_OUTPUT and will normally refuse to open — it is tried because the attempt
         * costs nothing and a handful of OEM builds allow it.
         */
        val CANDIDATES: List<Pair<Int, AudioSourceUsed>> = listOf(
            MediaRecorder.AudioSource.VOICE_CALL to AudioSourceUsed.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to AudioSourceUsed.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC to AudioSourceUsed.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION to AudioSourceUsed.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.CAMCORDER to AudioSourceUsed.CAMCORDER,
            MediaRecorder.AudioSource.UNPROCESSED to AudioSourceUsed.UNPROCESSED,
            MediaRecorder.AudioSource.DEFAULT to AudioSourceUsed.DEFAULT,
        )
    }
}
