const messages = {
  en: {
    skip: 'Skip to content', navHome: 'Home', navDocs: 'Plugin docs', heroEyebrow: 'Open Android wireless toolkit', heroTitleA: 'Hack the', heroTitleB: 'World.', heroLead: 'Dolphy brings Bluetooth, infrared, NFC, HID and network tools together in a bold Material 3 Expressive interface.', download: 'Download', androidApp: 'Native Android app', badgeText: 'Pocket wireless lab', plugins: 'Plugins', featureEyebrow: 'One app. More possibilities.', featureTitle: 'A toolkit built for exploration.', featureLead: 'Scan, analyze, control and experiment with wireless technology from one carefully designed Android app.', featureWirelessTitle: 'Bluetooth & wireless', featureWirelessBody: 'Discover BLE and advertising devices, test Bluetooth audio, work with HID and explore nearby signals.', featureIrTitle: 'Infrared tools', featureIrBody: 'Use universal remotes, TV Be Gone, IR databases and signal tools with built-in or external hardware.', featureNfcTitle: 'NFC & networks', featureNfcBody: 'Read NFC tags, inspect local networks and keep useful diagnostics close at hand.', featurePluginsTitle: 'Powerful Python plugins', featurePluginsBody: 'Create screens, hook app actions and add hardware integrations with a single readable .dolphyplugin file.', pluginEyebrow: 'Made to be extended', pluginTitle: 'Turn an idea into a Dolphy tool.', pluginLead: 'The plugin engine exposes expressive UI, app hooks and Android device capabilities through a focused Python API.', pluginPointOne: 'Build complete Material 3 screens', pluginPointTwo: 'Extend existing features and actions', pluginPointThree: 'Integrate Bluetooth, Wi-Fi, USB, IR and more', readDocs: 'Read plugin docs', downloadEyebrow: 'Ready to explore?', downloadTitle: 'Get the latest Dolphy release.', downloadLead: 'Download the APK from GitHub Releases and turn your Android device into a pocket wireless toolkit.', openReleases: 'Open Releases', footerTagline: 'Hack the World.',
    integrationTruthTitle: 'Integration points, not arbitrary patching', integrationTruthBody: 'Screen hooks work for every route hosted by Dolphy. Action and service hooks work only where the app explicitly dispatches a named integration point. A plugin does not automatically intercept every Kotlin function.', screenHookTitle: 'Screen hooks in detail', screenHookLead: 'Routes and patterns are case-insensitive. The asterisk matches any number of characters. If several plugins match, higher priority wins where only one contribution can be used.', modeOverlay: 'Renders a full-size transparent layer over the existing screen.', modeTop: 'Places plugin UI in a Material surface at the top of the screen.', modeBottom: 'Places plugin UI above the bottom navigation area.', modeFab: 'Places compact plugin UI at the bottom end with navigation-safe padding.', modeReplace: 'The highest-priority replacement visually covers the built-in screen. Its underlying Compose screen still exists, so this is UI replacement rather than bytecode patching.', routePatternsTitle: 'Useful route patterns', routePatternsBody: 'Use the navigation event while developing to inspect the current route. Routes with arguments are reported as their navigation template, for example nfc_result/{id}.', logicHooksTitle: 'Action hooks and services', logicHooksLead: 'Action hooks form a priority-ordered pipeline. Services form a priority-ordered provider list. Both use JSON-compatible payloads.', returnNone: 'Continue without changing the action.', returnPayload: 'Replace the payload passed to lower-priority hooks and the built-in implementation.', returnHandled: 'Mark the action handled by the plugin. Use result to return a value.', returnCancel: 'Stop the hook chain and cancel the built-in operation.', returnFalse: 'Cancel immediately. True marks the action handled.', currentPointsTitle: 'Integration points available now', currentPointsBody: 'Logic: infrared.transmit. Service: infrared.transmitter with available and transmit operations. Events: activity_create, activity_destroy and navigation. New named points require a corresponding call in Dolphy\'s Kotlin code.', fullApiTitle: 'Complete runtime map', fullApiLead: 'These groups mirror the callable surface currently shipped with Dolphy. Open a group to see every public helper name.', baseHelpers: 'Plugin helpers', uiHelpers: 'Native Compose UI builders', bridgeHelpers: 'Runtime bridge', androidHelpers: 'Android, files and advanced Bluetooth', deviceHelpers: 'Wireless, network and elevated device tools',
    docsEyebrow: 'Developer documentation', docsTitle: 'Build something powerful for Dolphy.', docsLead: 'A plugin is one readable Python file with the .dolphyplugin extension. It can create screens, integrate hardware and extend existing Dolphy features.', quickStart: 'Quick start', examples: 'Examples', onThisPage: 'On this page', tocOverview: 'Overview', tocMetadata: 'Metadata', tocQuick: 'Quick start', tocUi: 'Expressive UI', tocIntegration: 'App integration', tocApis: 'Android & device APIs', tocLifecycle: 'Lifecycle', tocInstall: 'Install & safety', overviewTitle: 'What a plugin can do', overviewLead: 'Plugins run inside Dolphy and use the same design system and hardware access layer as the app.', capScreens: 'Create complete screens', capScreensBody: 'Compose multi-screen Material 3 Expressive tools with state and callbacks.', capHooks: 'Extend Dolphy', capHooksBody: 'Add overlays, FABs, replace screens and hook named actions.', capHardware: 'Integrate hardware', capHardwareBody: 'Work with BLE, classic Bluetooth, Wi-Fi, USB, NFC and infrared.', capSystem: 'Use Android capabilities', capSystemBody: 'Access activities, intents, storage, shell, Root and Shizuku when available.', trustedTitle: 'Power comes with responsibility', trustedBody: 'Plugins are executable Python code. Install only files you trust and review the source before granting elevated access.', metadataTitle: 'File metadata', metadataLead: 'Declare the plugin identity at the beginning of the file. Dolphy uses it for the preview and plugin library.', copy: 'Copy', metaId: 'Stable unique identifier', metaName: 'Display name', metaVersion: 'Release version', metaAuthor: 'Author or team', metaDescription: 'Short summary', metaIcon: 'Material Symbols icon name', quickTitle: 'Your first plugin', quickLead: 'Subclass BasePlugin, describe a screen and let Dolphy render it with the active app color scheme.', uiTitle: 'Expressive UI from Python', uiLead: 'UI builders create native Compose components. State updates and callbacks stay in your plugin file.', integrationTitle: 'Extend existing Dolphy features', integrationLead: 'Hooks let a plugin participate in current screens and logic without copying the app implementation.', hookScreenBody: 'Add an overlay or FAB to a route, or replace a screen with your own plugin screen.', hookActionBody: 'Observe, transform, handle or cancel a named action before the default implementation runs.', serviceBody: 'Supply a new implementation for a capability such as an external infrared transmitter.', apisTitle: 'Android and device APIs', apisLead: 'The bridge gives plugins controlled access to the platform and wireless hardware available on the device.', apiContext: 'App context', apiActivity: 'Current activity', apiAndroid: 'Intents & storage', apiDevice: 'Bluetooth, Wi-Fi, NFC, IR & USB', apiShell: 'Shell commands', apiRoot: 'Root access', apiShizuku: 'Shizuku access', lifecycleTitle: 'Plugin lifecycle', lifecycleLead: 'Keep setup, UI and cleanup predictable by using the runtime lifecycle methods.', lifeLoad: 'Plugin loaded', lifeScreen: 'Screen created', lifeEvent: 'Events & actions', lifeUnload: 'Plugin unloaded', installTitle: 'Install, update and recover', installLead: 'Dolphy is registered as a .dolphyplugin file handler and shows a safe preview before executing code.', stepOpen: 'Open the file', stepOpenBody: 'Tap a .dolphyplugin file in your file manager and choose Dolphy.', stepReview: 'Review metadata', stepReviewBody: 'Check the icon, author, version and description on the installation page.', stepInstall: 'Install', stepInstallBody: 'Code starts only after you press Install. Reopening the file updates it.', safeTitle: 'Automatic Safe Mode', safeBody: 'If the app closes while plugin code is running, the next launch disables every plugin and offers a crash log. You can then enable plugins one by one.', docsCtaTitle: 'Ready to build?', docsCtaBody: 'Start with the examples in the Dolphy repository.', viewExamples: 'View examples'
  },
  ru: {
    skip: 'Перейти к содержимому', navHome: 'Главная', navDocs: 'Документация', heroEyebrow: 'Открытый набор беспроводных инструментов', heroTitleA: 'Hack the', heroTitleB: 'World.', heroLead: 'Dolphy объединяет Bluetooth, инфракрасные инструменты, NFC, HID и сети в выразительном интерфейсе Material 3 Expressive.', download: 'Скачать', androidApp: 'Нативное Android-приложение', badgeText: 'Карманная беспроводная лаборатория', plugins: 'Плагины', featureEyebrow: 'Одно приложение. Больше возможностей.', featureTitle: 'Инструменты для исследования.', featureLead: 'Сканируйте, анализируйте, управляйте и экспериментируйте с беспроводными технологиями в одном продуманном Android-приложении.', featureWirelessTitle: 'Bluetooth и беспроводная связь', featureWirelessBody: 'Ищите BLE- и Advert-устройства, тестируйте Bluetooth-аудио, работайте с HID и исследуйте сигналы поблизости.', featureIrTitle: 'Инфракрасные инструменты', featureIrBody: 'Используйте универсальные пульты, TV Be Gone, базы ИК-сигналов и встроенное или внешнее оборудование.', featureNfcTitle: 'NFC и сети', featureNfcBody: 'Читайте NFC-метки, исследуйте локальные сети и держите полезную диагностику под рукой.', featurePluginsTitle: 'Мощные Python-плагины', featurePluginsBody: 'Создавайте экраны, подключайтесь к действиям приложения и добавляйте оборудование одним читаемым файлом .dolphyplugin.', pluginEyebrow: 'Создано для расширения', pluginTitle: 'Превратите идею в инструмент Dolphy.', pluginLead: 'Движок плагинов открывает выразительный UI, хуки приложения и возможности Android-устройства через удобный Python API.', pluginPointOne: 'Создавайте полноценные экраны Material 3', pluginPointTwo: 'Расширяйте существующие функции и действия', pluginPointThree: 'Подключайте Bluetooth, Wi-Fi, USB, ИК и другое', readDocs: 'Документация плагинов', downloadEyebrow: 'Готовы исследовать?', downloadTitle: 'Скачайте свежую версию Dolphy.', downloadLead: 'Загрузите APK из GitHub Releases и превратите Android-устройство в карманный набор беспроводных инструментов.', openReleases: 'Открыть Releases', footerTagline: 'Hack the World.',
    integrationTruthTitle: 'Точки интеграции, а не произвольный патчинг', integrationTruthBody: 'Хуки экранов работают на каждом маршруте Dolphy. Хуки действий и сервисов работают только там, где приложение явно вызывает именованную точку интеграции. Плагин не перехватывает автоматически любую Kotlin-функцию.', screenHookTitle: 'Хуки экранов подробно', screenHookLead: 'Маршруты и шаблоны не зависят от регистра. Звёздочка заменяет любое количество символов. Если подходит несколько плагинов, в местах с единственным вкладом побеждает больший приоритет.', modeOverlay: 'Рисует полноразмерный прозрачный слой поверх существующего экрана.', modeTop: 'Помещает интерфейс плагина в Material-поверхность сверху экрана.', modeBottom: 'Помещает интерфейс плагина над областью нижней навигации.', modeFab: 'Размещает компактный интерфейс снизу справа с безопасным отступом от навигации.', modeReplace: 'Замена с наибольшим приоритетом визуально закрывает штатный экран. Нижележащий Compose-экран продолжает существовать, поэтому это замена UI, а не патч байткода.', routePatternsTitle: 'Полезные шаблоны маршрутов', routePatternsBody: 'Во время разработки смотрите текущий маршрут через событие navigation. Маршруты с аргументами приходят как шаблон навигации, например nfc_result/{id}.', logicHooksTitle: 'Хуки действий и сервисы', logicHooksLead: 'Хуки действий образуют цепочку по приоритету. Сервисы образуют список провайдеров по приоритету. Оба механизма используют JSON-совместимые данные.', returnNone: 'Продолжить действие без изменений.', returnPayload: 'Заменить данные для хуков с меньшим приоритетом и штатной реализации.', returnHandled: 'Пометить действие обработанным плагином. Поле result возвращает результат.', returnCancel: 'Остановить цепочку хуков и отменить штатную операцию.', returnFalse: 'Немедленно отменить. True помечает действие обработанным.', currentPointsTitle: 'Доступные сейчас точки интеграции', currentPointsBody: 'Логика: infrared.transmit. Сервис: infrared.transmitter с операциями available и transmit. События: activity_create, activity_destroy и navigation. Новая именованная точка требует соответствующего вызова в Kotlin-коде Dolphy.', fullApiTitle: 'Полная карта среды выполнения', fullApiLead: 'Эти группы соответствуют вызываемому API текущей версии Dolphy. Раскройте группу, чтобы увидеть все публичные имена.', baseHelpers: 'Методы плагина', uiHelpers: 'Нативные Compose-компоненты', bridgeHelpers: 'Бридж среды выполнения', androidHelpers: 'Android, файлы и расширенный Bluetooth', deviceHelpers: 'Беспроводные, сетевые и привилегированные инструменты',
    docsEyebrow: 'Документация разработчика', docsTitle: 'Создайте для Dolphy что-то мощное.', docsLead: 'Плагин — это один читаемый Python-файл с расширением .dolphyplugin. Он может создавать экраны, подключать оборудование и расширять существующие функции Dolphy.', quickStart: 'Быстрый старт', examples: 'Примеры', onThisPage: 'На этой странице', tocOverview: 'Обзор', tocMetadata: 'Метаданные', tocQuick: 'Быстрый старт', tocUi: 'Выразительный UI', tocIntegration: 'Интеграция', tocApis: 'API Android и устройства', tocLifecycle: 'Жизненный цикл', tocInstall: 'Установка и безопасность', overviewTitle: 'Что может плагин', overviewLead: 'Плагины работают внутри Dolphy и используют ту же дизайн-систему и слой доступа к оборудованию.', capScreens: 'Создавать целые экраны', capScreensBody: 'Собирайте многоэкранные инструменты Material 3 Expressive с состоянием и обработчиками.', capHooks: 'Расширять Dolphy', capHooksBody: 'Добавляйте оверлеи и FAB, заменяйте экраны и подключайтесь к именованным действиям.', capHardware: 'Подключать оборудование', capHardwareBody: 'Работайте с BLE, классическим Bluetooth, Wi-Fi, USB, NFC и ИК.', capSystem: 'Использовать возможности Android', capSystemBody: 'Обращайтесь к Activity, Intent, хранилищу, Shell, Root и Shizuku, когда они доступны.', trustedTitle: 'Большая свобода требует осторожности', trustedBody: 'Плагины — исполняемый Python-код. Устанавливайте только доверенные файлы и проверяйте исходник перед выдачей расширенного доступа.', metadataTitle: 'Метаданные файла', metadataLead: 'Объявите данные плагина в начале файла. Dolphy использует их на экране предпросмотра и в библиотеке плагинов.', copy: 'Копировать', metaId: 'Постоянный уникальный идентификатор', metaName: 'Отображаемое название', metaVersion: 'Версия выпуска', metaAuthor: 'Автор или команда', metaDescription: 'Краткое описание', metaIcon: 'Название иконки Material Symbols', quickTitle: 'Первый плагин', quickLead: 'Наследуйте BasePlugin, опишите экран, и Dolphy отрисует его в активной цветовой схеме приложения.', uiTitle: 'Expressive UI на Python', uiLead: 'UI-билдеры создают нативные Compose-компоненты. Состояние и обработчики остаются в одном файле плагина.', integrationTitle: 'Расширение функций Dolphy', integrationLead: 'Хуки позволяют плагину участвовать в работе существующих экранов и логики без копирования реализации приложения.', hookScreenBody: 'Добавьте оверлей или FAB на маршрут либо полностью замените экран своим.', hookActionBody: 'Наблюдайте, изменяйте, обрабатывайте или отменяйте именованное действие до штатной реализации.', serviceBody: 'Предоставьте новую реализацию возможности, например внешний инфракрасный передатчик.', apisTitle: 'API Android и устройства', apisLead: 'Бридж предоставляет плагинам управляемый доступ к платформе и беспроводному оборудованию устройства.', apiContext: 'Контекст приложения', apiActivity: 'Текущая Activity', apiAndroid: 'Intent и хранилище', apiDevice: 'Bluetooth, Wi-Fi, NFC, ИК и USB', apiShell: 'Команды Shell', apiRoot: 'Доступ Root', apiShizuku: 'Доступ Shizuku', lifecycleTitle: 'Жизненный цикл плагина', lifecycleLead: 'Используйте методы жизненного цикла, чтобы инициализация, интерфейс и очистка работали предсказуемо.', lifeLoad: 'Плагин загружен', lifeScreen: 'Экран создан', lifeEvent: 'События и действия', lifeUnload: 'Плагин выгружен', installTitle: 'Установка, обновление и восстановление', installLead: 'Dolphy зарегистрирован как обработчик .dolphyplugin и показывает безопасный предпросмотр до выполнения кода.', stepOpen: 'Откройте файл', stepOpenBody: 'Нажмите на .dolphyplugin в файловом менеджере и выберите Dolphy.', stepReview: 'Проверьте данные', stepReviewBody: 'Посмотрите иконку, автора, версию и описание на странице установки.', stepInstall: 'Установите', stepInstallBody: 'Код запускается только после нажатия «Установить». Повторное открытие файла обновит плагин.', safeTitle: 'Автоматический безопасный режим', safeBody: 'Если приложение закрылось во время работы кода плагина, следующий запуск отключит все плагины и предложит журнал сбоя. После этого их можно включать по одному.', docsCtaTitle: 'Готовы начать?', docsCtaBody: 'Возьмите за основу примеры из репозитория Dolphy.', viewExamples: 'Открыть примеры'
  }
}

