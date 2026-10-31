# Dolphy plugin examples

Each `.dolphyplugin` file can be opened directly with Dolphy. Every plugin below is also listed in the
in-app plugin store (Plugins → available plugins), where it can be downloaded, installed, updated,
enabled, disabled and deleted without leaving the app.

Plugins are executable user-provided code with broad access to application and device capabilities. Install plugins only from trusted sources and inspect their source code before use. The Dolphy developer is not responsible for damage caused by malicious, modified or untrusted plugins.

## Catalog

| Plugin | Section | Permissions | What it does |
|---|---|---|---|
| [Wi-Fi Scanner MD3E](wifi_scanner_md3e.dolphyplugin) | Other | `wifi` | Connection, nearby networks, security, band, channel and signal details. |
| [BLE Scanner DEX MD3E](ble_scanner_dex_md3e.dolphyplugin) | Other | `bluetooth`, `dex` | Batched BLE scanning enriched by an embedded DEX analyzer. |
| [Radio Lab](radio_lab.dolphyplugin) | Bluetooth | — | Live BLE scan plus Wi-Fi interface diagnostics. |
| [Integration Hooks](integration_hooks.dolphyplugin) | Plugins | — | Screen FAB, IR transmit hook and a service provider example. |
| [BLE Asset Pack](ble_asset_pack.dolphyplugin) | Plugins | `ble_spam`, `bluetooth`, `download`, `files`, `infrared` | Custom BLE advertising mode and permission-aware asset downloads. |
| [IR Signal Lab](ir_signal_lab.dolphyplugin) | Infrared | `infrared` | Emitter and carrier diagnostics plus raw pulse-pattern transmission with presets. |
| [BLE GATT Explorer](ble_gatt_explorer.dolphyplugin) | Bluetooth | `bluetooth` | Connect to a peripheral, discover services and read characteristics. |
| [BLE Advert Lab](ble_advert_lab.dolphyplugin) | Bluetooth | `ble_spam`, `bluetooth` | Compose manufacturer data and broadcast a custom advertisement. |
| [Classic Bluetooth Lab](bt_classic_lab.dolphyplugin) | Bluetooth | `bluetooth` | Bonded devices, discovery, bonding actions and device details. |
| [Wi-Fi Analyzer](wifi_analyzer.dolphyplugin) | Other | `wifi` | Connection and DHCP lease details plus per-channel congestion. |
| [LAN Scanner](lan_scanner.dolphyplugin) | Other | `network` | Bounded subnet ping sweep and TCP port scan per host. |
| [NFC Toolkit](nfc_toolkit.dolphyplugin) | Other | `nfc` | Adapter status, live tag capture history and technology list. |
| [Device Inspector](device_inspector.dolphyplugin) | Other | — | Build, battery, connectivity and supported hardware features. |
| [Sensor Monitor](sensor_monitor.dolphyplugin) | Other | — | Every sensor on the device with a live value stream. |
| [Crypto Toolbox](crypto_toolbox.dolphyplugin) | Other | — | SHA/MD5 hashes, Base64, hex conversion and random identifiers. |
| [Plugin File Browser](file_browser.dolphyplugin) | Other | `files` | Sandbox browsing with text preview, sharing, folders and delete. |
| [Root Console](root_console.dolphyplugin) | Other | `root` | Root commands with presets and on-screen output. |
| [Shizuku Console](shizuku_console.dolphyplugin) | Other | `shizuku` | Privileged shell through Shizuku, including the permission request. |
| [Shell Console](shell_console.dolphyplugin) | Other | `shell` | Unprivileged shell commands running as the Dolphy app. |
| [USB Inspector](usb_inspector.dolphyplugin) | Other | `usb` | Attached USB devices with vendor, product and interface classes. |

## Scanner examples

### Wi-Fi Scanner MD3E

[Download the plugin](wifi_scanner_md3e.dolphyplugin)

Uses the native Dolphy device bridge and Material 3 Expressive UI. It displays the current connection, nearby networks, SSID and BSSID, RSSI, signal percentage, security type, band, frequency, channel, channel width and raw capability flags. The list supports All, Secure and Open filters.

Required permission group: `wifi`.

### BLE Scanner DEX MD3E

[Download the plugin](ble_scanner_dex_md3e.dolphyplugin)

Uses the native batched BLE scanner and loads an embedded DEX class from the same text plugin file. The DEX analyzer enriches advertisements with signal quality, estimated distance, payload size, advertisement type and common manufacturer names. The detail screen includes address, RSSI, TX power, connectability, service UUIDs, manufacturer records and the raw payload.

Required permission groups: `bluetooth`, `dex`.

The embedded class is `com.dolphy.examples.ble.BleAnalyzer`. Its readable Java source is available in [`dex-source`](dex-source/com/dolphy/examples/ble/BleAnalyzer.java). The plugin verifies the embedded DEX with SHA-256 before loading it.

