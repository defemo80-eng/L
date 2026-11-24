package com.example.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.actions.DeviceActionExecutor
import com.example.actions.DeviceActionResult
import com.example.data.ai.DeiAiEngine
import com.example.data.model.AssistantStatus
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.DeiAiSettings
import com.example.data.model.Emotion
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryItem
import com.example.data.model.MemoryProposal
import com.example.data.model.MessageSender
import com.example.data.model.PendingDeviceAction
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.SettingsRepository
import com.example.voice.DeiAiVoiceService
import com.example.voice.VoiceManager
import com.example.voice.VoiceOption
import com.example.voice.WakeWordEventBus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private const val TAG = "DeiAiViewModel"

sealed interface DataStreamState<out T> {
    data object Loading : DataStreamState<Nothing>
    data class Success<T>(val data: T) : DataStreamState<T>
    data class Error(val message: String) : DataStreamState<Nothing>
}

class DeiAiViewModel(
    private val appContext: Context,
    private val currentUserId: String,
    private val displayName: String?,
    private val firestoreRepo: FirestoreRepository,
    private val settingsRepo: SettingsRepository,
    private val aiEngine: DeiAiEngine,
    private val voiceManager: VoiceManager,
    private val actionExecutor: DeviceActionExecutor
) : ViewModel() {

    val settings: StateFlow<DeiAiSettings> = settingsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DeiAiSettings()
    )

    val conversationsState: StateFlow<DataStreamState<List<Conversation>>> =
        firestoreRepo.observeConversations(currentUserId)
            .map<List<Conversation>, DataStreamState<List<Conversation>>> { DataStreamState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing conversations", root)
                emit(DataStreamState.Error(root.message ?: "No se pudo cargar el historial."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataStreamState.Loading
            )

    val memoriesState: StateFlow<DataStreamState<List<MemoryItem>>> =
        firestoreRepo.observeMemories(currentUserId)
            .map<List<MemoryItem>, DataStreamState<List<MemoryItem>>> { DataStreamState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing memories", root)
                emit(DataStreamState.Error(root.message ?: "No se pudieron cargar los recuerdos."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataStreamState.Loading
            )

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessagesState: StateFlow<DataStreamState<List<ChatMessage>>> =
        _activeConversationId.flatMapLatest { convId ->
            if (convId.isNullOrBlank()) {
                flowOf(DataStreamState.Success(emptyList()))
            } else {
                firestoreRepo.observeMessages(currentUserId, convId)
                    .map<List<ChatMessage>, DataStreamState<List<ChatMessage>>> { DataStreamState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val root = firestoreError ?: error
                        Log.w(TAG, "Error observing messages", root)
                        emit(DataStreamState.Error(root.message ?: "No se pudieron cargar los mensajes."))
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataStreamState.Success(emptyList())
        )

    private val _currentEmotion = MutableStateFlow(Emotion.ALEGRIA)
    val currentEmotion: StateFlow<Emotion> = _currentEmotion.asStateFlow()

    private val _assistantStatus = MutableStateFlow(AssistantStatus.INACTIVO)
    val assistantStatus: StateFlow<AssistantStatus> = _assistantStatus.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _infoBanner = MutableStateFlow<String?>(null)
    val infoBanner: StateFlow<String?> = _infoBanner.asStateFlow()

    private val _pendingAction = MutableStateFlow<PendingDeviceAction?>(null)
    val pendingAction: StateFlow<PendingDeviceAction?> = _pendingAction.asStateFlow()

    private val _pendingMemoryProposals = MutableStateFlow<List<MemoryProposal>>(emptyList())
    val pendingMemoryProposals: StateFlow<List<MemoryProposal>> = _pendingMemoryProposals.asStateFlow()

    private val _greetingText = MutableStateFlow("")
    val greetingText: StateFlow<String> = _greetingText.asStateFlow()

    val isListening: StateFlow<Boolean> = voiceManager.isListening
    val isSpeaking: StateFlow<Boolean> = voiceManager.isSpeaking
    val rmsLevel: StateFlow<Float> = voiceManager.rmsLevel
    val partialTranscript: StateFlow<String> = voiceManager.partialTranscript
    val availableVoices: StateFlow<List<VoiceOption>> = voiceManager.availableVoices
    val isWakeServiceRunning: StateFlow<Boolean> = WakeWordEventBus.isServiceRunning
    val isBackgroundMicActive: StateFlow<Boolean> = WakeWordEventBus.isBackgroundMicActive

    private var hasSpokenInitialGreeting = false

    init {
        viewModelScope.launch {
            combine(
                voiceManager.isListening,
                voiceManager.isSpeaking
            ) { listening, speaking ->
                when {
                    listening -> AssistantStatus.ESCUCHANDO
                    speaking -> AssistantStatus.HABLANDO
                    _assistantStatus.value == AssistantStatus.PROCESANDO -> AssistantStatus.PROCESANDO
                    else -> AssistantStatus.INACTIVO
                }
            }.collect { derived ->
                if (_assistantStatus.value != AssistantStatus.PROCESANDO || derived != AssistantStatus.INACTIVO) {
                    _assistantStatus.value = derived
                }
            }
        }

        // Update dynamic greeting from memories or Google displayName
        viewModelScope.launch {
            memoriesState.collect { memState ->
                val memories = (memState as? DataStreamState.Success)?.data.orEmpty()
                val rememberedName = memories
                    .firstOrNull { it.authorized && it.categoryEnum == MemoryCategory.NOMBRE }
                    ?.value
                    ?: displayName?.split(" ")?.firstOrNull()
                _greetingText.value = buildGreeting(rememberedName)
                if (!hasSpokenInitialGreeting && memState is DataStreamState.Success) {
                    hasSpokenInitialGreeting = true
                    if (settings.value.autoSpeak && _activeConversationId.value == null) {
                        voiceManager.speak(
                            text = _greetingText.value,
                            emotion = Emotion.ALEGRIA,
                            settings = settings.value
                        )
                    }
                }
            }
        }

        // Listen for «Hola DeiAI» wake-word events
        viewModelScope.launch {
            WakeWordEventBus.wakeWordEvents.collect { remainingCommand ->
                handleWakeWordTriggered(remainingCommand)
            }
        }
    }

    private fun buildGreeting(name: String?): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when {
            hour in 5..11 -> "Buenos días"
            hour in 12..19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        return if (!name.isNullOrBlank()) {
            "¡$timeGreeting, $name! Soy DeiAI, ¿qué vamos a hacer hoy?"
        } else {
            "¡Holaaa! $timeGreeting, soy DeiAI. ¿Qué vamos a hacer hoy?"
        }
    }

    fun handleWakeWordTriggered(command: String) {
        voiceManager.stopSpeaking()
        if (command.isNotBlank()) {
            sendMessage(command, fromVoice = true)
        } else {
            _currentEmotion.value = Emotion.ENTUSIASMO
            val reply = "¡Aquí estoy! Te escucho."
            if (settings.value.autoSpeak) {
                voiceManager.speak(reply, Emotion.ENTUSIASMO, settings.value) {
                    startVoiceCapture()
                }
            } else {
                startVoiceCapture()
            }
        }
    }

    fun startNewConversation() {
        voiceManager.stopSpeaking()
        voiceManager.stopListening()
        _activeConversationId.value = null
        _currentEmotion.value = Emotion.ALEGRIA
        _pendingAction.value = null
        _pendingMemoryProposals.value = emptyList()
        _errorBanner.value = null
    }

    fun selectConversation(conversationId: String) {
        voiceManager.stopSpeaking()
        _activeConversationId.value = conversationId
        val conv = (conversationsState.value as? DataStreamState.Success)
            ?.data
            ?.firstOrNull { it.id == conversationId }
        if (conv != null) {
            _currentEmotion.value = conv.emotionEnum
        }
    }

    fun interruptAssistant() {
        voiceManager.stopSpeaking()
        voiceManager.stopListening()
        _assistantStatus.value = AssistantStatus.INACTIVO
    }

    fun toggleMicrophone() {
        if (voiceManager.isSpeaking.value) {
            // Barge-in: interrupt DeiAI and start listening immediately
            voiceManager.stopSpeaking()
            startVoiceCapture()
            return
        }
        if (voiceManager.isListening.value) {
            voiceManager.stopListening()
        } else {
            startVoiceCapture()
        }
    }

    fun startVoiceCapture() {
        val hasMic = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            _errorBanner.value = "Necesito permiso de micrófono para escucharte. Actívalo en Ajustes o en el botón de micrófono."
            return
        }

        _errorBanner.value = null
        voiceManager.startListening(
            languageTag = settings.value.languageTag,
            onResult = { spokenText ->
                sendMessage(spokenText, fromVoice = true)
            },
            onError = { err ->
                _errorBanner.value = err
            }
        )
    }

    fun sendMessage(rawText: String, fromVoice: Boolean = false) {
        val cleanText = rawText.trim()
        if (cleanText.isEmpty()) return

        // Check for conversational stop commands in continuous mode
        val lower = cleanText.lowercase(Locale("es", "ES"))
        if (fromVoice && (lower == "para" || lower == "silencio" || lower == "cállate" || lower == "adios" || lower == "adiós")) {
            voiceManager.stopSpeaking()
            voiceManager.stopListening()
            _infoBanner.value = "Escucha pausada."
            return
        }

        voiceManager.stopSpeaking()
        _errorBanner.value = null

        viewModelScope.launch {
            _assistantStatus.value = AssistantStatus.PROCESANDO
            try {
                val activeSettings = settings.value
                val currentMemories = (memoriesState.value as? DataStreamState.Success)?.data.orEmpty()
                val currentHistory = (activeMessagesState.value as? DataStreamState.Success)?.data.orEmpty()

                var convId = _activeConversationId.value
                var currentCount = currentHistory.size.toLong()
                var isNewConversation = false

                if (convId.isNullOrBlank()) {
                    val created = firestoreRepo.createConversation(
                        title = cleanText.take(48),
                        initialPreview = cleanText,
                        initialEmotion = _currentEmotion.value
                    )
                    if (created.isFailure) {
                        _errorBanner.value = "No se pudo guardar la conversación: ${created.exceptionOrNull()?.localizedMessage}"
                        _assistantStatus.value = AssistantStatus.INACTIVO
                        return@launch
                    }
                    convId = created.getOrThrow()
                    _activeConversationId.value = convId
                    currentCount = 0L
                    isNewConversation = true
                }

                // Persist user message
                val userMsgResult = firestoreRepo.appendMessage(
                    conversationId = convId,
                    sender = MessageSender.USER,
                    text = cleanText,
                    emotion = _currentEmotion.value,
                    updateTitleIfDefault = if (isNewConversation) cleanText.take(48) else null,
                    newMessageCount = currentCount + 1L
                )
                if (userMsgResult.isFailure) {
                    Log.w(TAG, "Failed to persist user message", userMsgResult.exceptionOrNull())
                }

                // Run Gemini or offline fallback
                val aiResult = aiEngine.generateResponse(
                    userMessage = cleanText,
                    history = currentHistory,
                    memories = currentMemories,
                    settings = activeSettings
                )

                aiResult.onSuccess { output ->
                    _currentEmotion.value = output.emotion

                    // Execute or prepare Android device action
                    val actionResult = actionExecutor.executeOrPrepare(
                        type = output.deviceActionType,
                        target = output.deviceActionTarget,
                        detail = output.deviceActionDetail
                    )
                    val actionSummary = when (actionResult) {
                        is DeviceActionResult.Executed -> actionResult.summary
                        is DeviceActionResult.RequiresConfirmation -> {
                            _pendingAction.value = actionResult.pendingAction
                            "Esperando confirmación para llamada"
                        }
                        is DeviceActionResult.Failed -> {
                            _infoBanner.value = actionResult.reason
                            actionResult.reason
                        }
                        DeviceActionResult.None -> null
                    }

                    // Handle extracted memories
                    if (output.memoryProposals.isNotEmpty()) {
                        if (activeSettings.askBeforeRemembering) {
                            _pendingMemoryProposals.value = output.memoryProposals
                        } else {
                            output.memoryProposals.forEach { proposal ->
                                firestoreRepo.saveOrUpdateMemory(
                                    category = proposal.category,
                                    key = proposal.key,
                                    value = proposal.value,
                                    authorized = true
                                )
                            }
                        }
                    }

                    // Persist DeiAI reply
                    firestoreRepo.appendMessage(
                        conversationId = convId,
                        sender = MessageSender.DEIAI,
                        text = output.reply,
                        emotion = output.emotion,
                        actionTaken = actionSummary,
                        newMessageCount = currentCount + 2L
                    )

                    _assistantStatus.value = AssistantStatus.INACTIVO

                    if (activeSettings.autoSpeak) {
                        voiceManager.speak(
                            text = output.reply,
                            emotion = output.emotion,
                            settings = activeSettings,
                            onFinished = {
                                if (activeSettings.continuousConversation && actionResult !is DeviceActionResult.RequiresConfirmation) {
                                    startVoiceCapture()
                                }
                            }
                        )
                    }
                }.onFailure { err ->
                    _assistantStatus.value = AssistantStatus.INACTIVO
                    val msg = err.localizedMessage ?: "Error al conectar con Gemini."
                    _errorBanner.value = msg
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _assistantStatus.value = AssistantStatus.INACTIVO
                _errorBanner.value = e.localizedMessage ?: "Ocurrió un error inesperado."
            }
        }
    }

    fun confirmPendingAction() {
        val action = _pendingAction.value ?: return
        _pendingAction.value = null
        when (val result = actionExecutor.executeConfirmedAction(action)) {
            is DeviceActionResult.Executed -> _infoBanner.value = result.summary
            is DeviceActionResult.Failed -> _errorBanner.value = result.reason
            else -> Unit
        }
    }

    fun dismissPendingAction() {
        _pendingAction.value = null
        _infoBanner.value = "Acción cancelada."
    }

    fun approveMemoryProposal(proposal: MemoryProposal) {
        viewModelScope.launch {
            firestoreRepo.saveOrUpdateMemory(
                category = proposal.category,
                key = proposal.key,
                value = proposal.value,
                authorized = true
            )
            _pendingMemoryProposals.value = _pendingMemoryProposals.value.filterNot { it == proposal }
            _infoBanner.value = "Recuerdo guardado: ${proposal.key}"
        }
    }

    fun rejectMemoryProposal(proposal: MemoryProposal) {
        _pendingMemoryProposals.value = _pendingMemoryProposals.value.filterNot { it == proposal }
    }

    fun saveMemoryManual(
        existingId: String? = null,
        category: MemoryCategory,
        key: String,
        value: String,
        authorized: Boolean = true
    ) {
        viewModelScope.launch {
            val res = firestoreRepo.saveOrUpdateMemory(existingId, category, key, value, authorized)
            if (res.isFailure) {
                _errorBanner.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo guardar el recuerdo."
            } else {
                _infoBanner.value = "Recuerdo actualizado."
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            val res = firestoreRepo.deleteMemory(memoryId)
            if (res.isFailure) {
                _errorBanner.value = "No se pudo eliminar el recuerdo."
            }
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            val res = firestoreRepo.clearAllMemories()
            if (res.isSuccess) {
                _infoBanner.value = "Todos los recuerdos fueron eliminados."
            } else {
                _errorBanner.value = "No se pudieron eliminar todos los recuerdos."
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
            }
            val res = firestoreRepo.deleteConversation(conversationId)
            if (res.isFailure) {
                _errorBanner.value = "No se pudo eliminar la conversación."
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            _activeConversationId.value = null
            val res = firestoreRepo.clearAllConversations()
            if (res.isSuccess) {
                _infoBanner.value = "Historial de conversaciones eliminado."
            } else {
                _errorBanner.value = "No se pudo borrar el historial."
            }
        }
    }

    fun updateSettings(transform: (DeiAiSettings) -> DeiAiSettings) {
        viewModelScope.launch {
            settingsRepo.updateSettings(transform)
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(wakeWordEnabled = enabled) }
            if (enabled) {
                val hasMic = ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    DeiAiVoiceService.start(appContext, settings.value.languageTag)
                    _infoBanner.value = "Activación por voz «Hola DeiAI» habilitada."
                } else {
                    _errorBanner.value = "Se requiere permiso de micrófono para activar «Hola DeiAI»."
                    settingsRepo.updateSettings { it.copy(wakeWordEnabled = false) }
                }
            } else {
                DeiAiVoiceService.stop(appContext)
                _infoBanner.value = "Escucha en segundo plano desactivada."
            }
        }
    }

    fun testVoice() {
        voiceManager.refreshAvailableVoices()
        voiceManager.speak(
            text = "Hola, así suena mi voz con la configuración actual.",
            emotion = _currentEmotion.value,
            settings = settings.value
        )
    }

    fun refreshVoices() {
        voiceManager.refreshAvailableVoices()
    }

    fun clearBanners() {
        _errorBanner.value = null
        _infoBanner.value = null
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
    }
}