messages.en.metaLibrary = 'Load before regular plugins as a shared library'
messages.en.metaDesignLibrary = 'Mark a shared design library'
messages.en.settingsContribTitle = 'Native plugin settings'
messages.en.settingsContribBody = 'api.registerSettings renders header, switch, slider, nav and card entries directly in Dolphy Settings.'
messages.en.lifecycleAliasesTitle = 'Accepted lifecycle names'
messages.en.lifecycleAliasesBody = 'Load and enable callbacks run when the session starts. Disable and unload callbacks run when it stops. Specific on_<event> handlers take precedence over on_event.'
messages.en.stormHookTitle = 'Example: intercept every IR Storm transmission'
messages.en.stormHookLead = 'IR Storm calls the shared infrared.transmit action once for every signal. The action payload does not contain the source screen, so a plugin that targets only IR Storm must remember the latest navigation event.'
messages.en.stormPayloadName = 'Signal or button name when available'
messages.en.stormPayloadFrequency = 'Carrier frequency in hertz'
messages.en.stormPayloadPattern = 'Alternating mark and space durations in microseconds'
messages.en.stormPayloadProtocol = 'Protocol label when known'
messages.en.stormPayloadCode = 'Original encoded value when available'
messages.en.stormPayloadTimings = 'Original timing representation when available'
messages.en.stormScopeTitle = 'What this example affects'
messages.en.stormScopeBody = 'It intercepts both ir_storm and other/ir_storm. Returning None on every other route preserves ordinary remotes, TV Be Gone and IR Jammer. Remove the route check to intercept every Dolphy IR transmission.'
messages.en.stormModify = 'Change frequency or pattern, then let Dolphy continue sending.'
messages.en.stormHandle = 'The plugin sent the signal; skip the built-in transmitter and report success.'
messages.en.stormCancel = 'Block the signal without sending it.'
messages.en.stormAdapterTitle = 'Built-in and external transmitters'
messages.en.stormAdapterBody = 'The example sends through Android\'s built-in ConsumerIrManager without re-entering the hook. For a USB transmitter, replace only send_to_external_ir with the device protocol implemented through api.getAndroid(), Android USB classes or a service provider.'
messages.en.tocHookCatalog = 'Hook catalog'
messages.en.hookCatalogTitle = 'Complete hook catalog'
messages.en.hookCatalogLead = 'Every screen route, named action, service operation and event currently emitted by Dolphy.'
messages.en.hookCatalogRuleTitle = 'Call and intercept are different'
messages.en.hookCatalogRuleBody = 'The Android and Device API lists contain functions a plugin can call. Only the entries below can be intercepted. Registering hook_action("*") does not expose functions that Dolphy never dispatches.'
messages.en.actionCatalogTitle = 'Named action hooks'
messages.en.catalogName = 'Name'
messages.en.catalogHandler = 'Python handler'
messages.en.catalogWhere = 'Triggered by'
messages.en.infraredActionWhere = 'Every send through the shared IR backend: IR Storm, IR Jammer, TV Be Gone, universal and database remotes.'
messages.en.actionCatalogNote = 'Registration: hook_action("infrared.transmit", priority). Wildcards are supported, for example hook_action("infrared.*"). This is the only named action emitted by the current app build.'
messages.en.serviceCatalogTitle = 'Service providers and operations'
messages.en.infraredAvailableWhere = 'When Dolphy checks whether any IR transmitter is available.'
messages.en.infraredTransmitWhere = 'After action hooks, before the built-in ConsumerIrManager fallback.'
messages.en.serviceCatalogNote = 'Registration: provide_service("infrared.transmitter", priority). Service identifiers are exact and do not use wildcard matching.'
messages.en.eventCatalogTitle = 'Events'
messages.en.genericEventPayload = 'Fallback for events without a specific on_<event> method'
messages.en.routeCatalogTitle = 'All screen routes available to hook_screen'
messages.en.routeCatalogLead = 'There are 104 routes in the current inner navigation host. Parameterized routes are matched by their template shown below, including braces.'
messages.en.allRoutesTitle = '104 exact route names'
messages.en.allRoutesHint = 'Use an exact name or an asterisk pattern'
messages.en.newHookTitle = 'When a function is missing from this catalog'
messages.en.newHookBody = 'It cannot currently be intercepted through the public plugin hook API. Dolphy must add invokeActionHooks(name, payload) or invokeServices(service, operation, payload) at that Kotlin call site before plugin authors can hook it.'
messages.ru.metaLibrary = 'Загружать раньше обычных плагинов как общую библиотеку'
messages.ru.metaDesignLibrary = 'Пометить общую дизайн-библиотеку'
messages.ru.settingsContribTitle = 'Нативные настройки плагина'
messages.ru.settingsContribBody = 'api.registerSettings отрисовывает header, switch, slider, nav и card непосредственно в настройках Dolphy.'
messages.ru.lifecycleAliasesTitle = 'Допустимые имена методов жизненного цикла'
messages.ru.lifecycleAliasesBody = 'Методы загрузки и включения вызываются при старте сессии. Методы отключения и выгрузки — при её остановке. Специальный on_<event> имеет приоритет над on_event.'
messages.ru.stormHookTitle = 'Пример: перехват каждой отправки ИК Шторма'
messages.ru.stormHookLead = 'ИК Шторм вызывает общее действие infrared.transmit для каждого сигнала. Payload не содержит экран-источник, поэтому плагин только для Шторма должен запоминать последнее событие navigation.'
messages.ru.stormPayloadName = 'Название сигнала или кнопки, если оно доступно'
messages.ru.stormPayloadFrequency = 'Несущая частота в герцах'
messages.ru.stormPayloadPattern = 'Чередующиеся длительности импульсов и пауз в микросекундах'
messages.ru.stormPayloadProtocol = 'Название протокола, если он известен'
messages.ru.stormPayloadCode = 'Исходное закодированное значение, если доступно'
messages.ru.stormPayloadTimings = 'Исходное представление таймингов, если доступно'
messages.ru.stormScopeTitle = 'На что влияет этот пример'
messages.ru.stormScopeBody = 'Он перехватывает ir_storm и other/ir_storm. Возврат None на остальных маршрутах сохраняет работу обычных пультов, TV Be Gone и ИК Глушилки. Уберите проверку маршрута, чтобы перехватывать все ИК-отправки Dolphy.'
messages.ru.stormModify = 'Изменить частоту или паттерн, а затем продолжить штатную отправку Dolphy.'
messages.ru.stormHandle = 'Плагин сам отправил сигнал: пропустить встроенный передатчик и вернуть успех.'
messages.ru.stormCancel = 'Заблокировать сигнал без отправки.'
messages.ru.stormAdapterTitle = 'Встроенные и внешние передатчики'
messages.ru.stormAdapterBody = 'Пример отправляет через встроенный Android ConsumerIrManager без повторного входа в хук. Для USB-передатчика замените только send_to_external_ir реализацией протокола устройства через api.getAndroid(), Android USB-классы или сервис-провайдер.'
messages.ru.tocHookCatalog = 'Каталог хуков'
messages.ru.hookCatalogTitle = 'Полный каталог перехвата'
messages.ru.hookCatalogLead = 'Все экранные маршруты, именованные действия, сервисные операции и события, которые сейчас выдаёт Dolphy.'
messages.ru.hookCatalogRuleTitle = 'Вызвать и перехватить — разные возможности'
messages.ru.hookCatalogRuleBody = 'Списки Android и Device API содержат функции, которые плагин может вызвать. Перехватить можно только элементы каталога ниже. Регистрация hook_action("*") не открывает функции, которые Dolphy не отправляет в диспетчер.'
messages.ru.actionCatalogTitle = 'Именованные хуки действий'
messages.ru.catalogName = 'Название'
messages.ru.catalogHandler = 'Обработчик Python'
messages.ru.catalogWhere = 'Что вызывает'
messages.ru.infraredActionWhere = 'Каждая отправка через общий ИК-бэкенд: ИК Шторм, ИК Глушилка, TV Be Gone, универсальные пульты и пульты из баз.'
messages.ru.actionCatalogNote = 'Регистрация: hook_action("infrared.transmit", priority). Поддерживаются шаблоны, например hook_action("infrared.*"). Это единственное именованное действие в текущей сборке приложения.'
messages.ru.serviceCatalogTitle = 'Сервис-провайдеры и операции'
messages.ru.infraredAvailableWhere = 'Когда Dolphy проверяет наличие любого ИК-передатчика.'
messages.ru.infraredTransmitWhere = 'После action-хуков, перед резервной отправкой через встроенный ConsumerIrManager.'
messages.ru.serviceCatalogNote = 'Регистрация: provide_service("infrared.transmitter", priority). Идентификаторы сервисов точные и не поддерживают шаблоны.'
messages.ru.eventCatalogTitle = 'События'
messages.ru.genericEventPayload = 'Резервный обработчик событий без специального метода on_<event>'
messages.ru.routeCatalogTitle = 'Все экранные маршруты для hook_screen'
messages.ru.routeCatalogLead = 'Во внутренней навигации текущей версии 104 маршрута. Маршруты с параметрами сопоставляются по показанному шаблону вместе с фигурными скобками.'
messages.ru.allRoutesTitle = '104 точных названия маршрутов'
messages.ru.allRoutesHint = 'Используйте точное имя или шаблон со звёздочкой'
messages.ru.newHookTitle = 'Если функции нет в этом каталоге'
messages.ru.newHookBody = 'Сейчас её нельзя перехватить через публичный API хуков. В соответствующее место Kotlin-кода Dolphy нужно добавить invokeActionHooks(name, payload) или invokeServices(service, operation, payload), после чего плагинмейкеры смогут её перехватывать.'

