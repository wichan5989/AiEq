# Kai Soundgood Ai GEQ v2.6 — Android integration milestone

Based on v2.5. Adds a conservative Live Readiness panel wired into the existing Compose screen, PCM frame count, and a pure Kotlin readiness policy with test cases. Retains RTA display modes, EQ routing UI, follow/lock, microphone capture and OSC read-only query.

**Important:** The RTA capture still comes from the Android microphone. Selecting a mixer CH, bus, or measurement microphone in the UI does not route that signal into Android. The M32 OSC protocol is not treated as a verified FFT transport. GEQ/PEQ/Gate writes remain disabled. No neural-network model is included. No hardware or Android APK build has been validated.

## Test
`kotlinc app/src/main/java/com/kai/soundgood/aigeq/LiveReadiness.kt tests/ReadinessSmoke.kt -include-runtime -d /tmp/readiness.jar && java -jar /tmp/readiness.jar`

## Next
1. Build in Android Studio (Gradle 8.9 / JDK 17 and Android SDK 35); fix any build issues.
2. Test OSC read-only and PCM capture on a real device.
3. Add actual USB Audio capture selection and verify its relationship to the M32 CH/Bus source.
4. Verify FX routing and PEQ mapping on an isolated bus before implementing any write transaction.
