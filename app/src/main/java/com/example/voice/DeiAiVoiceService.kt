package com.example.voice

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Normalizer
import java.util.Locale

private const val TAG = "DeiAiVoiceService"
private const val CHANNEL_ID = "deiai_voice_wake_channel"
private const val NOTIFICATION_ID = 7001

object WakeWordEventBus {
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isBackgroundMicActive = MutableStateFlow(false)
    val isBackgroundMicActive: StateFlow<Boolean> = _isBackgroundMicActive.asStateFlow()

    private val _wakeWordEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val wakeWordEvents: SharedFlow<String> = _wakeWordEvents.asSharedFlow()

    internal fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    internal fun setBackgroundMicActive(active: Boolean) {
        _isBackgroundMicActive.value = active
    }

    internal fun emitWakeCommand(remainingCommand: String) {
        _wakeWordEvents.tryEmit(remainingCommand)
    }
}

class DeiAiVoiceService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var languageTag = "es-ES"

    companion object {
        const val ACTION_START = "com.example.voice.ACTION_START"
        const val ACTION_STOP = "com.example.voice.ACTION_STOP"
        const val EXTRA_LANGUAGE_TAG = "extra_language_tag"
        const val EXTRA_WAKE_TRIGGERED = "extra_wake_triggered"
        const val EXTRA_WAKE_COMMAND = "extra_wake_command"

        fun start(context: Context, languageTag: String = "es-ES") {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasMic) return

            val intent = Intent(context, DeiAiVoiceService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LANGUAGE_TAG, languageTag)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DeiAiVoiceService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        languageTag = intent?.getStringExtra(EXTRA_LANGUAGE_TAG) ?: "es-ES"

        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        active = true
        WakeWordEventBus.setServiceRunning(true)
        scheduleNextListen(delayMs = 300L)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.voice_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.voice_service_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher_round)
        .setContentTitle(getString(R.string.voice_service_notif_title))
        .setContentText(getString(R.string.voice_service_notif_text))
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            android.R.drawable.ic_media_pause,
            getString(R.string.voice_service_pause),
            PendingIntent.getService(
                this,
                1,
                Intent(this, DeiAiVoiceService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun scheduleNextListen(delayMs: Long) {
        if (!active) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ startWakeWordListening() }, delayMs)
    }

    private fun startWakeWordListening() {
        if (!active) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "SpeechRecognizer unavailable in background service")
            WakeWordEventBus.setBackgroundMicActive(false)
            return
        }

        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    WakeWordEventBus.setBackgroundMicActive(true)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    WakeWordEventBus.setBackgroundMicActive(false)
                }

                override fun onError(error: Int) {
                    WakeWordEventBus.setBackgroundMicActive(false)
                    if (active) {
                        val backoff = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1800L else 900L
                        scheduleNextListen(backoff)
                    }
                }

                override fun onResults(results: Bundle?) {
                    WakeWordEventBus.setBackgroundMicActive(false)
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    handleTranscript(heard, isFinal = true)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val heard = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (containsWakePhrase(heard) != null) {
                        recognizer?.cancel()
                        handleTranscript(heard, isFinal = false)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            WakeWordEventBus.setBackgroundMicActive(false)
            Log.e(TAG, "Error in background wake-word loop", e)
            scheduleNextListen(2000L)
        }
    }

    private fun handleTranscript(transcript: String, isFinal: Boolean) {
        val remaining = containsWakePhrase(transcript)
        if (remaining != null) {
            Log.i(TAG, "Wake phrase detected! Remaining command: '$remaining'")
            WakeWordEventBus.emitWakeCommand(remaining)
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_WAKE_TRIGGERED, true)
                putExtra(EXTRA_WAKE_COMMAND, remaining)
            }
            try {
                startActivity(launchIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to bring MainActivity to front from background", e)
            }
            scheduleNextListen(3500L)
        } else if (isFinal && active) {
            scheduleNextListen(600L)
        }
    }

    private fun containsWakePhrase(raw: String): String? {
        val decomposed = Normalizer.normalize(raw.lowercase(Locale("es", "ES")), Normalizer.Form.NFD)
        val clean = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").trim()
        val triggers = listOf("hola deiai", "hola dei ai", "hola day ai", "oye deiai", "oye dei ai")
        for (trigger in triggers) {
            val idx = clean.indexOf(trigger)
            if (idx >= 0) {
                return clean.substring(idx + trigger.length).trim()
            }
        }
        return null
    }

    private fun stopSelfCleanly() {
        active = false
        mainHandler.removeCallbacksAndMessages(null)
        WakeWordEventBus.setBackgroundMicActive(false)
        WakeWordEventBus.setServiceRunning(false)
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        active = false
        mainHandler.removeCallbacksAndMessages(null)
        WakeWordEventBus.setBackgroundMicActive(false)
        WakeWordEventBus.setServiceRunning(false)
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
        super.onDestroy()
    }
}