messages.en.metaLibrary = 'Load before ordinary plugins and mark the file as a library'
messages.en.metaDesignLibrary = 'Load early and mark the file as a design library'
messages.en.manifestRulesTitle = 'Manifest parsing rules'
messages.en.manifestRulesBody = 'The file is UTF-8 text and may be at most 48 MiB. String fields must be top-level single- or double-quoted assignments. id is lowercased, invalid characters become underscores and the result is limited to 64 characters. Missing fields fall back to the filename, version 1.0 and icon extension.'
messages.en.libraryTruthTitle = 'Library flags in Python'
messages.en.libraryTruthBody = '__library__ and __design_library__ load before consumers. __dependencies__ controls dependency order, and libraries can export Java classes and live objects by name.'
messages.en.runtimeTruthTitle = 'Public format and runtime'
messages.en.runtimeTruthBody = 'A file opened or shared as .dolphyplugin is always executed by the embedded Python runtime. The repository still contains an internal JavaScript compatibility runtime for previously stored .js plugins, but it is not the documented public .dolphyplugin authoring format.'
messages.en.baseSignatureTitle = 'Complete BasePlugin API'
messages.en.baseSignatureBody = 'These helpers delegate to the native bridge. api and ui are also available directly on self.'
messages.en.moduleSectionsTitle = 'Where add_module can place a card'
messages.en.moduleSectionsBody = 'Built-in sections are INFRARED, BLUETOOTH, OTHER and PLUGINS. Aliases such as ir, bt, ble, misc and localized names are normalized; any other text creates a custom section. order sorts cards, screen chooses the plugin screen, and icon accepts Material icon syntax.'
messages.en.uiTextStyles = 'headlineLarge/Medium/Small, titleLarge/Medium/Small, bodyLarge/Medium/Small and labelLarge/Medium/Small; camelCase and snake_case are accepted.'
messages.en.uiColorValues = 'primary/accent, muted/secondary, error/red, success/green, warning/orange, or #RGB/#ARGB/#RRGGBB/#AARRGGBB.'
messages.en.uiIconValues = 'A built-in alias or any bundled Material icon as style:name: filled, outlined, rounded, sharp, twotone or automirrored.'
messages.en.uiImageValues = 'asset path, asset:// URL, data:image/...;base64 URI or raw Base64. Scale: fit, crop, fill, fillWidth, fillHeight, inside or none.'
messages.en.uiCallbackValues = 'Click receives no value; text fields receive str, switches and checkboxes bool, sliders float, tabs int, and option controls the selected value.'
messages.en.uiWebViewValues = 'Loads either url or html, with JavaScript and DOM storage enabled. Treat remote and embedded content as trusted executable UI.'
messages.en.uiSignatureTitle = 'Complete UiFactory signatures'
messages.en.uiSignatureBody = 'Every argument below is supported by the Python runtime. Dimensions and spacing use dp.'
messages.en.settingsHeaderSchema = 'title'
messages.en.settingsSwitchSchema = 'key, title, subtitle, default: bool'
messages.en.settingsSliderSchema = 'key, title, subtitle, min, max, default, steps'
messages.en.settingsNavSchema = 'title, subtitle, icon, screen; always opens plugin/{pluginId}/{screen}'
messages.en.settingsCardSchema = 'title, subtitle, icon and optional screen'
messages.en.settingsStorageTitle = 'Storage and updates'
messages.en.settingsStorageBody = 'Switch and slider values use the same per-plugin JSON store as get_setting/set_setting. The host persists a value before calling on_setting_changed({key, value}); sliders emit the event when interaction finishes.'
messages.en.settingKeysTitle = 'Where setting keys come from'
messages.en.settingKeysBody = 'There is no predefined key catalog. The plugin author creates any string key and uses the same spelling when reading, writing or declaring a Settings item. Keys only collide inside the same plugin because every pluginId has a separate store. Prefer stable names such as capture_enabled or radio.power; changing a key creates a new setting.'
messages.en.settingKeyContract = 'Any non-empty string chosen by the plugin. There are currently no reserved host keys.'
messages.en.settingValueContract = 'Any json.dumps-compatible value: None, bool, number, str, list or dict.'
messages.en.settingDefaultContract = 'get_setting returns the supplied default when the key has never been saved.'
messages.en.settingLifetimeContract = 'Values survive restarts and enable/disable. Deleting the plugin removes its entire settings store.'
messages.en.layerBase = 'Convenience helpers for modules, navigation, state, screen/action/service hooks and shell access.'
messages.en.layerUi = 'Builds a JSON-compatible UI tree rendered as native Compose Material 3 components.'
messages.en.layerBridge = 'Registration, Context/Activity access, persistent JSON settings, execution and batched BLE scanning.'
messages.en.layerAndroid = 'Direct Android integration: storage, assets, media, crypto, IR, classic Bluetooth, RFCOMM, GATT, BLE server/advertiser, Wi-Fi and NSD.'
messages.en.layerDevice = 'Higher-level wireless, NFC, network, Root and Shizuku operations with JSON-oriented results.'
messages.en.layerJava = 'Chaquopy can import Android and Java classes directly when a bridge helper is not enough.'
messages.en.apiContractsTitle = 'Data and execution contracts'
messages.en.apiContractsBody = 'Most hardware helpers do not open permission prompts. Check hasPermission/capability methods and request Android permissions through Activity APIs when needed. Long-running scans, GATT, RFCOMM, sensors, audio, advertising, BLE servers, NSD and Wi-Fi P2P must be stopped during on_plugin_disable/on_plugin_unload.'
messages.en.bridgeSignatureTitle = 'Direct bridge signatures'
messages.en.bridgeSignatureBody = 'Use BasePlugin wrappers where they exist. Direct bridge methods preserve Java return types; settings and command methods exchange JSON text.'
messages.en.deviceCompleteTitle = 'Device control and system tools'
messages.en.deviceCompleteBody = 'Device status, radio controls and advanced Root operations'
messages.en.genericActionBody = 'Generic fallback for every registered action without a matching hook_<normalized_action> method. Dots and other non-alphanumeric characters normalize to underscores.'
messages.en.serviceAvailableContract = 'Makes an external transmitter count as available; {"ok": true} or raw True are also accepted.'
messages.en.serviceTransmitContract = 'Stops provider fallback and reports success. handled false lets the next provider or built-in ConsumerIrManager run.'
messages.en.serviceGenericContract = 'Generic fallback when service_<service>_<operation> is not defined.'
messages.en.routeCatalogLead = 'There are 108 routes in the current navigation hosts. Parameterized routes are matched by their template shown below, including braces.'
messages.en.allRoutesTitle = '108 exact route names'
messages.en.screenResolverContract = 'Primary renderer. main also accepts create_screen or screen_main. Missing screens render a native error placeholder.'
messages.en.callbackContract = 'Python callables passed into UI builders receive only the value type produced by that component. Declaring fewer positional arguments is supported.'
messages.en.eventResolverContract = 'Specific event handler wins; otherwise on_event(name, payload) is called.'
messages.en.actionResolverContract = 'Specific normalized action handler wins; otherwise on_action(action, payload) is called.'
messages.en.serviceResolverContract = 'Specific normalized service handler wins; otherwise on_service(id, operation, payload) is called.'
messages.en.refreshContract = 'Invalidates registered plugin UI so screen functions run again with the latest plugin state.'
messages.en.installPreviewContract = 'Reads metadata and size without executing Python; shows whether the same id is already installed.'
messages.en.installUpdateContract = 'Saves the source privately and starts it immediately unless Safe Mode is active. Installing the same id replaces the previous session and keeps its pinned state.'
messages.en.installToggleContract = 'Starts or stops the runtime and removes all registered modules, settings, screens, services and actions when disabled.'
messages.en.installShareContract = 'Pin changes ordering. Share exports the original source as application/x-dolphy-plugin with the .dolphyplugin extension.'
messages.en.installDeleteContract = 'Stops the runtime and permanently removes source, metadata, registrations and the plugin private settings store.'
messages.en.installFailureContract = 'A plugin that cannot start is saved disabled during installation; a corrupt stored source can be quarantined as .broken during restoration.'
messages.en.safeDetailsTitle = 'Safe Mode behavior'
messages.en.safeDetailsBody = 'The recovery sheet explains that a plugin error closed the app and offers Copy log or Disable Safe Mode. Dismissing it leaves Safe Mode enabled and a persistent item remains on the Plugins screen. While active, every plugin is disabled and cannot be enabled until Safe Mode is turned off.'

