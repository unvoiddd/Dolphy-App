const sdkText = {
  en: {
    search: 'Search methods, parameters or capabilities',
    all: 'All', methods: 'methods', publicApi: 'Public API', noResults: 'No API methods match this query.',
    parameters: 'Parameters', returns: 'Returns', callback: 'Callback', requirements: 'Requirements', example: 'Example',
    noParameters: 'No parameters.', optional: 'optional', defaultValue: 'Default',
    callbackReturn: 'The call returns immediately. The final result is delivered to the callback.',
    callbackJson: 'A String callback normally receives JSON text. Convert it with json.loads(str(value)). A Boolean callback receives True or False.',
    booleanCheck: 'Boolean state: true when the condition is satisfied, otherwise false.',
    booleanAction: 'true when the operation was accepted; false when it is unsupported, denied or failed.',
    jsonReturn: 'JSON text. Convert it with json.loads(str(value)).',
    nullableReturn: 'The declared value or None when it is unavailable or the operation fails.',
    unitReturn: 'No return value.', nodeReturn: 'A JSON-compatible native UI node rendered by Dolphy.',
    directReturn: 'Returns the declared Java/Python value. Java objects are exposed through Chaquopy.',
    general: 'Runs in the plugin process. Handle Java and Python exceptions around direct platform calls.',
    ui: 'No Android permission is required. Return this node from a plugin screen or another UI builder.',
    files: 'String paths are relative to this plugin private directory unless File or Uri is explicitly required.',
    bluetooth: 'Requires compatible Bluetooth hardware and relevant Android permissions. Android 12+ separates scan, connect and advertise access.',
    wifi: 'Requires Wi-Fi hardware. Scan and nearby operations can require Location or Nearby Wi-Fi permission depending on Android version.',
    nfc: 'Requires NFC hardware and an enabled adapter. Tag methods need a tag delivered to Dolphy while the plugin is active.',
    infrared: 'Requires a built-in IR emitter or a plugin-provided external transmitter service.',
    network: 'Requires network access. Validate untrusted hosts, payloads and size limits in plugin code.',
    root: 'Requires working Root. Command callbacks use {ok, code, out, err, via}; always check ok and code.',
    shizuku: 'Requires Shizuku to be running and permission granted to Dolphy.',
    notification: 'Android 13+ can require POST_NOTIFICATIONS before a notification is visible.',
    location: 'Location data can require runtime location permission and an enabled provider.',
    sensor: 'Stop listeners during unload to prevent leaks and background battery use.',
    dex: 'Runs trusted native code in the Dolphy process. Verify SHA-256, keep payloads private and release every resource during unload.',
    resource: 'This call can own a long-lived resource. Stop it explicitly or call release() from on_plugin_unload.',
    does: 'Performs', checks: 'Checks', reads: 'Reads', writes: 'Writes', starts: 'Starts', stops: 'Stops', opens: 'Opens', creates: 'Creates', deletes: 'Deletes', sends: 'Sends', registers: 'Registers', through: 'through'
  },
  ru: {
    search: 'Поиск по методам, параметрам и возможностям',
    all: 'Все', methods: 'методов', publicApi: 'Публичный API', noResults: 'По этому запросу методы API не найдены.',
    parameters: 'Параметры', returns: 'Возвращает', callback: 'Callback', requirements: 'Условия и ограничения', example: 'Пример',
    noParameters: 'Параметров нет.', optional: 'необязательный', defaultValue: 'По умолчанию',
    callbackReturn: 'Вызов завершается сразу, итоговый результат приходит в callback.',
    callbackJson: 'Callback типа String обычно получает JSON-текст: преобразуйте его через json.loads(str(value)). Boolean-callback получает True или False.',
    booleanCheck: 'Булево состояние: true, когда условие выполнено, иначе false.',
    booleanAction: 'true, если операция принята; false, если она не поддерживается, запрещена или завершилась ошибкой.',
    jsonReturn: 'JSON-текст. Преобразуйте его через json.loads(str(value)).',
    nullableReturn: 'Объявленное значение либо None, если оно недоступно или операция завершилась ошибкой.',
    unitReturn: 'Значение не возвращается.', nodeReturn: 'JSON-совместимый узел нативного интерфейса, который отрисовывает Dolphy.',
    directReturn: 'Возвращает объявленное Java/Python-значение. Java-объекты передаются через Chaquopy.',
    general: 'Выполняется в процессе плагина. Прямые платформенные вызовы следует оборачивать в обработку Java- и Python-исключений.',
    ui: 'Разрешения Android не нужны. Верните узел из экрана плагина или другого UI-билдера.',
    files: 'Строковые пути относятся к приватной папке этого плагина, если явно не требуется File или Uri.',
    bluetooth: 'Нужно совместимое Bluetooth-оборудование и соответствующие разрешения Android. В Android 12+ scan, connect и advertise разделены.',
    wifi: 'Нужно Wi-Fi-оборудование. Сканирование и nearby-операции могут требовать Location или Nearby Wi-Fi в зависимости от Android.',
    nfc: 'Нужны NFC и включённый адаптер. Для методов метки Dolphy должен получить Tag, пока плагин активен.',
    infrared: 'Нужен встроенный ИК-передатчик либо внешний передатчик, предоставленный плагином как сервис.',
    network: 'Нужен доступ к сети. Проверяйте недоверенные адреса, данные и ограничения размера.',
    root: 'Нужен рабочий Root. Callback команд содержит {ok, code, out, err, via}; всегда проверяйте ok и code.',
    shizuku: 'Shizuku должен быть запущен, а Dolphy должно быть выдано разрешение.',
    notification: 'В Android 13+ для видимого уведомления может потребоваться POST_NOTIFICATIONS.',
    location: 'Для геоданных могут требоваться runtime-разрешение и включённый провайдер.',
    sensor: 'Останавливайте listeners при выгрузке, чтобы не оставлять фоновые задачи и расход батареи.',
    dex: 'Запускает доверенный нативный код в процессе Dolphy. Проверяйте SHA-256, храните нагрузку приватно и освобождайте все ресурсы при выгрузке.',
    resource: 'Вызов может создать долгоживущий ресурс. Остановите его явно либо вызовите release() в on_plugin_unload.',
    does: 'Выполняет', checks: 'Проверяет', reads: 'Читает', writes: 'Записывает', starts: 'Запускает', stops: 'Останавливает', opens: 'Открывает', creates: 'Создаёт', deletes: 'Удаляет', sends: 'Отправляет', registers: 'Регистрирует', through: 'через'
  }
}

