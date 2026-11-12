# v3.8 Per-Frequency EQ History & Undo Preparation

Added `EqPointHistory.kt` as a pure Kotlin per-band ledger. Each history entry holds the exact EQ target/tool/band, verified snapshot token, original gain, proposed gain, state and revision. Independent history records prevent Undo on one band from affecting other bands.

- Fail closed on stale readback, wrong snapshot token, duplicate active band, invalid band, and cut exceeding 3 dB.
- `simulateApply` / `simulateUndo` are explicitly **simulations**. No OSC writer is present or enabled.
- History is in-memory; restarting the app discards approvals.
- Existing queue UI remains preview-only; this new ledger is a tested engine module and is not yet connected to the Compose UI or live console readback.
- Next: bind verified M32 PEQ readback to history and surface per-band baseline/proposal/status in UI, then compile an APK.

Run test with `kotlinc` using `EqRouting.kt`, `SafeEqTransaction.kt`, `EqPointHistory.kt`, and `tests/EqPointHistorySmoke.kt`.
