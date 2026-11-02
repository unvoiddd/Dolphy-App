package com.droid.dolphy.plugin

import com.droid.dolphy.plugin.model.OtherCardContribution
import com.droid.dolphy.plugin.model.PluginActionHookContribution
import com.droid.dolphy.plugin.model.PluginScreenContribution
import com.droid.dolphy.plugin.model.PluginServiceContribution
import com.droid.dolphy.plugin.model.SettingsSectionContribution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object PluginRegistry {
    private val _otherCards = MutableStateFlow<List<OtherCardContribution>>(emptyList())
    val otherCards: StateFlow<List<OtherCardContribution>> = _otherCards.asStateFlow()

    private val _settingsSections = MutableStateFlow<List<SettingsSectionContribution>>(emptyList())
    val settingsSections: StateFlow<List<SettingsSectionContribution>> = _settingsSections.asStateFlow()

    private val _screenContributions = MutableStateFlow<List<PluginScreenContribution>>(emptyList())
    val screenContributions: StateFlow<List<PluginScreenContribution>> = _screenContributions.asStateFlow()

    private val _services = MutableStateFlow<List<PluginServiceContribution>>(emptyList())
    val services: StateFlow<List<PluginServiceContribution>> = _services.asStateFlow()

    private val _actionHooks = MutableStateFlow<List<PluginActionHookContribution>>(emptyList())
    val actionHooks: StateFlow<List<PluginActionHookContribution>> = _actionHooks.asStateFlow()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun clearPlugin(pluginId: String) {
        PluginBleModeRegistry.clearPlugin(pluginId)
        _otherCards.update { list -> list.filterNot { it.pluginId == pluginId } }
        _settingsSections.update { list -> list.filterNot { it.pluginId == pluginId } }
        _screenContributions.update { list -> list.filterNot { it.pluginId == pluginId } }
        _services.update { list -> list.filterNot { it.pluginId == pluginId } }
        _actionHooks.update { list -> list.filterNot { it.pluginId == pluginId } }
        bump()
    }

    fun clearAll() {
        PluginBleModeRegistry.clearAll()
        _otherCards.value = emptyList()
        _settingsSections.value = emptyList()
        _screenContributions.value = emptyList()
        _services.value = emptyList()
        _actionHooks.value = emptyList()
        bump()
    }

    fun addOtherCard(card: OtherCardContribution) {
        val savedSection = prefs?.getString("${card.pluginId}:${card.screenId}:${card.title}:section", null)
        val savedOrder = prefs?.getInt("${card.pluginId}:${card.screenId}:${card.title}:order", -1) ?: -1
        val finalCard = if (savedSection != null) {
            card.copy(section = savedSection, order = if (savedOrder >= 0) savedOrder else card.order)
        } else {
            card
        }
        _otherCards.update { list ->
            list.filterNot {
                it.pluginId == card.pluginId && it.screenId == card.screenId && it.title == card.title
            } + finalCard
        }
        bump()
    }

    fun addSettingsSection(section: SettingsSectionContribution) {
        _settingsSections.update { list ->
            list.filterNot { it.pluginId == section.pluginId && it.title == section.title } + section
        }
        bump()
    }

    fun addScreenContribution(contribution: PluginScreenContribution) {
        _screenContributions.update { list ->
            list.filterNot {
                it.pluginId == contribution.pluginId &&
                    it.routePattern == contribution.routePattern &&
                    it.screenId == contribution.screenId &&
                    it.mode == contribution.mode
            } + contribution
        }
        bump()
    }

    fun addService(contribution: PluginServiceContribution) {
        _services.update { list ->
            list.filterNot {
                it.pluginId == contribution.pluginId && it.serviceId == contribution.serviceId
            } + contribution
        }
        bump()
    }

    fun addActionHook(contribution: PluginActionHookContribution) {
        _actionHooks.update { list ->
            list.filterNot {
                it.pluginId == contribution.pluginId && it.actionPattern == contribution.actionPattern
            } + contribution
        }
        bump()
    }

    fun touch() {
        bump()
    }

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: android.content.Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences("plugin_card_placements", android.content.Context.MODE_PRIVATE)
        }
    }

    fun moveOtherCard(
        pluginId: String,
        screenId: String,
        title: String,
        targetSection: String,
        newIndex: Int = -1,
        context: android.content.Context? = null
    ) {
        if (context != null) init(context)
        _otherCards.update { list ->
            val card = list.firstOrNull { it.pluginId == pluginId && it.screenId == screenId && it.title == title }
                ?: return@update list
            val updated = card.copy(section = targetSection)
            val withoutCard = list.filterNot { it.pluginId == pluginId && it.screenId == screenId && it.title == title }
            val inTarget = withoutCard.filter { it.section == targetSection }.toMutableList()
            val outsideTarget = withoutCard.filter { it.section != targetSection }
            val insertAt = if (newIndex in 0..inTarget.size) newIndex else inTarget.size
            inTarget.add(insertAt, updated)
            val reordered = inTarget.mapIndexed { idx, c -> c.copy(order = idx) }

            prefs?.edit()?.let { editor ->
                editor.putString("${pluginId}:${screenId}:${title}:section", targetSection)
                editor.putInt("${pluginId}:${screenId}:${title}:order", insertAt)
                editor.apply()
            }

            outsideTarget + reordered
        }
        bump()
    }

    fun otherBySection(): Map<String, List<OtherCardContribution>> {
        return _otherCards.value
            .sortedBy { it.order }
            .groupBy { it.section }
    }

    private fun bump() {
        _revision.update { it + 1 }
    }
}

