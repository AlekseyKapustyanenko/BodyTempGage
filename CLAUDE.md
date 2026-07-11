# BodyTempGage — agent notes

Kotlin/Android project. Two apps read a Xiaomi BLE body thermometer:
`:app` (phone) and `:wear` (Wear OS). Shared pure logic lives in `:core` (JVM,
unit-tested), Android glue in `:common-android`.

## Building & testing

- The Android SDK is installed by the **environment setup script** at `$ANDROID_HOME`
  (`/root/android-sdk` in the cloud). compileSdk / build-tools 35.
- Use the system **`gradle`**, not `./gradlew` — the wrapper's distribution download is
  blocked by the sandbox proxy; the system `gradle` matches the pinned wrapper version.
- Unit tests: `gradle :core:test`.
- `release` build types are debug-signed (so `adb install` works) with minify off; the
  `:wear` APK is ARM-only.

## Deliver release builds after code changes

Whenever you finish code changes that affect the apps — anything under `app/`, `wear/`,
`common-android/`, or `core/` — build and hand the user installable **release** APKs
before ending your turn (in addition to committing/pushing, not instead of it):

1. Run `scripts/build-release.sh` (builds both; Gradle skips the unaffected module).
2. Send the release APK(s) for the affected app(s) with the file-delivery tool:
   - `app/build/outputs/apk/release/app-release.apk`
   - `wear/build/outputs/apk/release/wear-release.apk`

   A change under `core/` or `common-android/` affects **both** apps — send both.
3. If an APK exceeds the chat upload limit (~30 MiB), zip it first and send the zip.
