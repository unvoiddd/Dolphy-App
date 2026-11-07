package com.droid.dolphy.nfc

import android.app.Application
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.NdefMessage
import android.nfc.Tag
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.droid.dolphy.trackNfcRead
import com.droid.dolphy.nfc.db.NfcDatabase
import com.droid.dolphy.nfc.db.NfcScanEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class NfcArmedOperation {
    data object Erase : NfcArmedOperation()
    data class WriteNdef(val message: NdefMessage) : NfcArmedOperation()
}

sealed class NfcWriteOutcome {
    data object Idle : NfcWriteOutcome()
    data object Success : NfcWriteOutcome()
    data class Failure(val message: String) : NfcWriteOutcome()
}

class NfcViewModel(private val app: Application) : AndroidViewModel(app) {
    private val dao = NfcDatabase.get(app).scans()

    val history: StateFlow<List<NfcScanEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val openResultChannel = Channel<Long>(Channel.BUFFERED)
    val openResultEvents = openResultChannel.receiveAsFlow()

    private val _armedOperation = MutableStateFlow<NfcArmedOperation?>(null)
    val armedOperation: StateFlow<NfcArmedOperation?> = _armedOperation.asStateFlow()

    private val _writeOutcome = MutableStateFlow<NfcWriteOutcome>(NfcWriteOutcome.Idle)
    val writeOutcome: StateFlow<NfcWriteOutcome> = _writeOutcome.asStateFlow()


    private var lastWriteForRetry: NdefMessage? = null

    fun scanDetails(id: Long): Flow<NfcScanEntity?> = dao.observeById(id)

    fun armErase() {
        lastWriteForRetry = null
        _writeOutcome.value = NfcWriteOutcome.Idle
        _armedOperation.value = NfcArmedOperation.Erase
    }

    fun armWrite(message: NdefMessage) {
        lastWriteForRetry = message
        _writeOutcome.value = NfcWriteOutcome.Idle
        _armedOperation.value = NfcArmedOperation.WriteNdef(message)
    }

    fun disarmWrite() {
        _armedOperation.value = null
        _writeOutcome.value = NfcWriteOutcome.Idle
    }

    fun retryLastWrite() {
        val msg = lastWriteForRetry
        if (msg != null) {
            armWrite(msg)
        } else {
            armErase()
        }
    }

    fun clearWriteFailure() {
        if (_writeOutcome.value is NfcWriteOutcome.Failure) {
            _writeOutcome.value = NfcWriteOutcome.Idle
        }
    }

    fun handleNfcIntent(intent: Intent) {
        if (intent.action !in setOf(NfcAdapter.ACTION_TAG_DISCOVERED, NfcAdapter.ACTION_TECH_DISCOVERED, NfcAdapter.ACTION_NDEF_DISCOVERED)) return
        extractTag(intent)?.let { tag ->
            try {
                com.droid.dolphy.plugin.PluginManager.dispatchNfcTag(tag)
            } catch (t: Throwable) {
                android.util.Log.w("NfcViewModel", "plugin NFC dispatch", t)
            }
        }
        viewModelScope.launch {
            if (tryProcessWriterIntent(intent)) return@launch
            ingestReadIntent(intent)
        }
    }

    suspend fun tryProcessWriterIntent(intent: Intent): Boolean {
        val action = intent.action ?: return false
        if (action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_NDEF_DISCOVERED
        ) {
            return false
        }
        val op = _armedOperation.value ?: return false
        val tag = extractTag(intent) ?: return false

        val result = withContext(Dispatchers.IO) {
            when (op) {
                is NfcArmedOperation.Erase -> NfcNdefWriter.eraseToEmpty(tag)
                is NfcArmedOperation.WriteNdef -> NfcNdefWriter.writeNdefMessage(tag, op.message)
            }
        }
        _armedOperation.value = null
        result.onSuccess { vibrateNfcSuccess(app) }
        _writeOutcome.value = result.fold(
            onSuccess = { NfcWriteOutcome.Success },
            onFailure = { e -> NfcWriteOutcome.Failure(e.message ?: "") },
        )
        return true
    }

    private suspend fun ingestReadIntent(intent: Intent) {
        val analyzed = NfcTagAnalyzer.analyzeIntent(intent) ?: return
        vibrateNfcSuccess(app)
        val id = dao.insert(analyzed)
        trackNfcRead(app)
        openResultChannel.trySend(id)
    }

    private fun extractTag(intent: Intent): Tag? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
    }
}

class NfcViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NfcViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NfcViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
