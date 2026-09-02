package io.github.meko123456.zari.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.meko123456.zari.call.CallDirection
import io.github.meko123456.zari.data.Diagnostics
import io.github.meko123456.zari.data.Recording
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** The list: every recording, newest first, grouped by day. */
@Composable
fun RecordingsScreen(
    recordings: List<Recording>,
    playing: String?,
    log: List<Diagnostics.Entry>,
    setup: List<SetupStep>,
    isSelfTesting: Boolean,
    verdict: RecordingsViewModel.Verdict,
    manual: RecordingsViewModel.ManualState?,
    isProbing: Boolean,
    probeResults: List<Pair<String, Int?>>,
    probeWasOutsideCall: Boolean,
    onSelfTest: () -> Unit,
    onProbeAgain: () -> Unit,
    onStartManual: () -> Unit,
    onStopManual: () -> Unit,
    onProbeNow: () -> Unit,
    onToggle: (Recording) -> Unit,
    onDelete: (Recording) -> Unit,
    onShare: (Recording) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Zari", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        if (setup.isNotEmpty()) {
            item { SetupCard(setup) }
        }

        item {
            RecordNowCard(
                manual = manual,
                isProbing = isProbing,
                probeResults = probeResults,
                probeWasOutsideCall = probeWasOutsideCall,
                onStart = onStartManual,
                onStop = onStopManual,
                onProbe = onProbeNow,
            )
        }

        item { VerdictCard(verdict = verdict, onProbeAgain = onProbeAgain) }

        item { SelfTestCard(isSelfTesting = isSelfTesting, onSelfTest = onSelfTest) }

        if (recordings.isEmpty()) {
            item { EmptyCard(hasSetupWork = setup.isNotEmpty()) }
        }

        var lastDay: LocalDate? = null
        for (recording in recordings) {
            val day = Format.dayKey(recording.startedAtMillis, zone)
            if (day != lastDay) {
                lastDay = day
                item(key = "day-$day") {
                    Text(
                        Format.dayLabel(recording.startedAtMillis, today, zone),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item(key = recording.fileName) {
                RecordingRow(
                    recording = recording,
                    isPlaying = playing == recording.fileName,
                    timeText = clock.format(Date(recording.startedAtMillis)),
                    onToggle = { onToggle(recording) },
                    onDelete = { onDelete(recording) },
                    onShare = { onShare(recording) },
                )
            }
        }

        if (log.isNotEmpty()) {
            item { LogCard(log) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    isPlaying: Boolean,
    timeText: String,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        colors = if (isPlaying) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (recording.direction) {
                        CallDirection.INCOMING -> "↓"
                        CallDirection.OUTGOING -> "↑"
                        CallDirection.UNKNOWN -> "•"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Column(Modifier.fillMaxWidth(0.72f)) {
                    Text(
                        recording.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    // Show the number as well when a contact name replaced it: the name answers
                    // "who", the number answers "which of their numbers".
                    if (recording.contactName != null && recording.number != null) {
                        Text(
                            recording.number,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                    Text(timeText, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        Format.duration(recording.durationMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (recording.isSilent) {
                Text(
                    "Silent — Android gave the app a muted microphone for this call",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onToggle) { Text(if (isPlaying) "Stop" else "Play") }
                TextButton(onClick = onShare) { Text("Share") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

/** One thing the user has to grant, and why. */
data class SetupStep(val title: String, val why: String, val action: (() -> Unit)?)

@Composable
private fun SetupCard(steps: List<SetupStep>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Before it can record", style = MaterialTheme.typography.titleMedium)
            steps.forEachIndexed { index, step ->
                if (index > 0) HorizontalDivider()
                Column {
                    Text(step.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        step.why,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (step.action != null) {
                        TextButton(onClick = step.action) { Text("Open settings") }
                    }
                }
            }
        }
    }
}

/**
 * The one control that can actually work on Android 14 and later.
 *
 * Automatic recording is not possible: starting microphone capture from the background throws a
 * SecurityException, and no permission or exemption lifts it for the microphone service type. What
 * is allowed is recording while the app is on screen — on Samsung, float Zari over the call in
 * pop-up view and tap Record.
 *
 * The live level is the point of the card. It answers "is any audio arriving" within a second,
 * instead of after the call when the file turns out to be silence.
 */
@Composable
private fun RecordNowCard(
    manual: RecordingsViewModel.ManualState?,
    isProbing: Boolean,
    probeResults: List<Pair<String, Int?>>,
    probeWasOutsideCall: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onProbe: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Record the call you are on", style = MaterialTheme.typography.titleMedium)
            Text(
                "Android will not let any app start recording a call by itself. Keep Zari on " +
                    "screen — pop-up view works while you are on a call — and start it here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (manual != null) {
                Text(
                    Format.levelBar(manual.peak),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (manual.peak >= Recording.SILENT_THRESHOLD) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Text(
                    "${Format.duration(manual.elapsedMillis)}  ·  ${manual.source}  ·  peak ${manual.peak}" +
                        if (manual.peak < Recording.SILENT_THRESHOLD) "  ·  nothing is arriving" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onStop) { Text("Stop and save") }
            } else {
                TextButton(onClick = onStart) { Text("Record now") }
            }

            HorizontalDivider()
            Text(
                "Or measure every audio source Android allows. Do it during a call: outside one " +
                    "they all work, which is why testing outside a call proves nothing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onProbe, enabled = !isProbing) {
                Text(if (isProbing) "Measuring…" else "Measure every source now")
            }
            if (probeResults.isNotEmpty() && probeWasOutsideCall) {
                Text(
                    "Measured with no call in progress, so this says nothing about call " +
                        "recording — every source works outside a call. Run it again during one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            for ((source, peak) in probeResults) {
                Text(
                    "$source  ${peak?.let { Format.levelBar(it, 8) + "  " + it } ?: "refused to open"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if ((peak ?: 0) >= Recording.SILENT_THRESHOLD) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * States plainly what the app has found out about this phone. A call recorder that cannot record
 * on your device should say so at the top of the screen, not leave you to work it out from a pile
 * of silent files.
 */
@Composable
private fun VerdictCard(verdict: RecordingsViewModel.Verdict, onProbeAgain: () -> Unit) {
    when (verdict) {
        RecordingsViewModel.Verdict.Unknown -> Unit

        is RecordingsViewModel.Verdict.Works -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Recording works on this phone", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Using the ${verdict.source} audio source. Turn on speakerphone to capture " +
                        "the other side as well.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        is RecordingsViewModel.Verdict.Muted -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This phone will not let any app record calls",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Every audio source Android allows was measured during a real call and all of " +
                        "them returned silence. The microphone itself is fine — the self-test " +
                        "below proves that — so this is the platform refusing, not a fault in the " +
                        "app or a setting you have missed.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (verdict.evidence.isNotEmpty()) {
                    Text(
                        verdict.evidence.joinToString("   ") { (source, peak) ->
                            "$source ${if (peak < 0) "refused" else "peak $peak"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "What does work: your phone's own recorder, if your region's firmware enables " +
                        "it; putting the call on speakerphone and recording on a second device; or " +
                        "a business VoIP line that records on the server.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onProbeAgain) { Text("Try every source again") }
            }
        }
    }
}

@Composable
private fun SelfTestCard(isSelfTesting: Boolean, onSelfTest: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Check the microphone", style = MaterialTheme.typography.titleMedium)
            Text(
                "Records five seconds right now. If this comes out silent the app cannot record " +
                    "at all; if this works but call recordings are silent, Android muted the " +
                    "microphone because a call was in progress.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onSelfTest, enabled = !isSelfTesting) {
                Text(if (isSelfTesting) "Recording…" else "Record five seconds")
            }
        }
    }
}

@Composable
private fun EmptyCard(hasSetupWork: Boolean) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No recordings yet", style = MaterialTheme.typography.titleMedium)
            Text(
                if (hasSetupWork) {
                    "Finish the steps above, then make or take a call."
                } else {
                    "Make or take a call and it will appear here."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Android does not let an ordinary app tap the call audio itself, so the recording " +
                    "comes from the microphone. Turn on speakerphone and both sides are captured; " +
                    "without it you may only hear yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LogCard(log: List<Diagnostics.Entry>) {
    val stamp = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What the recorder did", style = MaterialTheme.typography.titleSmall)
            for (entry in log.take(8)) {
                Text(
                    "${stamp.format(Date(entry.atMillis))}  ${entry.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
