# v3.6 — AI Operating Modes

- Default: MONITOR ONLY (read-only bypass). RTA and feedback suggestions continue.
- ASSIST + CONFIRM: one detected point at a time can be reviewed and confirmed as a **preview only**.
- Switching mode, target, tool or detecting a new candidate clears pending approval.
- The app **never writes EQ** in either mode. This is not a field-ready feedback suppressor.
- Audio comes from Android microphone/selected USB input, not directly from M32 CH/Bus OSC.
- Android APK and physical M32/EVO4 tests have not been performed.

## Next validation
Build with Android Studio, verify mode-switch cancellation and source/target changes, then test read-only OSC and audio routing on real hardware.
