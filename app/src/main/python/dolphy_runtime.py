import inspect
import json
import traceback


class UiFactory:
    def __init__(self, session):
        self.session = session

    def _callback(self, value):
        if value is None:
            return None
        if isinstance(value, str):
            return value
        return self.session.register_callback(value)

    def _children(self, children):
        if children is None:
            return []
        if isinstance(children, (list, tuple)):
            return [item for item in children if item is not None]
        return [children]

    def scaffold(self, content, title=None, show_back=True, fab=None, actions=None):
        top_bar = None
        if title is not None:
            top_bar = {"title": str(title), "showBack": bool(show_back), "actions": actions or []}
        return {"type": "scaffold", "topBar": top_bar, "content": content, "fab": fab}

    def top_action(self, icon, on_click=None):
        return {"icon": icon, "onClickId": self._callback(on_click)}

    def column(self, children=None, padding=0, spacing=8, fill_max_size=False):
        return {"type": "column", "children": self._children(children), "padding": padding, "spacing": spacing, "fillMaxSize": fill_max_size}

    def row(self, children=None, padding=0, spacing=8, fill_max_width=True):
        return {"type": "row", "children": self._children(children), "padding": padding, "spacing": spacing, "fillMaxWidth": fill_max_width}

    def box(self, children=None, padding=0, fill_max_size=False):
        return {"type": "box", "children": self._children(children), "padding": padding, "fillMaxSize": fill_max_size}

    def lazy_column(self, children=None, padding=0, spacing=4, fill_max_size=True):
        return {"type": "lazyColumn", "children": self._children(children), "padding": padding, "spacing": spacing, "fillMaxSize": fill_max_size}

    def lazy_row(self, children=None, padding=0, spacing=8, fill_max_width=True):
        return {"type": "lazyRow", "children": self._children(children), "padding": padding, "spacing": spacing, "fillMaxWidth": fill_max_width}

    def text(self, text, style="bodyMedium", color=None, max_lines=2147483647):
        return {"type": "text", "text": str(text), "style": style, "color": color, "maxLines": max_lines}

    def button(self, text, on_click=None, style="filled", enabled=True, fill_max_width=False):
        return {"type": "button", "text": str(text), "onClickId": self._callback(on_click), "style": style, "enabled": enabled, "fillMaxWidth": fill_max_width}

    def bounce_button(self, text, on_click=None, enabled=True, fill_max_width=True):
        return {"type": "bounceButton", "text": str(text), "onClickId": self._callback(on_click), "enabled": enabled, "fillMaxWidth": fill_max_width}

    def split_button(self, text, on_click=None, on_secondary=None, enabled=True, fill_max_width=True):
        return {"type": "splitButton", "primaryText": str(text), "onPrimaryId": self._callback(on_click), "onSecondaryId": self._callback(on_secondary), "enabled": enabled, "fillMaxWidth": fill_max_width}

    def icon_button(self, icon, on_click=None, enabled=True):
        return {"type": "iconButton", "icon": icon, "onClickId": self._callback(on_click), "enabled": enabled}

    def text_field(self, value="", on_change=None, label="", single_line=True, fill_max_width=True):
        return {"type": "textField", "value": str(value), "onChangeId": self._callback(on_change), "label": label, "singleLine": single_line, "fillMaxWidth": fill_max_width}

    def switch(self, checked=False, on_change=None, title="", subtitle="", enabled=True):
        return {"type": "switch", "checked": bool(checked), "onChangeId": self._callback(on_change), "title": title, "subtitle": subtitle, "enabled": enabled}

    def checkbox(self, checked=False, on_change=None, title="", subtitle="", enabled=True):
        return {"type": "checkbox", "checked": bool(checked), "onChangeId": self._callback(on_change), "title": title, "subtitle": subtitle, "enabled": enabled}

    def slider(self, value=0, on_change=None, minimum=0, maximum=100, title="", steps=0):
        return {"type": "slider", "value": value, "onChangeId": self._callback(on_change), "min": minimum, "max": maximum, "title": title, "steps": steps}

    def linear_progress(self, progress=None, fill_max_width=True):
        return {"type": "linearProgress", "progress": progress, "fillMaxWidth": fill_max_width}

    def circular_progress(self, progress=None):
        return {"type": "circularProgress", "progress": progress}

    def wavy_progress(self, progress=None, size=64):
        return {"type": "wavyProgress", "progress": progress, "size": size}

    def divider(self, vertical=False):
        return {"type": "divider", "vertical": vertical}

    def spacer(self, height=8, width=0):
        return {"type": "spacer", "height": height, "width": width}

    def icon(self, name, size=24, tint=None):
        return {"type": "icon", "name": name, "size": size, "tint": tint}

    def image(self, source, width=None, height=None, scale="fit", corner_radius=0, fill_max_width=False, content_description=""):
        return {"type": "image", "source": source, "width": width, "height": height, "scale": scale, "cornerRadius": corner_radius, "fillMaxWidth": fill_max_width, "contentDescription": content_description}

    def chip(self, text, selected=False, on_click=None):
        return {"type": "chip", "text": str(text), "selected": selected, "onClickId": self._callback(on_click)}

    def card(self, children=None, padding=16, index=None, count=None, on_click=None):
        return {"type": "materialCard", "children": self._children(children), "contentPadding": padding, "segmentedIndex": index, "segmentedCount": count, "onClickId": self._callback(on_click)}

    def function_row(self, title, description="", icon="extension", icon_tint=None, on_click=None):
        return {"type": "functionRow", "title": title, "description": description, "icon": icon, "iconTint": icon_tint, "onClickId": self._callback(on_click)}

    def segmented_list(self, children=None, spacing=4):
        return {"type": "segmentedList", "children": self._children(children), "spacing": spacing}

    def settings_row(self, title, subtitle="", icon=None, on_click=None, trailing=None):
        return {"type": "settingsRow", "title": title, "subtitle": subtitle, "icon": icon, "onClickId": self._callback(on_click), "trailing": trailing}

    def tabs(self, tabs, selected=0, on_select=None):
        return {"type": "tabRow", "tabs": list(tabs), "selectedIndex": selected, "onSelectId": self._callback(on_select)}

    def button_group(self, options, selected, on_select=None):
        normalized = [[str(item[0]), str(item[1])] for item in options]
        return {"type": "connectedButtonGroup", "options": normalized, "selectedValue": str(selected), "onSelectId": self._callback(on_select)}

    def floating_toolbar(self, items):
        normalized = []
        for item in items:
            value = dict(item)
            value["onClickId"] = self._callback(value.pop("on_click", value.get("onClickId")))
            normalized.append(value)
        return {"type": "floatingToolbar", "items": normalized}

    def toolbar_item(self, icon, on_click=None, selected=False, label=""):
        return {"icon": icon, "selected": selected, "on_click": on_click, "label": label}

    def radio_group(self, options, selected, on_select=None, title=""):
        normalized = [[str(item[0]), str(item[1])] for item in options]
        return {"type": "radioGroup", "options": normalized, "selectedValue": str(selected), "onSelectId": self._callback(on_select), "title": title}

    def dropdown(self, options, selected, on_select=None, label="", enabled=True):
        normalized = [[str(item[0]), str(item[1])] for item in options]
        return {"type": "dropdown", "options": normalized, "selectedValue": str(selected), "onSelectId": self._callback(on_select), "label": label, "enabled": enabled}

    def webview(self, url="", html="", fill_max_size=True, height=320):
        return {"type": "webView", "url": url, "html": html, "fillMaxSize": fill_max_size, "height": height}

    def log_panel(self, text, max_height=200):
        return {"type": "logPanel", "text": str(text), "maxHeight": max_height}

    def alert_dialog(self, show, title, message, confirm_text="OK", dismiss_text="Cancel", on_confirm=None, on_dismiss=None, cancelable=True):
        return {"type": "alertDialog", "show": show, "title": title, "message": message, "confirmText": confirm_text, "dismissText": dismiss_text, "onConfirmId": self._callback(on_confirm), "onDismissId": self._callback(on_dismiss), "cancelable": cancelable}

    def empty(self):
        return {"type": "empty"}