const sdkParams = {
  callback: ['Function receiving the asynchronous result.', 'Функция, получающая асинхронный результат.'],
  onEvent: ['Function called for each state or data event.', 'Функция для каждого события состояния или данных.'],
  onDeviceJs: ['Function receiving a JSON batch of discovered devices.', 'Функция, получающая JSON-пакет найденных устройств.'],
  onResult: ['Function receiving the asynchronous result.', 'Функция, получающая асинхронный результат.'],
  cb: ['Event receiver; pass None to detach it.', 'Получатель событий; передайте None, чтобы отключить его.'],
  path: ['Path inside plugin-private storage.', 'Путь внутри приватного хранилища плагина.'],
  address: ['Bluetooth MAC address.', 'MAC-адрес Bluetooth-устройства.'],
  serviceUuid: ['GATT service UUID.', 'UUID сервиса GATT.'], charUuid: ['GATT characteristic UUID.', 'UUID характеристики GATT.'], descUuid: ['GATT descriptor UUID.', 'UUID дескриптора GATT.'],
  scanId: ['Stable identifier used to stop this scan.', 'Постоянный идентификатор для последующей остановки сканирования.'],
  command: ['Shell command text.', 'Текст shell-команды.'], enabled: ['Target enabled state.', 'Требуемое состояние включения.'],
  hex: ['Even-length hexadecimal bytes.', 'Байты в HEX-строке чётной длины.'], payloadHex: ['Advertising payload as hexadecimal bytes.', 'Advertising payload в виде HEX-строки.'],
  recordsJson: ['JSON array describing NDEF records.', 'JSON-массив с описанием NDEF-записей.'], headersJson: ['JSON object with HTTP headers, or None.', 'JSON-объект HTTP-заголовков либо None.'],
  maxBytes: ['Maximum accepted byte count.', 'Максимально допустимое число байт.'], timeoutMs: ['Timeout in milliseconds.', 'Таймаут в миллисекундах.'],
  freqHz: ['Carrier frequency in hertz.', 'Несущая частота в герцах.'], pattern: ['Alternating mark and space durations in microseconds.', 'Чередующиеся длительности импульса и паузы в микросекундах.'],
  key: ['Stable key or platform property name.', 'Постоянный ключ либо имя системного свойства.'], value: ['New value.', 'Новое значение.'],
  url: ['Absolute URL.', 'Абсолютный URL.'], on_change: ['Callback receiving the new component value.', 'Callback, получающий новое значение компонента.'],
  on_click: ['Zero-argument click callback.', 'Callback без аргументов при нажатии.'], on_select: ['Callback receiving the selected index or value.', 'Callback, получающий выбранный индекс или значение.']
}

