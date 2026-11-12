# v3.2 — Audio capture diagnostics

Adds a pure Kotlin capture-health monitor for read errors, short reads, silence, clipping, RMS and peak. UI shows diagnostics and stops capture if a selected USB device is no longer the routed device. Incomplete reads are not analyzed as complete FFT windows.

Limitations: no Android APK built here; USB unplug handling relies on routed-device polling and read errors, not a registered AudioDeviceCallback. PCM audio from M32 requires physical routing to an Android-recognized interface. EQ writes remain disabled.
