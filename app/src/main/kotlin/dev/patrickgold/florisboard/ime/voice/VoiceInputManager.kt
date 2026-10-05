/*
 * Copyright (C) 2025-2026 The FlorisBoard Contributors / Foldboard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.FlorisImeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceInputManager(private val context: Context) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _statusText = MutableStateFlow("Tap mic to speak")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val byokEngine = ByokAiEngine(context)

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureRecognizer(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _statusText.value = "Speech recognition not available on device"
            return false
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@VoiceInputManager)
            }
        }
        return true
    }

    fun startListening() {
        if (!hasRecordPermission()) {
            _statusText.value = "Microphone permission needed (grant in Settings)"
            return
        }
        if (!ensureRecognizer()) return

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            }
            speechRecognizer?.startListening(intent)
            _isListening.value = true
            _statusText.value = "Listening..."
        } catch (e: Exception) {
            e.printStackTrace()
            _statusText.value = "Error starting voice recognition"
            _isListening.value = false
        }
    }

    fun stopListening() {
        _isListening.value = false
        _rmsDb.value = 0f
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleListening() {
        if (_isListening.value) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun clearTranscript() {
        _transcript.value = ""
        _partialText.value = ""
        _statusText.value = "Cleared"
    }

    fun applyAiPolish(onFinished: ((String) -> Unit)? = null) {
        val current = _transcript.value.ifBlank { _partialText.value }
        if (current.isBlank()) return

        _statusText.value = "Polishing with AI..."
        byokEngine.processText(current) { polished ->
            _transcript.value = polished
            _partialText.value = ""
            _statusText.value = "AI Formatted"
            onFinished?.invoke(polished)
        }
    }

    fun commitToInputConnection(autoPolish: Boolean = true): String {
        stopListening()
        var text = _transcript.value.ifBlank { _partialText.value }.trim()
        if (text.isBlank()) return ""

        if (autoPolish) {
            text = LocalAiEngine.formatSpeech(text)
        }

        val ic = FlorisImeService.currentInputConnection()
        ic?.commitText(text, 1)

        // Also sync to AVF
        AvfBridge.sendVoiceToAvf(text)

        _transcript.value = ""
        _partialText.value = ""
        _statusText.value = "✓ Inserted"
        return text
    }

    fun sendToAvf(): Boolean {
        stopListening()
        val text = _transcript.value.ifBlank { _partialText.value }.trim()
        if (text.isBlank()) return false
        val formatted = LocalAiEngine.formatSpeech(text)
        val success = AvfBridge.sendVoiceToAvf(formatted)
        if (success) {
            _transcript.value = ""
            _partialText.value = ""
            _statusText.value = "✓ Sent to AVF Herdr"
        } else {
            _statusText.value = "Failed to write to .voice_in.txt"
        }
        return success
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        speechRecognizer = null
    }

    // RecognitionListener callbacks
    override fun onReadyForSpeech(params: Bundle?) {
        _statusText.value = "Listening..."
    }

    override fun onBeginningOfSpeech() {
        _statusText.value = "Speaking..."
    }

    override fun onRmsChanged(rmsdB: Float) {
        _rmsDb.value = rmsdB.coerceAtLeast(0f)
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _isListening.value = false
        _statusText.value = "Processing speech..."
        _rmsDb.value = 0f
    }

    override fun onError(error: Int) {
        _isListening.value = false
        _rmsDb.value = 0f
        val msg = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
            else -> "Speech recognition error ($error)"
        }
        _statusText.value = msg
    }

    override fun onResults(results: Bundle?) {
        _isListening.value = false
        _rmsDb.value = 0f
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val recognized = matches[0]
            val formatted = LocalAiEngine.formatSpeech(recognized)
            _transcript.value = if (_transcript.value.isBlank()) {
                formatted
            } else {
                "${_transcript.value} $formatted"
            }
            _partialText.value = ""
            _statusText.value = "Tap insert or mic to continue"
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            _partialText.value = matches[0]
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
