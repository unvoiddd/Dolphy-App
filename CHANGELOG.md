# Changelog

All notable changes to Dolphy are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security

- **Plugin integrity:** `.dolphyplugin` packages can carry an ECDSA P-256
  signature over their canonical source. A signature that does not match the
  payload is always rejected, and unsigned or untrusted-key packages require an
  explicit opt-in that is now surfaced in the install preview.
- **Plugin transport:** imports are HTTPS-only. Plain-HTTP links are rejected
  rather than upgraded, redirects are followed manually so a downgrade cannot
  slip through, and the download policy is covered by unit tests.
- **Plugin WebView:** file and content access are disabled, mixed content is
  blocked, popups and autoplay are off, and navigation is restricted to HTTPS.
- Removed the unused `READ_EXTERNAL_STORAGE` permission.

### Changed

- CI now runs the unit tests of every module. The previous step executed
  `:app:testDebugUnitTest`, which had no sources, so no test ever ran.

## [1.0.0] - 2026-10-30

Dolphy 1.0.0 establishes the stable release baseline for the plugin-enabled
build. It introduces the in-app plugin platform, local and URL plugin import,
private release signing, and a modern Android toolchain.

### Highlights

- **Plugin platform:** an in-app store, a 20-plugin official catalog, install
  and update flows, enable/disable controls, plugin sharing, and a safe mode
  that activates after repeated plugin crashes.
- **Plugin import:** install any `.dolphyplugin` file from local storage or
  from a direct URL, with a mandatory preview and consent step before install.
- **Private release signing:** release APKs are signed with a dedicated
  RSA-4096 keystore and built by GitHub Actions, so updates install in place.
- **Toolchain modernization:** Android Gradle Plugin 9.5, Gradle 9.8.2,
  Kotlin 2.4.21, and compileSdk 37.

### Added

#### Plugin platform

- In-app plugin store with the complete official catalog of 20 example
  plugins, covering Wi-Fi, BLE, classic Bluetooth, NFC, IR, USB, networking,
  device inspection, sensors, crypto, file browsing, and privileged shells.
- Plugin manager with installed-plugin list, one-tap install and update,
  update-all, enable/disable, pinning, deletion, and sharing.
- Plugin preview screen showing the manifest name, author, version, size,
  runtime, declared capabilities, and detected permissions before installation.
- Plugin permission model covering BLE Spam, Bluetooth, Wi-Fi, infrared, USB,
  network access, plugin files, downloads, root, Shizuku, shell commands, and
  DEX loading.
- JavaScript and Python plugin runtimes with plugin UI rendering, host screen
  and surface extension points, and plugin-to-app bridge APIs.
- Safe mode with a crash log and a user-visible recovery notice when a plugin
  repeatedly fails during startup.

#### Plugin import

- **Choose file:** opens the system document picker and accepts
  `.dolphyplugin` files from local storage.
- **Paste link:** accepts direct HTTP/HTTPS links, normalizes links that omit a
  scheme to HTTPS, downloads the plugin to a temporary cache directory, and
  opens the existing preview and consent flow.
- Import safeguards: 48 MB size limit, `.dolphyplugin` filename enforcement,
  sanitized cache filenames, and automatic cleanup of imports older than
  24 hours.
- Localized import UI in Russian, English, and Arabic.

#### Localization

- Complete Russian, English, and Arabic resources, with Russian as the default
  locale and an explicit locale configuration for Android 13 and later.
- Localized plugin catalog descriptions, plugin manager screens, security
  warnings, and import dialogs.

#### Release engineering

- Dedicated release signing configuration backed by GitHub Actions secrets;
  the keystore is never committed or uploaded as a build artifact.
- Signed release workflow for `v*` tags and manual dispatch, including APK
  signature verification and GitHub release publication.
- Release signing documentation covering secret setup, local builds, and
  keystore backup requirements.

### Changed

- Upgraded the Android toolchain to AGP 9.5.0, Gradle 9.8.2, Kotlin 2.4.21,
  KSP 2.3.13, and compileSdk 37.
