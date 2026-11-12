# v3.4 Live Audio Recovery & Monitoring

- CaptureRecoveryPolicy uses a monotonic clock and detects startup silence in the capture API (no PCM reads), ongoing stalls, and selected input disappearance/route changes.
- Capture is stopped on failure. No automatic fallback to phone microphone and no automatic restart; replug USB and press Start again.
- Manual restart constructs a fresh AudioRecord, frame assembler, and diagnostics monitor.
- UI reports capture phase and reason.
- Silence in valid PCM is not considered a stalled capture.
- Android APK and physical EVO4/M32 tests have NOT been run. EQ writes remain disabled.
