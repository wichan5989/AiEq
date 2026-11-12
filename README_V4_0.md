# v4.0 — Floating Advisor UI safety integration

- Compact floating advisor by default to avoid obscuring the mixer controls. Expand and hide supported.
- Responsive overlay width capped at 280 dp and 78% of parent width; EQ/RTA content remains full width underneath.
- Fix: repeated live feedback detections no longer erase the suggestion queue on every detected peak.
- Guard overlay Review/Confirm/Ignore/Undo callbacks against Monitor Only mode.
- Next only navigates; Undo only resets one preview item. No OSC EQ writes.
- Version metadata updated to 4.0.0-dev.

## Validation
Core queue logic smoke-tested with Kotlin CLI. Android APK **not built**: Android SDK/Gradle unavailable here. Actual screen sizes, overlay touch targets, AudioRecord and M32 must be validated on hardware.