messages.ru.metaLibrary = 'Загружать раньше обычных плагинов и помечать файл как библиотеку'
messages.ru.metaDesignLibrary = 'Загружать раньше и помечать файл как дизайн-библиотеку'
messages.ru.manifestRulesTitle = 'Правила чтения манифеста'
messages.ru.manifestRulesBody = 'Файл является текстом UTF-8 размером не более 48 МиБ. Строковые поля должны быть верхнеуровневыми присваиваниями в одинарных или двойных кавычках. id переводится в нижний регистр, недопустимые символы заменяются подчёркиваниями, длина ограничена 64 символами. Для пропущенных полей используются имя файла, версия 1.0 и иконка extension.'
messages.ru.libraryTruthTitle = 'Флаги библиотек в Python'
messages.ru.libraryTruthBody = '__library__ и __design_library__ загружаются раньше потребителей. __dependencies__ задаёт порядок зависимостей, а библиотеки могут экспортировать Java-классы и живые объекты по имени.'
messages.ru.runtimeTruthTitle = 'Публичный формат и среда выполнения'
messages.ru.runtimeTruthBody = 'Файл, открытый или отправленный как .dolphyplugin, всегда выполняется встроенной Python-средой. В репозитории остаётся внутренняя JavaScript-совместимость для ранее сохранённых .js-плагинов, но это не публичный формат разработки .dolphyplugin.'
messages.ru.baseSignatureTitle = 'Полный API BasePlugin'
messages.ru.baseSignatureBody = 'Эти методы делегируют работу нативному бриджу. api и ui также доступны напрямую через self.'
messages.ru.moduleSectionsTitle = 'Куда add_module может добавить карточку'
messages.ru.moduleSectionsBody = 'Встроенные разделы: INFRARED, BLUETOOTH, OTHER и PLUGINS. Псевдонимы ir, bt, ble, misc и локализованные названия нормализуются; любой другой текст создаёт пользовательский раздел. order задаёт сортировку, screen — экран плагина, icon принимает синтаксис Material Icons.'
messages.ru.uiTextStyles = 'headlineLarge/Medium/Small, titleLarge/Medium/Small, bodyLarge/Medium/Small и labelLarge/Medium/Small; поддерживаются camelCase и snake_case.'
messages.ru.uiColorValues = 'primary/accent, muted/secondary, error/red, success/green, warning/orange либо #RGB/#ARGB/#RRGGBB/#AARRGGBB.'
messages.ru.uiIconValues = 'Встроенный псевдоним или любая включённая Material-иконка в формате style:name: filled, outlined, rounded, sharp, twotone или automirrored.'
messages.ru.uiImageValues = 'Путь asset, URL asset://, URI data:image/...;base64 либо чистый Base64. Масштаб: fit, crop, fill, fillWidth, fillHeight, inside или none.'
messages.ru.uiCallbackValues = 'Клик приходит без значения; текстовое поле передаёт str, переключатели bool, ползунок float, вкладки int, элементы выбора — выбранное значение.'
messages.ru.uiWebViewValues = 'Загружает url или html с включёнными JavaScript и DOM storage. Считайте удалённое и встроенное содержимое доверенным исполняемым UI.'
messages.ru.uiSignatureTitle = 'Полные сигнатуры UiFactory'
messages.ru.uiSignatureBody = 'Python-среда поддерживает каждый указанный аргумент. Размеры и отступы задаются в dp.'
messages.ru.settingsHeaderSchema = 'title'
messages.ru.settingsSwitchSchema = 'key, title, subtitle, default: bool'
messages.ru.settingsSliderSchema = 'key, title, subtitle, min, max, default, steps'
messages.ru.settingsNavSchema = 'title, subtitle, icon, screen; открывает plugin/{pluginId}/{screen}'
messages.ru.settingsCardSchema = 'title, subtitle, icon и необязательный screen'
messages.ru.settingsStorageTitle = 'Хранение и обновления'
messages.ru.settingsStorageBody = 'Переключатели и ползунки используют то же отдельное JSON-хранилище плагина, что get_setting/set_setting. Хост сохраняет значение до вызова on_setting_changed({key, value}); ползунок отправляет событие после окончания взаимодействия.'
messages.ru.settingKeysTitle = 'Откуда берутся ключи настроек'
messages.ru.settingKeysBody = 'Предопределённого каталога ключей нет. Автор плагина придумывает любую строку и использует одинаковое написание при чтении, записи и объявлении элемента настроек. Ключи могут пересечься только внутри одного плагина: у каждого pluginId отдельное хранилище. Используйте постоянные имена вроде capture_enabled или radio.power; смена ключа создаёт новую настройку.'
messages.ru.settingKeyContract = 'Любая выбранная плагином непустая строка. Зарезервированных хостом ключей сейчас нет.'
messages.ru.settingValueContract = 'Любое значение, совместимое с json.dumps: None, bool, число, str, list или dict.'
messages.ru.settingDefaultContract = 'get_setting возвращает переданный default, если ключ ещё никогда не сохранялся.'
messages.ru.settingLifetimeContract = 'Значения сохраняются после перезапуска и включения/отключения. Удаление плагина удаляет всё его хранилище настроек.'
messages.ru.layerBase = 'Удобные методы модулей, навигации, состояния, хуков экранов/действий/сервисов и доступа к shell.'
messages.ru.layerUi = 'Строит JSON-совместимое дерево, которое отрисовывается нативными Compose-компонентами Material 3.'
messages.ru.layerBridge = 'Регистрация, Context/Activity, постоянные JSON-настройки, выполнение команд и пакетное BLE-сканирование.'
messages.ru.layerAndroid = 'Прямая интеграция Android: файлы, assets, медиа, криптография, ИК, classic Bluetooth, RFCOMM, GATT, BLE-сервер/реклама, Wi-Fi и NSD.'
messages.ru.layerDevice = 'Высокоуровневые операции беспроводной связи, NFC, сети, Root и Shizuku с JSON-ориентированными результатами.'
messages.ru.layerJava = 'Chaquopy позволяет напрямую импортировать классы Android и Java, когда методов бриджа недостаточно.'
messages.ru.apiContractsTitle = 'Контракты данных и выполнения'
messages.ru.apiContractsBody = 'Большинство аппаратных методов не показывает запрос разрешения. Проверяйте hasPermission и capability-методы, а при необходимости запрашивайте Android-разрешения через Activity API. Длительные сканирования, GATT, RFCOMM, датчики, аудио, advertising, BLE-серверы, NSD и Wi-Fi P2P нужно останавливать в on_plugin_disable/on_plugin_unload.'
messages.ru.bridgeSignatureTitle = 'Сигнатуры прямого бриджа'
messages.ru.bridgeSignatureBody = 'Используйте обёртки BasePlugin там, где они есть. Прямые методы бриджа сохраняют Java-типы возврата; настройки и методы команд обмениваются JSON-текстом.'
messages.ru.deviceCompleteTitle = 'Управление устройством и системные инструменты'
messages.ru.deviceCompleteBody = 'Состояние устройства, управление радиомодулями и расширенные Root-операции'
messages.ru.genericActionBody = 'Общий обработчик любого зарегистрированного действия, для которого нет hook_<нормализованное_действие>. Точки и другие небуквенно-цифровые символы заменяются подчёркиваниями.'
messages.ru.serviceAvailableContract = 'Делает внешний передатчик доступным; также принимаются {"ok": true} или чистый True.'
messages.ru.serviceTransmitContract = 'Останавливает перебор провайдеров и сообщает об успехе. handled false передаёт управление следующему провайдеру или встроенному ConsumerIrManager.'
messages.ru.serviceGenericContract = 'Общий обработчик, если service_<service>_<operation> не определён.'
messages.ru.routeCatalogLead = 'В текущих хостах навигации 108 маршрутов. Маршруты с параметрами сопоставляются по показанному шаблону вместе с фигурными скобками.'
messages.ru.allRoutesTitle = '108 точных названий маршрутов'
messages.ru.screenResolverContract = 'Основной рендерер. Для main также принимаются create_screen или screen_main. Отсутствующий экран показывает нативную заглушку ошибки.'
messages.ru.callbackContract = 'Python-функции в UI-билдерах получают только тип значения соответствующего компонента. Можно объявлять меньше позиционных аргументов.'
messages.ru.eventResolverContract = 'Приоритет имеет конкретный обработчик события, иначе вызывается on_event(name, payload).'
messages.ru.actionResolverContract = 'Приоритет имеет конкретный нормализованный обработчик действия, иначе вызывается on_action(action, payload).'
messages.ru.serviceResolverContract = 'Приоритет имеет конкретный нормализованный обработчик сервиса, иначе вызывается on_service(id, operation, payload).'
messages.ru.refreshContract = 'Инвалидирует зарегистрированный UI плагина, чтобы функции экранов выполнились снова с актуальным состоянием.'
messages.ru.installPreviewContract = 'Читает метаданные и размер без выполнения Python; показывает, установлен ли уже такой id.'
messages.ru.installUpdateContract = 'Сохраняет исходник приватно и сразу запускает его, если не активен Безопасный режим. Установка того же id заменяет прежнюю сессию и сохраняет закрепление.'
messages.ru.installToggleContract = 'Запускает или останавливает runtime и при отключении удаляет все зарегистрированные модули, настройки, экраны, сервисы и действия.'
messages.ru.installShareContract = 'Закрепление меняет сортировку. Поделиться экспортирует исходный текст как application/x-dolphy-plugin с расширением .dolphyplugin.'
messages.ru.installDeleteContract = 'Останавливает runtime и навсегда удаляет исходник, метаданные, регистрации и приватное хранилище настроек плагина.'
messages.ru.installFailureContract = 'Не запустившийся при установке плагин сохраняется отключённым; повреждённый сохранённый исходник при восстановлении может быть помещён в карантин с суффиксом .broken.'
messages.ru.safeDetailsTitle = 'Поведение Безопасного режима'
messages.ru.safeDetailsBody = 'Экран восстановления сообщает, что ошибка плагина закрыла приложение, и предлагает «Скопировать лог» или «Отключить Безопасный режим». Сворачивание окна оставляет режим включённым, а на экране «Плагины» остаётся постоянный пункт. Пока режим активен, все плагины отключены и включить их нельзя.'

