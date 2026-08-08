package com.droid.dolphy

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class FlipperWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        BleSpamRuntime.init(context)
        IrWidgetRuntime.init(context)
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        BleSpamRuntime.init(context)
        IrWidgetRuntime.init(context)
        val action = intent.action ?: return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            handleAction(context, prefs, widgetId, action)
            updateAll(context)
        }
    }

    companion object {
        private const val PREFS = "FlipperWidgetPrefs"

        private const val KEY_SCREEN = "flipper_widget_screen"
        private const val KEY_INDEX = "flipper_widget_index"
        private const val KEY_IR_MODE = "flipper_widget_ir_mode"
        private const val KEY_IR_CATEGORY = "flipper_widget_ir_category"

        const val ACTION_UP = "com.droid.dolphy.action.FLIPPER_WIDGET_UP"
        const val ACTION_DOWN = "com.droid.dolphy.action.FLIPPER_WIDGET_DOWN"
        const val ACTION_LEFT = "com.droid.dolphy.action.FLIPPER_WIDGET_LEFT"
        const val ACTION_RIGHT = "com.droid.dolphy.action.FLIPPER_WIDGET_RIGHT"
        const val ACTION_OK = "com.droid.dolphy.action.FLIPPER_WIDGET_OK"
        const val ACTION_BACK = "com.droid.dolphy.action.FLIPPER_WIDGET_BACK"

        const val ACTION_BLE_IOS = "com.droid.dolphy.action.FLIPPER_WIDGET_BLE_IOS"
        const val ACTION_BLE_SAMSUNG = "com.droid.dolphy.action.FLIPPER_WIDGET_BLE_SAMSUNG"
        const val ACTION_BLE_ANDROID = "com.droid.dolphy.action.FLIPPER_WIDGET_BLE_ANDROID"
        const val ACTION_BLE_WINDOWS = "com.droid.dolphy.action.FLIPPER_WIDGET_BLE_WINDOWS"

        private val mainItems = listOf("BLE", "IR")

        private val irMenuItems = listOf(
            "Óí. ÒÂ",
            "Óí. Audio",
            "Óí. AC",
            "Óí. Proj",
            "ÈÊ øòîðì",
            "ÈÊ ãëóøèëêà"
        )

        private val bleSections = listOf(BleSection.IOS, BleSection.SAMSUNG, BleSection.ANDROID, BleSection.WINDOWS)

        private val tvActions = listOf(
            UniversalAction("Power", "Power"),
            UniversalAction("Ch_next", "CH+"),
            UniversalAction("Ch_prev", "CH-"),
            UniversalAction("Vol_up", "VOL+"),
            UniversalAction("Vol_dn", "VOL-"),
            UniversalAction("Mute", "Mute")
        )

        private val audioActions = listOf(
            UniversalAction("Power", "Power"),
            UniversalAction("Mute", "Mute"),
            UniversalAction("Play", "Play"),
            UniversalAction("Pause", "Pause"),
            UniversalAction("Vol_up", "VOL+"),
            UniversalAction("Vol_dn", "VOL-")
        )

        private val acActions = listOf(
            UniversalAction("Off", "Off"),
            UniversalAction("Dh", "Dry"),
            UniversalAction("Cool_hi", "Cool+"),
            UniversalAction("Cool_lo", "Cool-"),
            UniversalAction("Heat_hi", "Heat+"),
            UniversalAction("Heat_lo", "Heat-")
        )

        private val projectorActions = listOf(
            UniversalAction("Power", "Power"),
            UniversalAction("Mute", "Mute"),
            UniversalAction("Vol_up", "VOL+"),
            UniversalAction("Vol_dn", "VOL-"),
            UniversalAction("Ch_next", "CH+"),
            UniversalAction("Ch_prev", "CH-")
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, FlipperWidgetProvider::class.java)
            )
            updateAll(context, manager, ids)
        }

        fun updateAll(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val accentColor = context.getSharedPreferences("DolphyPrefs", Context.MODE_PRIVATE)
                .getInt("accent_color", 0xFFF98022.toInt())
            val inactiveColor = Color.parseColor("#7A7A7A")

            appWidgetIds.forEach { id ->
                val screen = getScreen(prefs, id)
                val index = getIndex(prefs, id)
                val irMode = getIrMode(prefs, id)
                val irCategory = getIrCategory(prefs, id)
                val views = RemoteViews(context.packageName, R.layout.flipper_widget)


                views.setImageViewResource(R.id.ble_item_ios, R.drawable.ble_logo_ios_white)
                views.setImageViewResource(R.id.ble_item_samsung, R.drawable.ble_widget_samsung)
                views.setImageViewResource(R.id.ble_item_android, R.drawable.ble_widget_android)
                views.setImageViewResource(R.id.ble_item_windows, R.drawable.ble_widget_windows)


                views.setViewVisibility(R.id.screen_main, if (screen == WidgetScreen.MAIN) android.view.View.VISIBLE else android.view.View.GONE)
                views.setViewVisibility(R.id.screen_ble, if (screen == WidgetScreen.BLE) android.view.View.VISIBLE else android.view.View.GONE)
                views.setViewVisibility(R.id.screen_ir, if (screen == WidgetScreen.IR) android.view.View.VISIBLE else android.view.View.GONE)


                views.setTextColor(R.id.main_item_ble, if (screen == WidgetScreen.MAIN && index == 0) accentColor else Color.WHITE)
                views.setTextColor(R.id.main_item_ir, if (screen == WidgetScreen.MAIN && index == 1) accentColor else Color.WHITE)


                val activeStates = bleSections.map { BleSpamRuntime.isSectionActive(it) }
                val bleIds = listOf(R.id.ble_item_ios, R.id.ble_item_samsung, R.id.ble_item_android, R.id.ble_item_windows)
                bleIds.forEachIndexed { i, viewId ->
                    views.setInt(viewId, "setColorFilter", if (activeStates[i]) accentColor else inactiveColor)
                    val bg = if (screen == WidgetScreen.BLE && index == i) {
                        R.drawable.widget_item_bg_selected
                    } else {
                        R.drawable.widget_item_bg
                    }
                    views.setInt(viewId, "setBackgroundResource", bg)
                }


                val irIds = listOf(
                    R.id.ir_item_1,
                    R.id.ir_item_2,
                    R.id.ir_item_3,
                    R.id.ir_item_4,
                    R.id.ir_item_5,
                    R.id.ir_item_6
                )

                if (screen == WidgetScreen.IR && irMode == IrMode.MENU) {
                    views.setTextViewText(R.id.ir_header_title, "IR ìåíþ")
                    irIds.forEachIndexed { i, viewId ->
                        val label = irMenuItems.getOrNull(i).orEmpty()
                        views.setTextViewText(viewId, label)
                        views.setViewVisibility(viewId, android.view.View.VISIBLE)
                        val isSelected = index == i
                        val isStorm = i == 4 && IrWidgetRuntime.isStormRunning()
                        val isJammer = i == 5 && IrWidgetRuntime.isJammerRunning()
                        val color = when {
                            isSelected -> accentColor
                            isStorm || isJammer -> accentColor
                            else -> Color.WHITE
                        }
                        views.setTextColor(viewId, color)
                    }
                } else if (screen == WidgetScreen.IR) {
                    val title = when (irCategory) {
                        UniversalCategory.TV -> "Óí. ÒÂ"
                        UniversalCategory.AUDIO -> "Óí. Audio"
                        UniversalCategory.AC -> "Óí. AC"
                        UniversalCategory.PROJECTOR -> "Óí. Proj"
                    }
                    views.setTextViewText(R.id.ir_header_title, title)
                    val actions = actionsForCategory(irCategory)
                    irIds.forEachIndexed { i, viewId ->
                        val action = actions.getOrNull(i)
                        if (action == null) {
                            views.setTextViewText(viewId, "")
                            views.setViewVisibility(viewId, android.view.View.GONE)
                        } else {
                            views.setTextViewText(viewId, action.displayName)
                            views.setViewVisibility(viewId, android.view.View.VISIBLE)
                            views.setTextColor(viewId, if (index == i) accentColor else Color.WHITE)
                        }
                    }
                }


                views.setOnClickPendingIntent(R.id.btn_up, buildPendingIntent(context, ACTION_UP, id, 1))
                views.setOnClickPendingIntent(R.id.btn_down, buildPendingIntent(context, ACTION_DOWN, id, 2))
                views.setOnClickPendingIntent(R.id.btn_left, buildPendingIntent(context, ACTION_LEFT, id, 3))
                views.setOnClickPendingIntent(R.id.btn_right, buildPendingIntent(context, ACTION_RIGHT, id, 4))
                views.setOnClickPendingIntent(R.id.btn_ok, buildPendingIntent(context, ACTION_OK, id, 5))
                views.setOnClickPendingIntent(R.id.btn_back, buildPendingIntent(context, ACTION_BACK, id, 6))


                views.setOnClickPendingIntent(R.id.ble_item_ios, buildPendingIntent(context, ACTION_BLE_IOS, id, 7))
                views.setOnClickPendingIntent(R.id.ble_item_samsung, buildPendingIntent(context, ACTION_BLE_SAMSUNG, id, 8))
                views.setOnClickPendingIntent(R.id.ble_item_android, buildPendingIntent(context, ACTION_BLE_ANDROID, id, 9))
                views.setOnClickPendingIntent(R.id.ble_item_windows, buildPendingIntent(context, ACTION_BLE_WINDOWS, id, 10))

                manager.updateAppWidget(id, views)
            }
        }

        private fun buildPendingIntent(context: Context, action: String, widgetId: Int, requestCode: Int): PendingIntent {
            val intent = Intent(context, FlipperWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            return PendingIntent.getBroadcast(
                context,
                widgetId * 100 + requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun handleAction(context: Context, prefs: android.content.SharedPreferences, widgetId: Int, action: String) {
            var screen = getScreen(prefs, widgetId)
            var index = getIndex(prefs, widgetId)
            var irMode = getIrMode(prefs, widgetId)
            var irCategory = getIrCategory(prefs, widgetId)

            when (action) {
                ACTION_UP -> index = moveUp(screen, irMode, index)
                ACTION_DOWN -> index = moveDown(screen, irMode, irCategory, index)
                ACTION_LEFT -> index = moveLeft(screen, irMode, index)
                ACTION_RIGHT -> index = moveRight(screen, irMode, index)
                ACTION_BACK -> {
                    if (screen == WidgetScreen.IR && irMode == IrMode.UNIVERSAL) {
                        irMode = IrMode.MENU
                        index = 0
                    } else {
                        screen = WidgetScreen.MAIN
                        index = 0
                    }
                }
                ACTION_OK -> {
                    when (screen) {
                        WidgetScreen.MAIN -> {
                            screen = if (index == 0) WidgetScreen.BLE else WidgetScreen.IR
                            index = 0
                        }
                        WidgetScreen.BLE -> toggleBleByIndex(index)
                        WidgetScreen.IR -> {
                            if (irMode == IrMode.MENU) {
                                when (index) {
                                    0 -> {
                                        irMode = IrMode.UNIVERSAL
                                        irCategory = UniversalCategory.TV
                                        index = 0
                                    }
                                    1 -> {
                                        irMode = IrMode.UNIVERSAL
                                        irCategory = UniversalCategory.AUDIO
                                        index = 0
                                    }
                                    2 -> {
                                        irMode = IrMode.UNIVERSAL
                                        irCategory = UniversalCategory.AC
                                        index = 0
                                    }
                                    3 -> {
                                        irMode = IrMode.UNIVERSAL
                                        irCategory = UniversalCategory.PROJECTOR
                                        index = 0
                                    }
                                    4 -> IrWidgetRuntime.toggleStorm()
                                    5 -> IrWidgetRuntime.toggleJammer()
                                }
                            } else {
                                val actions = actionsForCategory(irCategory)
                                val actionItem = actions.getOrNull(index)
                                if (actionItem != null) {
                                    IrWidgetRuntime.sendUniversal(irCategory, actionItem)
                                }
                            }
                        }
                    }
                }
                ACTION_BLE_IOS -> toggleBleByIndex(0)
                ACTION_BLE_SAMSUNG -> toggleBleByIndex(1)
                ACTION_BLE_ANDROID -> toggleBleByIndex(2)
                ACTION_BLE_WINDOWS -> toggleBleByIndex(3)
            }

            saveState(prefs, widgetId, screen, index, irMode, irCategory)
        }

        private fun moveUp(screen: WidgetScreen, irMode: IrMode, index: Int): Int = when (screen) {
            WidgetScreen.MAIN -> (index - 1).coerceAtLeast(0)
            WidgetScreen.IR -> if (irMode == IrMode.MENU) (index - 1).coerceAtLeast(0) else (index - 1).coerceAtLeast(0)
            WidgetScreen.BLE -> if (index >= 2) index - 2 else index
        }

        private fun moveDown(screen: WidgetScreen, irMode: IrMode, irCategory: UniversalCategory, index: Int): Int = when (screen) {
            WidgetScreen.MAIN -> (index + 1).coerceAtMost(mainItems.lastIndex)
            WidgetScreen.IR -> {
                val max = if (irMode == IrMode.MENU) irMenuItems.lastIndex else actionsForCategory(irCategory).lastIndex
                (index + 1).coerceAtMost(max)
            }
            WidgetScreen.BLE -> if (index <= 1) index + 2 else index
        }

        private fun moveLeft(screen: WidgetScreen, irMode: IrMode, index: Int): Int = when (screen) {
            WidgetScreen.BLE -> if (index == 1 || index == 3) index - 1 else index
            else -> index
        }

        private fun moveRight(screen: WidgetScreen, irMode: IrMode, index: Int): Int = when (screen) {
            WidgetScreen.BLE -> if (index == 0 || index == 2) index + 1 else index
            else -> index
        }

        private fun toggleBleByIndex(index: Int) {
            val section = bleSections.getOrNull(index) ?: return
            BleSpamRuntime.toggleSection(section)
        }

        private fun getScreen(prefs: android.content.SharedPreferences, widgetId: Int): WidgetScreen {
            val value = prefs.getString("${KEY_SCREEN}_$widgetId", WidgetScreen.MAIN.name)
            return runCatching { WidgetScreen.valueOf(value ?: WidgetScreen.MAIN.name) }
                .getOrDefault(WidgetScreen.MAIN)
        }

        private fun getIndex(prefs: android.content.SharedPreferences, widgetId: Int): Int {
            return prefs.getInt("${KEY_INDEX}_$widgetId", 0)
        }

        private fun getIrMode(prefs: android.content.SharedPreferences, widgetId: Int): IrMode {
            val value = prefs.getString("${KEY_IR_MODE}_$widgetId", IrMode.MENU.name)
            return runCatching { IrMode.valueOf(value ?: IrMode.MENU.name) }
                .getOrDefault(IrMode.MENU)
        }

        private fun getIrCategory(prefs: android.content.SharedPreferences, widgetId: Int): UniversalCategory {
            val value = prefs.getString("${KEY_IR_CATEGORY}_$widgetId", UniversalCategory.TV.name)
            return runCatching { UniversalCategory.valueOf(value ?: UniversalCategory.TV.name) }
                .getOrDefault(UniversalCategory.TV)
        }

        private fun saveState(
            prefs: android.content.SharedPreferences,
            widgetId: Int,
            screen: WidgetScreen,
            index: Int,
            irMode: IrMode,
            irCategory: UniversalCategory
        ) {
            prefs.edit()
                .putString("${KEY_SCREEN}_$widgetId", screen.name)
                .putInt("${KEY_INDEX}_$widgetId", index)
                .putString("${KEY_IR_MODE}_$widgetId", irMode.name)
                .putString("${KEY_IR_CATEGORY}_$widgetId", irCategory.name)
                .apply()
        }

        private fun actionsForCategory(category: UniversalCategory): List<UniversalAction> {
            return when (category) {
                UniversalCategory.TV -> tvActions
                UniversalCategory.AUDIO -> audioActions
                UniversalCategory.AC -> acActions
                UniversalCategory.PROJECTOR -> projectorActions
            }
        }

    }
}

private enum class WidgetScreen {
    MAIN,
    BLE,
    IR
}

private enum class IrMode {
    MENU,
    UNIVERSAL
}

