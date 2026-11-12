# Kai Soundgood Ai GEQ v2.1 — M32 read-only diagnostics

Incremental milestone on v2.0. Adds target-range validation and an explicit read-only diagnostic report for incomplete OSC replies. It does not enable GEQ/PEQ/Gate writes, does not prove OSC mapping, and does not stream M32 audio/RTA.

## Test
Run the pure Kotlin smoke test with `kotlinc` against `EqRouting.kt`, `OscClient.kt`, `M32LiveStateReader.kt`, `M32Diagnostics.kt` and `tests/DiagnosticsSmoke.kt`. No Android SDK is required for this narrow test.

## Hardware validation next
1. On an isolated/offline M32 scene, connect the Android device to the same LAN as the console.
2. Verify IP 192.168.0.100, port 10023, firmware and PEQ reply paths against the M32 OSC documentation.
3. Capture read-only diagnostics on CH 1, BUS 1, MAIN LR and MTX 1.
4. Confirm all expected replies and correct values against the console UI before considering write access.

No Android APK build or live M32 test has been performed in this environment.
