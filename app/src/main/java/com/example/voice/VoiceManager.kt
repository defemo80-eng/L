package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.model.DeiAiSettings
import com.example.data.model.Emotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

private const val TAG = "VoiceManager"

data class VoiceOption(
    val name: String,
    val displayLabel: String,
    val localeTag: String
)

class VoiceManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _availableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private var onSpeakingFinishedCallback: (() -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                val defaultLocale = Locale("es", "ES")
                tts?.language = defaultLocale
                refreshAvailableVoices()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        mainHandler.post {
                            onSpeakingFinishedCallback?.invoke()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        _isSpeaking.value = false
                    }
                })
            } else {
                Log.w(TAG, "TextToSpeech initialization failed with status $status")
            }
        }
    }

    fun refreshAvailableVoices() {
        val engine = tts ?: return
        if (!ttsReady) return
        try {
            val voices = engine.voices.orEmpty()
                .filter { it.locale.language.equals("es", ignoreCase = true) }
                .sortedWith(compareBy<Voice> { it.locale.country }.thenBy { it.name })
                .mapIndexed { index, voice ->
                    val country = voice.locale.displayCountry.ifBlank { voice.locale.toLanguageTag() }
                    val quality = if (voice.quality >= Voice.QUALITY_HIGH) "Alta calidad" else "Estándar"
                    VoiceOption(
                        name = voice.name,
                        displayLabel = "Voz ${index + 1} ($country • $quality)",
                        localeTag = voice.locale.toLanguageTag()
                    )
                }
            _availableVoices.value = voices
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query TTS voices", e)
        }
    }

    fun speak(
        text: String,
        emotion: Emotion,
        settings: DeiAiSettings,
        onFinished: (() -> Unit)? = null
    ) {
        if (!ttsReady) {
            onFinished?.invoke()
            return
        }
        val engine = tts ?: return
        onSpeakingFinishedCallback = onFinished

        val locale = Locale.forLanguageTag(settings.languageTag)
        engine.language = locale

        if (settings.voiceName.isNotBlank()) {
            val selected = engine.voices?.firstOrNull { it.name == settings.voiceName }
            if (selected != null) {
                engine.voice = selected
            }
        }

        val pitch = if (settings.emotionsEnabled) emotion.ttsPitch else 1.0f
        val rate = (settings.speechRate * if (settings.emotionsEnabled) emotion.ttsRateMultiplier else 1.0f)
            .coerceIn(0.5f, 2.0f)

        engine.setPitch(pitch)
        engine.setSpeechRate(rate)

        // Clean emojis before TTS so voice sounds natural
        val cleanText = text
            .replace(Regex("[\\p{So}\\p{Cn}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleanText.isEmpty()) {
            onFinished?.invoke()
            return
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volume.coerceIn(0f, 1f))
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }

        val utteranceId = UUID.randomUUID().toString()
        _isSpeaking.value = true
        engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stopSpeaking() {
        onSpeakingFinishedCallback = null
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
        _isSpeaking.value = false
    }

    fun startListening(
        languageTag: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        stopSpeaking()
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _isListening.value = false
                onError("El reconocimiento de voz no está disponible en este dispositivo.")
                return@post
            }

            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _partialTranscript.value = ""
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Normalize roughly from [-2, 10] to [0, 1]
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _rmsLevel.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _rmsLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                        _partialTranscript.value = ""
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No alcancé a escucharte. Inténtalo de nuevo."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No detecté voz a tiempo."
                            SpeechRecognizer.ERROR_AUDIO -> "Error al acceder al micrófono."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta permiso de micrófono."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Se requiere conexión o paquete de voz offline para dictado."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocimiento de voz está ocupado."
                            else -> "No pude procesar tu voz (código $error)."
                        }
                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                        _partialTranscript.value = ""
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            onResult(text)
                        } else {
                            onError("No entendí lo que dijiste. ¿Puedes repetirlo?")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        _partialTranscript.value = partial
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                _isListening.value = true
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                _isListening.value = false
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                onError("Error al iniciar el micrófono: ${e.localizedMessage ?: "desconocido"}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping SpeechRecognizer", e)
            }
            _isListening.value = false
            _rmsLevel.value = 0f
            _partialTranscript.value = ""
        }
    }

    fun release() {
        stopListening()
        stopSpeaking()
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