messages.en.settingsContribTitle = 'Native plugin settings'
messages.en.settingsContribBody = 'api.registerSettings adds Material 3 Expressive sections directly to Dolphy Settings. Switches and sliders persist in the plugin store and emit setting_changed; nav and card items open plugin screens.'
messages.en.coreReplaceTitle = 'Replace complete core screens'
messages.en.coreReplaceBody = 'Use replace with other, bluetooth or settings to cover the complete core screen. Bluetooth also exposes three live sub-screen targets, so a plugin can replace All, BLE or Advert independently.'
messages.en.currentPointsBody = 'Dolphy exposes shared infrared transmission plus Bluetooth section selection, All actions, BLE modes, Advert presets and every outgoing BLE advertising packet. The complete exact list is in the hook catalog.'
messages.en.bleHookTitle = 'Example: replace BLE advertising logic'
messages.en.bleHookLead = 'bluetooth.advertising.transmit runs for custom Advert presets and packets generated by every built-in BLE spam mode. Change data to rewrite the packet, return cancel to block it, or handled after transmitting with your own backend.'
messages.en.bleModeApiTitle = 'Add a first-class BLE Spam mode'
messages.en.bleModeApiLead = 'register_ble_mode adds the plugin beside the built-in BLE modes using the same card, active state, shared delay and Stop All behavior. Dolphy sends ble_mode_start, ble_mode_stop and ble_mode_delay_changed only to the owning plugin. The plugin owns its advertising loop and must stop it on both stop and unload.'
messages.en.assetApiTitle = 'Download and apply plugin assets'
messages.en.assetApiLead = 'download_assets requests all files as one batch. Each destination is confined to this plugin’s private directory; maxBytes limits each response and sha256 can verify it. Unless the user disabled confirmation in Plugin settings → Security, Dolphy shows the plugin name, file count and declared total size before network transfer. apply_asset imports a saved file into a stock Bad USB editor, user IR remotes or QR Audio Spoofer custom HTML.'
messages.en.assetPathContract = 'Returns the absolute path of a location inside the plugin’s private folder for Java and DEX APIs.'
messages.en.assetDownloadContract = 'Items accept url, path, sizeBytes, maxBytes, sha256 and headers. The callback receives {ok, files} as JSON text.'
messages.en.assetApplyContract = 'Supported types: bad_hid, ir_remote and qr_audio_html. Returns a parsed result dictionary.'
messages.en.bleAssetHelpers = 'BLE modes and plugin assets'
messages.en.bleAssetBridgeHelpers = 'BLE and asset bridge'
messages.en.metaPermissions = 'Capabilities shown before installation; Dolphy also detects direct API usage in the source'
messages.en.stepReviewBody = 'Review the icon, author, version, description and detected or declared capabilities such as DEX loading, downloads, files, hooks, Bluetooth, Wi-Fi, USB, IR, Root, Shizuku and Shell.'
messages.en.installLead = 'Dolphy handles .dolphyplugin files and shows a compact, partially expanded Material sheet before any code runs. Installation closes the sheet and confirms success with a Dolphy toast.'
messages.en.btSectionWhere = 'Before switching between All, BLE and Advert. section or index may be changed.'
messages.en.btDeviceWhere = 'Before selecting a discovered classic Bluetooth device.'
messages.en.btIntervalWhere = 'When the All interval slider changes. intervalMs may be changed.'
messages.en.btScanWhere = 'Before classic and BLE discovery starts on All.'
messages.en.btAllStartWhere = 'Before All starts repeated classic pairing requests.'
messages.en.btAllStopWhere = 'Before All stops its active operation.'
messages.en.bleDelayWhere = 'Before changing the BLE mode delay. delayMs may be changed.'
messages.en.bleModeWhere = 'Before toggling an individual BLE mode.'
messages.en.bleKitchenWhere = 'Before toggling Kitchen Sink.'
messages.en.bleStopWhere = 'Before stopping all BLE modes.'
messages.en.bleSectionToggleWhere = 'Before toggling every mode in an iOS, Samsung, Android, Windows, Xiaomi or Phantom group.'
messages.en.bleSectionOpenWhere = 'Before opening a BLE group screen. section may be redirected.'
messages.en.advertAddWhere = 'Before saving a new Advert preset; all fields may be changed.'
messages.en.advertUpdateWhere = 'Before updating an Advert preset; all fields may be changed.'
messages.en.advertDeleteWhere = 'Before deleting an Advert preset.'
messages.en.advertStartWhere = 'Before starting a preset; fields may be changed or the start may be fully handled.'
messages.en.advertStopWhere = 'Before stopping an Advert preset.'
messages.en.advertTransmitWhere = 'Before every outgoing advertising packet from Advert or built-in BLE modes.'
messages.en.advertisingStopWhere = 'Before an Advert cycle or built-in BLE advertiser stops; an external backend can stop its own transmission and return handled.'
messages.en.actionCatalogNote = 'Registration uses hook_action(name, priority). Wildcards are supported: bluetooth.*, bluetooth.ble.* and bluetooth.advert.preset.* can intercept whole families.'
messages.en.advertPayloadTitle = 'bluetooth.advertising.transmit payload'
messages.en.advertPayloadBody = 'metadata.source is ble_spam or advert; Advert also includes metadata.preset. data and scanResponse contain includeDeviceName, includeTxPowerLevel, manufacturerData [{id,data}], serviceUuids and serviceData [{uuid,data}]. Binary values are uppercase hex. Returning a modified payload rebuilds the native AdvertiseData.'
messages.en.virtualRoutesTitle = 'Bluetooth sub-screen targets'
messages.en.virtualRoutesBody = 'These are live hook targets inside the bluetooth route rather than navigation destinations. They support every hook_screen mode.'