## Radio and Bluetooth examples

### IR Signal Lab

[Download the plugin](ir_signal_lab.dolphyplugin)

Reports the IR emitter state through the `ConsumerIrManager` bridge, lists the carrier frequencies the device accepts and transmits raw pulse patterns. Presets build NEC frames, and every duration can be edited before sending. Repeat sends the pattern three times.

Required permission group: `infrared`.

### BLE GATT Explorer

[Download the plugin](ble_gatt_explorer.dolphyplugin)

Scans for BLE peripherals, connects on tap, discovers services and lists every characteristic with its properties. Selecting a characteristic issues a read, and the latest value is shown as hexadecimal together with a GATT event log.

Required permission group: `bluetooth`.

### BLE Advert Lab

[Download the plugin](ble_advert_lab.dolphyplugin)

Validates a manufacturer id and an even-length hexadecimal payload, then starts a custom advertisement with optional device name and connectable mode.

Required permission groups: `ble_spam`, `bluetooth`.

### Classic Bluetooth Lab

[Download the plugin](bt_classic_lab.dolphyplugin)

Lists bonded devices, runs classic discovery, opens a detail card with class, type, RSSI and cached service UUIDs, and can bond or remove a bond.

Required permission group: `bluetooth`.

## Network examples

### Wi-Fi Analyzer

[Download the plugin](wifi_analyzer.dolphyplugin)

Combines `wifiDetailedInfoJson`, `wifiDhcpInfoJson` and `wifiChannelsJson` into one screen: SSID/BSSID, signal, link speed, band, channel, full DHCP lease and the busiest channels with their strongest RSSI.

Required permission group: `wifi`.

### LAN Scanner

[Download the plugin](lan_scanner.dolphyplugin)

Detects the local subnet from the Wi-Fi address, sweeps a bounded host range with at most eight concurrent pings, lists the hosts that answer and scans 16 common TCP ports on demand.

Required permission group: `network`.

## NFC, device and utility examples

### NFC Toolkit

[Download the plugin](nfc_toolkit.dolphyplugin)

Shows adapter availability, reader mode and HCE support, opens NFC settings and records every tag delivered by Dolphy with its UID and technology list.

Required permission group: `nfc`.

### Device Inspector

[Download the plugin](device_inspector.dolphyplugin)

Renders build information, battery level and charging state, the active network transports and every supported hardware feature.

No permission group required.

### Sensor Monitor

[Download the plugin](sensor_monitor.dolphyplugin)

Lists all sensors with vendor, range and resolution, and streams live values from the selected sensor until it is stopped.

No permission group required.

### Crypto Toolbox

[Download the plugin](crypto_toolbox.dolphyplugin)

Computes SHA-256, SHA-512, SHA-1 and MD5 digests, converts text to and from Base64 and hexadecimal, generates secure random bytes and UUIDs, copies results to the clipboard and keeps a short history.

No permission group required.

### Plugin File Browser

[Download the plugin](file_browser.dolphyplugin)

Browses the plugin sandbox, previews text files, creates folders, shares files and deletes entries after confirmation.

Required permission group: `files`.

### Root Console

[Download the plugin](root_console.dolphyplugin)

Checks root availability, runs arbitrary commands through the root broker and ships read-only presets for identity, SELinux, storage, processes, packages and properties.

Required permission group: `root`.

### Shizuku Console

[Download the plugin](shizuku_console.dolphyplugin)

Reports whether Shizuku is running and granted, requests the permission and executes privileged commands with the same preset set.

Required permission group: `shizuku`.

### Shell Console

[Download the plugin](shell_console.dolphyplugin)

Runs unprivileged shell commands as the Dolphy app user and is useful for properties, package lists and environment inspection.

Required permission group: `shell`.

### USB Inspector

[Download the plugin](usb_inspector.dolphyplugin)

Lists attached USB devices, decodes the vendor and product ids, and shows the device class, subclass, protocol and interface count.

Required permission group: `usb`.

## Примеры сканеров

Плагины являются исполняемым пользовательским кодом и могут получать широкий доступ к функциям приложения и устройства. Устанавливайте плагины только из доверенных источников и проверяйте их исходный код. Разработчик Dolphy не несёт ответственности за ущерб, нанесённый вредоносными, изменёнными или недоверенными плагинами.

`Wi-Fi Scanner MD3E` показывает подробности текущего подключения и найденных сетей, включая защиту, диапазон, канал, ширину канала и уровень сигнала.

`BLE Scanner DEX MD3E` сканирует BLE-рекламу, загружает анализатор из встроенного DEX и показывает адрес, RSSI, TX power, сервисы, manufacturer data, тип рекламы и raw payload.

Полный список официальных плагинов доступен в магазине плагинов внутри приложения: загрузка, установка, обновление, включение, отключение и удаление выполняются на месте.
