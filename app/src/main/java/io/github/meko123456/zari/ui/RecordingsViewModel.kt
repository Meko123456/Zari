package io.github.meko123456.zari.ui

import android.app.Application
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.meko123456.zari.call.AudioSourceUsed
import io.github.meko123456.zari.data.Diagnostics
import io.github.meko123456.zari.data.FileNames
import io.github.meko123456.zari.data.Recording
import io.github.meko123456.zari.data.RecordingStore
import io.github.meko123456.zari.data.SourceMemory
import io.github.meko123456.zari.call.CallDirection
import io.github.meko123456.zari.call.CallRecorder
import io.github.meko123456.zari.call.CallerResolver
import io.github.meko123456.zari.call.MicProbe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RecordingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RecordingStore(application)
    private val diagnostics = Diagnostics(application)
    private val sourceMemory = SourceMemory(application)
    private val resolver = CallerResolver(application)
    private var player: MediaPlayer? = null

    val recordings: StateFlow<List<Recording>> = store.recordings

    var playing by mutableStateOf<String?>(null)
        private set

    var log by mutableStateOf<List<Diagnostics.Entry>>(emptyList())
        private set

    var isSelfTesting by mutableStateOf(false)
        private set

    /** Live state of a hand-started recording. */
    var manual by mutableStateOf<ManualState?>(null)
        private set

    /** Per-source measurements from the last hand-run probe. */
    var probeResults by mutableStateOf<List<Pair<String, Int?>>>(emptyList())
        private set

    var isProbing by mutableStateOf(false)
        private set

    /** What the app has established about recording calls on *this* device. */
    var verdict by mutableStateOf<Verdict>(Verdict.Unknown)
        private set

    init {
        refresh()
    }

    fun refresh() {
        store.reload()
        log = diagnostics.read()
        verdict = when {
            sourceMemory.allSilent() -> Verdict.Muted(sourceMemory.lastProbe())
            sourceMemory.working() != null -> Verdict.Works(sourceMemory.working()!!.name)
            else -> Verdict.Unknown
        }
    }

    /** Forgets the verdict so the next call probes every source again. */
    fun probeAgain() {
        sourceMemory.clear()
        diagnostics.log("Verdict cleared — the next call will try every audio source again")
        refresh()
    }

    /**
     * Records five seconds from the microphone right now, with the app in the foreground.
     *
     * This exists to split one question into two. If a call recording comes out silent, the cause
     * is either "this app cannot record at all" (permissions, hardware, a broken recorder) or
     * "Android muted the microphone *because* a call was in progress". A foreground self-test
     * answers the first, so the second is not a guess.
     */
    fun selfTest() {
        if (isSelfTesting) return
        isSelfTesting = true
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val recorder = CallRecorder(getApplication())
            val file = store.newFile("selftest-$startedAt.m4a")
            val started = recorder.start(file)
            if (started.isFailure) {
                diagnostics.log(
                    "Self-test could not open the microphone: " +
                        (started.exceptionOrNull()?.javaClass?.simpleName ?: "unknown"),
                )
                isSelfTesting = false
                refresh()
                return@launch
            }
            repeat(SELF_TEST_SAMPLES) {
                recorder.sampleLevel()
                delay(SELF_TEST_INTERVAL_MILLIS)
            }
            val peak = recorder.stop()
            store.add(
                Recording(
                    fileName = file.name,
                    number = null,
                    contactName = "Microphone self-test",
                    direction = CallDirection.UNKNOWN,
                    startedAtMillis = startedAt,
                    durationMillis = System.currentTimeMillis() - startedAt,
                    peakAmplitude = peak,
                ),
            )
            diagnostics.log("Self-test via ${recorder.sourceUsed}, peak $peak")
            isSelfTesting = false
            refresh()
        }
    }

    /**
     * Starts recording with the app in the foreground.
     *
     * This exists because Android 14 forbids *starting* microphone capture from the background,
     * so a call cannot trigger it — a `SecurityException` is thrown before any audio is touched,
     * and no permission or exemption lifts it for the microphone service type. What is still
     * allowed is recording while the app is visible, which on Samsung means floating Zari over
     * the call in pop-up view and tapping this.
     *
     * The level is published while recording so it is obvious within a second whether audio is
     * actually arriving, rather than after the call when the file turns out to be silence.
     */
    fun startManual() {
        if (manual != null) return
        val startedAt = System.currentTimeMillis()
        val recorder = CallRecorder(getApplication())
        val file = store.newFile("pending-manual.m4a")
        file.delete()
        val mark = resolver.newestDate()
        val started = recorder.start(file, sourceMemory.working())
        if (started.isFailure) {
            diagnostics.log(
                "Could not open the microphone: " +
                    (started.exceptionOrNull()?.javaClass?.simpleName ?: "unknown"),
            )
            refresh()
            return
        }
        manual = ManualState(startedAt = startedAt, peak = 0, source = recorder.sourceUsed.name)
        viewModelScope.launch {
            while (manual != null && recorder.isRecording) {
                val peak = recorder.sampleLevel()
                manual = manual?.copy(peak = peak, elapsedMillis = System.currentTimeMillis() - startedAt)
                delay(LEVEL_INTERVAL_MILLIS)
            }
            val peak = recorder.stop()
            val info = resolveWithRetries(mark)
            val name = FileNames.forCall(
                startedAtMillis = startedAt,
                number = info?.number,
                contactName = info?.contactName,
                direction = info?.direction ?: CallDirection.UNKNOWN,
                timestamp = { millis -> STAMP.format(java.util.Date(millis)) },
            )
            val destination = java.io.File(file.parentFile, name)
            val saved = if (file.renameTo(destination)) destination else file
            store.add(
                Recording(
                    fileName = saved.name,
                    number = info?.number,
                    contactName = info?.contactName,
                    direction = info?.direction ?: CallDirection.UNKNOWN,
                    startedAtMillis = startedAt,
                    durationMillis = System.currentTimeMillis() - startedAt,
                    peakAmplitude = peak,
                ),
            )
            diagnostics.log("Hand-started recording via ${recorder.sourceUsed}, peak $peak")
            manual = null
            refresh()
        }
    }

    fun stopManual() {
        manual = null
    }

    /**
     * Measures every audio source Android permits, right now.
     *
     * Run it during a live call and it answers the only question that matters: whether *any*
     * source hears the call on this phone. Run outside a call it will show healthy levels for
     * everything, which is exactly why the answer has to be taken during one.
     */
    fun probeNow() {
        if (isProbing) return
        isProbing = true
        probeResults = emptyList()
        viewModelScope.launch {
            val probe = MicProbe()
            val results = mutableListOf<Pair<AudioSourceUsed, Int?>>()
            for ((platform, label) in MicProbe.CANDIDATES) {
                val peak = probe.peakOf(platform, MicProbe.PROBE_MILLIS)
                results += label to peak
                probeResults = results.map { (source, value) -> source.name to value }
            }
            val winner = results.firstOrNull { (_, peak) -> peak != null && peak >= MicProbe.AUDIBLE_THRESHOLD }
            if (winner != null) {
                sourceMemory.remember(winner.first)
                diagnostics.log("Probe: ${winner.first} hears audio (peak ${winner.second})")
            } else {
                sourceMemory.rememberAllSilent(results)
                diagnostics.log(
                    "Probe: every source silent — " +
                        results.joinToString(", ") { (s, p) -> "$s=${p?.toString() ?: "refused"}" },
                )
            }
            isProbing = false
            refresh()
        }
    }

    private suspend fun resolveWithRetries(mark: Long?): io.github.meko123456.zari.call.CallerInfo? {
        repeat(RESOLVE_ATTEMPTS) {
            resolver.readAfterCall(mark)?.let { return it }
            delay(RESOLVE_INTERVAL_MILLIS)
        }
        return null
    }

    /** Plays a recording, or stops it if it is the one already playing. */
    fun toggle(recording: Recording) {
        if (playing == recording.fileName) {
            stop()
            return
        }
        stop()
        val file = store.fileFor(recording)
        if (!file.exists()) return
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            playing = recording.fileName
        }.onFailure { stop() }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playing = null
    }

    fun delete(recording: Recording) {
        if (playing == recording.fileName) stop()
        store.delete(recording)
    }

    fun fileFor(recording: Recording) = store.fileFor(recording)

    /** A recording in progress, started by hand. */
    data class ManualState(
        val startedAt: Long,
        val peak: Int,
        val source: String,
        val elapsedMillis: Long = 0,
    )

    /** The three states this app can honestly be in on a given phone. */
    sealed interface Verdict {
        /** Nothing has been established yet: no call has been recorded since installing. */
        data object Unknown : Verdict

        /** A source was found that hears a live call. */
        data class Works(val source: String) : Verdict

        /**
         * Every permitted source was tried during a real call and all read silence. On this phone
         * no app without system privileges can record a call, and no setting changes that.
         */
        data class Muted(val evidence: List<Pair<String, Int>>) : Verdict
    }

    private companion object {
        const val SELF_TEST_SAMPLES = 20
        const val SELF_TEST_INTERVAL_MILLIS = 250L
        const val LEVEL_INTERVAL_MILLIS = 200L
        const val RESOLVE_ATTEMPTS = 12
        const val RESOLVE_INTERVAL_MILLIS = 500L
        val STAMP = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
