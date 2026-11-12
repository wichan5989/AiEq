# v4.1 — Draggable Floating AI Advisor

- Advisor remains a floating overlay over the full-size EQ/RTA screen.
- Drag the advisor header to reposition it; coordinates are clamped to the available viewport.
- Minimize, hide, Monitor Only, Assist + Confirm, per-frequency Review/Confirm/Ignore/Undo/Next remain.
- Undo and Confirm are PREVIEW ONLY; mixer OSC writes remain disabled.
- Android APK build NOT verified here (Android SDK/Gradle unavailable). Run a Gradle build in Android Studio and inspect Compose layout on phone/tablet.
- The header is draggable, not the entire panel, to avoid accidental drags when pressing buttons.