messages.ru.settingsContribTitle = 'Нативные настройки плагина'
messages.ru.settingsContribBody = 'api.registerSettings добавляет разделы Material 3 Expressive прямо в настройки Dolphy. Переключатели и ползунки сохраняются в хранилище плагина и отправляют setting_changed, а nav и card открывают экраны плагина.'
messages.ru.coreReplaceTitle = 'Полная замена основных экранов'
messages.ru.coreReplaceBody = 'Режим replace с other, bluetooth или settings закрывает весь основной экран. Внутри Bluetooth также доступны три живые цели, поэтому All, BLE и Advert можно заменять независимо.'
messages.ru.currentPointsBody = 'Dolphy открывает общую отправку ИК, выбор раздела Bluetooth, действия All, режимы BLE, пресеты Advert и каждый исходящий BLE advertising-пакет. Полный точный список приведён в каталоге хуков.'
messages.ru.bleHookTitle = 'Пример: замена логики BLE advertising'
messages.ru.bleHookLead = 'bluetooth.advertising.transmit вызывается для пользовательских пресетов Advert и пакетов всех встроенных BLE-режимов. Измените data для переписывания пакета, верните cancel для блокировки или handled после отправки через свой бэкенд.'
messages.ru.bleModeApiTitle = 'Добавление полноценного режима BLE Spam'
messages.ru.bleModeApiLead = 'register_ble_mode добавляет режим плагина рядом со штатными режимами BLE с такой же карточкой, активным состоянием, общей задержкой и действием «Остановить всё». Dolphy отправляет ble_mode_start, ble_mode_stop и ble_mode_delay_changed только плагину-владельцу. Циклом advertising управляет сам плагин; его обязательно нужно остановить при stop и unload.'
messages.ru.assetApiTitle = 'Загрузка и применение ассетов плагина'
messages.ru.assetApiLead = 'download_assets запрашивает все файлы одним пакетом. Каждый путь ограничен приватной папкой плагина, maxBytes задаёт предел ответа, а sha256 проверяет файл. Если подтверждение не отключено в «Настройки плагинов → Безопасность», Dolphy до начала передачи показывает имя плагина, число файлов и заявленный общий размер. apply_asset импортирует сохранённый файл в штатный редактор Bad USB, пользовательские ИК-пульты или пользовательский HTML QR Audio Spoofer.'
messages.ru.assetPathContract = 'Возвращает абсолютный путь внутри приватной папки плагина для Java- и DEX-API.'
messages.ru.assetDownloadContract = 'Элементы поддерживают url, path, sizeBytes, maxBytes, sha256 и headers. Callback получает JSON-текст {ok, files}.'
messages.ru.assetApplyContract = 'Поддерживаемые типы: bad_hid, ir_remote и qr_audio_html. Возвращает разобранный словарь результата.'
messages.ru.bleAssetHelpers = 'BLE-режимы и ассеты плагина'
messages.ru.bleAssetBridgeHelpers = 'Бридж BLE и ассетов'
messages.ru.metaPermissions = 'Возможности, показываемые до установки; Dolphy также обнаруживает прямые вызовы API в исходнике'
messages.ru.stepReviewBody = 'Проверьте иконку, автора, версию, описание и обнаруженные или объявленные возможности: загрузку DEX и файлов, ассеты, хуки, Bluetooth, Wi‑Fi, USB, ИК, Root, Shizuku и Shell.'
messages.ru.installLead = 'Dolphy открывает .dolphyplugin и до запуска кода показывает компактный частично свёрнутый Material-экран. После установки экран закрывается, а результат подтверждается тостом Dolphy.'
messages.ru.btSectionWhere = 'Перед переключением All, BLE и Advert. Можно изменить section или index.'
messages.ru.btDeviceWhere = 'Перед выбором найденного классического Bluetooth-устройства.'
messages.ru.btIntervalWhere = 'При изменении интервала All. Можно изменить intervalMs.'
messages.ru.btScanWhere = 'Перед запуском classic- и BLE-поиска на All.'
messages.ru.btAllStartWhere = 'Перед запуском повторных запросов сопряжения в All.'
messages.ru.btAllStopWhere = 'Перед остановкой активной операции All.'
messages.ru.bleDelayWhere = 'Перед изменением задержки BLE-режимов. Можно изменить delayMs.'
messages.ru.bleModeWhere = 'Перед переключением отдельного BLE-режима.'
messages.ru.bleKitchenWhere = 'Перед переключением Kitchen Sink.'
messages.ru.bleStopWhere = 'Перед остановкой всех BLE-режимов.'
messages.ru.bleSectionToggleWhere = 'Перед переключением всех режимов группы iOS, Samsung, Android, Windows, Xiaomi или Phantom.'
messages.ru.bleSectionOpenWhere = 'Перед открытием экрана группы BLE. Можно перенаправить section.'
messages.ru.advertAddWhere = 'Перед сохранением нового пресета Advert; можно изменить все поля.'
messages.ru.advertUpdateWhere = 'Перед обновлением пресета Advert; можно изменить все поля.'
messages.ru.advertDeleteWhere = 'Перед удалением пресета Advert.'
messages.ru.advertStartWhere = 'Перед запуском пресета; можно изменить поля или полностью обработать запуск.'
messages.ru.advertStopWhere = 'Перед остановкой пресета Advert.'
messages.ru.advertTransmitWhere = 'Перед каждым исходящим advertising-пакетом Advert или встроенных BLE-режимов.'
messages.ru.advertisingStopWhere = 'Перед остановкой цикла Advert или встроенного BLE-рекламодателя; внешний бэкенд может остановить свою передачу и вернуть handled.'
messages.ru.actionCatalogNote = 'Регистрация выполняется через hook_action(name, priority). Шаблоны bluetooth.*, bluetooth.ble.* и bluetooth.advert.preset.* перехватывают целые семейства.'
messages.ru.advertPayloadTitle = 'Данные bluetooth.advertising.transmit'
messages.ru.advertPayloadBody = 'metadata.source содержит ble_spam или advert; для Advert также передаётся metadata.preset. В data и scanResponse есть includeDeviceName, includeTxPowerLevel, manufacturerData [{id,data}], serviceUuids и serviceData [{uuid,data}]. Двоичные данные записаны HEX в верхнем регистре. Изменённый payload заново собирается в нативный AdvertiseData.'
messages.ru.virtualRoutesTitle = 'Цели подэкранов Bluetooth'
messages.ru.virtualRoutesBody = 'Это живые цели внутри маршрута bluetooth, а не отдельные страницы навигации. Они поддерживают все режимы hook_screen.'

messages.en.tocSdkReference = 'SDK reference'
messages.en.sdkReferenceTitle = 'Callable SDK reference'
messages.en.sdkReferenceLead = 'Search the complete public API. Every entry includes its exact signature, parameters, return contract, requirements and a Python call example.'
messages.en.sdkReadingTitle = 'How to read the contracts'
messages.en.sdkReadingBody = 'A nullable type can become None. Boolean actions report whether Android accepted the request, not whether a remote device completed it. Methods with callbacks finish asynchronously. JSON results remain strings until json.loads is called.'

