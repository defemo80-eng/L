package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class Emotion(
    val displayName: String,
    val badgeEmoji: String,
    val ttsPitch: Float,
    val ttsRateMultiplier: Float,
    val toneHint: String
) {
    ALEGRIA("Alegría", "😊", 1.12f, 1.04f, "alegre, cercana y positiva"),
    TRISTEZA("Tristeza", "🥺", 0.90f, 0.92f, "suave, comprensiva y melancólica"),
    ENOJO_DIVERTIDO("Enojo divertido", "😤", 1.08f, 1.08f, "indignada en broma, juguetona y expresiva"),
    SORPRESA("Sorpresa", "😲", 1.22f, 1.06f, "asombrada y muy expresiva"),
    CURIOSIDAD("Curiosidad", "🤔", 1.06f, 1.00f, "inquisitiva, atenta e interesada"),
    ENTUSIASMO("Entusiasmo", "🤩", 1.18f, 1.10f, "llena de energía, motivación y chispa"),
    TRANQUILIDAD("Tranquilidad", "😌", 0.96f, 0.95f, "serena, equilibrada y relajante");

    companion object {
        fun safeFromRaw(raw: String?): Emotion =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: ALEGRIA
    }
}

enum class MemoryCategory(val displayName: String) {
    NOMBRE("Nombre"),
    PREFERENCIA("Preferencia"),
    DATO("Dato personal"),
    INTERES("Interés");

    companion object {
        fun safeFromRaw(raw: String?): MemoryCategory =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: DATO
    }
}

enum class PersonalityStyle(
    val displayName: String,
    val description: String,
    val systemDirective: String
) {
    AMIGABLE(
        "Amigable y juguetona",
        "Cercana, expresiva y con chispa natural.",
        "Eres cercana, cálida, expresiva y con sentido del humor ligero."
    ),
    PROFESIONAL(
        "Profesional y precisa",
        "Clara, estructurada y directa al punto.",
        "Eres elocuente, precisa, ordenada y profesional sin perder amabilidad."
    ),
    EMPATICA(
        "Empática y cálida",
        "Comprensiva, paciente y reconfortante.",
        "Eres muy comprensiva, empática, paciente y transmites calma."
    ),
    INGENIOSA(
        "Ingeniosa y ocurrente",
        "Ágil, creativa y con bromas sutiles.",
        "Eres ágil mentalmente, creativa, ocurrente y te gusta responder con ingenio."
    )
}

enum class AssistantStatus(val label: String) {
    INACTIVO("En espera"),
    ESCUCHANDO("Escuchando..."),
    PROCESANDO("Pensando..."),
    HABLANDO("Hablando...")
}

enum class MessageSender {
    USER,
    DEIAI;

    companion object {
        fun safeFromRaw(raw: String?): MessageSender =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: DEIAI
    }
}

enum class DeviceActionType {
    NONE,
    GET_TIME,
    OPEN_APP,
    CREATE_REMINDER,
    OPEN_SETTINGS,
    CALL_CONTACT;

    companion object {
        fun safeFromRaw(raw: String?): DeviceActionType =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: NONE
    }
}

data class PendingDeviceAction(
    val type: DeviceActionType,
    val target: String,
    val detail: String,
    val confirmationMessage: String
)

data class MemoryProposal(
    val category: MemoryCategory,
    val key: String,
    val value: String
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "Nueva conversación",
    val lastMessagePreview: String = "",
    val lastEmotion: String = Emotion.ALEGRIA.name,
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val emotionEnum: Emotion
        get() = Emotion.safeFromRaw(lastEmotion)
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val sender: String = MessageSender.DEIAI.name,
    val text: String = "",
    val emotion: String = Emotion.ALEGRIA.name,
    val actionTaken: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val senderEnum: MessageSender
        get() = MessageSender.safeFromRaw(sender)

    val emotionEnum: Emotion
        get() = Emotion.safeFromRaw(emotion)
}

@Keep
@IgnoreExtraProperties
data class MemoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = MemoryCategory.DATO.name,
    val key: String = "",
    val value: String = "",
    val authorized: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val categoryEnum: MemoryCategory
        get() = MemoryCategory.safeFromRaw(category)
}

data class DeiAiSettings(
    val voiceName: String = "",
    val speechRate: Float = 1.0f,
    val volume: Float = 0.9f,
    val autoSpeak: Boolean = true,
    val continuousConversation: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val personalityStyle: PersonalityStyle = PersonalityStyle.AMIGABLE,
    val emotionsEnabled: Boolean = true,
    val rememberName: Boolean = true,
    val rememberPreferences: Boolean = true,
    val rememberFacts: Boolean = true,
    val askBeforeRemembering: Boolean = false,
    val languageTag: String = "es-ES",
    val darkMode: Boolean = true,
    val notificationsEnabled: Boolean = true
)
