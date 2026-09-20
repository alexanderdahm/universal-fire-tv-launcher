# Universal Fire TV Launcher

A tiny, configurable launcher app for **Amazon Fire TV** and **Android TV**.

It contains no player and no third party code. It does exactly one of two things,
decided at build time:

* **Single app mode** — the tile starts one configured app (Plex, Kodi, VLC, …) and
  immediately gets out of the way.
* **Picker mode** — when no target package is configured, the tile opens a Leanback
  grid of *every* launchable app on the device; selecting one starts it.

In picker mode one of those apps can additionally be set as the **autostart app**: it is
started automatically a few seconds after the Fire TV has booted.

Turning this project into a launcher for a different app means changing three
values in `app/build.gradle.kts` — no code, no artwork, no manifest edits.

---

## Table of contents

- [Project overview](#project-overview)
- [Compatibility](#compatibility)
- [Configuration — build a launcher for another app](#configuration--build-a-launcher-for-another-app)
- [Autostart after a Fire TV boot](#autostart-after-a-fire-tv-boot)
- [Project structure](#project-structure)
- [How it works](#how-it-works)
- [Build instructions](#build-instructions)
- [GitHub Actions usage](#github-actions-usage)
- [ADB installation](#adb-installation)
- [ADB uninstall](#adb-uninstall)
- [Troubleshooting](#troubleshooting)
- [Artwork and trademarks](#artwork-and-trademarks)
- [License](#license)

---

## Project overview

| Item | Value |
| --- | --- |
| Project name | Universal Fire TV Launcher |
| Base application ID | `com.example.universallauncher` (+ configurable suffix) |
| Language | Kotlin |
| Build system | Gradle (Kotlin DSL) |
| Android Gradle Plugin | 8.4.2 |
| Gradle | 8.7 (wrapper checked in) |
| Kotlin | 1.9.24 |
| JDK required to build | 17 |
| `compileSdk` | 34 |
| `targetSdk` | 30 |
| `minSdk` | 21 (Android 5.0 Lollipop) |
| Dependencies | `androidx.appcompat:appcompat:1.6.1`, `androidx.leanback:leanback:1.0.0` |

## Compatibility

* **Fire OS 5.x and newer** — Fire TV Stick 2nd generation (Android 5.1, API 22) upwards.
* **Android TV / Google TV** — the `LEANBACK_LAUNCHER` filter puts the tile on the home screen.
* **Android 5.0+ phones and tablets** — the `LAUNCHER` filter puts it in the app drawer.

Fire TV specifics that are wired up and must not be broken when editing the manifest:

* `MAIN` + `LEANBACK_LAUNCHER` **and** `MAIN` + `LAUNCHER` intent filters on the entry activity.
* `android:banner` on both `<application>` and `<activity>`; 320 × 180 at xhdpi.
* `android.software.leanback` declared `required="false"` — Fire OS 5 devices do **not**
  report that feature, so `required="true"` would hide the app.
* `android.hardware.touchscreen` declared `required="false"` — otherwise the platform
  treats a touchscreen as required and filters the app off TV devices.
* `android:exported="true"` on the launcher activity.
* A `<queries>` block declaring the two launcher intents, which is what Android 11+
  package visibility needs; it is ignored on Fire OS 5.

## Configuration — build a launcher for another app

Everything lives in one block at the top of **`app/build.gradle.kts`**:

```kotlin
val targetPackage: String = launcherProperty("targetPackage", "")
val appLabel:     String = launcherProperty("appLabel",     "Universal Launcher")
val appIconColor: String = launcherProperty("appIconColor", "#FF2F7DF6")
val appIdSuffix:  String = launcherProperty("appIdSuffix",  "")
```

| Value | Reaches the app as | Effect |
| --- | --- | --- |
| `targetPackage` | `BuildConfig.TARGET_PACKAGE` | Package to launch. **Empty = app picker mode.** |
| `appLabel` | `BuildConfig.APP_LABEL` + `@string/app_name` | Tile name, banner text, dialog text |
| `appIconColor` | `BuildConfig.APP_ICON_COLOR` + `@color/launcher_icon_color` | Launcher icon, TV banner, picker accent |
| `appIdSuffix` | appended to the application ID | Lets several launchers coexist on one device |

Either edit the defaults in the file, or pass them on the command line — both produce
identical results:

```bash
# Netflix
./gradlew assembleRelease \
  -PtargetPackage=com.netflix.ninja \
  -PappLabel="Netflix Launcher" \
  -PappIconColor="#FFE50914" \
  -PappIdSuffix=".netflix"

# VLC
./gradlew assembleRelease \
  -PtargetPackage=org.videolan.vlc \
  -PappLabel="VLC Launcher" \
  -PappIconColor="#FFFF8800" \
  -PappIdSuffix=".vlc"

# Kodi
./gradlew assembleRelease \
  -PtargetPackage=org.xbmc.kodi \
  -PappLabel="Kodi Launcher" \
  -PappIconColor="#FF00A8E1" \
  -PappIdSuffix=".kodi"

# Plex, starting itself after every Fire TV boot
./gradlew assembleRelease \
  -PtargetPackage=com.plexapp.android \
  -PappLabel="Plex Launcher" \
  -PappIconColor="#FFF08A1E" \
  -PappIdSuffix=".plex" \
  -Pautostart=true
```

Common target packages:

| App | Package |
| --- | --- |
| Plex | `com.plexapp.android` |
| Kodi | `org.xbmc.kodi` |
| VLC | `org.videolan.vlc` |
| Netflix (Fire TV / Android TV) | `com.netflix.ninja` |
| SmartTube | `com.teamsmart.videomanager.tv` |
| Moonlight | `com.limelight` |
| Jellyfin (Android TV) | `org.jellyfin.androidtv` |

Not sure about a package name? Ask the device:

```bash
adb shell pm list packages | grep -i <name>
```

### Picker mode

Leave `targetPackage` empty and the tile opens a Leanback vertical grid of every
launchable app on the device — TV entry points (`LEANBACK_LAUNCHER`) preferred, phone
entry points as fallback, this launcher itself excluded, sorted by name. Selecting a
card starts that app through the same code path the single app mode uses.

### The icon and banner are generated

There is no artwork in the repository. A Gradle task (`GenerateLauncherArtworkTask` in
`app/build.gradle.kts`) draws the launcher icon, the round icon and the 16:9 TV banner
with Java2D at build time, using `appIconColor` and `appLabel`. Change the colour and
the label, and the next build produces matching artwork for every density bucket —
including the banner text. The adaptive icon (API 26+) picks up the same colour through
`@color/launcher_icon_color`.

## Autostart after a Fire TV boot

In picker mode the launcher can start one installed app automatically after the Fire TV
has booted — the behaviour Televizo offers as a checkbox inside the app.

**Setting it up, remote only:**

1. Open the launcher, move the focus to an app card.
2. **Hold OK** on that card. A dialog shows what is configured right now and offers
   *Set as autostart app* (or *Disable autostart* when that app is already the one).
3. Confirm. A toast states *“&lt;app&gt; will start automatically after the next Fire TV
   boot.”*

The grid title always shows the current state — `Universal Launcher — autostart: Kodi`,
or the hint when nothing is configured — and the configured card shows *Autostart app*
instead of its package name. At most one app can be set: picking another one replaces the
previous choice, and *Disable autostart* switches the feature off again.

**How it works:**

| Piece | Behaviour |
| --- | --- |
| Storage | `AutostartSettings` — one `SharedPreferences` file (`universal_launcher_settings`) holding `autostart_enabled` and `autostart_package`. No database. |
| Trigger | `BootReceiver`, a manifest declared `BroadcastReceiver` for `android.intent.action.BOOT_COMPLETED`, with the `RECEIVE_BOOT_COMPLETED` permission (normal, granted at install time). |
| Delay | 3 s (`BootReceiver.AUTOSTART_DELAY_MS`). Fire TV is still starting system components and its home screen right after the broadcast; starting immediately risks being pushed straight back. `goAsync()` keeps the process alive across the delay, well inside the ~10 s a receiver may take. |
| Launch | `AppLauncher.launch()` — the exact same resolution and start path the picker and the single app mode use. The receiver never opens the launcher itself. |
| Missing app | An uninstalled package, or one without a launchable activity, is dropped from the settings instead of failing: the boot is silent, and the picker shows no autostart app any more. |
| Repeated broadcast | A one shot guard makes a second `BOOT_COMPLETED` in the same process a no-op. |

**Single app builds:** a build with a `targetPackage` has no grid to pick from, so the
setting is made at build time with `-Pautostart=true`. Such a build starts its target app
after every boot. A choice made on the device always wins over the build time one.

**Fire TV limitations:**

* Fire OS 8 is Android 11, and Android 10+ restricts *background activity starts*. A boot
  receiver starting an activity is exactly that, so the start can be silently refused by
  the system depending on the Fire OS build and its launcher policy. The app handles this
  as a failed launch (logged under the `UniversalLauncher` tag, no crash) — there is no
  API a sideloaded app can use to force it.
* The setting lives in credential encrypted storage, so the receiver is deliberately
  **not** `directBootAware`: it runs on `BOOT_COMPLETED`, not on `LOCKED_BOOT_COMPLETED`.
* Autostart is configured from the picker grid in picker builds (`targetPackage` empty),
  and with `-Pautostart=true` in single app builds.

## Project structure

```
.
├── .github/workflows
│   ├── android.yml              default CI build
│   └── build-launchers.yml      matrix: one APK per target app
├── app
│   ├── build.gradle.kts         ← configuration + artwork generator
│   ├── proguard-rules.pro
│   └── src/main
│       ├── AndroidManifest.xml
│       ├── java/com/example/universallauncher
│       │   ├── LauncherConfig.kt           typed view onto BuildConfig
│       │   ├── LauncherIntents.kt          categories, query intents, PM compat
│       │   ├── AppLauncher.kt              resolve + start (shared by both modes)
│       │   ├── LaunchableApp.kt            picker model
│       │   ├── InstalledAppsRepository.kt  scan of launchable apps
│       │   ├── MainActivity.kt             invisible entry point / router
│       │   ├── AppPickerActivity.kt        Leanback host
│       │   ├── AppPickerFragment.kt        vertical grid
│       │   ├── AppCardPresenter.kt         card rendering
│       │   ├── AutostartSettings.kt        the autostart setting (SharedPreferences)
│       │   └── BootReceiver.kt             BOOT_COMPLETED → start the autostart app
│       └── res
│           ├── drawable/ic_launcher_foreground.xml
│           ├── mipmap-anydpi-v26/ic_launcher{,_round}.xml
│           └── values/{colors,dimens,strings,themes}.xml
│   └── src/test/java/com/example/universallauncher
│       ├── AutostartSettingsTest.kt        autostart persistence + cleanup
│       ├── BootReceiverLogicTest.kt        the decisions BootReceiver makes
│       └── FakeSharedPreferences.kt        in memory preferences for the tests
├── build.gradle.kts
├── gradle/wrapper/              Gradle wrapper (checked in)
├── gradlew / gradlew.bat
├── CHANGELOG.md
├── LICENSE
└── README.md
```

## How it works

`MainActivity` has no layout and never draws anything.

**Single app mode** (`TARGET_PACKAGE` set):

1. `PackageManager.getLaunchIntentForPackage(TARGET_PACKAGE)`.
2. If that returns `null`, query the launcher activities of that package —
   `CATEGORY_LEANBACK_LAUNCHER` first, then `CATEGORY_LAUNCHER`.
3. Start the first exported match as an explicit component, then `finish()`.
4. If nothing can be started, show a dialog:
   **“&lt;APP_LABEL&gt; is not installed or cannot be launched.”**

**Picker mode** (`TARGET_PACKAGE` empty): `MainActivity` hands over to `AppPickerActivity`
and finishes. The grid is filled on a background thread (package scanning and icon
loading are slow on a Fire TV Stick), and a card click starts the app through
`AppLauncher.start()` — the same launch path, no duplicated logic.

**Autostart** (optional, picker mode): `BootReceiver` receives `BOOT_COMPLETED`, reads
`AutostartSettings`, checks that the stored package still resolves to a launch intent and
starts it 3 s later through `AppLauncher.launch()` — see
[Autostart after a Fire TV boot](#autostart-after-a-fire-tv-boot).

Deprecated `PackageManager` overloads are avoided on modern devices: the API 33+ flag
based methods are used where available, the legacy int-flag overloads — the only ones
Fire OS 5 has — stay behind an explicit `Build.VERSION.SDK_INT` check.

## Build instructions

### Requirements

* JDK **17** (Temurin, Zulu, or the JDK bundled with Android Studio).
* Android SDK with **platform 34** and **build-tools 34.0.0**.
* Android Studio Hedgehog (2023.1.1) or newer — optional, the command line is enough.

### Command line

```bash
./gradlew assembleRelease          # Linux / macOS
gradlew.bat assembleRelease        # Windows
```

Artifacts:

```
app/build/outputs/apk/release/app-release.apk
app/build/outputs/apk/debug/app-debug.apk
```

Local unit tests (plain JVM, JUnit 4, no device and no emulator needed):

```bash
./gradlew testDebugUnitTest
```

Both are signed with the **debug keystore** (`~/.android/debug.keystore`, created on the
first build), so the release APK sideloads directly — no signing secrets, no keystore
setup.

If the SDK cannot be located, create `local.properties` (not checked in):

```properties
sdk.dir=/home/<user>/Android/Sdk
# Windows (forward slashes avoid escaping problems):
# sdk.dir=C:/Users/<user>/AppData/Local/Android/Sdk
```

Note: Android Lint reports `ExpiredTargetSdkVersion` because `targetSdk = 30` is below
the Google Play requirement. That is intentional for Fire OS 5 compatibility and this app
is sideloaded, not published on Play, so `lint { abortOnError = false }` keeps the build
green.

## GitHub Actions usage

Two workflows, both requiring **no signing secrets**:

**`.github/workflows/android.yml`** — plain CI. `ubuntu-latest`, Temurin JDK 17,
`gradle/actions/setup-gradle@v3` for dependency/distribution caching, then
`testDebugUnitTest` followed by `assembleRelease` with the default configuration. It
uploads `UniversalLauncher-default-apk`, plus the HTML test report as `unit-test-report`
when a test fails.

**`.github/workflows/build-launchers.yml`** — the launcher matrix. One job per target
app, each injecting its own `-PtargetPackage`, `-PappLabel`, `-PappIconColor` and
`-PappIdSuffix`, producing:

```
UniversalLauncher-Plex.apk
UniversalLauncher-Kodi.apk
UniversalLauncher-VLC.apk
UniversalLauncher-SmartTube.apk
UniversalLauncher-Moonlight.apk
UniversalLauncher-Picker.apk      (app picker build)
```

Each APK is uploaded as its own artifact, and a final `bundle` job collects all of them
into a single `UniversalLauncher-all-apks` artifact. Because every entry uses a different
`appIdSuffix`, all of them can be installed on the same device side by side.

Adding another launcher = adding four lines to the `matrix.launcher` list.

To download builds: repository → **Actions** → select the run → **Artifacts**.
To trigger by hand: **Actions ▸ Build launcher matrix ▸ Run workflow**.

## ADB installation

Enable ADB on the Fire TV first:

1. `Settings ▸ My Fire TV ▸ Developer options ▸ ADB debugging = ON`
   (on newer Fire OS versions also `Apps from Unknown Sources = ON`).
2. Note the IP under `Settings ▸ My Fire TV ▸ About ▸ Network`.

```bash
adb connect 192.168.1.42:5555
adb install app-release.apk
```

Confirm the authorisation prompt on the TV the first time.

```bash
adb install -r app-release.apk          # reinstall, keep data
adb install -r -d app-release.apk       # allow downgrade
adb shell pm list packages | grep universallauncher
adb shell monkey -p com.example.universallauncher -c android.intent.category.LEANBACK_LAUNCHER 1
adb disconnect 192.168.1.42:5555
```

With an `appIdSuffix`, use the full ID, e.g. `com.example.universallauncher.kodi`.

## ADB uninstall

```bash
adb uninstall com.example.universallauncher
adb uninstall com.example.universallauncher.kodi     # a suffixed build
```

Keep app data while removing the app:

```bash
adb shell pm uninstall -k com.example.universallauncher
```

## Troubleshooting

**The tile does not show up on the Fire TV home screen**
Fire OS caches the home rows. Reboot (`adb reboot`), or open
`Settings ▸ Applications ▸ Manage Installed Applications` once. The app is always
reachable from the classic app grid because it also declares `CATEGORY_LAUNCHER`. Note
that the upper home rows are curated by Amazon — sideloaded apps usually surface there
only after being started once.

**“&lt;label&gt; is not installed or cannot be launched.”**
The configured `targetPackage` is missing, or it exposes no launchable activity that is
visible to this app. Check the real package name:

```bash
adb shell pm list packages | grep -i <name>
adb shell cmd package resolve-activity --brief <package>
```

Then rebuild with the corrected `-PtargetPackage`.

**The picker shows nothing / “No launchable applications were found.”**
Only apps with a `MAIN` + `LAUNCHER`/`LEANBACK_LAUNCHER` activity can be listed, because
those are the only ones that can be started. If the list is empty on an Android 11+
device, the `<queries>` block in `AndroidManifest.xml` was removed — it is required for
package visibility.

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**
An older build with a different signature is installed. Uninstall it first. This also
happens when moving from the old `com.example.plexlauncher` ID to this project's ID —
uninstall the old package.

**`INSTALL_FAILED_OLDER_SDK`**
The device is below API 21. A Fire TV Stick 2nd gen is API 22; check `adb devices -l`.

**Gradle sync fails with “Unsupported class file major version”**
The build requires JDK 17. Android Studio:
`Settings ▸ Build, Execution, Deployment ▸ Build Tools ▸ Gradle ▸ Gradle JDK = 17`.

**Build fails with `Failed to find target with hash string 'android-34'`**
`sdkmanager "platforms;android-34" "build-tools;34.0.0"`, or install API 34 from the
Android Studio SDK Manager.

**The banner has no text**
The artwork generator draws text with Java2D; on a build machine without any installed
fonts it falls back to the graphical mark and logs a warning. Install a font package
(e.g. `fontconfig` + `fonts-dejavu` on a minimal Linux image).

**Logs from the device**

```bash
adb logcat -s UniversalLauncher:V AndroidRuntime:E
```

## Artwork and trademarks

All artwork is **generated at build time** from a colour value: a rounded square with a
white play triangle, and a matching TV banner. No third party logo, wordmark or other
copyrighted asset is included, and app names such as Plex, Kodi, VLC, Netflix, SmartTube
or Moonlight are used only nominatively to identify the app a build targets. This project
is independent and unaffiliated with any of them.

## License

Released under the MIT License — see [`LICENSE`](LICENSE).
