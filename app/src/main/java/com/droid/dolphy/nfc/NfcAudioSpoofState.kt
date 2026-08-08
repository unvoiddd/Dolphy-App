package com.droid.dolphy.nfc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object NfcAudioSpoofState {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    private val _spoofUrl = MutableStateFlow<String?>(null)
    val spoofUrl: StateFlow<String?> = _spoofUrl

    fun startSpoofing(url: String) {
        _spoofUrl.value = url
        _isActive.value = true
    }

    fun stopSpoofing() {
        _isActive.value = false
        _spoofUrl.value = null
    }

    fun updateUrl(url: String) {
        _spoofUrl.value = url
    }
}

