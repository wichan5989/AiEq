# Build and test v4.1

1. Open this folder in Android Studio with Android SDK 35, JDK 17 and network access for Gradle dependencies.
2. Sync Gradle, run `gradle :app:assembleDebug` (or Build > Build APK(s)).
3. Install app/build/outputs/apk/debug/app-debug.apk on Android.
4. In landscape and portrait, drag the AI Advisor by its header; ensure EQ/RTA remains full-size, and the overlay remains within the screen.
5. Confirm Monitor Only rejects approvals; Assist + Confirm can preview a point; Undo/Next affect individual points only.
6. Verify M32 hardware EQ remains unchanged. No live write path is enabled.

This environment has not built or device-tested the APK. Kotlin-only checks cover queue and window coordinate clamp.
