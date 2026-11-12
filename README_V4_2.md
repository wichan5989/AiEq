# v4.2 — Top-right floating advisor placement fix and build handoff

- Fixes v4.1 placement bug: the floating advisor now starts at the **top-right** of the GEQ/PEQ screen, not top-left.
- Dragging the header to the left moves the window left. Position is clamped to the viewport.
- GEQ/PEQ remains full-size underneath the overlay.
- Monitor Only remains the default and OSC EQ writes remain disabled.

## Build on a machine with Android Studio
1. Open this directory in Android Studio, using JDK 17 and Android SDK 35.
2. Allow Gradle 8.9 or compatible Gradle to sync. The project does **not** include a Gradle wrapper; use Android Studio's Gradle support or a locally installed compatible Gradle.
3. Build > Build APK(s), or `gradle :app:assembleDebug`.
4. Install the resulting `app/build/outputs/apk/debug/app-debug.apk` on the phone.
5. Check landscape and portrait; the advisor should start at top-right and drag within the viewport.
6. Verify the advisor cannot change live M32 EQ settings.

## Verification status
This environment has no Android SDK/Gradle, so an Android APK build and device testing are **not** verified. Kotlin-only tests of bounds/queue were performed in earlier milestones, not a full Compose build.
