package com.droid.dolphy.plugin.python

import com.droid.dolphy.plugin.model.UiNode
import org.json.JSONArray
import org.json.JSONObject

object PythonUiParser {
    fun parse(raw: String): UiNode = parseNode(JSONObject(raw))

    private fun parseNode(o: JSONObject?): UiNode {
        if (o == null) return UiNode.Empty
        return when (o.optString("type")) {
            "scaffold" -> UiNode.Scaffold(
                topBar = parseTopBar(o.optJSONObject("topBar")),
                content = parseNode(o.optJSONObject("content")),
                fab = o.optJSONObject("fab")?.let(::parseNode),
            )
            "column" -> UiNode.Column(children(o), f(o, "padding"), f(o, "spacing", 8f), b(o, "fillMaxSize"))
            "row" -> UiNode.Row(children(o), f(o, "padding"), f(o, "spacing", 8f), b(o, "fillMaxWidth", true))
            "box" -> UiNode.Box(children(o), f(o, "padding"), b(o, "fillMaxSize"))
            "lazyColumn" -> UiNode.LazyColumn(children(o), f(o, "padding"), f(o, "spacing", 4f), b(o, "fillMaxSize", true))
            "lazyRow" -> UiNode.LazyRow(children(o), f(o, "padding"), f(o, "spacing", 8f), b(o, "fillMaxWidth", true))
            "text" -> UiNode.Text(o.optString("text"), o.optString("style", "bodyMedium"), nullable(o, "color"), o.optInt("maxLines", Int.MAX_VALUE))
            "button" -> UiNode.Button(o.optString("text"), nullable(o, "onClickId"), o.optString("style", "filled"), b(o, "enabled", true), b(o, "fillMaxWidth"))
            "bounceButton" -> UiNode.BounceButton(o.optString("text"), nullable(o, "onClickId"), b(o, "enabled", true), b(o, "fillMaxWidth", true))
            "splitButton" -> UiNode.SplitButton(o.optString("primaryText"), nullable(o, "onPrimaryId"), nullable(o, "onSecondaryId"), b(o, "enabled", true), b(o, "fillMaxWidth", true))
            "iconButton" -> UiNode.IconButton(o.optString("icon", "extension"), nullable(o, "onClickId"), b(o, "enabled", true))
            "textField" -> UiNode.TextField(o.optString("value"), nullable(o, "onChangeId"), o.optString("label"), b(o, "singleLine", true), b(o, "fillMaxWidth", true))
            "switch" -> UiNode.Switch(b(o, "checked"), nullable(o, "onChangeId"), o.optString("title"), o.optString("subtitle"), b(o, "enabled", true))
            "checkbox" -> UiNode.Checkbox(b(o, "checked"), nullable(o, "onChangeId"), o.optString("title"), o.optString("subtitle"), b(o, "enabled", true))
            "slider" -> UiNode.Slider(f(o, "value"), nullable(o, "onChangeId"), f(o, "min"), f(o, "max", 100f), o.optString("title"), o.optInt("steps"))
            "linearProgress" -> UiNode.LinearProgress(nullableFloat(o, "progress"), b(o, "fillMaxWidth", true))
            "circularProgress" -> UiNode.CircularProgress(nullableFloat(o, "progress"))
            "wavyProgress" -> UiNode.WavyProgress(nullableFloat(o, "progress"), f(o, "size", 64f))
            "divider" -> UiNode.Divider(b(o, "vertical"))
            "spacer" -> UiNode.Spacer(f(o, "height", 8f), f(o, "width"))
            "icon" -> UiNode.Icon(o.optString("name", "extension"), f(o, "size", 24f), nullable(o, "tint"))
            "image" -> UiNode.Image(o.optString("source"), nullableFloat(o, "width"), nullableFloat(o, "height"), o.optString("scale", "fit"), f(o, "cornerRadius"), b(o, "fillMaxWidth"), o.optString("contentDescription"))
            "chip" -> UiNode.Chip(o.optString("text"), b(o, "selected"), nullable(o, "onClickId"))
            "materialCard" -> UiNode.MaterialCard(children(o), f(o, "contentPadding", 16f), nullableInt(o, "segmentedIndex"), nullableInt(o, "segmentedCount"), nullable(o, "onClickId"))
            "functionRow" -> UiNode.FunctionRow(o.optString("title"), o.optString("description"), o.optString("icon", "extension"), nullable(o, "iconTint"), nullable(o, "onClickId"))
            "segmentedList" -> UiNode.SegmentedList(children(o), f(o, "spacing", 4f))
            "settingsRow" -> UiNode.SettingsRow(o.optString("title"), o.optString("subtitle"), nullable(o, "icon"), nullable(o, "onClickId"), o.optJSONObject("trailing")?.let(::parseNode))
            "tabRow" -> UiNode.TabRow(strings(o.optJSONArray("tabs")), o.optInt("selectedIndex"), nullable(o, "onSelectId"))
            "connectedButtonGroup" -> UiNode.ConnectedButtonGroup(options(o), o.optString("selectedValue"), nullable(o, "onSelectId"))
            "floatingToolbar" -> UiNode.FloatingToolbar(toolbarItems(o.optJSONArray("items")))
            "radioGroup" -> UiNode.RadioGroup(options(o), o.optString("selectedValue"), nullable(o, "onSelectId"), o.optString("title"))
            "dropdown" -> UiNode.Dropdown(options(o), o.optString("selectedValue"), nullable(o, "onSelectId"), o.optString("label"), b(o, "enabled", true))
            "webView" -> UiNode.WebView(o.optString("url"), o.optString("html"), b(o, "fillMaxSize", true), f(o, "height", 320f))
            "logPanel" -> UiNode.LogPanel(o.optString("text"), f(o, "maxHeight", 200f))
            "alertDialog" -> UiNode.AlertDialog(b(o, "show"), o.optString("title"), o.optString("message"), o.optString("confirmText", "OK"), o.optString("dismissText", "Отмена"), nullable(o, "onConfirmId"), nullable(o, "onDismissId"), cancelable = b(o, "cancelable", true))
            else -> UiNode.Empty
        }
    }

