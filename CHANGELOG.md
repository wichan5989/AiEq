# Kai Soundgood Ai GEQ Android milestones
Based on v1.6 source. All live DSP writes remain disabled.

## v1.7
- Adds capability-first FX observation model and GEQ routing check.
- Does not claim live FX discovery: actual firmware OSC mapping is still unverified.

## v1.8
- Adds bounded EQ proposal validation, operator confirmation, concurrent-change check, simulator readback and guarded undo.
- Live OSC write is intentionally not implemented.

## v1.9
- Adds pre/post median spectral peak comparison with 100-cent matching tolerance.
- Does not establish causal proof of feedback suppression.

## v2.0
- Adds fail-closed FOH readiness checklist. Live EQ write remains OFF regardless of checklist.
- v2.0 is a source-code milestone, not a hardware-certified release.

## v3.2
- AudioCaptureDiagnostics and UI counters for read errors, partial blocks, silence, clipping, RMS and peak.
- Stop capture when selected device routing changes.
- Suppress FFT processing on incomplete blocks.

## v3.3
- Added PCM frame assembler, nonblocking read, stall guard, sample-rate guard and tests.

## v3.4
- Startup PCM timeout, route-loss fail closed, recovery status in UI, manual restart workflow.

## v3.5
- LiveAudioSessionGuard freshness/route gate and UI status.
- Clear stale feedback candidate and approval after capture stops.
- Version metadata updated to 3.5.0-dev.
- No APK/hardware validation or live EQ writes.

## v3.6
- AI Monitor Only / Assist + Confirm UI; per-point preview; fail-closed mode controller.

## v3.7
Added per-frequency feedback suggestion queue and preview-only Undo/Next controls.

## v3.8
- Added per-frequency EQ history and guarded simulated undo; mixer write remains disabled.

## v3.9
- Added top-right FloatingAiAdvisor overlay with shared queue and per-point controls; preview only.

## v4.0
Compact responsive advisor, persistent live suggestion queue, mode-gated callbacks; no hardware writes.

## v4.1
- Draggable floating AI Advisor header with viewport constraints.
- Explicit Android Studio APK build and device validation checklist.
- Read-only / preview-only safety preserved.