function sdkEscape(value) {
  return String(value).replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]))
}

function sdkSplit(source) {
  if (!source.trim()) return []
  const result = []
  let current = ''
  let depth = 0
  for (const char of source) {
    if ('(<[{'.includes(char)) depth += 1
    if (')>]}'.includes(char)) depth -= 1
    if (char === ',' && depth === 0) {
      result.push(current.trim())
      current = ''
    } else current += char
  }
  if (current.trim()) result.push(current.trim())
  return result
}

function sdkInferReturn(name, group) {
  if (group === 'ui') return 'UiNode'
  if (/^load_dex/.test(name)) return 'ClassLoader'
  if (name === 'dex_class' || /^import_java_class$/.test(name)) return 'Class?'
  if (/^new_dex_instance$|^import_java_object$/.test(name)) return 'Object?'
  if (/^export_(dex|app|java)/.test(name)) return 'Boolean'
  if (name === 'java_exports') return 'Object'
  if (/^(add_module|navigate|navigate_app|refresh|set_setting|toast|hook_screen|provide_service|hook_action|register)/.test(name)) return 'Unit'
  return 'Any'
}

function sdkParse(signature, group) {
  const arrow = signature.lastIndexOf(' -> ')
  const declaration = arrow >= 0 ? signature.slice(0, arrow) : signature
  const open = declaration.indexOf('(')
  const close = declaration.lastIndexOf(')')
  const name = open >= 0 ? declaration.slice(0, open) : declaration
  const parts = open >= 0 && close >= open ? sdkSplit(declaration.slice(open + 1, close)) : []
  return {
    name,
    returns: arrow >= 0 ? signature.slice(arrow + 4) : sdkInferReturn(name, group),
    params: parts.map(item => {
      const equals = item.indexOf('=')
      const left = (equals >= 0 ? item.slice(0, equals) : item).trim()
      const colon = left.indexOf(':')
      return {name: (colon >= 0 ? left.slice(0, colon) : left).trim(), type: (colon >= 0 ? left.slice(colon + 1) : 'Any').trim(), defaultValue: equals >= 0 ? item.slice(equals + 1).trim() : ''}
    })
  }
}

function sdkWords(name) {
  return name.replace(/Json$/, ' JSON').replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/_/g, ' ').toLowerCase()
}

function sdkDescription(parsed, owner, text, lang) {
  const name = parsed.name
  let verb = text.does
  if (/^(is|has)|Supports|Available|Enabled/.test(name)) verb = text.checks
  else if (/(Get|Read|List|Info|Status|Capabilities|Stat|Results|Last|^get)/.test(name)) verb = text.reads
  else if (/(Write|Set|Put|Append|Import|Push)/.test(name)) verb = text.writes
  else if (/(Start|Connect|Discover|Play|Enable|Install|Request)/.test(name)) verb = text.starts
  else if (/(Stop|Disconnect|Cancel|Disable|Release|Unload)/.test(name)) verb = text.stops
  else if (/(Open|Share|Dial|Navigate)/.test(name)) verb = text.opens
  else if (/(Create|Mkdir|Add)/.test(name)) verb = text.creates
  else if (/(Delete|Remove|Uninstall|Kill)/.test(name)) verb = text.deletes
  else if (/(Send|Transmit|Notify|Broadcast)/.test(name)) verb = text.sends
  else if (/(Register|Hook|Provide)/.test(name)) verb = text.registers
  return lang === 'ru' ? verb + ' операцию «' + sdkWords(name) + '» ' + text.through + ' ' + owner + '.' : verb + ' the ' + sdkWords(name) + ' operation ' + text.through + ' ' + owner + '.'
}

