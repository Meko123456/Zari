package io.github.meko123456.zari.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** What the platform can tell us about the call that just ended. */
data class CallerInfo(
    val number: String?,
    val direction: CallDirection,
    /** The network's own duration in seconds, useful as a sanity check on the recording. */
    val networkDurationSeconds: Long?,
    val contactName: String?,
)

/**
 * Reads the number and direction from the call log after the call ends, then turns the number
 * into a contact name.
 *
 * The call log is used rather than the `PHONE_STATE` broadcast extras because since Android 10
 * those extras are withheld unless the app holds `READ_CALL_LOG` anyway — and the log carries
 * the direction and the network's duration too, which the broadcast never did.
 *
 * The entry is written by the platform *around* the moment the call ends, so a single immediate
 * read frequently comes back with the previous call. The service therefore takes a [newestDate]
 * mark before recording starts and only accepts a row newer than it — see [CallLogMatch] for why
 * comparing against the recording's start time instead was wrong.
 */
class CallerResolver(private val context: Context) {

    /** Newest call-log timestamp right now, used as the "before the call" mark. */
    fun newestDate(): Long? {
        if (!granted(Manifest.permission.READ_CALL_LOG)) return null
        val cursor = runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.DATE),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 1",
            )
        }.getOrNull() ?: return null
        cursor.use { return if (it.moveToFirst()) it.getLong(0) else null }
    }

    fun readAfterCall(markBeforeCall: Long?): CallerInfo? {
        if (!granted(Manifest.permission.READ_CALL_LOG)) return null
        val columns = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
        )
        val cursor = runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                columns,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 1",
            )
        }.getOrNull() ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null
            val date = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))
            val duration = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DURATION))
            if (!CallLogMatch.isOurs(date, duration, markBeforeCall, System.currentTimeMillis())) {
                // Still the previous call's row: the platform has not written ours yet.
                return null
            }
            val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                ?.takeIf { value -> value.isNotBlank() }
            val type = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE))
            return CallerInfo(
                number = number,
                direction = when (type) {
                    CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
                    CallLog.Calls.INCOMING_TYPE, CallLog.Calls.MISSED_TYPE -> CallDirection.INCOMING
                    else -> CallDirection.UNKNOWN
                },
                networkDurationSeconds = duration,
                contactName = number?.let(::contactName),
            )
        }
    }

    /** Resolves a number to a contact name, or null when it is not in contacts. */
    fun contactName(number: String): String? {
        if (!granted(Manifest.permission.READ_CONTACTS)) return null
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(number),
        )
        val cursor = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )
        }.getOrNull() ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            return it.getString(0)?.takeIf { name -> name.isNotBlank() }
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

}
