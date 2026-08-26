# Dolphy plugin examples

Each `.dolphyplugin` file can be opened directly with Dolphy. Review its readable Python source and requested permissions before installation.

Plugins are executable user-provided code with broad access to application and device capabilities. Install plugins only from trusted sources and inspect their source code before use. The Dolphy developer is not responsible for damage caused by malicious, modified or untrusted plugins.

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

## Примеры сканеров

Плагины являются исполняемым пользовательским кодом и могут получать широкий доступ к функциям приложения и устройства. Устанавливайте плагины только из доверенных источников и проверяйте их исходный код. Разработчик Dolphy не несёт ответственности за ущерб, нанесённый вредоносными, изменёнными или недоверенными плагинами.

`Wi-Fi Scanner MD3E` показывает подробности текущего подключения и найденных сетей, включая защиту, диапазон, канал, ширину канала и уровень сигнала.

`BLE Scanner DEX MD3E` сканирует BLE-рекламу, загружает анализатор из встроенного DEX и показывает адрес, RSSI, TX power, сервисы, manufacturer data, тип рекламы и raw payload.
