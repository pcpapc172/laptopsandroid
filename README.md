# Laptop Manager for Android

A native Kotlin and Java phone client for [Laptop Manager](https://github.com/pcpapc172/laptops). Sign in with the **same server username and password** as the desktop app. The endpoint is `https://13.42.221.242`; the app uses the existing HTTPS API and does not require another server.

- Persian RTL layout, Vazir font, light/dark themes, bottom navigation and record cards.
- Register laptop loans, select predefined teachers/grades, edit records and notes, receive returns, and search archived/history records.
- Inventory counts, accessory lending and partial returns, missing-item reports and recovery.
- Admin presets, stock types/defaults, item edits and user management; staff/viewer permissions match the server.
- Automatic incoming/outgoing sync, durable offline edits, conflict details/retry/discard, and password-checked offline access.

Requires Android 8.0 or newer. Kotlin implements the native UI and sync scheduling; Java implements HTTPS transport, the SQLite journal, protocol merging and credential protection.

## Build

Use Java 17, Gradle 8.13 (included wrapper), Android SDK platform 36 and build tools 35.0.0. These match the local Android build guidance in `../skills`.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. When dependencies are cached, add `--offline`. If Google Maven is inaccessible locally, an optional Google artifact mirror can be supplied with `-PgoogleMavenMirror=https://maven.aliyun.com/repository/google`; the default build uses official Google Maven and Maven Central.

```bash
adb devices
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL shell am start -n ir.laptops.mobile/.MainActivity
```

Debug builds use the developer machine's debug signing key. Keep that key for consistent local test updates. GitHub's APK artifact is a test build and uses the runner's key; it is not an upgrade channel for the separately signed local install. A production signing key must be configured before distributing release updates.

## Verification

Local Java tests cover patch merging, counter deltas, UUID normalization and secure endpoint validation. The HTTP integration test runs against a temporary loopback copy of the desktop server when `LAPTOPS_SERVER_SOURCE` points to its checkout with `.server-venv` installed. CI checks out the compatible desktop server tag solely for this test; no test accesses the live database.

On-phone tests use uniquely named local test databases and credential preferences. They cover offline restart/dependencies, a lost full-return acknowledgement, competing description/location changes, conflict retry, role restrictions and Android Keystore/offline password verification.

```bash
./gradlew :app:assembleDebugAndroidTest
adb -s DEVICE_SERIAL install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument -w \
  -e class ir.laptops.mobile.StoreInstrumentedTest \
  ir.laptops.mobile.test/android.test.InstrumentationTestRunner
```

The initial APK was installed on the connected Samsung SM-S711B via ADB. Five computer-side tests (including the API fixture) and four isolated on-phone tests passed. The standalone APK builds and Android lint passes. Later UI fixes add explicit teacher/grade selection buttons and light system-bar icons in dark mode; these fixes are compiled into the updated APK and are prepared without interrupting the user's running test session.

See [sync and credentials](docs/sync.md) for storage and background behavior. Vazir's license is bundled under `app/src/main/assets/licenses/`.