function sdkRequirement(parsed, group, text) {
  const name = parsed.name
  const result = []
  if (group === 'ui') result.push(text.ui)
  else if (/^(files|pluginFiles|importUri|exportSandbox|assets|createCamera|importCamera)/.test(name)) result.push(text.files)
  else if (/^(bt|ble|gatt)/.test(name)) result.push(text.bluetooth)
  else if (/^wifi/.test(name)) result.push(text.wifi)
  else if (/^(nfc|onNfc)/.test(name)) result.push(text.nfc)
  else if (/^ir/.test(name)) result.push(text.infrared)
  else if (/^(http|tcp|ping|portScan|network|netInterfaces|nsd)/.test(name)) result.push(text.network)
  else if (/^root/.test(name)) result.push(text.root)
  else if (/^shizuku/.test(name)) result.push(text.shizuku)
  else if (/^notify/.test(name)) result.push(text.notification)
  else if (/^location/.test(name)) result.push(text.location)
  else if (/^sensor/.test(name)) result.push(text.sensor)
  else if (/(Dex|dex|java_class|java_object|java_exports)/.test(name)) result.push(text.dex)
  else result.push(text.general)
  if (/(Start|Connect|Discover|Play|Advertise|Server)/.test(name) && !/(Stop|Disconnect)/.test(name)) result.push(text.resource)
  return result
}

function sdkReturn(parsed, text) {
  if (parsed.params.some(param => /callback|onEvent|onResult|onDeviceJs|cb/.test(param.name))) return text.callbackReturn
  if (parsed.returns === 'Boolean') return /^(is|has)|Supports|Available|Enabled/.test(parsed.name) ? text.booleanCheck : text.booleanAction
  if (parsed.returns === 'String' && /Json$/.test(parsed.name)) return text.jsonReturn
  if (parsed.returns.endsWith('?')) return text.nullableReturn
  if (parsed.returns === 'Unit') return text.unitReturn
  if (parsed.returns === 'UiNode') return text.nodeReturn
  return text.directReturn
}

function sdkExampleValue(param) {
  if (/callback|onEvent|onResult|onDeviceJs|cb/.test(param.name)) return 'self.on_result'
  if (param.defaultValue) return ''
  if (param.name === 'freqHz' || param.name === 'frequency') return '38000'
  if (param.name === 'pattern') return '[9000, 4500, 560, 560]'
  if (/address/i.test(param.name)) return '"AA:BB:CC:DD:EE:FF"'
  if (/uuid/i.test(param.name)) return '"0000180f-0000-1000-8000-00805f9b34fb"'
  if (/path|file/i.test(param.name)) return '"data/output.bin"'
  if (/url/i.test(param.name)) return '"https://example.org"'
  if (/hex|b64|data/i.test(param.name)) return '"01020304"'
  if (/json/i.test(param.name)) return '"{}"'
  if (/text|title|name|key|action|command|host|iface|pkg|service|mode/i.test(param.name)) return '"value"'
  if (/Array|List/.test(param.type)) return '[]'
  if (/Boolean/.test(param.type)) return 'True'
  if (/Int|Long|Float|Double/.test(param.type)) return '0'
  if (param.type.endsWith('?')) return 'None'
  return 'value'
}

function sdkExample(parsed, owner) {
  const values = parsed.params.map(sdkExampleValue).filter(Boolean)
  const call = owner + '.' + parsed.name + '(' + values.join(', ') + ')'
  if (parsed.params.some(param => /callback|onEvent|onResult|onDeviceJs|cb/.test(param.name))) return 'def on_result(self, value):\n    print(value)\n\n' + call
  return (parsed.returns === 'Unit' ? '' : 'result = ') + call
}

function sdkCatalog() {
  const groups = []
  const seen = new Set()
  document.querySelectorAll('.api-reference details').forEach(details => {
    const strong = details.querySelector('summary strong')
    if (!strong) return
    let group = ''
    let owner = ''
    if (strong.matches('[data-i18n="deviceCompleteTitle"]')) { group = 'device'; owner = 'self.api.getDevice()' }
    else if (strong.textContent.trim() === 'BasePlugin') { group = 'base'; owner = 'self' }
    else if (strong.textContent.trim() === 'UiFactory') { group = 'ui'; owner = 'self.ui' }
    else if (strong.textContent.trim() === 'api') { group = 'bridge'; owner = 'self.api' }
    else if (strong.textContent.includes('getAndroid')) { group = 'android'; owner = 'self.api.getAndroid()' }
    else if (strong.textContent.includes('getDevice')) { group = 'device'; owner = 'self.api.getDevice()' }
    if (!group) return
    details.querySelectorAll('.api-chips code').forEach(code => {
      const parsed = sdkParse(code.textContent.trim(), group)
      const key = group + ':' + parsed.name
      if (seen.has(key)) return
      seen.add(key)
      groups.push({group, owner, signature: code.textContent.trim(), parsed})
    })
  })
  return groups
}

