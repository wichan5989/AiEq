# Kai Soundgood Ai GEQ v3.1 — Android Input Integration

Adds audio input enumeration, device selection, preferred-device routing and routed-device verification to the existing AudioRecord capture loop. Captured PCM still drives the existing RTA and feedback detector. Input selection is disabled during recording.

## Important limitations
- No Android APK was built in this environment (Android SDK/Gradle not available). Source must be opened in Android Studio and built/tested on hardware.
- RTA CH/Bus selection does not provide PCM from M32 over OSC. A physical feed from the relevant M32 output to a supported USB interface is required.
- USB routing depends on Android device/interface compatibility; routedDevice must match the selected input.
- Existing PEQ/GEQ/Gate live writes remain disabled. Do not use as automatic feedback protection in a live show.
- Source routing and capture loop should be tested for disconnects, permission changes and long-duration stability before production use.

## Test plan
1. Install debug APK, grant RECORD_AUDIO, and refresh inputs.
2. Select built-in input, start detector, verify frame counter and RTA updates.
3. Stop, connect EVO4 by USB OTG, refresh, select USB input, start; verify actual input ID.
4. Disconnect USB during recording: ensure stop/error, and reselect after reconnect.
5. Connect a safely attenuated M32 monitor/bus output to interface input and confirm RTA responds to that feed. Avoid speaker-level signals.
