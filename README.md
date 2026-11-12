# Kai Soundgood Ai GEQ — Android v1.0 source

Black/gold Midas-inspired (not official Midas artwork) Jetpack Compose UI, 31-band GEQ visualization, M32 OSC `/xinfo` connection probe, and experimental Android microphone peak detector. Assist only; **NO GEQ WRITES** in this build.

## Open / build
1. Unzip and open the root folder in Android Studio (or an Android Gradle IDE that supports AGP 8.7.3, Kotlin 2.0.21, Java 17).
2. Sync dependencies online; build `app` and install. No prebuilt APK or Gradle wrapper included.
3. Connect phone and M32R to the same reachable network. Set console IP to `192.168.0.100` or edit `OscClient` default.
4. Tap CONNECT M32. A response to `/xinfo` indicates OSC communication; it does not prove all routing or EQ state.
5. Grant microphone permission, tap START DETECTOR, and inspect suggestions. The phone microphone is **not** a direct measurement of M32 bus audio.

## Safety / limitations
- 31-band view is illustrative, not a synchronized console GEQ.
- FFT-like detector uses windowed Goertzel at 31 nominal centers, not full-resolution FFT. Sensitivity uses 10 dB prominence and 700 ms persistence; prototype, may false-trigger.
- GEQ writes intentionally locked. FX-slot-specific OSC mapping, normalized parameter encoding, insert routing, pre-write snapshot, safe −3 dB cumulative limit, and readback verification must be validated on a real M32 before enabling writes.
- AudioRecord UNPROCESSED may not be truly unprocessed on every Android device; no external USB interface selection yet.
- Do not use as the only protection against feedback at a live event.
- Network probing and analysis are independent; no automatic EQ action.

## Next milestone
Implement explicit FX-slot GEQ address mapping and a simulator-backed transactional write manager; then test on a disconnected bus before enabling live writes. Add USB audio input selection, proper FFT, and ring-out confidence tracking.

## v1.1 — RTA visualizations
- Four modes: Bar, Line, Area, and scrolling Spectrogram (last 90 analyzed frames).
- Five-color dBFS heat palette: cyan → green → yellow → orange → red.
- Adjustable display floor from −120 to −30 dBFS (ceiling fixed at 0 dBFS).
- Spectrum derived from the Android audio input via 31 Goertzel center frequencies; **not** OSC RTA, not calibrated dB SPL.
- No GEQ write commands have been enabled. All controls remain advisory.
- Display floor adjusts visualization only, not the actual input gain or feedback detector threshold.

## v1.2 RTA Visual Style
- Pixel LED: quantized rectangular LED cells (Bar/Area), discrete pixel points (Line), and spaced heatmap cells (Spectrogram).
- Smooth: continuous color-filled Bar/Area, continuous colored Line, and seamless Spectrogram.
- Visual Style is independent of RTA mode; existing -120 to -30 dBFS floor adjustment is retained.
- Rendering only: no changes to audio analysis, feedback decisions, or locked GEQ write.
- Source update only; not compiled or tested on Android hardware.

## v1.3 — EQ routing and Follow Layers (prototype)
- Target selector: input CH 1–32, BUS 1–16, MAIN LR, MATRIX 1–6.
- EQ selector: OFF, PEQ, GEQ. PEQ is 4 bands on inputs and 6 on bus/LR/matrix.
- GEQ is **not** assumed to exist on any target; requires a verified GEQ FX slot and insert routing.
- Capability state starts UNKNOWN, so PEQ/GEQ buttons remain disabled until a future console-state adapter validates them.
- Follow Layers preview follows the app's local layer buttons (L1–L4 map to CH1/9/17/25); disabling Follow locks the target. This is NOT external M32 layer synchronization.
- No live EQ writes; apply is disabled. Never infer an FX insert from a target number.
- Source has not been compiled or tested on an Android device.


## v1.4 RTA Source Lock & Follow
- Independent RTA source selector: Follow selected app EQ target, Manual CH 1–32 / Bus 1–16 / LR / Matrix 1–6, or measurement mic assigned to mixer CH 1–32.
- RTA Lock freezes the resolved source until unlocked. EQ target and RTA source remain independent.
- **Important:** selector is UI/routing state only. No PCM audio or FFT from selected M32 input/bus is currently received via OSC. The actual analyzer still uses Android AudioRecord. Connecting a measurement mic to the console does not automatically route its audio to the phone. Hardware routing/verified spectrum protocol must be implemented before claiming this works live.
- GEQ/PEQ writes remain disabled pending console state verification. Not compiled/tested on Android.


## v1.5 Android-first architecture
- `AiFeedbackEngine.kt`: pure Kotlin advisory policy with evidence, confidence, voice guard, tool selection, cumulative cut guard. No neural model bundled.
- `MixerAdapter`: capability-first contract. M32 adapter currently read-only/unknown; QU and M-AIR adapters intentionally unsupported placeholders. No fabricated OSC mapping.
- Android UI: AUTO/PEQ/GEQ/GATE preference, advisory explanation, confidence. Existing RTA and source routing retained.
- PC roadmap: extract platform-independent Kotlin decision core to Kotlin Multiplatform or port documented policy; replace audio and transport adapters per platform.
- IMPORTANT: Source selection for M32 CH/Bus remains UI-only; Android microphone is the actual audio input. EQ write remains disabled.
- Future real AI: supply and validate a model with live multitrack recordings and false-positive tests before enabling any write path.

### Build status
Not compiled in this environment (Android SDK/Gradle wrapper not present). Import into Android Studio, sync Gradle, compile and test on device.

## v1.6 — Read-only M32 PEQ state inspection
- Added strict OSC reply decoder for string/int/float/bool messages, rejecting unsupported tags and bundles.
- Added a **READ PEQ STATE · NO WRITES** button. Queries PEQ on/frequency/gain/Q for the currently selected CH, Bus, Main LR or Matrix and reports missing replies.
- Snapshot is intentionally diagnostic: even complete replies do NOT prove an FX GEQ is inserted, EQ routing, calibrated RTA, or permission to write.
- OSC queries use the existing UDP transport and are run off the UI thread. This is a first-pass protocol probe; exact firmware/address/value behavior must be checked against the physical console. No FX-slot GEQ scan, transactional writer, or live EQ/Gate control yet.
- No Gradle wrapper/Android SDK build verification in this environment. Do not use this build for show-critical decisions.
