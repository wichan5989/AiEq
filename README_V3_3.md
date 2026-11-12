# v3.3 — Audio capture stability

- Assembles arbitrary short PCM16 reads into exact 4096-sample FFT windows, preserving order without padding.
- Uses nonblocking AudioRecord reads with a short delay on zero reads; detects >1.5 s without samples after first successful read.
- Verifies actual AudioRecord sample rate is 48000 Hz before processing; no resampler is implemented.
- Stops capture on USB route mismatch, read errors, failed start, or unexpected sample rate.
- Uses existing audio diagnostics and UI warnings.

**Limitations:** Android APK not built here; no device/USB/M32 hardware tests. The stall timer starts after the first successful read, so no-first-sample timeouts are not yet detected. USB reconnect requires operator restart; automatic reconnect is deliberately not attempted. OSC remains read-only; no live EQ writes.
