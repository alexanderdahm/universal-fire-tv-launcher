# Changelog

All notable changes to this project are documented here.

## [2.0.0] — Universal Fire TV Launcher

Refactor of the single purpose *Plex Launcher* into a reusable, configurable launcher
for **any** installed Android / Fire TV application. Same repository, same Gradle
project, same Fire TV manifest guarantees — everything Plex specific is gone.

### Breaking changes

* **Application ID changed** from `com.example.plexlauncher` to
  `com.example.universallauncher` (plus an optional configurable suffix). Uninstall the
  old app before installing a new build:
  `adb uninstall com.example.plexlauncher`.
* **Kotlin package renamed** `com.example.plexlauncher` → `com.example.universallauncher`.
* **Gradle project renamed** `PlexLauncher` → `UniversalFireTvLauncher`
  (`settings.gradle.kts`).
* `app_name` and `launcher_icon_color` are **no longer static resources**; they are
  generated from the build configuration. Defining them again in `values/` breaks the
  build with a duplicate resource error.

### Added

* **Build time configuration** in one block at the top of `app/build.gradle.kts`,
  readable both as edited defaults and as `-P` command line overrides:
  * `targetPackage` → `BuildConfig.TARGET_PACKAGE`
  * `appLabel` → `BuildConfig.APP_LABEL` and `@string/app_name` (via `resValue`)
  * `appIconColor` → `BuildConfig.APP_ICON_COLOR` and `@color/launcher_icon_color`
  * `appIdSuffix` → appended to the application ID so several launchers coexist
  * `buildFeatures { buildConfig = true }` was enabled for this (it was off before).
* **`LauncherConfig`** — typed, single access point to those values, including
  `isSingleAppLauncher` which decides the mode at runtime.
* **App picker mode (new feature).** When `TARGET_PACKAGE` is empty the launcher shows a
  Leanback vertical grid of every launchable application on the device:
  * `AppPickerActivity` — `FragmentActivity` host with a `Theme.Leanback` derived theme.
  * `AppPickerFragment` — `VerticalGridSupportFragment`, 5 columns, D-pad focus zoom,
    populated on a background thread because package scanning and icon loading are slow
    on a Fire TV Stick.
  * `AppCardPresenter` — `ImageCardView` cards; the info strip is tinted with the
    configured accent colour.
  * `InstalledAppsRepository` — scans launchable apps, prefers `LEANBACK_LAUNCHER` over
    `LAUNCHER` per package, excludes this launcher, sorts by label.
  * `LaunchableApp` — model holding package, label, icon and the resolved launch intent.
  * New dependency: `androidx.leanback:leanback:1.0.0`.
* **Build time artwork generation** (`GenerateLauncherArtworkTask` in
  `app/build.gradle.kts`): pure Java2D, headless safe, wired into the resource merge via
  the AGP `androidComponents` / `addGeneratedSourceDirectory` API. It renders, for every
  density bucket, the launcher icon (rounded square), the round icon (circle) and the
  16:9 TV banner (320 × 180 at xhdpi) including the app label as text, all from
  `appIconColor` and `appLabel`. Label rendering is wrapped in a fallback so a build
  machine without fonts still produces a valid banner.
* **`.github/workflows/build-launchers.yml`** — build matrix producing
  `UniversalLauncher-Plex.apk`, `-Kodi`, `-VLC`, `-SmartTube`, `-Moonlight` and
  `-Picker` (the app picker build), each with its own BuildConfig values, artifact and
  application ID suffix, plus a `bundle` job that collects them into a single
  `UniversalLauncher-all-apks` artifact.
* `values/dimens.xml` — picker card geometry.
* `CHANGELOG.md` — this file.

### Changed

* **Launch logic generalised and de-duplicated.** The old `MainActivity` held the whole
  flow inline against a hard coded package. It is now split into:
  * `LauncherIntents.kt` — the launcher categories (`LEANBACK_LAUNCHER` first,
    `LAUNCHER` second), the query/explicit intent builders, and the `PackageManager`
    API 33+ / legacy compatibility shims. One definition, used everywhere.
  * `AppLauncher.kt` — `launchIntentFor()` (standard launch intent → launcher activity
    query → first exported match) and `start()` (new task flags, `ActivityNotFoundException`
    and `SecurityException` handling). Both the configured launch and every picker card
    click go through this same pair, so no launch logic is duplicated.
  * `MainActivity.kt` — reduced to a router: launch the configured app, or open the
    picker; plus the single error dialog.
* **Error message** is now one generic string,
  `"%1$s is not installed or cannot be launched."` (`message_cannot_launch`), replacing
  the two Plex specific messages.
* **`<queries>` is now generic.** The fixed `<package android:name="com.plexapp.android">`
  entry was replaced by the two launcher `<intent>` declarations, which give visibility of
  every startable app — what both the configured target and the picker need, with no
  package name in the manifest.
* **Themes renamed and extended**: `Theme.PlexLauncher` → `Theme.UniversalLauncher`
  (unchanged translucent, chrome free behaviour), plus the new
  `Theme.UniversalLauncher.Picker` for the Leanback grid.
* `AndroidManifest.xml` — added the non exported `AppPickerActivity`; the Fire TV
  critical parts are unchanged (both intent filters, banner on application and activity,
  `leanback`/`touchscreen`/`faketouch`/`gamepad` all `required="false"`,
  `exported="true"` on the entry activity).
* Adaptive icon background now references `@color/launcher_icon_color` instead of a fixed
  orange vector.
* `.github/workflows/android.yml` — artifact renamed to `UniversalLauncher-default-apk`;
  it now builds the default (picker) configuration.
* `README.md` — rewritten around configuration, rebranding examples
  (`com.netflix.ninja`, `org.videolan.vlc`, `org.xbmc.kodi`), picker mode, the matrix
  workflow and updated ADB/troubleshooting sections.
* `app/proguard-rules.pro` — keep rule points at the new package.

### Removed

* `MainActivity.kt` in `com/example/plexlauncher/` and every Plex specific code path,
  including the hard coded `PLEX_PACKAGE` constant and the Plex-only error strings.
* All checked in artwork: `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher{,_round}.png` and
  `drawable-{m,h,xh,xxh,xxxh}dpi/banner.png` — generated at build time now.
* `drawable/ic_launcher_background.xml` (fixed orange plate) — replaced by the
  configured colour.
* The orange brand colours in `values/colors.xml`.
* `vectorDrawables.useSupportLibrary` from `defaultConfig` — unused at `minSdk 21`.

### Verified

* `./gradlew clean assembleDebug` (default/picker configuration) — **BUILD SUCCESSFUL**.
* `./gradlew assembleRelease -PtargetPackage=org.xbmc.kodi -PappLabel="Kodi Launcher"
  -PappIconColor="#FF00A8E1" -PappIdSuffix=".kodi"` — **BUILD SUCCESSFUL**, producing an
  APK with application ID `com.example.universallauncher.kodi`, label `Kodi Launcher` and
  freshly generated cyan artwork.
* Generated `BuildConfig` contains `TARGET_PACKAGE`, `APP_LABEL` and `APP_ICON_COLOR`.
* Generated artwork verified at all five density buckets, banner 320 × 180 at xhdpi.

---

## [1.0.0] — Plex Launcher

Initial release: an invisible Fire TV launcher that started
`com.plexapp.android` and finished, with the Fire TV manifest wiring
(`LEANBACK_LAUNCHER` + `LAUNCHER`, banner, TV features not required), a translucent
themed no-UI activity, static generated artwork, a GitHub Actions release build and
documentation.
