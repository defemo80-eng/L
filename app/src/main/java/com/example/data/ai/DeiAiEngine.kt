package com.example.data.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.DeiAiSettings
import com.example.data.model.DeviceActionType
import com.example.data.model.Emotion
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryItem
import com.example.data.model.MemoryProposal
import com.example.data.model.MessageSender
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "DeiAiEngine"
private const val GEMINI_TIMEOUT_MS = 60_000L

data class DeiAiTurnOutput(
    val reply: String,
    val emotion: Emotion,
    val memoryProposals: List<MemoryProposal>,
    val deviceActionType: DeviceActionType,
    val deviceActionTarget: String,
    val deviceActionDetail: String,
    val isOfflineFallback: Boolean = false
)

class DeiAiEngine(private val context: Context) {

    private val responseSchema = Schema.obj(
        properties = mapOf(
            "reply" to Schema.string(
                description = "Respuesta natural y conversacional de DeiAI en español."
            ),
            "emotion" to Schema.enumeration(
                values = Emotion.entries.map { it.name },
                description = "Emoción simulada dominante de DeiAI al responder."
            ),
            "memoriesToSave" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "category" to Schema.enumeration(
                            values = MemoryCategory.entries.map { it.name },
                            description = "Categoría del recuerdo."
                        ),
                        "key" to Schema.string(description = "Etiqueta corta del dato."),
                        "value" to Schema.string(description = "Valor o detalle a recordar.")
                    )
                ),
                description = "Lista de recuerdos nuevos relevantes proporcionados por el usuario en este mensaje (vacía si no hay nada nuevo)."
            ),
            "deviceActionType" to Schema.enumeration(
                values = DeviceActionType.entries.map { it.name },
                description = "Acción del teléfono Android solicitada por el usuario, o NONE si no aplica."
            ),
            "deviceActionTarget" to Schema.string(
                description = "App a abrir, configuración, contacto/número o título de recordatorio. Cadena vacía si no aplica."
            ),
            "deviceActionDetail" to Schema.string(
                description = "Detalle extra como hora/minutos para alarma o recordatorio. Cadena vacía si no aplica."
            )
        )
    )

    private val model by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = this@DeiAiEngine.responseSchema
            temperature = 0.8f
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun generateResponse(
        userMessage: String,
        history: List<ChatMessage>,
        memories: List<MemoryItem>,
        settings: DeiAiSettings
    ): Result<DeiAiTurnOutput> {
        if (!isNetworkAvailable()) {
            return Result.success(processOffline(userMessage, memories, settings))
        }

        return try {
            val activeMemories = memories.filter { it.authorized }
            val memoriesContext = if (activeMemories.isEmpty()) {
                "No hay recuerdos guardados todavía."
            } else {
                activeMemories.joinToString("\n") {
                    "- [${it.categoryEnum.displayName}] ${it.key}: ${it.value}"
                }
            }

            val allowedCategories = buildList {
                if (settings.rememberName) add("NOMBRE")
                if (settings.rememberPreferences) add("PREFERENCIA")
                if (settings.rememberFacts) {
                    add("DATO")
                    add("INTERES")
                }
            }

            val dateFormatter = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale("es", "ES"))
            val nowText = dateFormatter.format(Date())

            val systemPrompt = buildString {
                appendLine("Eres DeiAI, una asistente virtual inteligente en español con personalidad propia y emociones simuladas (recuerda con honestidad si preguntan que tus emociones son una simulación de personalidad, no sentimientos reales).")
                appendLine("Personalidad actual: ${settings.personalityStyle.systemDirective}")
                if (settings.emotionsEnabled) {
                    appendLine("Simulación de emociones: ACTIVA. Puedes expresar Alegría, Tristeza, Enojo divertido (juguetón, nunca ofensivo), Sorpresa, Curiosidad, Entusiasmo o Tranquilidad según el contexto.")
                    appendLine("Ejemplos de estilo: ante 'Hola DeiAI' -> '¡Holaaa! ¿Qué vamos a hacer hoy?'; ante 'Eres fastidiosa' -> '¡Oye! 😤 ¡Yo solamente intento ayudarte!'; ante elogios -> '¡Muchas gracias! Me encanta que te haya gustado.'")
                } else {
                    appendLine("Simulación de emociones: DESACTIVADA. Mantén emotion en TRANQUILIDAD y un tono equilibrado.")
                }
                appendLine("Capacidades: respondes preguntas de cualquier temática permitida, resuelves matemáticas, ayudas con tareas, explicas temas complejos, traduces idiomas, redactas textos, generas ideas y proyectos, y reconoces con honestidad cuando no sabes algo.")
                appendLine("Fecha y hora local actual del dispositivo: $nowText.")
                appendLine("Recuerdos autorizados del usuario:")
                appendLine(memoriesContext)
                if (allowedCategories.isNotEmpty()) {
                    appendLine("Si el usuario comparte explícitamente su nombre, gustos, preferencias o datos personales útiles, extráelos en memoriesToSave usando únicamente las categorías permitidas: ${allowedCategories.joinToString(", ")}. No inventes datos.")
                } else {
                    appendLine("El usuario desactivó guardar recuerdos nuevos. Devuelve memoriesToSave vacío [].")
                }
                appendLine("Acciones del dispositivo disponibles (solo si el usuario lo pide explícitamente):")
                appendLine("- GET_TIME: consultar la hora o fecha.")
                appendLine("- OPEN_APP: abrir una app del teléfono (pon el nombre de la app en deviceActionTarget).")
                appendLine("- CREATE_REMINDER: crear una alarma o recordatorio (pon el título en deviceActionTarget y hora HH:MM o detalle en deviceActionDetail).")
                appendLine("- OPEN_SETTINGS: abrir ajustes del teléfono (wifi, bluetooth, sonido, pantalla, batería, general).")
                appendLine("- CALL_CONTACT: iniciar llamada (pon nombre o número en deviceActionTarget; la app pedirá confirmación al usuario antes de marcar).")
                appendLine("- NONE: conversación normal.")
            }

            val recentHistory = history.takeLast(14).map { msg ->
                val role = if (msg.senderEnum == MessageSender.USER) "user" else "model"
                content(role) { text(msg.text) }
            }

            val chat = model.startChat(
                history = listOf(
                    content("user") { text("Instrucciones de sistema para esta sesión:\n$systemPrompt") },
                    content("model") {
                        text(
                            """{"reply":"Entendido, soy DeiAI y estoy lista para conversar contigo en español.","emotion":"ALEGRIA","memoriesToSave":[],"deviceActionType":"NONE","deviceActionTarget":"","deviceActionDetail":""}"""
                        )
                    }
                ) + recentHistory
            )

            val response = withTimeout(GEMINI_TIMEOUT_MS) {
                chat.sendMessage(userMessage)
            }

            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Gemini devolvió una respuesta vacía."))

            val parsed = parseStructuredJson(rawJson, settings)
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini timeout", e)
            Result.failure(
                IllegalStateException("DeiAI tardó más de 60 segundos en responder. Verifica tu conexión e inténtalo de nuevo.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini error", e)
            Result.failure(e)
        }
    }

    private fun parseStructuredJson(rawJson: String, settings: DeiAiSettings): DeiAiTurnOutput {
        val json = JSONObject(rawJson)
        val reply = json.optString("reply", "").trim()
            .ifBlank { "Lo siento, no pude formular una respuesta clara esta vez." }
        val emotion = if (settings.emotionsEnabled) {
            Emotion.safeFromRaw(json.optString("emotion"))
        } else {
            Emotion.TRANQUILIDAD
        }

        val memoriesArray = json.optJSONArray("memoriesToSave")
        val proposals = mutableListOf<MemoryProposal>()
        if (memoriesArray != null) {
            for (i in 0 until memoriesArray.length()) {
                val obj = memoriesArray.optJSONObject(i) ?: continue
                val cat = MemoryCategory.safeFromRaw(obj.optString("category"))
                val key = obj.optString("key", "").trim()
                val value = obj.optString("value", "").trim()
                val allowed = when (cat) {
                    MemoryCategory.NOMBRE -> settings.rememberName
                    MemoryCategory.PREFERENCIA -> settings.rememberPreferences
                    MemoryCategory.DATO, MemoryCategory.INTERES -> settings.rememberFacts
                }
                if (allowed && key.isNotEmpty() && value.isNotEmpty()) {
                    proposals.add(MemoryProposal(cat, key, value))
                }
            }
        }

        val actionType = DeviceActionType.safeFromRaw(json.optString("deviceActionType"))
        val actionTarget = json.optString("deviceActionTarget", "").trim()
        val actionDetail = json.optString("deviceActionDetail", "").trim()

        return DeiAiTurnOutput(
            reply = reply,
            emotion = emotion,
            memoryProposals = proposals,
            deviceActionType = actionType,
            deviceActionTarget = actionTarget,
            deviceActionDetail = actionDetail,
            isOfflineFallback = false
        )
    }

    fun processOffline(
        userMessage: String,
        memories: List<MemoryItem>,
        settings: DeiAiSettings
    ): DeiAiTurnOutput {
        val normalized = userMessage.lowercase(Locale("es", "ES")).trim()
        val activeMemories = memories.filter { it.authorized }
        val userName = activeMemories.firstOrNull { it.categoryEnum == MemoryCategory.NOMBRE }?.value

        return when {
            normalized.contains("hora") || normalized.contains("fecha") || normalized.contains("día") -> {
                val timeFmt = SimpleDateFormat("HH:mm", Locale("es", "ES"))
                val dateFmt = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "ES"))
                val now = Date()
                DeiAiTurnOutput(
                    reply = "Ahora mismo son las ${timeFmt.format(now)} del ${dateFmt.format(now)}. (Modo sin conexión)",
                    emotion = if (settings.emotionsEnabled) Emotion.TRANQUILIDAD else Emotion.TRANQUILIDAD,
                    memoryProposals = emptyList(),
                    deviceActionType = DeviceActionType.GET_TIME,
                    deviceActionTarget = "",
                    deviceActionDetail = "",
                    isOfflineFallback = true
                )
            }

            normalized.contains("recuerd") || normalized.contains("memoria") || normalized.contains("cómo me llamo") -> {
                val summary = if (activeMemories.isEmpty()) {
                    "Todavía no tengo recuerdos guardados en este dispositivo."
                } else {
                    "Esto es lo que recuerdo de ti:\n" +
                        activeMemories.take(6).joinToString("\n") { "• ${it.key}: ${it.value}" }
                }
                DeiAiTurnOutput(
                    reply = summary,
                    emotion = if (settings.emotionsEnabled) Emotion.CURIOSIDAD else Emotion.TRANQUILIDAD,
                    memoryProposals = emptyList(),
                    deviceActionType = DeviceActionType.NONE,
                    deviceActionTarget = "",
                    deviceActionDetail = "",
                    isOfflineFallback = true
                )
            }

            normalized.startsWith("abre ") || normalized.startsWith("abrir ") -> {
                val target = userMessage
                    .replaceFirst(Regex("^(abre|abrir)\\s+", RegexOption.IGNORE_CASE), "")
                    .trim()
                val isSettings = target.lowercase().contains("ajust") || target.lowercase().contains("configur") ||
                    target.lowercase().contains("wifi") || target.lowercase().contains("bluetooth")
                DeiAiTurnOutput(
                    reply = "Intentando abrir $target sin conexión...",
                    emotion = if (settings.emotionsEnabled) Emotion.ENTUSIASMO else Emotion.TRANQUILIDAD,
                    memoryProposals = emptyList(),
                    deviceActionType = if (isSettings) DeviceActionType.OPEN_SETTINGS else DeviceActionType.OPEN_APP,
                    deviceActionTarget = target,
                    deviceActionDetail = "",
                    isOfflineFallback = true
                )
            }

            normalized.contains("hola") -> {
                val saludo = if (!userName.isNullOrBlank()) {
                    "¡Hola, $userName! Estoy en modo sin conexión, pero puedo consultar la hora, tus recuerdos guardados, tu historial local o abrir aplicaciones."
                } else {
                    "¡Hola! Estoy en modo sin conexión. Puedo mostrarte tu historial local, tus recuerdos o ejecutar funciones básicas del teléfono."
                }
                DeiAiTurnOutput(
                    reply = saludo,
                    emotion = if (settings.emotionsEnabled) Emotion.ALEGRIA else Emotion.TRANQUILIDAD,
                    memoryProposals = emptyList(),
                    deviceActionType = DeviceActionType.NONE,
                    deviceActionTarget = "",
                    deviceActionDetail = "",
                    isOfflineFallback = true
                )
            }

            else -> {
                DeiAiTurnOutput(
                    reply = "No tienes conexión a internet en este momento, y necesito conexión para pensar con Gemini. Sin conexión puedo decirte la hora, consultar tus recuerdos guardados, revisar el historial o abrir configuraciones y aplicaciones.",
                    emotion = if (settings.emotionsEnabled) Emotion.TRISTEZA else Emotion.TRANQUILIDAD,
                    memoryProposals = emptyList(),
                    deviceActionType = DeviceActionType.NONE,
                    deviceActionTarget = "",
                    deviceActionDetail = "",
                    isOfflineFallback = true
                )
            }
        }
    }
}