messages.ru.tocSdkReference = 'Справочник SDK'
messages.ru.sdkReferenceTitle = 'Справочник вызываемого SDK'
messages.ru.sdkReferenceLead = 'Ищите по всему публичному API. У каждого метода показаны точная сигнатура, параметры, возвращаемое значение, ограничения и пример вызова из Python.'
messages.ru.sdkReadingTitle = 'Как читать контракты'
messages.ru.sdkReadingBody = 'Nullable-тип может стать None. Boolean у действия сообщает, принял ли Android запрос, а не завершило ли его удалённое устройство. Методы с callback работают асинхронно. JSON остаётся строкой до вызова json.loads.'

messages.en.tocDexLibraries = 'DEX & libraries'
messages.en.metaDependencies = 'Required plugin ids loaded before this plugin'
messages.en.manifestRulesBody = 'The file is UTF-8 text and may be at most 48 MiB. String fields must be top-level single- or double-quoted assignments. id is lowercased, invalid characters become underscores and the result is limited to 64 characters. Missing fields fall back to the filename, version 1.0 and icon extension.'
messages.en.libraryTruthTitle = 'Library plugins and dependencies'
messages.en.libraryTruthBody = 'Library plugins start before consumers. __dependencies__ declares required plugin ids, determines startup order and prevents a consumer from starting while a dependency is missing or disabled. Java classes and objects may be exported by name and imported by dependent plugins.'
messages.en.surfaceHooksTitle = 'App chrome surfaces'
messages.en.surfaceHooksBody = 'hook_surface targets UI outside an ordinary screen route. bottom_bar can be overlaid or fully replaced. The replacement follows plugin priority and disappears automatically when the plugin is disabled.'
messages.en.surfaceCatalogTitle = 'Hookable host surfaces'
messages.en.surfaceCatalogBody = 'Use hook_surface rather than hook_screen for UI owned by the app shell.'
messages.en.dexLibrariesTitle = 'DEX loading and library plugins'
messages.en.dexLibrariesLead = 'Keep a plugin as one readable Python file while embedding compiled Java or Kotlin code and sharing native capabilities with other plugins.'
messages.en.dexTrustTitle = 'DEX is native trusted code'
messages.en.dexTrustBody = 'A loaded DEX runs inside the Dolphy process with the app permissions. Always publish the SHA-256, verify it before loading and never execute an untrusted downloaded payload.'
messages.en.dexEmbeddedTitle = 'Embed and load a raw DEX'
messages.en.dexEmbeddedLead = 'Store the raw DEX as Base64 in the .dolphyplugin file. Dolphy removes whitespace, decodes it, verifies SHA-256 and creates the class loader. Raw DEX uses InMemoryDexClassLoader on Android 8+; older Android and ZIP-based JAR/APK payloads use a private read-only code-cache file and DexClassLoader.'
messages.en.dexLoadBase64Contract = 'Loads raw DEX, JAR or APK bytes stored as Base64 and returns the module ClassLoader.'
messages.en.dexLoadFileContract = 'Copies and loads a DEX/JAR/APK file. Use plugin-private storage and verify its digest.'
messages.en.dexClassContract = 'Loads a Class from a module owned by the current plugin.'
messages.en.dexInstanceContract = 'Calls an accessible no-argument constructor and returns the Java object.'
messages.en.dexLoaderContract = 'Returns an already loaded module ClassLoader or None.'
messages.en.dexDependencyLoaderContract = 'Returns an app parent loader which can also resolve modules exported by declared dependencies.'
messages.en.libraryExportsTitle = 'Export classes and live objects'
messages.en.libraryExportsLead = 'Exports are process-local named references. A library owns every class and object it exports; disabling, updating or deleting that library removes its modules and exports from the registry.'
messages.en.exportDexClassBody = 'Resolve and publish a class from the current DEX module.'
messages.en.exportAppClassBody = 'Publish a Dolphy or dependency class by its fully qualified name.'
messages.en.exportJavaClassBody = 'Publish a Class object already obtained through Java reflection.'
messages.en.exportJavaObjectBody = 'Publish a live Java object, adapter, controller or facade.'
messages.en.importJavaClassBody = 'Get a named Class export from any enabled dependency.'
messages.en.importJavaObjectBody = 'Get a named live object export from any enabled dependency.'
messages.en.javaExportsBody = 'Inspect all loaded modules and exported class/object names for diagnostics.'
messages.en.sourceBridgeTitle = 'Expose Dolphy internals from open source'
messages.en.sourceBridgeLead = 'Use the repository to find a fully qualified JVM class name, then export that Class or wrap it in a stable DEX facade. Consumers should depend on the library facade instead of duplicating fragile reflection code.'
messages.en.sourceBridgeBoundaryTitle = 'Class access versus host hooks'
messages.en.sourceBridgeBoundaryBody = 'Class export enables reflection, construction and method calls; it does not rewrite an already compiled Compose function. Use hook_screen for screens, hook_surface("bottom_bar") for the navigation panel, action hooks for logic and service providers for replaceable hardware backends. A DEX library can hide reflection behind a stable facade shared by other plugins.'
messages.en.dexLimitsTitle = 'Limits and lifecycle'
messages.en.dexLimitsBody = 'A .dolphyplugin may be 48 MiB; each decoded DEX/JAR/APK payload may be 32 MiB. Disabling, deleting or updating a library automatically disables enabled dependent plugins first; re-enable consumers when the library is ready. Android cannot unload a Class once code has referenced it. Dolphy drops loaders and exports when the owner stops, but a process restart is the only guaranteed complete unload after an update. Keep export names globally unique and close threads, receivers, sockets and hardware handles in on_plugin_unload.'

