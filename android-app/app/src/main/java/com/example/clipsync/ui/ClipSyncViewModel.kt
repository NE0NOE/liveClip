package com.example.clipsync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.clipsync.data.ClipSyncRepository
import com.example.clipsync.data.SavedComputer
import com.example.clipsync.data.ServerConfig
import com.example.clipsync.data.ServerStatus
import com.example.clipsync.data.SyncMode
import com.example.clipsync.service.ClipSyncService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SyncState {
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR
}

data class ClipItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val length: Int,
    val formattedTime: String
)

data class ClipSyncUiState(
    val text: String = "",
    val syncState: SyncState = SyncState.IDLE,
    val lastSyncMessage: String = "Listo para escribir",
    val lastSyncTimestamp: Long = 0L,
    val serverConfig: ServerConfig = ServerConfig(),
    val serverStatus: ServerStatus = ServerStatus(isOnline = false, message = "Comprobando conexión..."),
    val history: List<ClipItem> = emptyList(),
    val savedComputers: List<SavedComputer> = emptyList(),
    val isSettingsOpen: Boolean = false,
    val isHistoryOpen: Boolean = false,
    val isQrScannerOpen: Boolean = false,
    val isServiceRunning: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ClipSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClipSyncRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(ClipSyncUiState())
    val uiState: StateFlow<ClipSyncUiState> = _uiState.asStateFlow()

    private val textFlow = MutableStateFlow("")
    private var lastSentText: String = ""
    private var syncJob: Job? = null
    private var healthCheckJob: Job? = null

    init {
        val config = repository.getServerConfig()
        val computers = repository.getSavedComputers()
        _uiState.value = _uiState.value.copy(
            serverConfig = config,
            savedComputers = computers
        )

        // Observe background service running state
        viewModelScope.launch {
            ClipSyncService.isRunning.collect { running ->
                _uiState.value = _uiState.value.copy(isServiceRunning = running)
            }
        }

        checkConnection()
        startPeriodicHealthCheck()
        setupDebounceSync()
    }

    private fun setupDebounceSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState
                .flatMapLatest { state ->
                    // In LIVE_TYPING mode, ultra-low 60ms debounce for instant keystroke streaming
                    // In CLIPBOARD mode, 180ms debounce so typing automatically syncs to PC
                    val delayMs = if (state.serverConfig.syncMode == SyncMode.LIVE_TYPING) 60L else 180L
                    textFlow.debounce(delayMs)
                }
                .distinctUntilChanged()
                .collect { text ->
                    if (text != lastSentText) {
                        performSync(text)
                    }
                }
        }
    }

    fun onTextChanged(newText: String) {
        _uiState.value = _uiState.value.copy(text = newText)
        textFlow.value = newText
    }

    fun pasteFromLocalClipboard(context: android.content.Context): Boolean {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = clipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val pasted = clip.getItemAt(0).text?.toString() ?: ""
            if (pasted.isNotEmpty()) {
                onTextChanged(pasted)
                performSync(pasted)
                scheduleHistoryRecord(pasted, immediate = true)
                return true
            }
        }
        return false
    }

    fun setSyncMode(mode: SyncMode) {
        val newConfig = _uiState.value.serverConfig.copy(syncMode = mode)
        updateServerConfig(newConfig)
        lastSentText = ""
        if (_uiState.value.text.isNotEmpty()) {
            performSync(_uiState.value.text)
        }
    }

    fun updateServerConfig(config: ServerConfig) {
        repository.saveServerConfig(config)
        _uiState.value = _uiState.value.copy(serverConfig = config)
        checkConnection()
        setupDebounceSync()
    }

    fun toggleBackgroundService(enable: Boolean) {
        val context = getApplication<Application>().applicationContext
        val newConfig = _uiState.value.serverConfig.copy(backgroundServiceEnabled = enable)
        updateServerConfig(newConfig)

        if (enable) {
            ClipSyncService.startService(context)
        } else {
            ClipSyncService.stopService(context)
        }
    }

    fun openQrScanner(open: Boolean) {
        _uiState.value = _uiState.value.copy(isQrScannerOpen = open)
    }

    fun handleQrScanned(qrContent: String): Boolean {
        val parsedConfig = repository.parseQrCode(qrContent)
        return if (parsedConfig != null) {
            updateServerConfig(parsedConfig)
            _uiState.value = _uiState.value.copy(isQrScannerOpen = false)
            checkConnection()
            true
        } else {
            false
        }
    }

    fun selectSavedComputer(computer: SavedComputer) {
        val current = _uiState.value.serverConfig
        val newConfig = current.copy(host = computer.host, port = computer.port)
        repository.saveComputer(computer)
        updateServerConfig(newConfig)
        refreshSavedComputers()
    }

    fun deleteSavedComputer(id: String) {
        repository.deleteComputer(id)
        refreshSavedComputers()
    }

    private fun refreshSavedComputers() {
        val computers = repository.getSavedComputers()
        _uiState.value = _uiState.value.copy(savedComputers = computers)
    }

    fun forceSync() {
        performSync(_uiState.value.text)
    }

    fun clearEditor() {
        historyDebounceJob?.cancel()
        onTextChanged("")
    }

    fun restoreClip(text: String) {
        onTextChanged(text)
        performSync(text)
        scheduleHistoryRecord(text, immediate = true)
    }

    fun openSettings(open: Boolean) {
        if (open) refreshSavedComputers()
        _uiState.value = _uiState.value.copy(isSettingsOpen = open)
    }

    fun openHistory(open: Boolean) {
        _uiState.value = _uiState.value.copy(isHistoryOpen = open)
    }

    fun checkConnection() {
        viewModelScope.launch {
            val status = repository.checkServerStatus(_uiState.value.serverConfig)
            _uiState.value = _uiState.value.copy(serverStatus = status)
            refreshSavedComputers()
        }
    }

    private fun startPeriodicHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = viewModelScope.launch {
            while (true) {
                delay(6000)
                val status = repository.checkServerStatus(_uiState.value.serverConfig)
                _uiState.value = _uiState.value.copy(serverStatus = status)
            }
        }
    }

    private var historyDebounceJob: Job? = null

    private fun scheduleHistoryRecord(text: String, immediate: Boolean = false) {
        historyDebounceJob?.cancel()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        if (immediate) {
            recordHistoryItem(trimmed)
            return
        }

        historyDebounceJob = viewModelScope.launch {
            delay(5000L) // Wait for 5 seconds of pause after user finishes typing
            recordHistoryItem(trimmed)
        }
    }

    private fun recordHistoryItem(trimmedText: String) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val formattedTime = timeFormat.format(Date())

        val currentList = _uiState.value.history.toMutableList()
        if (currentList.isNotEmpty() && currentList[0].text == trimmedText) {
            return
        }

        currentList.add(
            0,
            ClipItem(
                text = trimmedText,
                length = trimmedText.length,
                formattedTime = formattedTime
            )
        )
        if (currentList.size > 30) {
            currentList.removeAt(currentList.lastIndex)
        }
        _uiState.value = _uiState.value.copy(history = currentList)
    }

    private fun performSync(text: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(syncState = SyncState.SYNCING)

            val config = _uiState.value.serverConfig
            val result = if (config.syncMode == SyncMode.LIVE_TYPING) {
                repository.sendDirectType(text, config)
            } else {
                repository.sendClip(text, config)
            }

            if (result.isSuccess) {
                lastSentText = text
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val formattedTime = timeFormat.format(Date())

                val modeLabel = if (config.syncMode == SyncMode.LIVE_TYPING) "Escribiendo en PC en vivo" else "Copiado a la PC"

                _uiState.value = _uiState.value.copy(
                    syncState = SyncState.SUCCESS,
                    lastSyncMessage = "$modeLabel a las $formattedTime",
                    lastSyncTimestamp = System.currentTimeMillis(),
                    serverStatus = _uiState.value.serverStatus.copy(isOnline = true)
                )

                scheduleHistoryRecord(text, immediate = false)
            } else {
                _uiState.value = _uiState.value.copy(
                    syncState = SyncState.ERROR,
                    lastSyncMessage = "Error al sincronizar: ${result.exceptionOrNull()?.message ?: "Desconectado"}",
                    serverStatus = _uiState.value.serverStatus.copy(isOnline = false)
                )
            }
        }
    }
}