    private fun parseTopBar(o: JSONObject?): UiNode.TopBar? {
        if (o == null) return null
        val actions = mutableListOf<UiNode.TopBarAction>()
        val array = o.optJSONArray("actions") ?: JSONArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            actions += UiNode.TopBarAction(item.optString("icon", "extension"), nullable(item, "onClickId"))
        }
        return UiNode.TopBar(o.optString("title"), b(o, "showBack", true), actions)
    }

    private fun children(o: JSONObject): List<UiNode> {
        val result = mutableListOf<UiNode>()
        val array = o.optJSONArray("children") ?: JSONArray()
        for (i in 0 until array.length()) array.optJSONObject(i)?.let { result += parseNode(it) }
        return result
    }

    private fun options(o: JSONObject): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val array = o.optJSONArray("options") ?: JSONArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONArray(i) ?: continue
            result += item.optString(0) to item.optString(1)
        }
        return result
    }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return List(array.length()) { array.optString(it) }
    }

    private fun toolbarItems(array: JSONArray?): List<UiNode.ToolbarItem> {
        if (array == null) return emptyList()
        val result = mutableListOf<UiNode.ToolbarItem>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result += UiNode.ToolbarItem(item.optString("icon", "extension"), b(item, "selected"), nullable(item, "onClickId"), item.optString("label"))
        }
        return result
    }

    private fun nullable(o: JSONObject, key: String): String? = if (!o.has(key) || o.isNull(key)) null else o.optString(key)
    private fun nullableFloat(o: JSONObject, key: String): Float? = if (!o.has(key) || o.isNull(key)) null else o.optDouble(key).toFloat()
    private fun nullableInt(o: JSONObject, key: String): Int? = if (!o.has(key) || o.isNull(key)) null else o.optInt(key)
    private fun f(o: JSONObject, key: String, fallback: Float = 0f): Float = o.optDouble(key, fallback.toDouble()).toFloat()
    private fun b(o: JSONObject, key: String, fallback: Boolean = false): Boolean = o.optBoolean(key, fallback)
}