messages.ru.tocDexLibraries = 'DEX и библиотеки'
messages.ru.metaDependencies = 'Идентификаторы обязательных плагинов, загружаемых раньше этого'
messages.ru.manifestRulesBody = 'Файл является текстом UTF-8 размером не более 48 МиБ. Строковые поля должны быть верхнеуровневыми присваиваниями в одинарных или двойных кавычках. id переводится в нижний регистр, недопустимые символы заменяются подчёркиваниями, длина ограничена 64 символами. Для пропущенных полей используются имя файла, версия 1.0 и иконка extension.'
messages.ru.libraryTruthTitle = 'Плагины-библиотеки и зависимости'
messages.ru.libraryTruthBody = 'Библиотеки запускаются раньше потребителей. __dependencies__ объявляет обязательные id плагинов, определяет порядок запуска и не позволяет запустить потребителя, пока зависимость отсутствует или отключена. Java-классы и объекты можно экспортировать по имени и импортировать в зависимых плагинах.'
messages.ru.surfaceHooksTitle = 'Поверхности оболочки приложения'
messages.ru.surfaceHooksBody = 'hook_surface изменяет интерфейс за пределами обычного маршрута экрана. bottom_bar можно дополнить оверлеем или полностью заменить. Замена учитывает приоритет и автоматически исчезает при отключении плагина.'
messages.ru.surfaceCatalogTitle = 'Перехватываемые поверхности оболочки'
messages.ru.surfaceCatalogBody = 'Для интерфейса, принадлежащего оболочке приложения, используйте hook_surface вместо hook_screen.'
messages.ru.dexLibrariesTitle = 'Загрузка DEX и плагины-библиотеки'
messages.ru.dexLibrariesLead = 'Оставляйте плагин одним читаемым Python-файлом, встраивайте скомпилированный Java- или Kotlin-код и делитесь нативными возможностями с другими плагинами.'
messages.ru.dexTrustTitle = 'DEX является доверенным нативным кодом'
messages.ru.dexTrustBody = 'Загруженный DEX выполняется внутри процесса Dolphy с разрешениями приложения. Всегда публикуйте SHA-256, проверяйте его до загрузки и никогда не запускайте недоверенную скачанную нагрузку.'
messages.ru.dexEmbeddedTitle = 'Встраивание и загрузка сырого DEX'
messages.ru.dexEmbeddedLead = 'Сохраните сырой DEX в Base64 внутри .dolphyplugin. Dolphy удалит пробелы, декодирует данные, проверит SHA-256 и создаст загрузчик. На Android 8+ сырой DEX использует InMemoryDexClassLoader; старые версии Android и ZIP-контейнеры JAR/APK используют приватный read-only файл кодового кеша и DexClassLoader.'
messages.ru.dexLoadBase64Contract = 'Загружает сырой DEX, JAR или APK из Base64 и возвращает ClassLoader модуля.'
messages.ru.dexLoadFileContract = 'Копирует и загружает файл DEX/JAR/APK. Используйте приватное хранилище плагина и проверяйте хеш.'
messages.ru.dexClassContract = 'Загружает Class из модуля, принадлежащего текущему плагину.'
messages.ru.dexInstanceContract = 'Вызывает доступный конструктор без аргументов и возвращает Java-объект.'
messages.ru.dexLoaderContract = 'Возвращает ClassLoader уже загруженного модуля или None.'
messages.ru.dexDependencyLoaderContract = 'Возвращает родительский загрузчик приложения, который также видит модули объявленных зависимостей.'
messages.ru.libraryExportsTitle = 'Экспорт классов и живых объектов'
messages.ru.libraryExportsLead = 'Экспорты — именованные ссылки внутри текущего процесса. Библиотека владеет своими классами и объектами; при её отключении, обновлении или удалении модули и экспорты удаляются из реестра.'
messages.ru.exportDexClassBody = 'Находит и публикует класс из DEX-модуля текущего плагина.'
messages.ru.exportAppClassBody = 'Публикует класс Dolphy или зависимости по полному имени.'
messages.ru.exportJavaClassBody = 'Публикует объект Class, уже полученный через Java reflection.'
messages.ru.exportJavaObjectBody = 'Публикует живой Java-объект, адаптер, контроллер или фасад.'
messages.ru.importJavaClassBody = 'Получает именованный экспорт Class из включённой зависимости.'
messages.ru.importJavaObjectBody = 'Получает именованный экспорт живого объекта из включённой зависимости.'
messages.ru.javaExportsBody = 'Показывает загруженные модули и имена экспортированных классов и объектов для диагностики.'
messages.ru.sourceBridgeTitle = 'Открытие внутренних классов Dolphy по исходникам'
messages.ru.sourceBridgeLead = 'Найдите в репозитории полное JVM-имя класса, затем экспортируйте Class или оберните его стабильным DEX-фасадом. Потребителям лучше зависеть от фасада библиотеки, а не дублировать хрупкий reflection-код.'
messages.ru.sourceBridgeBoundaryTitle = 'Доступ к классам и хуки хоста — разные вещи'
messages.ru.sourceBridgeBoundaryBody = 'Экспорт класса даёт reflection, создание объектов и вызов методов, но не переписывает уже скомпилированную Compose-функцию. Для экранов используйте hook_screen, для панели навигации — hook_surface("bottom_bar"), для логики — action hooks, а для заменяемых аппаратных бэкендов — service providers. DEX-библиотека может скрыть reflection за стабильным фасадом для других плагинов.'
messages.en.docsSearch = 'Search documentation'
messages.en.tocStartGroup = 'Getting started'
messages.en.tocUiGroup = 'Interface'
messages.en.tocIntegrationGroup = 'Integration'
messages.en.tocNativeGroup = 'Native code'
messages.en.tocReferenceGroup = 'Reference'
messages.en.tocPluginSettings = 'Plugin settings'
messages.en.tocScreenHooks = 'Screen hooks'
messages.en.tocSurfaceHooks = 'App surfaces'
messages.en.tocLogicHooks = 'Actions & services'
messages.en.tocLibraryExports = 'Library exports'
messages.en.tocReflection = 'Java reflection'
messages.en.reflectionTitle = 'Java reflection helpers'
messages.en.reflectionLead = 'These helpers search the app plus DEX modules of declared dependencies. They can inspect private members, read or write fields, call a matching method and construct an object. They do not install a method hook and do not intercept future calls.'
messages.en.reflectionFind = 'Returns a Class or None using the app and dependency ClassLoaders.'
messages.en.reflectionInspect = 'Returns className plus inherited fields, methods, constructors, parameter types and static flags.'
messages.en.reflectionGet = 'Reads a public or private instance field. Pass a Class for a static field.'
messages.en.reflectionSet = 'Writes a public or private instance field. Pass a Class for a static field.'
messages.en.reflectionInvoke = 'Calls the first compatible overload by name, argument count and runtime types. Pass a Class for a static method.'
messages.en.reflectionNew = 'Calls the first compatible declared constructor, including a private constructor.'
messages.en.reflectionLimitsTitle = 'Reflection compatibility'
messages.en.reflectionLimitsBody = 'Private names and signatures may change between Dolphy builds. Catch exceptions, declare the library dependency, verify the app version yourself and prefer stable screen, surface, action and service hooks whenever one exists. Overload resolution does not perform arbitrary type conversion.'
messages.en.reflectionHelpers = 'Reflection helpers'
messages.en.reflectionBridgeHelpers = 'Reflection bridge'
messages.ru.docsSearch = 'Поиск по документации'
messages.ru.tocStartGroup = 'Начало работы'
messages.ru.tocUiGroup = 'Интерфейс'
messages.ru.tocIntegrationGroup = 'Интеграция'
messages.ru.tocNativeGroup = 'Нативный код'
messages.ru.tocReferenceGroup = 'Справочник'
messages.ru.tocPluginSettings = 'Настройки плагина'
messages.ru.tocScreenHooks = 'Хуки экранов'
messages.ru.tocSurfaceHooks = 'Поверхности приложения'
messages.ru.tocLogicHooks = 'Действия и сервисы'
messages.ru.tocLibraryExports = 'Экспорты библиотеки'
messages.ru.tocReflection = 'Java reflection'
messages.ru.reflectionTitle = 'Помощники Java reflection'
messages.ru.reflectionLead = 'Эти методы ищут классы приложения и DEX-модулей объявленных зависимостей. Они умеют описывать приватные члены, читать и менять поля, вызывать подходящий метод и создавать объект. Они не устанавливают method hook и не перехватывают будущие вызовы.'
messages.ru.reflectionFind = 'Возвращает Class или None через ClassLoader приложения и зависимостей.'
messages.ru.reflectionInspect = 'Возвращает className, унаследованные поля, методы, конструкторы, типы параметров и признаки static.'
messages.ru.reflectionGet = 'Читает публичное или приватное поле объекта. Для статического поля передайте Class.'
messages.ru.reflectionSet = 'Записывает публичное или приватное поле объекта. Для статического поля передайте Class.'
messages.ru.reflectionInvoke = 'Вызывает первую совместимую перегрузку по имени, числу аргументов и их runtime-типам. Для статического метода передайте Class.'
messages.ru.reflectionNew = 'Вызывает первый совместимый объявленный конструктор, включая приватный.'
messages.ru.reflectionLimitsTitle = 'Совместимость reflection'
messages.ru.reflectionLimitsBody = 'Приватные имена и сигнатуры могут меняться между сборками Dolphy. Обрабатывайте исключения, объявляйте зависимость от библиотеки, самостоятельно проверяйте версию приложения и предпочитайте стабильные хуки экранов, поверхностей, действий и сервисов. Выбор перегрузки не выполняет произвольное преобразование типов.'
messages.ru.reflectionHelpers = 'Помощники reflection'
messages.ru.reflectionBridgeHelpers = 'Бридж reflection'
messages.ru.dexLimitsTitle = 'Ограничения и жизненный цикл'
messages.ru.dexLimitsBody = 'Размер .dolphyplugin может достигать 48 МиБ, а каждый декодированный DEX/JAR/APK — 32 МиБ. При отключении, удалении или обновлении библиотеки Dolphy сначала автоматически отключает активные зависимые плагины; включите потребителей снова после готовности библиотеки. Android не умеет выгружать Class после использования кода. Dolphy удаляет ссылки на загрузчики и экспорты при остановке владельца, но полностью гарантированная выгрузка после обновления возможна только перезапуском процесса. Делайте имена экспортов глобально уникальными и закрывайте потоки, receiver, сокеты и оборудование в on_plugin_unload.'

const pageText = {
  home: {
    en: ['Dolphy — Wireless toolkit for Android', 'Dolphy is an expressive Android toolkit for Bluetooth, infrared, NFC, HID, networks and Python plugins.'],
    ru: ['Dolphy — беспроводные инструменты для Android', 'Dolphy — выразительный Android-набор для Bluetooth, ИК, NFC, HID, сетей и Python-плагинов.']
  },
  docs: {
    en: ['Dolphy Plugin Documentation', 'Documentation for creating Python .dolphyplugin extensions for Dolphy.'],
    ru: ['Документация плагинов Dolphy', 'Документация по созданию Python-расширений .dolphyplugin для Dolphy.']
  }
}

const storedLanguage = localStorage.getItem('dolphy-language')
const browserLanguage = (navigator.languages?.[0] || navigator.language || 'en').toLowerCase()
let language = storedLanguage === 'ru' || storedLanguage === 'en' ? storedLanguage : browserLanguage.startsWith('ru') ? 'ru' : 'en'

function setLanguage(nextLanguage) {
  language = nextLanguage
  document.documentElement.lang = language
  document.querySelectorAll('[data-i18n]').forEach(element => {
    const value = messages[language][element.dataset.i18n]
    if (value) element.textContent = value
  })
  document.querySelectorAll('[data-i18n-placeholder]').forEach(element => {
    const value = messages[language][element.dataset.i18nPlaceholder]
    if (value) element.placeholder = value
  })
  document.querySelectorAll('[data-language-label]').forEach(element => {
    element.textContent = language === 'ru' ? 'EN' : 'RU'
  })
  const page = document.body.dataset.page || 'home'
  const [title, description] = pageText[page][language]
  document.title = title
  const meta = document.querySelector('meta[name="description"]')
  if (meta) meta.content = description
  localStorage.setItem('dolphy-language', language)
}

document.querySelectorAll('[data-language-toggle]').forEach(button => {
  button.addEventListener('click', () => setLanguage(language === 'ru' ? 'en' : 'ru'))
})

document.querySelectorAll('[data-current-year]').forEach(element => {
  element.textContent = new Date().getFullYear()
})

document.querySelectorAll('.copy-button, .copy-code').forEach(button => {
  button.addEventListener('click', async () => {
    const code = button.closest('.code-block, .code-card')?.querySelector('code')?.innerText || ''
    await navigator.clipboard.writeText(code)
    const label = button.querySelector('[data-i18n]')
    if (label) label.textContent = language === 'ru' ? 'Скопировано' : 'Copied'
    setTimeout(() => {
      if (label) label.textContent = messages[language].copy
    }, 1400)
  })
})

const revealObserver = new IntersectionObserver(entries => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      entry.target.classList.add('visible')
      revealObserver.unobserve(entry.target)
    }
  })
}, { threshold: .01 })

document.querySelectorAll('.reveal').forEach(element => revealObserver.observe(element))

const tocLinks = [...document.querySelectorAll('.docs-toc a')]
const docNavSearch = document.querySelector('[data-doc-nav-search]')
if (docNavSearch) {
  docNavSearch.addEventListener('input', () => {
    const query = docNavSearch.value.trim().toLowerCase()
    tocLinks.forEach(link => { link.hidden = query && !link.textContent.toLowerCase().includes(query) })
    document.querySelectorAll('.docs-toc .toc-title').forEach(title => {
      let sibling = title.nextElementSibling
      let visible = false
      while (sibling && !sibling.classList.contains('toc-title')) {
        if (sibling.matches('a') && !sibling.hidden) visible = true
        sibling = sibling.nextElementSibling
      }
      title.hidden = Boolean(query) && !visible
    })
  })
}
const docSections = [...document.querySelectorAll('.doc-section[id]')]
if (tocLinks.length && docSections.length) {
  const tocObserver = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        tocLinks.forEach(link => link.classList.toggle('active', link.getAttribute('href') === `#${entry.target.id}`))
      }
    })
  }, { rootMargin: '-25% 0px -65% 0px' })
  docSections.forEach(section => tocObserver.observe(section))
}

setLanguage(language)
