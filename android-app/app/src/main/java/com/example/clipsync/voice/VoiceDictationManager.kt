package com.example.clipsync.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class VoiceEvent {
    data class PartialResult(val text: String) : VoiceEvent()
    data class FinalResult(val text: String) : VoiceEvent()
    data class Error(val errorCode: Int, val message: String) : VoiceEvent()
    object Ready : VoiceEvent()
    object EndOfSpeech : VoiceEvent()
}

class VoiceDictationManager(private val context: Context) {

    private val tag = "VoiceDictationManager"
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _voiceEvents = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 64)
    val voiceEvents: SharedFlow<VoiceEvent> = _voiceEvents.asSharedFlow()

    var isContinuous: Boolean = true
    private var shouldKeepListening: Boolean = false
    private var isRestarting: Boolean = false

    companion object {
        fun isRecognitionAvailable(context: Context): Boolean {
            return SpeechRecognizer.isRecognitionAvailable(context)
        }
    }

    private fun ensureRecognizer() {
        if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(tag, "onReadyForSpeech")
                isRestarting = false
                _isListening.value = true
                _voiceEvents.tryEmit(VoiceEvent.Ready)
            }

            override fun onBeginningOfSpeech() {
                Log.d(tag, "onBeginningOfSpeech")
                _isListening.value = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                // rmsdB typically -2 to 10
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _rmsLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(tag, "onEndOfSpeech - processing audio")
                _voiceEvents.tryEmit(VoiceEvent.EndOfSpeech)
                // DO NOT restart here! onEndOfSpeech is immediately followed by onResults or onError.
                // Restarting or cancelling here would abort the pending recognition result.
            }

            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Error de audio del micrófono"
                    SpeechRecognizer.ERROR_CLIENT -> "Cliente ocupado o cancelado"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permiso de micrófono no otorgado"
                    SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo de red agotado"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Pausa detectada"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                    SpeechRecognizer.ERROR_SERVER -> "Error en servidor de reconocimiento"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silencio detectado"
                    else -> "Error ($error)"
                }
                Log.d(tag, "SpeechRecognizer onError: $error - $errorMsg, shouldKeepListening=$shouldKeepListening, isContinuous=$isContinuous")

                if (shouldKeepListening && isContinuous) {
                    // Common non-fatal pauses: NO_MATCH (silence), SPEECH_TIMEOUT, or transient CLIENT busy
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_CLIENT -> {
                            restartListeningWithDelay(250L)
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            recreateAndRestart(350L)
                        }
                        else -> {
                            _voiceEvents.tryEmit(VoiceEvent.Error(error, errorMsg))
                            restartListeningWithDelay(500L)
                        }
                    }
                } else {
                    _voiceEvents.tryEmit(VoiceEvent.Error(error, errorMsg))
                    if (!shouldKeepListening) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull() ?: ""
                Log.d(tag, "onResults: $recognizedText")
                if (recognizedText.isNotBlank()) {
                    _voiceEvents.tryEmit(VoiceEvent.FinalResult(recognizedText))
                }

                if (shouldKeepListening && isContinuous) {
                    restartListeningWithDelay(150L)
                } else {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull() ?: ""
                if (recognizedText.isNotBlank()) {
                    _voiceEvents.tryEmit(VoiceEvent.PartialResult(recognizedText))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun buildRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            val locale = Locale.getDefault()
            val langTag = if (locale.language.isNotBlank()) locale.toLanguageTag() else "es-ES"
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
    }

    fun startListening() {
        mainHandler.post {
            shouldKeepListening = true
            _isListening.value = true
            try {
                if (speechRecognizer == null) {
                    ensureRecognizer()
                }
                speechRecognizer?.startListening(buildRecognizerIntent())
            } catch (e: Exception) {
                Log.e(tag, "Error starting speech recognition: ${e.message}", e)
                recreateAndRestart(200L)
            }
        }
    }

    private fun restartListeningWithDelay(delayMs: Long = 200L) {
        if (!shouldKeepListening || isRestarting) return
        isRestarting = true
        mainHandler.postDelayed({
            if (shouldKeepListening) {
                try {
                    speechRecognizer?.startListening(buildRecognizerIntent())
                } catch (e: Exception) {
                    Log.w(tag, "Restart failed, recreating recognizer: ${e.message}")
                    recreateAndRestart(150L)
                } finally {
                    isRestarting = false
                }
            } else {
                isRestarting = false
            }
        }, delayMs)
    }

    private fun recreateAndRestart(delayMs: Long = 200L) {
        mainHandler.postDelayed({
            if (shouldKeepListening) {
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (ignored: Exception) {}
                speechRecognizer = null

                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createListener())
                    }
                    try {
                        speechRecognizer?.startListening(buildRecognizerIntent())
                        _isListening.value = true
                    } catch (e: Exception) {
                        Log.e(tag, "Recreated startListening failed: ${e.message}")
                    }
                }
            }
            isRestarting = false
        }, delayMs)
    }

    fun stopListening() {
        shouldKeepListening = false
        isRestarting = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(tag, "Error stopping speech recognition: ${e.message}")
            }
            _isListening.value = false
            _rmsLevel.value = 0f
        }
    }

    fun destroyRecognizer() {
        shouldKeepListening = false
        isRestarting = false
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e(tag, "Error destroying speech recognizer: ${e.message}")
            }
            speechRecognizer = null
            _isListening.value = false
            _rmsLevel.value = 0f
        }
    }
}
