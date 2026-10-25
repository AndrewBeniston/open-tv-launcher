# Development

## Test on the computer

```sh
./gradlew testDebugUnitTest lintRelease
```

The tests check the sample data, the layout numbers, the colour contrast, the D-pad focus map, the Trakt sign-in, the rules for the Trakt rows and the scripts. The Trakt tests use a local mock server, never Trakt. The sample titles are made up, and release builds have no sample data. The focus tests run the real home screen with Robolectric on a 960 × 540 dp screen. They do not replace a check on the TV.

`HomeClearanceTest` walks the focus through each row at its first, middle and last item, at 960 × 540 dp and 1280 × 720 dp (a 1.5× TV). It measures the drawn bounds of the focused item with its 1.1 scale and its shadow. It fails when a focused item comes nearer than 12 dp to any text or to another row, when two texts overlap, or when a text is cut short with "…". `RowPivotTest` checks the fixed focus position.

To check the same things on a TV, run the screenshot pass. It walks the focus through Home and saves a capture at each stop. It starts only when the TV shows a home screen or the screensaver and nothing plays. The captures go to a new temporary folder, about 2 MB each. Delete the folder after you look at them.

```sh
scripts/screenshot-pass.sh <adb-serial> [output-folder]
```

The debug build installs as the app id with `.debug` at the end, next to the release build.

## Versions

Checked on 23 and 24 October 2026. The versions are in `gradle/libs.versions.toml`.

| Part | Version | Source |
| --- | --- | --- |
| Gradle | 9.8.2 | services.gradle.org/versions/current |
| Android Gradle Plugin | 9.4.1 | Google Maven. AGP 9.4 release notes: Gradle 9.6 or newer, JDK 17, API 37 or lower |
| Kotlin | 2.4.21 | Maven Central. AGP 9 compiles Kotlin itself, and the root build file raises its Kotlin version |
| Compose BOM | 2026.09.00 (Compose 1.12.1) | Google Maven |
| Compose for TV (`androidx.tv:tv-material`) | 1.1.0 | Google Maven |
| compileSdk and targetSdk | 37 (Android 17) | SDK repository: `platforms;android-37.0` is on the stable channel |
| minSdk | 30 (Android 11) | The oldest TV tested |
| OkHttp | 5.5.1 | Maven Central |
| kotlinx-serialization-json | 1.12.0 | Maven Central |
| DataStore Preferences | 1.3.0 | Maven Central |
| WorkManager | 2.12.0 | Google Maven |
| Coil (`coil-core`, `coil-network-okhttp`) | 3.6.3 | Maven Central |
