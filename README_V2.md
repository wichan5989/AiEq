# Kai Soundgood Ai GEQ v2.0 — Android source milestone

This archive extends the user's v1.6 source. It contains milestone modules for v1.7 through v2.0.

## What is implemented
- v1.7 conservative FX/GEQ capability model from explicitly verified observations; no fabricated OSC FX mapping.
- v1.8 safe PEQ/GEQ planning with explicit confirmation, maximum cumulative 3 dB cut, stale-state rejection, simulated readback/undo.
- v1.9 pre/post spectral analysis comparison for advice.
- v2.0 fail-closed hardware readiness checklist.

## Not yet implemented / validated
- Live FX discovery via verified M32 OSC addresses, PEQ/GEQ write encoding, actual hardware write/readback/undo.
- Direct M32 channel/bus spectrum acquisition; current analyzer uses Android microphone.
- Gate modification, trained AI model, and physical console/FOH testing.
- Android APK compilation (SDK/Gradle dependencies unavailable here).

## Build
Open the KaiSoundgoodAiGEQ directory in Android Studio with JDK 17 and sync Gradle online. No wrapper included. The new Kotlin classes are standalone modules; integration into the Compose controls and hardware transport requires the verification steps above.

## Cross-platform design
Decision, verification and safety modules avoid Android dependencies and can later be extracted into a Kotlin Multiplatform core. QU and M-AIR require separately verified protocol adapters.
