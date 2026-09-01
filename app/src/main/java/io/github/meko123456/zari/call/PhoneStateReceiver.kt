package io.github.meko123456.zari.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * The only moment the app learns a call has begun.
 *
 * It does no work itself: the state machine has to remember what happened before, so it lives in
 * the service, and this just forwards. Android delivers this broadcast more than once per change
 * (per SIM, among other reasons), which is why [CallMonitor] treats repeats as no-ops.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val raw = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val state = CallMonitor.parse(raw) ?: return
        Log.i(TAG, "phone state $state")
        runCatching { RecordingService.onPhoneState(context, state) }
            .onFailure { Log.e(TAG, "could not hand the state to the service", it) }
    }

    private companion object {
        const val TAG = "ZariReceiver"
    }
}
