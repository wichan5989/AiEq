# Build Debug APK from Android phone using GitHub Actions

This project has NOT been compiled or tested in this environment.

1. Create a GitHub repository (private is fine).
2. Upload the **contents** of this folder to the repository root, including `.github/workflows/build-debug-apk.yml`. GitHub mobile browser may hide dot-folders; using a GitHub-compatible Git client or importing the ZIP via an online development environment may be easier.
3. In GitHub: Actions > Build Android Debug APK > Run workflow.
4. When the workflow succeeds, open the run > Artifacts > `KaiSoundgoodAiGEQ-debug-apk`, download and unzip it.
5. Install `app-debug.apk` on the Android phone. Only install artifacts built by your own repository.
6. If build fails, copy the first Gradle compilation error for troubleshooting.

The workflow uses Java 17, Android SDK 35 and Gradle 8.9. It uses the Android Gradle Plugin and Kotlin versions specified by the project.
No APK is included in this ZIP. M32 mixer EQ writes remain disabled.
