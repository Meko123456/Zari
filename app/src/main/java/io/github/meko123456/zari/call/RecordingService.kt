package io.github.meko123456.zari.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.meko123456.zari.data.Diagnostics
import io.github.meko123456.zari.data.FileNames
import io.github.meko123456.zari.data.Recording
import io.github.meko123456.zari.data.RecordingStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Records for the duration of a call, then names and files the result.
 *
 * The number is not known while the call is in progress — the call log entry appears only when it
 * ends — so audio goes to a fixed temporary file and is renamed afterwards. That ordering also
 * means a crash mid-call leaves one obviously-named `pending.m4a` rather than an orphan with a
 * plausible name.
 */
class RecordingService : Service() {

    private lateinit var store: RecordingStore
    private lateinit var diagnostics: Diagnostics
    private lateinit var resolver: CallerResolver
    private lateinit var recorder: CallRecorder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var levelJob: Job? = null
    private var startedAtMillis = 0L
    private var direction = CallDirection.UNKNOWN

    override fun onCreate() {
        super.onCreate()
        store = RecordingStore(this)
        diagnostics = Diagnostics(this)
        resolver = CallerResolver(this)
        recorder = CallRecorder(this)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> beginRecording(
                intent.getStringExtra(EXTRA_DIRECTION)?.let(CallDirection::valueOf)
                    ?: CallDirection.UNKNOWN,
                intent.getLongExtra(EXTRA_AT, System.currentTimeMillis()),
            )
            ACTION_STOP -> finishRecording(keep = true)
            ACTION_DISCARD -> finishRecording(keep = false)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun beginRecording(callDirection: CallDirection, atMillis: Long) {
        direction = callDirection
        startedAtMillis = atMillis

        val foreground = runCatching { startForeground(NOTIFICATION_ID, notification()) }
        if (foreground.isFailure) {
            // Android 12+ refuses background foreground-service starts without an exemption, and
            // 14+ adds a microphone-specific eligibility check. Say so, in the app.
            diagnostics.log("Could not start recording service: ${foreground.exceptionOrNull()?.javaClass?.simpleName}")
            stopSelf()
            return
        }

        val target = store.newFile(PENDING_FILE)
        target.delete()
        val started = recorder.start(target)
        started.fold(
            onSuccess = { source -> diagnostics.log("Recording started via $source") },
            onFailure = { error ->
                diagnostics.log("No audio source available: ${error.javaClass.simpleName}")
                stopSelf()
            },
        )
        if (started.isFailure) return

        levelJob = scope.launch {
            // Sampling the level is what lets the app tell a silent file from a real recording.
            while (isActive && recorder.isRecording) {
                recorder.sampleLevel()
                delay(LEVEL_INTERVAL_MILLIS)
            }
        }
    }

    private fun finishRecording(keep: Boolean) {
        levelJob?.cancel()
        if (!recorder.isRecording) {
            stopSelf()
            return
        }
        val peak = recorder.stop()
        val source = recorder.sourceUsed
        val pending = store.newFile(PENDING_FILE)
        val endedAt = System.currentTimeMillis()

        if (!keep) {
            pending.delete()
            diagnostics.log("Call not answered — nothing kept")
            stopSelf()
            return
        }

        scope.launch {
            val info = resolveWithRetries()
            val resolvedDirection = info?.direction?.takeIf { it != CallDirection.UNKNOWN } ?: direction
            val name = FileNames.forCall(
                startedAtMillis = startedAtMillis,
                number = info?.number,
                contactName = info?.contactName,
                direction = resolvedDirection,
                timestamp = { millis -> STAMP.format(Date(millis)) },
            )
            val destination = File(pending.parentFile, name)
            val renamed = pending.renameTo(destination)
            val file = if (renamed) destination else pending

            store.add(
                Recording(
                    fileName = file.name,
                    number = info?.number,
                    contactName = info?.contactName,
                    direction = resolvedDirection,
                    startedAtMillis = startedAtMillis,
                    durationMillis = endedAt - startedAtMillis,
                    peakAmplitude = peak,
                ),
            )
            diagnostics.log(
                if (peak < Recording.SILENT_THRESHOLD) {
                    "Saved via $source but the audio is silent (peak $peak) — Android muted the mic"
                } else {
                    "Saved via $source, peak $peak"
                },
            )
            stopSelf()
        }
    }

    /**
     * The platform writes the call log entry around the same moment the call ends, so the first
     * read often returns the *previous* call. Retry briefly rather than mislabel a recording.
     */
    private suspend fun resolveWithRetries(): CallerInfo? {
        repeat(RESOLVE_ATTEMPTS) {
            resolver.readAfterCall(startedAtMillis)?.let { return it }
            delay(RESOLVE_INTERVAL_MILLIS)
        }
        return null
    }

    override fun onDestroy() {
        levelJob?.cancel()
        if (recorder.isRecording) recorder.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Call recording", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Shown while a call is being recorded." },
        )
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording this call")
            .setContentText("Tap stop to end the recording early.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(null, "Stop", stop).build(),
            )
            .build()
    }

    companion object {
        private const val TAG = "ZariService"
        private const val CHANNEL_ID = "call-recording"
        private const val NOTIFICATION_ID = 42
        private const val PENDING_FILE = "pending.m4a"
        private const val LEVEL_INTERVAL_MILLIS = 250L
        private const val RESOLVE_ATTEMPTS = 8
        private const val RESOLVE_INTERVAL_MILLIS = 400L

        const val ACTION_START = "io.github.meko123456.zari.START"
        const val ACTION_STOP = "io.github.meko123456.zari.STOP"
        const val ACTION_DISCARD = "io.github.meko123456.zari.DISCARD"
        private const val EXTRA_DIRECTION = "direction"
        private const val EXTRA_AT = "at"

        private val STAMP = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

        /**
         * The state machine lives here, at process level, because it has to remember RINGING from
         * one broadcast in order to interpret OFFHOOK in the next — and the service itself only
         * exists while a call is actually being recorded.
         */
        private val monitor = CallMonitor()

        fun onPhoneState(context: Context, state: PhoneState) {
            val action = monitor.onState(state, System.currentTimeMillis()) ?: return
            Log.i(TAG, "action $action")
            val intent = Intent(context, RecordingService::class.java)
            when (action) {
                is CallAction.Start -> {
                    intent.action = ACTION_START
                    intent.putExtra(EXTRA_DIRECTION, action.direction.name)
                    intent.putExtra(EXTRA_AT, action.atMillis)
                    val started = runCatching { ContextCompat.startForegroundService(context, intent) }
                    if (started.isFailure) {
                        Diagnostics(context).log(
                            "Android blocked the recording service: " +
                                "${started.exceptionOrNull()?.javaClass?.simpleName}. " +
                                "Grant \"Appear on top\" and disable battery optimisation.",
                        )
                    }
                }
                is CallAction.Stop -> {
                    intent.action = ACTION_STOP
                    runCatching { context.startService(intent) }
                }
                is CallAction.Discard -> {
                    intent.action = ACTION_DISCARD
                    runCatching { context.startService(intent) }
                }
            }
        }
    }
}
