# Zari 📞

Call recording for Android phones where the manufacturer's own recorder is switched off.

**Zari** (ზარი — "call") records your phone calls to the phone itself, and lists them with the
date, the time, the number and — if the number is in your contacts — the name.

## Read this before expecting it to work

Android deliberately closed call recording to third-party apps, and no app can reopen it:

- Since **Android 10**, the `VOICE_CALL` audio source needs `CAPTURE_AUDIO_OUTPUT`, a
  `signature|privileged` permission. Only apps preinstalled in the system image can hold it. A
  sideloaded app cannot get it at any price.
- Since **Android 11**, the accessibility-service workaround is gone, and Play policy has
  forbidden it since 2022 — which is also why this app is not on the Play Store and never will be.
- What remains is the **microphone**. Zari tries `VOICE_CALL` first (free to attempt, and some OEM
  builds still allow it), then `VOICE_COMMUNICATION`, then `MIC`, and records the first that
  opens. With speakerphone on, the mic hears both sides. Without it, you may only hear yourself —
  and on some devices Android hands the app a **muted** microphone for the duration of a call, in
  which case the file is digital silence.

Because that last failure is invisible, Zari measures the peak level of every recording and says
so: a silent recording is labelled silent, not served up as a successful one. There is also a
five-second microphone self-test, so "the app cannot record" and "Android muted the mic during the
call" are separate answers rather than one shrug.

### What this measured on a Galaxy S24 Ultra (Android 16, One UI 8.0.5, XSG/UAE)

- Microphone self-test, app in the foreground: **peak 19366**. The microphone is fine.
- The same source during a live call: **peak 0**. Not quiet — exactly zero, an unbroken run of
  digital silence.
- The only package on the device holding `CAPTURE_AUDIO_OUTPUT` is
  `com.samsung.android.incallui`. Samsung's own Voice Recorder does not hold it either, so no app
  on the phone — not even the manufacturer's recorder app — can tap call audio.

That is what the verdict card in the app says, with the per-source evidence, once it has probed a
real call. It is a better answer than a pile of silent files.

**If your phone is a Samsung, check first whether the recorder you already own is merely
disabled:**

```sh
adb shell settings get global call_recording_support   # 0 means switched off for your region
adb shell cat /system/etc/floating_feature.xml | grep -i VOICECALL_CONFIG_RECORDING
```

Samsung ships the feature and gates it per sales code. If the floating feature is absent, the
dialer's own `isVoiceCallRecordingSupportedByCsc` check fails and flipping the settings flag is not
enough — but it costs nothing to try, and it is the better recorder when it works.

## Setup

Three things, all of which the app asks for and explains:

1. **Microphone, phone state, call log and contacts.** The call log is where the number comes
   from; contacts turn it into a name.
2. **"Appear on top."** Nothing is ever drawn on top. Android 12+ refuses to let an app start a
   foreground service from the background, and holding this permission is one of the documented
   exemptions — without it the recorder cannot start at the moment a call begins.
3. **Battery optimisation off.** Otherwise the app is asleep when the call arrives.

Recordings live in the app's own external directory, so no storage permission is needed and
nothing is written where other apps can read it. Use **Share** to get one out. Backups and
device-to-device transfer are switched off explicitly, for both the Android 12+ and the older
rule files: recordings of your phone calls have no business in a cloud backup.

## The law is not the same everywhere

Recording your own calls is lawful in some countries and an offence in others, and the rule often
turns on whether the other party consented. Several manufacturers, Samsung included, disable
recording per region for exactly this reason — the UAE builds ship with it off. Check what applies
where you are before you rely on it.

## Build

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Android-only, deliberately: this is telephony, and there is no iOS equivalent to port to. The
logic that *is* worth testing — the call state machine, the file naming, the index, the
formatting — is pure Kotlin with unit tests, so the interesting parts are covered without a
device.

The call state machine is the piece to read first (`call/CallMonitor.kt`). The sequences Android
produces are not obvious: a missed call and an answered one differ only in the order of three
states, the same state is broadcast repeatedly, and a second call arriving mid-call briefly reports
RINGING while a call is already being recorded.

## License

[MIT](LICENSE) © 2026 Merab Kochlamazashvili
