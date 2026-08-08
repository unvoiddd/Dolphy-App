package com.droid.dolphy.plugin

import com.droid.dolphy.plugin.model.OtherCardContribution
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

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun clearPlugin(pluginId: String) {
        _otherCards.update { list -> list.filterNot { it.pluginId == pluginId } }
        _settingsSections.update { list -> list.filterNot { it.pluginId == pluginId } }
        bump()
    }

    fun clearAll() {
        _otherCards.value = emptyList()
        _settingsSections.value = emptyList()
        bump()
    }

    fun addOtherCard(card: OtherCardContribution) {
        _otherCards.update { list ->
            list.filterNot {
                it.pluginId == card.pluginId && it.screenId == card.screenId && it.title == card.title
            } + card
        }
        bump()
    }

    fun addSettingsSection(section: SettingsSectionContribution) {
        _settingsSections.update { list ->
            list.filterNot { it.pluginId == section.pluginId && it.title == section.title } + section
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