class BasePlugin:
    def __init__(self):
        self.api = None
        self.ui = None

    def add_module(self, title, description="", icon="extension", screen="main", section="PLUGINS", order=0):
        return self.api.registerModule(section, title, description, icon, screen, order)

    def navigate(self, screen):
        return self.api.navigate(str(screen))

    def navigate_app(self, route):
        return self.api.navigate("__app__:" + str(route))

    def refresh(self):
        return self.api.refresh()

    def get_setting(self, key, default=None):
        raw = self.api.getSettingJson(str(key), json.dumps(default))
        return json.loads(raw)

    def set_setting(self, key, value):
        return self.api.setSettingJson(str(key), json.dumps(value))

    def toast(self, text):
        return self.api.toast(str(text))

    def hook_screen(self, route, screen="main", mode="overlay", priority=0):
        return self.api.registerScreenHook(str(route), str(screen), str(mode), int(priority))

    def hook_surface(self, surface, screen="main", mode="overlay", priority=0):
        return self.api.registerSurfaceHook(str(surface), str(screen), str(mode), int(priority))

    def provide_service(self, service, priority=0):
        return self.api.registerService(str(service), int(priority))

    def hook_action(self, action, priority=0):
        return self.api.registerActionHook(str(action), int(priority))

    def register_ble_mode(self, mode_id, title, description="", icon="bluetooth_searching", order=0):
        return self.api.registerBleMode(str(mode_id), str(title), str(description), str(icon), int(order))

    def asset_path(self, path):
        return self.api.assetPath(str(path))

    def download_assets(self, items, on_result):
        return self.api.downloadAssets(json.dumps(list(items)), on_result)

    def apply_asset(self, asset_type, path, title=""):
        return json.loads(self.api.applyAsset(str(asset_type), str(path), str(title)))

    def load_dex_base64(self, module, encoded, sha256=""):
        return self.api.loadDexBase64(str(module), str(encoded), str(sha256))

    def load_dex_file(self, module, path, sha256=""):
        return self.api.loadDexFile(str(module), str(path), str(sha256))

    def dex_class(self, module, class_name):
        return self.api.loadDexClass(str(module), str(class_name))

    def new_dex_instance(self, module, class_name):
        return self.api.newDexInstance(str(module), str(class_name))

    def export_dex_class(self, name, module, class_name):
        return self.api.exportDexClass(str(name), str(module), str(class_name))

    def export_app_class(self, name, class_name):
        return self.api.exportAppClass(str(name), str(class_name))

    def export_java_class(self, name, java_class):
        return self.api.exportJavaClass(str(name), java_class)

    def import_java_class(self, name):
        return self.api.importJavaClass(str(name))

    def export_java_object(self, name, value):
        return self.api.exportJavaObject(str(name), value)

    def import_java_object(self, name):
        return self.api.importJavaObject(str(name))

    def java_exports(self):
        return json.loads(self.api.listJavaExports())

    def find_java_class(self, class_name):
        return self.api.findJavaClass(str(class_name))

    def get_java_field(self, target, field_name):
        return self.api.getJavaField(target, str(field_name))

    def set_java_field(self, target, field_name, value):
        return self.api.setJavaField(target, str(field_name), value)

    def invoke_java(self, target, method_name, *args):
        return self.api.invokeJava(target, str(method_name), list(args))

    def new_java_instance(self, java_class, *args):
        return self.api.newJavaInstance(java_class, list(args))

    def inspect_java_class(self, java_class):
        return json.loads(self.api.inspectJavaClass(java_class))

    def root(self, command):
        return json.loads(self.api.rootExec(str(command)))

    def shizuku(self, command):
        return json.loads(self.api.shizukuExec(str(command)))

    def shell(self, command):
        return json.loads(self.api.shellExec(str(command)))


