package io.github.meko123456.zari.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.meko123456.zari.data.Recording
import io.github.meko123456.zari.ui.theme.ZariTheme

class MainActivity : ComponentActivity() {

    /**
     * Bumped on every resume. The recording service writes while the activity is stopped, and
     * both the overlay grant and battery-optimisation setting are changed in system Settings, so
     * everything the screen shows can be stale by the time the user comes back.
     */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ZariTheme {
                val vm: RecordingsViewModel = viewModel()
                val recordings by vm.recordings.collectAsStateWithLifecycle()
                var permissionTick by remember { mutableIntStateOf(0) }

                val requestPermissions = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { permissionTick++ }

                LaunchedEffect(Unit) {
                    val missing = REQUIRED.filterNot { granted(it) }
                    if (missing.isNotEmpty()) requestPermissions.launch(missing.toTypedArray())
                }

                // Re-read on every resume: permissions and the overlay grant change outside the
                // app, and a recording can land while the app is open.
                val tick = resumeTick + permissionTick
                val setup = remember(tick) {
                    setupSteps(
                        onRequestPermissions = {
                            requestPermissions.launch(REQUIRED.filterNot { granted(it) }.toTypedArray())
                        },
                        onOpenOverlay = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    "package:$packageName".toUri(),
                                ),
                            )
                        },
                        onOpenBattery = {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        },
                    )
                }
                LaunchedEffect(tick) { vm.refresh() }

                Scaffold { insets ->
                    RecordingsScreen(
                        recordings = recordings,
                        playing = vm.playing,
                        log = vm.log,
                        setup = setup,
                        isSelfTesting = vm.isSelfTesting,
                        verdict = vm.verdict,
                        manual = vm.manual,
                        isProbing = vm.isProbing,
                        probeResults = vm.probeResults,
                        probeWasOutsideCall = vm.probeWasOutsideCall,
                        onSelfTest = vm::selfTest,
                        onProbeAgain = vm::probeAgain,
                        onStartManual = vm::startManual,
                        onStopManual = vm::stopManual,
                        onProbeNow = vm::probeNow,
                        onToggle = vm::toggle,
                        onDelete = vm::delete,
                        onShare = { recording -> share(recording, vm) },
                        modifier = Modifier.padding(insets),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /** What is still missing, phrased as things to do rather than permissions to grant. */
    private fun setupSteps(
        onRequestPermissions: () -> Unit,
        onOpenOverlay: () -> Unit,
        onOpenBattery: () -> Unit,
    ): List<SetupStep> {
        val steps = mutableListOf<SetupStep>()
        val missing = REQUIRED.filterNot { granted(it) }
        if (missing.isNotEmpty()) {
            steps += SetupStep(
                title = "Allow the microphone, phone state, call log and contacts",
                why = "The call log is where the number comes from; contacts turn it into a name.",
                action = onRequestPermissions,
            )
        }
        if (!Settings.canDrawOverlays(this)) {
            steps += SetupStep(
                title = "Allow \"Appear on top\"",
                why = "Nothing is ever drawn on top. Android 12 and later refuse to let an app " +
                    "start a recording service in the background, and holding this permission is " +
                    "one of the few documented exemptions — without it the recorder cannot start " +
                    "when a call begins.",
                action = onOpenOverlay,
            )
        }
        steps += SetupStep(
            title = "Turn off battery optimisation for Zari",
            why = "Samsung will otherwise put the app to sleep and the call will not be recorded.",
            action = onOpenBattery,
        )
        return steps
    }

    private fun share(recording: Recording, vm: RecordingsViewModel) {
        val file = vm.fileFor(recording)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "audio/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                null,
            ),
        )
    }

    private companion object {
        val REQUIRED: List<String> = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
