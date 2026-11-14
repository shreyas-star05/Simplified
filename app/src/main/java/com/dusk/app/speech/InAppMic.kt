package com.dusk.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dusk.app.data.SpeechChoice

/**
 * The in-app mic, used only when Settings picks an engine other than the keyboard.
 * Android's recogniser ends at every pause, so this restarts it until Stop and hands back
 * each finished piece of text. Same logic as the release 0 test build. Main thread only.
 */
class InAppMic(
    private val context: Context,
    private val choice: SpeechChoice,
    private val onPiece: (String) -> Unit,
    private val onStopped: (problem: String?) -> Unit,
) {
    var partial by mutableStateOf("")
        private set
    var listening by mutableStateOf(false)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wanted = false
    private var finished = false
    private var errorsInARow = 0
    private val onDevice = choice == SpeechChoice.ON_DEVICE || choice == SpeechChoice.ON_DEVICE_LONG

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (onDevice) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        if (choice == SpeechChoice.ON_DEVICE_LONG) {
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15_000)
        }
    }

    fun start() {
        wanted = true
        val r = try {
            when {
                onDevice && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                !onDevice && SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
                else -> null
            }
        } catch (e: Exception) { null }
        if (r == null) {
            finish(if (onDevice) "This phone has no offline speech recogniser. Use the keyboard mic." else "This phone has no speech recogniser. Use the keyboard mic.")
            return
        }
        recognizer = r
        r.setRecognitionListener(listener)
        listen()
    }

    fun stop() {
        if (finished) return
        wanted = false
        try { recognizer?.stopListening() } catch (e: Exception) { /* already stopped */ }
        handler.postDelayed({ finish(null) }, 1500)
    }

    private fun listen() {
        if (!wanted || finished) return
        try {
            recognizer?.startListening(intent())
            listening = true
        } catch (e: Exception) {
            finish("The mic could not start.")
        }
    }

    private fun give(text: String?) {
        val t = text?.trim().orEmpty()
        if (t.isNotEmpty()) onPiece(t)
        partial = ""
    }

    private fun ended() {
        listening = false
        if (wanted) handler.postDelayed({ listen() }, 150) else finish(null)
    }

    private fun finish(problem: String?) {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        if (partial.isNotEmpty()) give(partial)
        listening = false
        try { recognizer?.destroy() } catch (e: Exception) { /* already gone */ }
        recognizer = null
        onStopped(problem)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { errorsInARow = 0 }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(b: Bundle?) {
            partial = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        }
        override fun onResults(b: Bundle?) {
            give(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            ended()
        }
        override fun onSegmentResults(b: Bundle) {
            give(b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
        }
        override fun onEndOfSegmentedSession() = ended()
        override fun onError(error: Int) {
            if (partial.isNotEmpty()) give(partial)
            listening = false
            if (!wanted) { finish(null); return }
            val silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { finish("Microphone permission is off."); return }
            if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) { finish("The offline speech pack is not downloaded."); return }
            if (!silence && ++errorsInARow >= 4) { finish("The mic stopped. Your words so far are saved."); return }
            handler.postDelayed({ listen() }, if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 600 else 250)
        }
    }
}
