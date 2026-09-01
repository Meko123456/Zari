package io.github.meko123456.zari.ui

import android.app.Application
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.meko123456.zari.data.Diagnostics
import io.github.meko123456.zari.data.Recording
import io.github.meko123456.zari.data.RecordingStore
import io.github.meko123456.zari.data.SourceMemory
import io.github.meko123456.zari.call.CallDirection
import io.github.meko123456.zari.call.CallRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RecordingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RecordingStore(application)
    private val diagnostics = Diagnostics(application)
    private val sourceMemory = SourceMemory(application)
    private var player: MediaPlayer? = null

    val recordings: StateFlow<List<Recording>> = store.recordings

    var playing by mutableStateOf<String?>(null)
        private set

    var log by mutableStateOf<List<Diagnostics.Entry>>(emptyList())
        private set

    var isSelfTesting by mutableStateOf(false)
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
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
