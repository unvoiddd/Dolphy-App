# Dolphy

Dolphy is an Android toolbox for infrared remotes, Bluetooth and BLE, NFC tags, local network tools, and device experiments. It also includes a plugin system and a small dolphin companion with home screen widgets.

Requires Android 8.0 or later. The interface is available in English and Russian. Hardware support and Android permissions determine which tools are available on a particular phone.

[Release channel on Telegram](https://t.me/Dolphy_app_official) · [Plugin documentation](docs/plugins.html) · [Plugin examples](plugin-examples)

## Tools

### Infrared

- Browse the bundled Flipper-IRDB and Flipper firmware remote collections by category, brand, and model; search for a remote and save favorites.
- Import `.ir` files, manage custom remotes, and send individual commands from the remote control screen.
- Use universal remote collections and the IR Storm and IR Jammer transmission modes.
- Send through the phone's IR transmitter or a supported USB IR adapter. USB adapters require USB host support and permission to access the connected device. An arbitrary USB transmitter is not automatically compatible.

IR transmission requires a physical transmitter. The phone cannot receive or learn IR signals unless the selected hardware supports that operation. Repeated transmission modes do not guarantee that another remote will be blocked.

### Bluetooth and BLE

- Send BLE advertisements using the built-in Apple, Android, Samsung, Xiaomi, Vivo, Windows, and Phantom modes, or configure a custom manufacturer payload.
- Use Bond Spam to scan Bluetooth Classic devices, select a device, and run a pairing request test.
- Inspect nearby devices in NRF Scanner: addresses, RSSI, advertised service UUIDs, manufacturer data, GATT services, and available device information.
- Discover audio devices and use the audio, Fast Pair, TWS, smart light, and scooter tools where the target protocol is supported.
- Use the Bluetooth stress test tools to inspect device behavior under repeated traffic.

An advertised protocol or manufacturer ID does not establish that a device is vulnerable. Compatibility varies with device firmware, Bluetooth implementation, and Android version.

### Dolphy Chat

Dolphy Chat is a nearby group chat over BLE. Open the chat on both phones, grant Bluetooth access, and choose a display name. Internet access and an account are not required.

Messages are framed and split into packets, with UTF-8 decoded after the complete message has arrived. The screen shows pending, sent, and failed delivery states and lets you retry a failed message. Sending requires at least one discovered peer. A sent status means all selected peers acknowledged the Bluetooth writes; it is not a read receipt.

The encoded message, including its metadata, is limited to 4,096 bytes. The chat retains the latest 500 messages in memory. Messages are not encrypted by the application, and history is lost when the process ends. Both phones need a version using the current message format; older unframed chat versions are incompatible. There is no private conversation mode.

### Keyboard, mouse, and scripts

- Use the Bluetooth HID keyboard and mouse with a compatible paired computer or mobile device.
- Run the supported keyboard script commands and use the bundled script examples.
- Use USB keyboard features on phones with a compatible USB gadget configuration. Those features require root and kernel support; connecting a normal USB cable is not sufficient.

Bluetooth HID availability depends on the phone's HID Device profile and the receiving device. Root and Shizuku are used by specific privileged operations, rather than being required for every tool.

### NFC

- Read supported NFC tags and inspect the technologies, identifiers, NDEF records, and other information exposed by the tag.
- Write supported NDEF records, erase writable tags, and keep a local scan history.
- Save URL tags and emulate an NFC Forum Type 4 NDEF tag through Android host card emulation while the emulation screen is open.
- Share the local audio page through an NFC URL when the server is running.

Reading depends on the phone's NFC controller and tag technology. Read-only or protected tags cannot be written without the required access. Host card emulation does not clone a tag's UID, payment credentials, or every physical card protocol. It does not require changing the default payment application. A key search tool is not included.

### QR and local audio pages

Generate QR codes for text, URLs, Wi-Fi connection details, contacts, and other supported payloads. The local audio server serves a page from the phone and supports audio files, text to speech commands, page selection, and imported HTML.

The server uses the phone's local network address and tries ports 8080–8110. Other devices must be able to reach that address. The QR code contains a local URL; it is not an Internet hosting link. The foreground notification includes a Stop action. Playback may require a tap in the receiving browser because browsers restrict automatic audio playback. Closing the app's process ends the server.

### Local network, cameras, and printing

- Discover LAN hosts and inspect available network information and services.
- Use the network diagnostic hub, Wi-Fi tools, and password list management supported by the phone.
- Discover network cameras and open supported camera streams, including RTSP.
- Discover IPP printers or add an IPP/IPPS address manually, read printer capabilities, choose print options, and submit a document or test page.

Some network operations require root or Shizuku. Stock Android restricts Wi-Fi configuration and radio access, so a phone does not provide all the capabilities of a monitor-mode Wi-Fi adapter. A printer accepting a job does not mean the paper has already been printed. IPPS requires a trusted printer certificate.

### Video casting

Select a local video and choose one of three playback paths:

- **Google Cast:** select a receiver with the Cast chooser, then load the video through the Google Cast media session. Google Play services and a compatible Cast receiver are required.
- **DLNA:** discover MediaRenderer devices using SSDP and send the video URL to the device's advertised AVTransport service.
- **Browser:** open the local playback page on another device in the same network.

The video is copied into temporary app storage on a background thread and served over local HTTP with byte range support. Replacement and ViewModel cleanup remove the temporary file. Keep Dolphy open while the receiver uses the file. The receiver must support the video's container and codecs, and the network must allow devices to communicate. DIAL application launch is not used as a substitute for video playback.

### Appearance and companion

Choose the language, light or dark theme, accent color, font and interface scale, animations, performance mode, navigation style, and shortcut destination. The dolphin companion, progression, and widgets provide quick access to the app and selected tools.

## Permissions and hardware

| Tool | Main requirements |
| --- | --- |
| Bluetooth discovery | Bluetooth enabled; nearby device permissions on Android 12+; location permission for scans that require it |
| BLE advertising and chat | BLE advertising support and the advertising permission on Android 12+ |
| Bluetooth keyboard and mouse | HID Device profile support and a compatible paired receiver |
| Infrared | Built-in IR transmitter or a supported USB IR adapter |
| NFC reading and writing | NFC hardware enabled and a supported tag |
| NFC emulation | Android NFC host card emulation support |
| LAN tools, printing, casting, audio pages | A reachable local network; multicast discovery may be blocked by the router |
| USB hardware | USB host or gadget support appropriate to the tool |
| Privileged operations | Root or a working Shizuku setup where the selected operation requires it |

Bluetooth access is requested in the relevant tool. Root and Shizuku prompts are not shown automatically on startup. Camera, microphone, storage, and notification access are requested by the features that use them. A denied permission should not prevent independent tools from being opened.

## Plugins

Install `.dolphyplugin` files from the plugin manager. The current plugin API supports Python plugins, UI components, callbacks, settings, files, device APIs, extension surfaces, and optional DEX libraries. A JavaScript runtime is retained for existing plugins.

Plugins run inside the app process. Install code from sources you trust: plugin declarations and safe mode are not an operating-system sandbox. Safe mode can help recover from a plugin that fails during startup. See the [API documentation](docs/plugins.html), [examples](plugin-examples), and [manual regression fixtures](plugin-test-suite).

## Build

The project uses Kotlin, Jetpack Compose, Gradle 8.11.1, Android Gradle Plugin 8.9.1, and Chaquopy with embedded Python 3.11. The Android module compiles against SDK 36, targets SDK 36, and supports API 26 and later. Release builds include ARMv7, ARM64, x86, and x86_64 native libraries.

Use JDK 17 or a compatible Android Studio Gradle JDK, install Android SDK 36, and set the SDK location in `local.properties` or your environment. Dependencies are downloaded during the first build. The bundled IR assets are already under `app/src/main/assets`; the optional source asset sync task only runs when its source directory exists.

From PowerShell in the project root:

```powershell
.\gradlew.bat testDebugUnitTest lintRelease assembleRelease
```

For a debug APK:

```powershell
.\gradlew.bat assembleDebug
```

Outputs:

- Release: `app/build/outputs/apk/release/app-release.apk`
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Unit test report: `app/build/reports/tests/testDebugUnitTest/index.html`
- Lint report: `app/build/reports/lint-results-release.html`

The existing release configuration uses the Gradle debug signing key. Release signing has not been changed. Unit tests cover BLE message framing, HTTP byte ranges, and the bundled IEEE manufacturer database; hardware behavior still needs testing on the relevant phones, tags, receivers, and adapters.

## Source layout

`app/src/main/java/com/droid/dolphy` contains the application, navigation, shared UI, and Bluetooth advertisement implementations. Feature packages include `chat`, `nfc`, `nrf`, `hid`, `qr`, `tvcast`, `network`, `printer`, and `plugin`. Application theme and shared state are separated into `AppTheme.kt` and `SpamViewModel.kt`; IR asset parsing and indexing are in `IrRepository.kt`.

`app/src/main/assets` contains IR collections, BLE manufacturer prefixes, companion animations, scripts, and local web assets. `docs` contains the project and plugin documentation. `plugin-examples` and `plugin-test-suite` contain source examples and manual regression fixtures.

## License and credits

Dolphy is licensed under [GNU GPL v3](LICENSE). Bundled collections and third-party components retain their own licenses and notices; see, for example, the [Flipper-IRDB license](app/src/main/assets/Flipper-IRDB-main/LICENSE). The project includes upstream code, libraries, and assets rather than being written entirely from scratch.

Thanks to ZalexDev, ars3nb, Astrocodee, and the authors of the upstream Bluetooth tools, Flipper firmware, Flipper-IRDB, and the libraries used by the app.