function sdkRender() {
  const root = document.querySelector('[data-sdk-reference]')
  if (!root) return
  const lang = document.documentElement.lang === 'ru' ? 'ru' : 'en'
  const text = sdkText[lang]
  const query = (root.querySelector('[data-sdk-search]').value || '').trim().toLowerCase()
  const selected = root.dataset.layer || 'all'
  const entries = sdkCatalog().filter(item => (selected === 'all' || item.group === selected) && (!query || (item.owner + ' ' + item.signature + ' ' + sdkWords(item.parsed.name)).toLowerCase().includes(query)))
  root.querySelector('[data-sdk-count]').textContent = selected === 'all' && !query ? text.publicApi : entries.length + ' ' + text.methods
  const list = root.querySelector('[data-sdk-list]')
  if (!entries.length) {
    list.innerHTML = '<div class="sdk-empty"><span class="material-symbols-rounded">search_off</span><p>' + text.noResults + '</p></div>'
    return
  }
  list.innerHTML = entries.map(item => {
    const parsed = item.parsed
    const params = parsed.params.length ? parsed.params.map(param => {
      const known = sdkParams[param.name]?.[lang === 'ru' ? 1 : 0]
      const fallback = lang === 'ru' ? 'Значение «' + param.name + '» типа ' + param.type + '.' : 'The ' + param.name + ' value passed as ' + param.type + '.'
      const flags = [param.type.endsWith('?') ? text.optional : '', param.defaultValue ? text.defaultValue + ': ' + param.defaultValue : ''].filter(Boolean).join(' · ')
      return '<div><code>' + sdkEscape(param.name) + '</code><span><b>' + sdkEscape(param.type) + '</b> · ' + sdkEscape(known || fallback) + (flags ? '<small>' + sdkEscape(flags) + '</small>' : '') + '</span></div>'
    }).join('') : '<p class="sdk-none">' + text.noParameters + '</p>'
    const requirements = sdkRequirement(parsed, item.group, text).map(value => '<li>' + sdkEscape(value) + '</li>').join('')
    const callback = parsed.params.some(param => /callback|onEvent|onResult|onDeviceJs|cb/.test(param.name)) ? '<h4>' + text.callback + '</h4><p>' + sdkEscape(text.callbackJson) + '</p>' : ''
    const anchor = 'sdk-' + item.group + '-' + parsed.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')
    return '<details class="sdk-method" id="' + anchor + '"><summary><span class="sdk-owner">' + sdkEscape(item.owner) + '</span><strong>' + sdkEscape(parsed.name) + '</strong><code>' + sdkEscape(parsed.returns) + '</code><span class="material-symbols-rounded">expand_more</span></summary><div class="sdk-method-body"><p class="sdk-description">' + sdkEscape(sdkDescription(parsed, item.owner, text, lang)) + '</p><pre><code>' + sdkEscape(item.owner + '.' + item.signature.replace(' -> ', ' → ')) + '</code></pre><div class="sdk-detail-grid"><section><h4>' + text.parameters + '</h4><div class="sdk-params">' + params + '</div></section><section><h4>' + text.returns + '</h4><p>' + sdkEscape(sdkReturn(parsed, text)) + '</p>' + callback + '</section></div><h4>' + text.requirements + '</h4><ul class="sdk-requirements">' + requirements + '</ul><h4>' + text.example + '</h4><pre><code>' + sdkEscape(sdkExample(parsed, item.owner)) + '</code></pre></div></details>'
  }).join('')
}

function sdkInit() {
  const root = document.querySelector('[data-sdk-reference]')
  if (!root) return
  const search = root.querySelector('[data-sdk-search]')
  const filters = root.querySelector('[data-sdk-filters]')
  const synchronize = () => {
    const text = sdkText[document.documentElement.lang === 'ru' ? 'ru' : 'en']
    search.placeholder = text.search
    root.querySelector('[data-sdk-all]').textContent = text.all
    sdkRender()
  }
  filters.addEventListener('click', event => {
    const button = event.target.closest('[data-sdk-layer]')
    if (!button) return
    root.dataset.layer = button.dataset.sdkLayer
    filters.querySelectorAll('button').forEach(item => item.classList.toggle('selected', item === button))
    sdkRender()
  })
  search.addEventListener('input', sdkRender)
  new MutationObserver(synchronize).observe(document.documentElement, {attributes: true, attributeFilter: ['lang']})
  synchronize()
}

document.addEventListener('DOMContentLoaded', sdkInit)
