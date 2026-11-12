# Kai Soundgood Ai GEQ v3.0

Android-first source milestone. Hardware writes disabled. No hardware validation or APK build has been performed.

- v2.7: Android input device discovery and preferred input routing (requires runtime permission and device validation).
- v2.8: M32 session readiness gate; OSC reply alone does not validate FX routing.
- v2.9: PCM FFT integration and conservative persistence-based peak suggestions; not trained AI.
- v3.0: FOH safety policy, source gate, smoke test, release-readiness documentation.

IMPORTANT: Selected M32 CH/Bus cannot be captured as PCM solely by OSC selection. Route actual audio to a compatible USB interface; verify routing and latency. Gate changes are never automatic. Real EQ writes remain disabled.
