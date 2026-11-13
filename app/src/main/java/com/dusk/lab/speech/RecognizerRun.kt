package com.dusk.lab.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Engine(val label: String, val short: String) {
    KEYBOARD("A. Keyboard mic", "A keyboard"),
    DEFAULT("B. Phone recogniser", "B phone recogniser"),
    ON_DEVICE("C. On-device", "C on-device"),
    ON_DEVICE_LONG("D. On-device, long mode", "D on-device long mode"),
}

/**
 * One listening session with Android's SpeechRecognizer.
 * The recogniser is built for short phrases, so this restarts it every time it ends
 * until stop() is called, and counts the restarts and errors along the way.
 * All methods must be called on the main thread.
 */
class RecognizerRun(
    private val context: Context,
    private val engine: Engine,
    private val singleShot: Boolean = false,
    private val onFinished: (RecognizerRun) -> Unit,
) {
    var text by mutableStateOf("")
        private set
    var partial by mutableStateOf("")
        private set
    var status by mutableStateOf("Starting")
        private set
    var restarts by mutableIntStateOf(0)
        private set
    var listening by mutableStateOf(false)
        private set

    val errors = ArrayList<String>()
    var startedAt = 0L
        private set
    var endedAt = 0L
        private set
    var stoppedByItself = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wantRunning = false
    private var finished = false
    private var consecutiveErrors = 0
    private val onDevice = engine == Engine.ON_DEVICE || engine == Engine.ON_DEVICE_LONG

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (onDevice) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        if (engine == Engine.ON_DEVICE_LONG) {
            // Android 13+: ask for results in segments instead of ending at the first pause.
            putExtra(
                RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            )
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15_000)
        }
    }

    fun start() {
        startedAt = System.currentTimeMillis()
        wantRunning = true
        val r = create(context, onDevice)
        if (r == null) {
            errors += if (onDevice) "this phone has no on-device recogniser" else "this phone has no speech recogniser"
            status = errors.last()
            finish()
            return
        }
        recognizer = r
        r.setRecognitionListener(listener)
        listen()
    }

    /** The user pressed Stop. The recogniser gets a moment to hand over its last words. */
    fun stop() {
        if (finished) return
        wantRunning = false
        status = "Finishing"
        try { recognizer?.stopListening() } catch (e: Exception) { errors += "stop failed: ${e.message}" }
        handler.postDelayed({ finish() }, 2500)
    }

    private fun listen() {
        if (!wantRunning || finished) return
        try {
            recognizer?.startListening(intent())
            listening = true
            status = "Listening"
        } catch (e: Exception) {
            errors += "start failed: ${e.message}"
            finish()
        }
    }

    private fun append(piece: String?) {
        val p = piece?.trim().orEmpty()
        if (p.isNotEmpty()) text = if (text.isEmpty()) p else "$text $p"
        partial = ""
    }

    private fun sessionEnded() {
        listening = false
        if (singleShot) { finish(); return }
        if (wantRunning) {
            restarts++
            handler.postDelayed({ listen() }, 150)
        } else finish()
    }

    private fun finish() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        if (partial.isNotEmpty()) append(partial)
        endedAt = System.currentTimeMillis()
        listening = false
        if (status == "Listening" || status == "Finishing" || status == "Starting") status = "Stopped"
        try { recognizer?.destroy() } catch (e: Exception) { /* already gone */ }
        recognizer = null
        onFinished(this)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { consecutiveErrors = 0 }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        }

        override fun onResults(results: Bundle?) {
            append(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            sessionEnded()
        }

        override fun onSegmentResults(segmentResults: Bundle) {
            append(segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
        }

        override fun onEndOfSegmentedSession() {
            sessionEnded()
        }

        override fun onError(error: Int) {
            val name = errorName(error)
            // Silence between sentences is normal: keep what was heard and carry on.
            val benign = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (!benign) errors += name
            if (partial.isNotEmpty()) append(partial)
            listening = false
            if (!wantRunning || singleShot) { finish(); return }
            consecutiveErrors++
            if (!benign && consecutiveErrors >= 4) {
                stoppedByItself = true
                status = "Stopped by itself: $name"
                finish()
                return
            }
            restarts++
            handler.postDelayed({ listen() }, if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 600 else 250)
        }
    }

    companion object {
        const val LANGUAGE = "en-IN"

        fun create(context: Context, onDevice: Boolean): SpeechRecognizer? = try {
            if (onDevice) {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else null
            } else {
                if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
            }
        } catch (e: Exception) {
            null
        }

        fun errorName(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
            SpeechRecognizer.ERROR_NETWORK -> "network error"
            SpeechRecognizer.ERROR_AUDIO -> "audio error"
            SpeechRecognizer.ERROR_SERVER -> "server error"
            SpeechRecognizer.ERROR_CLIENT -> "client error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech heard"
            SpeechRecognizer.ERROR_NO_MATCH -> "no match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recogniser busy"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission missing"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "too many requests"
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "server disconnected"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "language not supported"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "language pack not downloaded"
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "cannot check support"
            else -> "error $code"
        }

        /** Asks each recogniser which languages it has, for the Results page. */
        fun checkSupport(context: Context, onDevice: Boolean, done: (String) -> Unit) {
            val name = if (onDevice) "On-device recogniser" else "Phone recogniser"
            val r = create(context, onDevice)
            if (r == null) { done("$name: not available on this phone"); return }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
            var answered = false
            val handler = Handler(Looper.getMainLooper())
            fun reply(s: String) {
                if (answered) return
                answered = true
                try { r.destroy() } catch (e: Exception) { /* already gone */ }
                done(s)
            }
            try {
                r.checkRecognitionSupport(intent, context.mainExecutor, object : RecognitionSupportCallback {
                    override fun onSupportResult(s: RecognitionSupport) {
                        fun has(l: List<String>) = l.any { it.equals(LANGUAGE, true) || it.startsWith("en-IN", true) }
                        reply(
                            "$name for $LANGUAGE: installed on phone ${has(s.installedOnDeviceLanguages)}, " +
                                "downloading ${has(s.pendingOnDeviceLanguages)}, " +
                                "can be downloaded ${has(s.supportedOnDeviceLanguages)}, " +
                                "online ${has(s.onlineLanguages)}. " +
                                "Installed packs: ${s.installedOnDeviceLanguages.joinToString().ifEmpty { "none" }}. " +
                                "Hindi installed: ${s.installedOnDeviceLanguages.any { it.startsWith("hi", true) }}.",
                        )
                    }

                    override fun onError(error: Int) = reply("$name: support check failed (${errorName(error)})")
                })
                handler.postDelayed({ reply("$name: support check gave no answer") }, 6000)
            } catch (e: Exception) {
                reply("$name: support check not possible (${e.message})")
            }
        }

        /** Asks the phone to download the offline pack for Indian English. */
        fun requestDownload(context: Context): String {
            val r = create(context, true) ?: return "No on-device recogniser on this phone"
            return try {
                r.triggerModelDownload(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE))
                "Download requested. Check again in a few minutes."
            } catch (e: Exception) {
                "Download request failed: ${e.message}"
            } finally {
                try { r.destroy() } catch (e: Exception) { /* already gone */ }
            }
        }
    }
}
