package com.droid.dolphy.plugin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.plugin.PluginIcons

@Composable
fun PluginAboutScreen(navController: NavController) {
    val accent = MaterialTheme.colorScheme.primary

    MaterialBackground(accentColor = accent) {
        Column(Modifier.fillMaxSize()) {
            SectionTopBar(
                title = stringResource(R.string.plugin_about_title),
                onBack = { navController.popBackStack() },
                accentColor = accent,
            )

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
            ) {
                item {
                    GuideHeader(
                        accent = accent,
                        title = stringResource(R.string.plugin_about_guide_title),
                        body = stringResource(R.string.plugin_about_guide_body),
                    )
                }

                item { SectionTitle("1. Каркас файла") }
                item {
                    CodeBlock(
                        """

                        function onLoad(api) {
                          api.other.add({ ... });
                        }

                        function screen_main(ui, api, state) {
                          return ui.scaffold({
                            topBar: { title: "My Tool", showBack: true },
                            content: ui.column({ padding: 16, spacing: 12 }, [
                              ui.text("Привет", { style: "headlineSmall" }),
                              ui.button({ text: "Жми", onClick: function () {
                                api.toast("OK");
                              }})
                            ])
                          });
                        }

                        function onUnload(api) {  }
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("2. Разделы экрана Другое") }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Куда попадёт карточка",
                        body = "Четыре встроенных раздела:\n\n" +
                            "• INFRARED - инфракрасные инструменты\n" +
                            "• BLUETOOTH - Bluetooth, BLE, HID\n" +
                            "• ПРОЧЕЕ - NFC, QR, Wi-Fi, LAN\n" +
                            "• ПЛАГИНЫ - карточки плагинов\n\n" +
                            "Поле section в api.other.add выбирает раздел. " +
                            "Алиасы: infrared/ir, bluetooth/ble, other/прочее, plugins/плагины. " +
                            "Любая другая строка создаёт свою группу с этим названием.",
                    )
                }
                item {
                    CodeBlock(
                        """
                        section: "infrared"
                        section: "bluetooth"
                        section: "other"
                        section: "plugins"

                        section: "Root Tools"
                        section: "Мои утилиты"
                        section: "Home Lab"
                        """.trimIndent(),
                    )
                }
                item {
                    VisualExample(
                        accent = accent,
                        icon = Icons.Default.Extension,
                        title = "My Tool",
                        description = "Пример карточки в выбранном разделе",
                        code = """
                            api.other.add({
                              section: "bluetooth",
                              title: "My Tool",
                              description: "Короткое описание под заголовком",
                              icon: "bluetooth_searching",
                              screen: "main",
                              color: 0xFF7C4DFF,
                              order: 0
                            });

                            api.other.add({
                              section: "Root Tools",
                              title: "Shell Lab",
                              icon: "terminal",
                              screen: "shell"
                            });
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("3. Иконка карточки в Другое") }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Поле icon",
                        body = "Строковое имя иконки. Неизвестный ключ даёт extension.\n" +
                            "Цвет: color как 0xAARRGGBB, например 0xFF4CAF50.",
                    )
                }
                item {
                    CodeBlock(
                        "Доступные имена icon:\n" +
                            PluginIcons.knownNames.joinToString(", "),
                    )
                }
                item {
                    CodeBlock(
                        """
                        api.other.add({
                          section: "plugins",
                          title: "Wi-Fi Tool",
                          icon: "wifi",
                          color: 0xFF4CAF50,
                          screen: "main"
                        });
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("4. UI-компоненты") }
                item {
                    CodeBlock(
                        """
                        ui.scaffold({ topBar: { title, showBack, actions }, content, fab })
                        ui.column / ui.row / ui.box / ui.lazyColumn
                        ui.text(text, { style, color, maxLines })
                        ui.button({ text, style, onClick, fillMaxWidth, enabled })
                        ui.iconButton / ui.textField / ui.switch / ui.slider / ui.chip
                        ui.linearProgress / ui.circularProgress
                        ui.spacer / ui.divider / ui.icon
                        ui.materialCard({ contentPadding, segmentedIndex, segmentedCount, onClick }, children)
                        ui.functionRow({ title, description, icon, iconTint, onClick })
                        ui.segmentedList / ui.settingsRow / ui.logPanel / ui.alertDialog
                        ui.webView
                        ui.image({ asset|src|path, width?, height?, size?, scale, cornerRadius, fillMaxWidth })

                        ui.m3.filledButton / tonalButton / outlinedButton / textButton
                        ui.m3.card / elevatedCard / outlinedCard / surface
                        ui.m3.title / body / label / listItem / fab / filterChip
                        ui.m3.buttons.filled  ·  ui.m3.cards.outlined

                        ui.m3.expressive.connectedButtonGroup({ options, selected, onSelect })
                        ui.m3.expressive.tabRow({ tabs, selected, onSelect })
                        ui.m3.expressive.bounceButton({ text, onClick })
                        ui.m3.expressive.splitButton({ text, onClick, onSecondary })
                        ui.m3.expressive.wavyProgress({ progress?, size? })
                        ui.m3.expressive.floatingToolbar({ items: [{icon, selected, onClick}] })
                        ui.checkbox / ui.radioGroup / ui.dropdown / ui.select / ui.lazyRow
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("5. Диалоги (api.dialog)") }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Модальные окна",
                        body = "Императивный API: api.dialog.show / api.dialog.dismiss.\n" +
                            "Не зависит от setState. Заголовок, текст и произвольный список кнопок.\n" +
                            "Стиль кнопки: filled | tonal | outlined | text | destructive.\n\n" +
                            "Альтернатива в дереве UI: ui.alertDialog({ show, title, message, buttons }).",
                    )
                }
                item {
                    CodeBlock(
                        """
                        api.dialog.show({
                          title: "Подтверждение",
                          message: "Удалить запись?",
                          cancelable: true,
                          buttons: [
                            { text: "Удалить", style: "destructive", onClick: function () {
                              api.toast("Удалено");
                            }},
                            { text: "Отмена", style: "text", onClick: function () {} }
                          ]
                        });

                        api.dialog.show({
                          title: "Готово",
                          text: "Операция завершена",
                          confirmText: "OK",
                          onConfirm: function () { api.toast("OK"); },
                          dismissText: "Закрыть",
                          onDismiss: function () {}
                        });

                        api.dialog.dismiss();

                        ui.alertDialog({
                          show: state.dlg === true,
                          title: "Заголовок",
                          message: "Текст",
                          buttons: [
                            { text: "Да", style: "filled", onClick: function () {
                              api.setState({ dlg: false });
                            }},
                            { text: "Нет", style: "text", onClick: function () {
                              api.setState({ dlg: false });
                            }}
                          ]
                        })
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("6. WebView") }
                item {
                    VisualExample(
                        accent = accent,
                        icon = Icons.Default.Language,
                        title = "WebView",
                        description = "Встроенный браузерный виджет на экране плагина",
                        code = """
                            ui.webView({
                              url: "https://example.com",
                              height: 360,
                              fillMaxSize: false
                            })

                            ui.webView({
                              html: "<html><body style='background:#111;color:#0f0'>" +
                                    "<h3>Hello from plugin</h3></body></html>",
                              height: 240
                            })

                            function screen_main(ui, api, state) {
                              return ui.scaffold({
                                topBar: { title: "Browser", showBack: true },
                                content: ui.column({ padding: 12, spacing: 8, fillMaxSize: true }, [
                                  ui.textField({
                                    value: state.url || "https://example.com",
                                    label: "URL",
                                    onChange: function (v) { api.setState({ url: v }); }
                                  }),
                                  ui.button({ text: "Открыть", onClick: function () {
                                    api.setState({ open: true });
                                  }}),
                                  (state.open
                                    ? ui.webView({ url: state.url, fillMaxSize: true, height: 480 })
                                    : ui.text("Введите URL", { color: "muted" }))
                                ])
                              });
                            }
                        """.trimIndent(),
                    )
                }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Замечания по WebView",
                        body = "• В странице включён JavaScript.\n" +
                            "• Это не полный браузер, без системной адресной строки.\n" +
                            "• Локальный UI удобно отдавать через html.\n" +
                            "• Для URL нужен доступ в сеть.",
                    )
                }

                item { SectionTitle("7. Material-карточка на экране") }
                item {
                    MaterialCard(Modifier.fillMaxWidth(), accentColor = accent, contentPadding = 16.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Заголовок", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Текст внутри MaterialCard Dolphy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = {}) { Text("Кнопка") }
                        }
                    }
                }
                item {
                    CodeBlock(
                        """
                        ui.materialCard({ contentPadding: 16 }, [
                          ui.text("Заголовок", { style: "titleMedium" }),
                          ui.text("Текст...", { style: "bodySmall", color: "muted" }),
                          ui.button({ text: "Кнопка", onClick: function () {} })
                        ])
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("8. Device / Android API (host)") }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Покрытие Android API приложения",
                        body = "Плагины получают host-обёртки системных API Dolphy: " +
                            "Wi-Fi, BLE/BT, BLE spam, IR, NFC, сеть, USB, location, sensors, audio, " +
                            "clipboard, vibrator, notifications, intents, файлы/media/camera, assets, " +
                            "crypto, timers, snackbar, root/shizuku (расширенный), export/import API.\n\n" +
                            "Права = права приложения (+ runtime через api.pm.request). " +
                            "Compose не пробрасывается: UI через ui.*.\n" +
                            "Media pick / camera / export / permission работают на экране плагина (PluginHost).",
                    )
                }
                item {
                    CodeBlock(
                        """
                        api.setState / getState / toast / log / navigate
                        api.dialog.show / dismiss
                        api.snackbar.show({ message, action?, onAction? }) / dismiss
                        api.bottomSheet.show({ title, message, buttons }) / dismiss
                        api.prefs.get / set
                        api.pluginId / pluginName / isLibrary
                        api.app.open("other/nfc_tools")
                        api.app.packageName

                        api.timers.setTimeout(ms, fn) → id
                        api.timers.setInterval(ms, fn) → id
                        api.timers.clear(id) / clearTimeout / clearInterval / clearAll

                        api.crypto.md5 / sha1 / sha256 / sha512 (text)
                        api.crypto.hash(algo, text) / hashFile(algo, path)
                        api.crypto.base64Encode / base64Decode
                        api.crypto.hexEncode / hexDecode
                        api.crypto.randomHex(n) / uuid()

                        api.pm.hasPermission("camera"|"location"|full.name)
                        api.pm.request("camera", cb)
                        api.pm.hasFeature / features

                        api.assets.list(path?) / exists(path)
                        api.assets.read / readText / readBase64 / dataUri

                        api.files.list / read / write / append / delete / exists
                        api.files.mkdir / stat / copy / move / absolute
                        api.files.readBase64 / writeBase64
                        api.files.share(path, mime?) / openWith(path, mime?)
                        api.files.pick / pickFile / add
                        api.files.export / saveAs({ path, name })

                        api.media.pickFile / pickImage / pickVideo / takePhoto
                        api.media.export / share / hasCamera
                        api.camera.takePhoto / pickImage / pickVideo / hasCamera

                        api.wifi.isEnabled / enable / disable / setEnabled
                        api.wifi.startScan / scan / getScanResults / connectionInfo
                        api.wifi.detailedInfo / dhcpInfo / channels / macAddress
                        api.wifi.configuredNetworks / is5GHzSupported / isP2pSupported
                        api.wifi.disconnect / reconnect / addSuggestion / openSettings
                        api.wifi.p2pDiscover / p2pConnect / p2pDisconnect / p2pGroupInfo

                        api.ble.capabilities / hasScanner / hasAdvertiser
                        api.ble.startScan / startScanFiltered / stopScan
                        api.ble.advertise / advertiseCustom / stopAdvertise
                        api.ble.connect / disconnect / discoverServices / services
                        api.ble.read / write / writeNoResponse
                        api.ble.enableNotifications / disableNotifications
                        api.ble.requestMtu / readRssi / setPreferredPhy
                        api.ble.writeDescriptor / readDescriptor
                        api.ble.serverStart / serverStop / serverNotify / serverRespond

                        api.bt.isEnabled / state / capabilities / enable / disable
                        api.bt.name / setName / address / scanMode / deviceInfo
                        api.bt.bondedDevices / createBond / removeBond / getBondState
                        api.bt.startDiscovery / stopDiscovery / openSettings
                        api.bt.connectRfcomm / sendRfcomm / sendRfcommText
                        api.bt.disconnectRfcomm / rfcommConnected

                        api.bleSpam.start / stop / toggle / stopAll / isActive
                        api.bleSpam.setDelay / getDelay / startMode / sections

                        api.nfc.isAvailable / isEnabled / capabilities
                        api.nfc.onTag(cb) / lastTag / openSettings / openTools
                        api.nfc.readNdef / writeNdef / writeNdefRaw
                        api.nfc.transceive(tech, hex, cb)
                        api.nfc.mifareRead / mifareWrite / ultralightRead / ultralightWrite
                        api.nfc.rootEnable / rootDisable

                        api.ir.hasEmitter / carrierFrequencies / transmit / send
                        api.ir.listButtons / playFile / playButton
                        api.ir.toggleStorm / toggleJammer / openRemotes

                        api.net.active / detail / interfaces
                        api.net.http(method, url, body, headers, cb)
                        api.net.download(url, sandboxPath, cb)
                        api.net.tcpReachable / ping / portScan
                        api.net.nsdDiscover / nsdStop

                        api.clipboard / vibrator / haptics / notify / intent
                        api.usb.devices / location / sensors / audio / device

                        api.root.wifiEnable/Disable / btEnable/Disable / nfcEnable/Disable
                        api.root.wifiStatus / btStatus / nfcStatus / iwScan / hciconfig
                        api.root.* (files, pm, input, magisk, selinux, pull/push…)
                        api.shizuku.* / api.shell.exec|via|quote

                        api.exportApi / importApi / listApis
                        api.exportDesign / importDesign / listDesigns
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("8b. Media / files — примеры") }
                item {
                    CodeBlock(
                        """
                        api.media.pickImage({ path: "media/photo.jpg" }, function (r) {
                          if (r.ok) api.toast(r.path + " · " + r.size + " B");
                        });

                        api.pm.request("camera", function (g) {
                          if (!g.granted && !g["android.permission.CAMERA"]) {
                            api.toast("Нет CAMERA"); return;
                          }
                          api.camera.takePhoto({ path: "media/shot.jpg" }, function (r) {
                            api.toast(r.ok ? r.path : (r.error || "cancel"));
                          });
                        });

                        api.files.export({ path: "notes/hello.txt", name: "hello.txt" }, function (r) {
                          api.toast(r.ok ? "exported" : "cancel");
                        });

                        api.files.share("notes/hello.txt", "text/plain");
                        api.files.openWith("media/photo.jpg", "image/jpeg");

                        api.net.download("https://example.com/a.txt", "dl/a.txt", function (r) {
                          if (r.ok) api.toast("saved " + r.path);
                        });
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("8c. Timers / crypto / snackbar") }
                item {
                    CodeBlock(
                        """
                        var id = api.timers.setTimeout(1000, function () {
                          api.snackbar.show({ message: "через 1с", action: "OK" });
                        });

                        api.crypto.sha256("hello");
                        api.crypto.hashFile("sha256", "notes/hello.txt");
                        api.crypto.base64Encode("data");
                        api.crypto.uuid();

                        api.bottomSheet.show({
                          title: "Действия",
                          message: "Выберите",
                          buttons: [
                            { text: "A", style: "filled", onClick: function () {} },
                            { text: "Закрыть", style: "text", onClick: function () {} }
                          ]
                        });
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("9. Assets приложения (api.assets)") }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Чтение встроенных ресурсов Dolphy",
                        body = "Плагины могут читать assets APK только на чтение: IR-база (Flipper-IRDB-main), " +
                            "анимации дельфина (dolphin/external), BadUSB-скрипты (bad_usb_scripts), " +
                            "иконки Flipper (flipper_icons), QR-audio (qr_spoofer), captive portal HTML, " +
                            "роутеры (routers/*.json) и др.\n\n" +
                            "Путь относительно корня assets. «..» и абсолютные пути отсекаются.\n" +
                            "Для UI: text через read, бинарники (png/mp3) через readBase64 или dataUri " +
                            "(удобно в ui.webView).",
                    )
                }
                item {
                    CodeBlock(
                        """
                        var root = JSON.parse(api.assets.list(""));

                        var ir = api.assets.read("Flipper-IRDB-main/TVs/Samsung/Samsung_BN59.ir");
                        if (ir) {
                        }

                        var ducky = api.assets.read("bad_usb_scripts/Win11_Rickroll.txt");

                        var uri = api.assets.dataUri("flipper_icons/AstroDolphy.jpg");

                        ui.image({
                          asset: "flipper_icons/AstroDolphy.jpg",
                          width: 160,
                          height: 120,
                          scale: "fit",
                          cornerRadius: 12,
                          fillMaxWidth: false
                        })

                        var frames = JSON.parse(api.assets.list("dolphin/external/L1_Doom_128x64"));
                        frames.filter(function (f) { return !f.isDir; }).forEach(function (f) {
                          var b64 = api.assets.readBase64(f.path);
                        });

                        api.assets.exists("qr_spoofer/index.html");
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("10. Dual API: Android ↔ Dolphy") }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Два стиля вызова host-API",
                        body = "В коде можно писать Android-стиль (домен.действие) и Dolphy-стиль " +
                            "(действие.домен). Оба пути ведут в один host-bridge.\n\n" +
                            "• Android: api.ir.send(freq, pattern)\n" +
                            "• Dolphy:  dolphy.send.ir(freq, pattern)  или  api.dolphy.send.ir(...)\n\n" +
                            "Глобальный объект dolphy доступен в любом плагине (не только api.dolphy).",
                    )
                }
                item {
                    CodeBlock(
                        """
                        api.ir.send(38000, [9000, 4500, 560, 560]);
                        api.ir.transmit(38000, [9000, 4500, 560, 560]);
                        api.wifi.scan(function (list) { ... });
                        api.shell.exec("id", function (r) { ... });

                        dolphy.send.ir(38000, [9000, 4500, 560, 560]);
                        dolphy.scan.wifi(function (list) { ... });
                        dolphy.scan.ble({ onDevice: function (d) { ... } });
                        dolphy.exec.shell("id", function (r) { ... });
                        dolphy.exec.root("id", cb);
                        dolphy.exec.shizuku("id", cb);
                        dolphy.toggle.irStorm();
                        dolphy.toggle.bleSpam("apple");
                        dolphy.read.clipboard();
                        dolphy.write.clipboard("hi");
                        dolphy.send.notify(1, "Title", "Text");
                        dolphy.play.tone(24, 200);
                        dolphy.vibrate(50);
                        dolphy.http("GET", "https://example.com", null, null, cb);

                        dolphy.ir.status();
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("11. Плагины-библиотеки (API + Design)") }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Что это",
                        body = "Плагин-библиотека — .js, который публикует общий API или design-pack " +
                            "для других плагинов (UI не обязателен).\n\n" +
                            "Заголовок:\n" +
                            "• library=true | type=library — логическая библиотека\n" +
                            "• design=true | type=design — design-pack (Material 3 и т.п.)\n" +
                            "Библиотеки грузятся первыми.\n\n" +
                            "• api.exportApi(name, object) / importApi / listApis\n" +
                            "• api.exportDesign(name, object) / importDesign / listDesigns\n" +
                            "• Dual alias при экспорте: android: \"ir.send\", dolphy: \"dolphy.send.ir\"\n\n" +
                            "importDesign автоматически подставляет ui в фабрики компонентов.",
                    )
                }
                item {
                    CodeBlock(
                        """

                        function onLoad(api) {
                          api.exportApi("math", {
                            add: function (a, b) { return Number(a) + Number(b); },
                            clamp: function (v, lo, hi) {
                              return Math.max(lo, Math.min(hi, v));
                            }
                          });
                        }


                        function onLoad(api) {
                          api.exportDesign("m3extra", {
                            heroCard: function (ui, props) {
                              return ui.m3.card({ contentPadding: 20 }, [
                                ui.m3.title(props.title || "Title"),
                                ui.m3.body(props.body || ""),
                                ui.m3.filledButton({
                                  text: props.action || "OK",
                                  onClick: props.onClick,
                                  fillMaxWidth: true
                                })
                              ]);
                            }
                          });
                        }


                        function onLoad(api) {
                          api.other.add({
                            section: "plugins",
                            title: "Uses libs",
                            icon: "extension",
                            screen: "main"
                          });
                        }

                        function screen_main(ui, api, state) {
                          var math = api.importApi("math");
                          var m3x = api.importDesign("m3extra");
                          return ui.scaffold({
                            topBar: { title: "Libs", showBack: true },
                            content: ui.column({ padding: 16, spacing: 12 }, [
                              ui.m3.title("Built-in Material 3"),
                              ui.m3.filledButton({ text: "toast", onClick: function () {
                                api.toast(math ? String(math.add(2, 3)) : "no math");
                              }}),
                              m3x
                                ? m3x.heroCard({
                                    title: "Design lib",
                                    body: "importDesign + ui.m3",
                                    onClick: function () { api.toast("ok"); }
                                  })
                                : ui.m3.body("Установите m3_extra")
                            ])
                          });
                        }
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("12. BLE spam API (api.bleSpam)") }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Встроенные протоколы спама",
                        body = "Высокоуровневый доступ к тем же BLE spam-движкам, что и в экране Bluetooth:\n\n" +
                            "• apple / ios / iphone — Continuity (AirPods/Beats/AirTag/Nearby). " +
                            "Логика iOS: непрерывный цикл advertise как в tutozz/blespam.\n" +
                            "• samsung — Easy Setup (Galaxy Buds / Watch)\n" +
                            "• android — Google Fast Pair\n" +
                            "• xiaomi — Xiaomi Quick Connect\n" +
                            "• windows — Microsoft Swift Pair\n" +
                            "• phantom — phantom advertisements\n" +
                            "• all / kitchen — kitchen sink (все протоколы по кругу)\n\n" +
                            "Нужны разрешения BLUETOOTH_ADVERTISE / BT включён. " +
                            "Не забудьте stop / stopAll в onUnload.",
                    )
                }
                item {
                    CodeBlock(
                        """

                        function onLoad(api) {
                          api.other.add({
                            section: "bluetooth",
                            title: "BLE Spam Panel",
                            icon: "bluetooth_searching",
                            color: 0xFF2196F3,
                            screen: "main"
                          });
                        }

                        function screen_main(ui, api, state) {
                          var active = [];
                          try { active = JSON.parse(api.bleSpam.active() || "[]"); } catch (e) {}
                          function row(key, label) {
                            var on = api.bleSpam.isActive(key);
                            return ui.functionRow({
                              title: label,
                              description: on ? "Активно" : "Выкл",
                              icon: "bluetooth",
                              onClick: function () {
                                api.bleSpam.toggle(key);
                                api.setState({ t: Date.now() });
                              }
                            });
                          }
                          return ui.scaffold({
                            topBar: { title: "BLE Spam", showBack: true },
                            content: ui.column({ padding: 12, spacing: 8 }, [
                              ui.text("Активно: " + active.join(", "), { color: "muted" }),
                              row("apple", "Apple / iPhone (Continuity)"),
                              row("samsung", "Samsung Easy Setup"),
                              row("android", "Android Fast Pair"),
                              row("xiaomi", "Xiaomi Quick Connect"),
                              row("windows", "Windows Swift Pair"),
                              ui.button({ text: "Stop all", style: "tonal", onClick: function () {
                                api.bleSpam.stopAll();
                                api.setState({ t: Date.now() });
                              }}),
                              ui.button({ text: "Delay 50ms", onClick: function () {
                                api.bleSpam.setDelay(50);
                              }})
                            ])
                          });
                        }

                        function onUnload(api) {
                          try { api.bleSpam.stopAll(); } catch (e) {}
                        }

                        """.trimIndent(),
                    )
                }

                item { SectionTitle("12. Пример: BLE scan") }
                item {
                    VisualExample(
                        accent = accent,
                        icon = Icons.Default.Bluetooth,
                        title = "BLE Scanner",
                        description = "section: bluetooth + scan",
                        code = """
                            api.other.add({
                              section: "bluetooth",
                              title: "BLE Scanner",
                              icon: "bluetooth_searching",
                              screen: "main"
                            });

                            api.ble.startScan(function (d) {
                              var list = (api.getState().devices) || [];
                              list = list.concat([d]);
                              api.setState({ devices: list });
                            });
                        """.trimIndent(),
                    )
                }

                item { SectionTitle("13. Root / Shizuku / Shell") }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Системный shell (расширенный root)",
                        body = "При запуске Dolphy запрашивает Shizuku (если сервис запущен) и root (su -c id).\n\n" +
                            "• api.root — su, максимум файловых/pm/am/net утилит\n" +
                            "• api.shizuku — ADB shell без root\n" +
                            "• api.shell.exec — сначала Shizuku, иначе root (shell.via)\n\n" +
                            "Ответы async: callback(r) где r = { ok?, code, out, err } " +
                            "(для exec/list/read… out — текст stdout).",
                    )
                }
                item {
                    CodeBlock(
                        """
                        available / isRooted
                        exec(cmd, cb)
                        script(multiline, cb)
                        id / which(bin)
                        exists / list|ls / stat (path, cb)
                        read(path, max?, cb) / readBase64(path, max?, cb)
                        write(path, text, cb) / writeBase64(path, b64, cb)
                        mkdir / rm|delete(path, recursive?, cb)
                        copy / move / chmod(mode, path) / chown(owner, path)
                        getprop(key?) / setprop(key, value)
                        packages(filter?) / pmPath(pkg)
                        install(apk) / uninstall / enable / disable / clear(pkg)
                        grant(pkg, perm) / revoke(pkg, perm)
                        forceStop(pkg) / start(component) / broadcast(action)
                        settingsGet(ns, key) / settingsPut(ns, key, value)
                        remount(rw?) / mount / df
                        ps(filter?) / kill(pid|name)
                        dmesg(lines?) / logcat(lines?) / services
                        iptables(args) / ip(args) / ifconfig / sysctl(key?, value?)
                        reboot(""|"recovery"|"bootloader"|"soft")
                        pull(remote, sandboxRel, cb)
                        push(sandboxRel, remote, cb)
                        quote(str)
                        """.trimIndent(),
                    )
                }
                item {
                    VisualExample(
                        accent = accent,
                        icon = Icons.Default.Security,
                        title = "Root — базово",
                        description = "uid 0, Magisk / KernelSU",
                        code = """
                            if (!api.root.available()) {
                              api.toast("Root недоступен");
                              return;
                            }
                            api.root.id(function (r) {
                              api.setState({ log: r.out || r.err || "" });
                            });
                            api.root.getprop("ro.build.version.release", function (r) {
                              api.toast(r.out || "?");
                            });
                            api.root.ls("/system", function (r) {
                              api.setState({ log: r.out || "" });
                            });
                        """.trimIndent(),
                    )
                }
                item {
                    VisualExample(
                        accent = accent,
                        icon = Icons.Default.Security,
                        title = "Root — файлы и pull/push",
                        description = "Чтение системных файлов в sandbox",
                        code = """
                            api.root.read("/system/build.prop", 200000, function (r) {
                              api.setState({ log: (r.out || "").slice(0, 2000) });
                            });

                            api.root.pull(
                              "/system/etc/hosts",
                              "root_pull/hosts",
                              function (r) {
                                if (r.ok) api.toast("→ " + r.path);
                              }
                            );

                            api.files.write("out/note.txt", "hello");
                            api.root.push("out/note.txt", "/data/local/tmp/note.txt", function (r) {
                              api.toast(r.ok ? "pushed" : (r.err || r.out));
                            });
                        """.trimIndent(),
                    )
                }
                item {
                    VisualExample(
                        accent = accent,
                        icon = Icons.Default.Security,
                        title = "Root — packages / props",
                        description = "pm, getprop, settings",
                        code = """
                            api.root.packages("dolphy", function (r) {
                              api.setState({ log: r.out || "" });
                            });
                            api.root.pmPath("com.droid.dolphy", function (r) {
                              api.toast((r.out || "").split("\\n")[0]);
                            });
                            api.root.settingsGet("system", "screen_brightness", function (r) {
                              api.toast("brightness=" + (r.out || "").trim());
                            });
                        """.trimIndent(),
                    )
                }
                item {
                    VisualExample(
                        accent = accent,
                        icon = Icons.Default.Terminal,
                        title = "Shizuku",
                        description = "ADB shell без root",
                        code = """
                            if (!api.shizuku.available()) {
                              api.toast("Запустите Shizuku");
                              return;
                            }
                            if (!api.shizuku.hasPermission()) {
                              api.shizuku.requestPermission();
                              return;
                            }
                            api.shizuku.exec("cmd wifi status", function (r) {
                              api.setState({ log: r.out || r.err || "" });
                            });
                        """.trimIndent(),
                    )
                }
                item {
                    VisualExample(
                        accent = accent,
                        icon = Icons.Default.Extension,
                        title = "api.shell",
                        description = "Сначала Shizuku, иначе root",
                        code = """
                            api.toast("via=" + api.shell.via());
                            api.shell.exec("getprop ro.product.model", function (r) {
                              if (r.code === -1) {
                                api.toast("Нужен Shizuku или root");
                                return;
                              }
                              api.toast(r.out);
                            });
                        """.trimIndent(),
                    )
                }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Безопасность",
                        body = "root/shizuku дают полный доступ к устройству (файлы, pm, reboot…). " +
                            "Ставь только свои или проверенные .js. " +
                            "Выключатель на экране Плагины отключает плагин и его карточки. " +
                            "Sandbox files.* изолирован; root.pull/push — мост наружу.",
                    )
                }

                item { SectionTitle("14. State, lifecycle, советы") }
                item {
                    CodeBlock(
                        """
                        api.setState({ count: (state.count || 0) + 1 });

                        api.ble.startScan(function (d) {
                          var list = state.devices || [];
                          if (!list.some(function (x) { return x.address === d.address; })) {
                            list = list.concat([d]).slice(-60);
                            api.setState({ devices: list });
                          }
                        });

                        api.wifi.scan({ maxResults: 30, callback: function (arr) {
                          api.setState({ aps: arr });
                        }});

                        function screen_main(...) { ... }
                        function screen_settings(...) { ... }
                        api.navigate("settings");

                        function onUnload(api) {
                          try { api.ble.stopScan(); } catch (e) {}
                        }
                        """.trimIndent(),
                    )
                }
                item {
                    GuideHeader(
                        accent = accent,
                        title = "Чеклист",
                        body = "• Уникальный id в @plugin\n" +
                            "• return ui.scaffold({ topBar, content })\n" +
                            "• section: infrared | bluetooth | other | plugins | своя группа\n" +
                            "• icon: имя из списка выше\n" +
                            "• library=true / design=true → библиотека (exportApi/exportDesign)\n" +
                            "• Dual API: api.ir.send ↔ dolphy.send.ir; dolphy.pick.* / root.*\n" +
                            "• Material 3: ui.m3.* + ui.m3.expressive.* + checkbox/dropdown\n" +
                            "• Media: api.media.pickImage / takePhoto / files.export (на экране плагина)\n" +
                            "• Permissions: api.pm.request(\"camera\"|…)\n" +
                            "• Assets: api.assets.read / list / dataUri\n" +
                            "• BLE spam: api.bleSpam.start(\"apple\"|\"samsung\"|…)\n" +
                            "• Диалоги: api.dialog / snackbar / bottomSheet\n" +
                            "• Timers/crypto: api.timers.* / api.crypto.*\n" +
                            "• Root max: api.root.read/pull/packages/… (§13)\n" +
                            "• WebView: ui.webView({ url|html })\n" +
                            "• Списки: ui.lazyColumn / lazyRow\n" +
                            "• Shell: api.root / shizuku / shell (via)\n" +
                            "• Отключение сохраняется, удаление снимает плагин до нового импорта",
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun GuideHeader(accent: Color, title: String, body: String) {
    MaterialCard(Modifier.fillMaxWidth(), accentColor = accent, contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Black.copy(0.4f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = code,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color(0xFFB2FF59),
        )
    }
}

@Composable
private fun VisualExample(
    accent: Color,
    icon: ImageVector,
    title: String,
    description: String,
    code: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MaterialCard(Modifier.fillMaxWidth(), accentColor = accent, contentPadding = 0.dp) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = accent)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("API:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CodeBlock(code)
    }
}