- Updated core dependencies, including AndroidX Core 1.19.1, Activity Compose
  1.13.0, Lifecycle 2.11.0, Navigation Compose 2.10.2, Room 2.8.5, Coil 2.7.0,
  Media3 1.11.1, Nordic BLE 2.11.0, Ktor 3.6.1, Rhino 1.9.2, Chaquopy 17.2.0,
  Kotlin coroutines 1.11.0, and kotlinx-datetime 0.8.0.
- Split CI into parallel **Build and test** and **Lint** jobs to shorten
  feedback time and isolate failures.
- Replaced the full `lintDebug` CI gate with `lintVitalRelease`, which checks
  release-blocking issues without the multi-hour analysis cost of the full
  debug lint on this single-module project.
- Release version codes now include a compatibility offset so the 1.0.0 line
  can update existing 2.7.0 test installations in place. Version 1.0.0 uses
  versionCode 30000.

### Fixed

- Removed every build warning: no deprecated Gradle features, AGP options,
  manifest attributes, Room schema exports, or Kotlin/Compose deprecations
  remain in the CI logs. Legacy BLE/WiFi/NFC platform APIs that minSdk 26 still
  requires are suppressed at file level with the reason documented.
- Silenced the GitHub Actions Node.js 20 and `ubuntu-latest` migration warnings
  by moving to the Node 24 action releases and pinning `ubuntu-24.04`.
- Fixed Android SDK setup on CI runners by removing the obsolete default
  `tools` package from `android-actions/setup-android`.
- Removed 16 stale English-only strings that blocked `lintVitalRelease` and
  prevented release builds from completing.
- Fixed the CI lint job failing because release signing was not configured by
  provisioning a temporary, job-scoped keystore used only for lint analysis.
- Completed the localization cleanup and resolved inconsistent Arabic and
  English strings across the app.
- Resolved source compatibility issues exposed by the AGP 9 and Kotlin 2.4
  upgrade.

### Security

- Release APKs are signed with a private RSA-4096 key and are never signed
  with the Android debug key.
- Signing credentials are stored outside the repository and injected into CI
  through encrypted GitHub Actions secrets.
- Plugins are treated as executable user code with app-level privileges.
  Installation requires an explicit preview that lists the plugin's declared
  capabilities, and the app warns users to install only trusted plugins.
- Added and updated security documentation, including the threat model,
  P0 hardening notes, and release signing runbook.

### Build and CI

- CI installs Android platform 37.0 and build-tools 37.0.0 for both build and
  lint jobs.
- The release workflow verifies the generated APK with `apksigner` before
  uploading it and publishing the GitHub release.
- Added a CI-only temporary keystore for lint so the release variant can be
  analyzed on branches and pull requests without exposing production secrets.

### Upgrade notes

- **Version:** `versionName` 1.0.0, `versionCode` 30000.
- **In-place updates:** versionCode 30000 is higher than the previous 2.7.0
  test build (20700), so release-signed installations can update normally.
- **Debug builds:** a debug-signed APK cannot be installed over a release-signed
  APK. Uninstall the debug build once before installing a release build.
- **Keystore:** keep the release keystore and its credentials backed up. Losing
  them makes future in-place updates impossible.
- **Plugin size:** plugins larger than 48 MB are rejected by the importer.
- **URL imports:** links must point directly to a `.dolphyplugin` file; HTML
  download pages are not parsed.

### Known limitations

- Full `lintDebug` remains slow on this large single-module project; CI uses
  `lintVitalRelease` as the release gate.
- Plugin URL import does not follow HTML landing pages or extract links from
  them; use the direct file URL.
- The plugin catalog currently ships with the official example plugins; it does
  not yet support third-party catalog indexes.

### Documentation

- [`docs/security/RELEASE_SIGNING.md`](docs/security/RELEASE_SIGNING.md) —
  release keystore, GitHub secrets, and local signing instructions.
- [`docs/security/THREAT-MODEL.md`](docs/security/THREAT-MODEL.md) — security
  model and trust boundaries.
- [`docs/security/P0-HARDENING.md`](docs/security/P0-HARDENING.md) — hardening
  work and verification notes.
- [`plugin-examples/README.md`](plugin-examples/README.md) — plugin catalog and
  example descriptions.

[1.0.0]: https://github.com/Alaa91H/Dolphy-App/releases/tag/v1.0.0