class PluginRuntimeSession:
    def __init__(self, source, bridge, manifest_json):
        self.bridge = bridge
        self.manifest = json.loads(manifest_json)
        self.callbacks = {}
        self.callback_sequence = 0
        self.ui = UiFactory(self)
        self.namespace = {
            "BasePlugin": BasePlugin,
            "api": bridge,
            "ui": self.ui,
            "__name__": "dolphy_plugin_" + self.manifest["id"],
        }
        exec(compile(source, self.manifest["id"] + ".dolphyplugin", "exec"), self.namespace, self.namespace)
        self.plugin = self._create_plugin()
        if self.plugin is not None:
            self.plugin.api = bridge
            self.plugin.ui = self.ui

    def _create_plugin(self):
        candidates = []
        for value in self.namespace.values():
            if inspect.isclass(value) and value is not BasePlugin and issubclass(value, BasePlugin):
                candidates.append(value)
        return candidates[-1]() if candidates else None

    def _resolve(self, *names):
        for name in names:
            if self.plugin is not None:
                value = getattr(self.plugin, name, None)
                if callable(value):
                    return value
            value = self.namespace.get(name)
            if callable(value):
                return value
        return None

    def _call(self, function, *args):
        if function is None:
            return None
        signature = inspect.signature(function)
        positional = [p for p in signature.parameters.values() if p.kind in (p.POSITIONAL_ONLY, p.POSITIONAL_OR_KEYWORD)]
        if any(p.kind == p.VAR_POSITIONAL for p in signature.parameters.values()):
            return function(*args)
        return function(*args[:len(positional)])

    def register_callback(self, callback):
        self.callback_sequence += 1
        callback_id = "py_" + str(self.callback_sequence)
        self.callbacks[callback_id] = callback
        if len(self.callbacks) > 2048:
            oldest = list(self.callbacks.keys())[:1024]
            for key in oldest:
                self.callbacks.pop(key, None)
        return callback_id

    def start(self):
        function = self._resolve("on_plugin_load", "on_load", "onLoad")
        self._call(function, self.bridge)
        enabled = self._resolve("on_plugin_enable", "on_enable", "onEnable")
        if enabled is not function:
            self._call(enabled, self.bridge)

    def stop(self):
        disabled = self._resolve("on_plugin_disable", "on_disable", "onDisable")
        self._call(disabled, self.bridge)
        function = self._resolve("on_plugin_unload", "on_unload", "onUnload")
        self._call(function, self.bridge)
        self.callbacks.clear()

    def render(self, screen_id, state_json):
        state = json.loads(state_json or "{}")
        function = self._resolve("screen_" + screen_id)
        if function is None and screen_id == "main":
            function = self._resolve("create_screen", "screen_main")
        if function is None:
            return json.dumps({"type": "column", "padding": 16, "children": [{"type": "text", "text": "Экран не найден", "style": "headlineSmall"}, {"type": "text", "text": "screen_" + screen_id, "style": "bodyMedium"}]})
        result = self._call(function, self.ui, self.bridge, state)
        return json.dumps(result if result is not None else {"type": "empty"}, ensure_ascii=False)

    def invoke(self, callback_id, value_json):
        callback = self.callbacks.get(callback_id)
        if callback is None:
            return False
        value = json.loads(value_json) if value_json is not None else None
        self._call(callback, value)
        return True

    def event(self, event_name, payload):
        specific = self._resolve("on_" + event_name)
        if specific is not None:
            return self._call(specific, payload)
        generic = self._resolve("on_event")
        if generic is not None:
            return self._call(generic, event_name, payload)
        return None

    def _safe_name(self, value):
        return "".join(char if char.isalnum() else "_" for char in str(value).lower()).strip("_")

    def service(self, service_id, operation, payload_json):
        payload = json.loads(payload_json or "{}")
        safe_service = self._safe_name(service_id)
        safe_operation = self._safe_name(operation)
        function = self._resolve("service_" + safe_service + "_" + safe_operation)
        if function is None:
            function = self._resolve("on_service")
            result = self._call(function, service_id, operation, payload)
        else:
            result = self._call(function, payload)
        if result is None:
            return None
        return json.dumps(result, ensure_ascii=False)

    def action_hook(self, action, payload_json):
        payload = json.loads(payload_json or "{}")
        function = self._resolve("hook_" + self._safe_name(action))
        if function is None:
            function = self._resolve("on_action")
            result = self._call(function, action, payload)
        else:
            result = self._call(function, payload)
        if result is None:
            return None
        return json.dumps(result, ensure_ascii=False)


def create_session(source, bridge, manifest_json):
    return PluginRuntimeSession(source, bridge, manifest_json)


def format_exception(error):
    return "".join(traceback.format_exception(type(error), error, error.__traceback__))
