# v3.5 — Live input freshness guard (source milestone)

Added `LiveAudioSessionGuard`: resets state on capture start/stop, requires verified Android input routing, and only marks FFT data usable for advice when a complete frame was received within 1200 ms. UI shows freshness and frame age. Stale candidates and preview approvals are cleared when capture stops. No EQ write path is enabled.

## Build status

Android APK **not built** here (Android SDK/Gradle not available). Kotlin pure-core guard test passed. Android Compose integration must be built in Android Studio and tested on device.

## Device test

1. Build debug APK in Android Studio with SDK 35 and JDK 17. Grant microphone permission.
2. Start with system microphone, verify FRESH appears only after complete FFT frames. Stop capture and verify NOT READY, candidate cleared.
3. Connect EVO4 using USB OTG, refresh devices, select EVO4, start. Verify Actual input ID matches requested ID.
4. Disconnect EVO4; capture must stop without switching to the phone microphone. Reconnect and manually restart.
5. Feed a safe, low-level test signal from an isolated M32 output to EVO4; compare frequency against an independent reference. Never use on an active PA for feedback suppression testing.

M32 channel/bus selection is UI-only until a physical audio route is made. The AI is advisory and is not a trained model.
