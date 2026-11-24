package com.example.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import com.example.data.model.DeviceActionType
import com.example.data.model.PendingDeviceAction
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface DeviceActionResult {
    data class Executed(val summary: String) : DeviceActionResult
    data class RequiresConfirmation(val pendingAction: PendingDeviceAction) : DeviceActionResult
    data class Failed(val reason: String) : DeviceActionResult
    data object None : DeviceActionResult
}

class DeviceActionExecutor(private val context: Context) {

    fun executeOrPrepare(
        type: DeviceActionType,
        target: String,
        detail: String
    ): DeviceActionResult {
        return when (type) {
            DeviceActionType.NONE -> DeviceActionResult.None
            DeviceActionType.GET_TIME -> {
                val fmt = SimpleDateFormat("HH:mm 'del' EEEE d 'de' MMMM", Locale("es", "ES"))
                DeviceActionResult.Executed("Hora consultada: ${fmt.format(Date())}")
            }
            DeviceActionType.OPEN_SETTINGS -> openSettings(target)
            DeviceActionType.OPEN_APP -> openApp(target)
            DeviceActionType.CREATE_REMINDER -> createReminderOrAlarm(target, detail)
            DeviceActionType.CALL_CONTACT -> {
                val safeTarget = target.ifBlank { "contacto solicitado" }
                DeviceActionResult.RequiresConfirmation(
                    PendingDeviceAction(
                        type = DeviceActionType.CALL_CONTACT,
                        target = safeTarget,
                        detail = detail,
                        confirmationMessage = "¿Confirmas abrir el marcador telefónico para llamar a «$safeTarget»?"
                    )
                )
            }
        }
    }

    fun executeConfirmedAction(action: PendingDeviceAction): DeviceActionResult {
        return when (action.type) {
            DeviceActionType.CALL_CONTACT -> {
                val digits = action.target.filter { it.isDigit() || it == '+' }
                val uri = if (digits.length >= 3) {
                    Uri.parse("tel:$digits")
                } else {
                    Uri.parse("tel:")
                }
                val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    DeviceActionResult.Executed("Marcador abierto para ${action.target}")
                } catch (e: Exception) {
                    DeviceActionResult.Failed("No se pudo abrir la aplicación de teléfono.")
                }
            }
            else -> DeviceActionResult.None
        }
    }

    private fun openSettings(target: String): DeviceActionResult {
        val norm = normalize(target)
        val action = when {
            norm.contains("wifi") || norm.contains("wi-fi") || norm.contains("internet") ->
                Settings.ACTION_WIFI_SETTINGS
            norm.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
            norm.contains("sonido") || norm.contains("volumen") || norm.contains("audio") ->
                Settings.ACTION_SOUND_SETTINGS
            norm.contains("pantalla") || norm.contains("brillo") ->
                Settings.ACTION_DISPLAY_SETTINGS
            norm.contains("bateria") -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            norm.contains("ubicacion") || norm.contains("gps") ->
                Settings.ACTION_LOCATION_SOURCE_SETTINGS
            norm.contains("aplicacion") || norm.contains("app") ->
                Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            DeviceActionResult.Executed("Ajustes abiertos (${target.ifBlank { "General" }})")
        } catch (e: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                DeviceActionResult.Executed("Ajustes generales abiertos")
            } catch (ex: Exception) {
                DeviceActionResult.Failed("No fue posible abrir la configuración del dispositivo.")
            }
        }
    }

    private fun openApp(target: String): DeviceActionResult {
        val norm = normalize(target)
        if (norm.isBlank()) {
            return DeviceActionResult.Failed("No especificaste qué aplicación abrir.")
        }

        // Common system intents first
        val systemIntent: Intent? = when {
            norm.contains("camara") -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            norm.contains("reloj") || norm.contains("alarma") -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            norm.contains("telefono") || norm.contains("llamar") -> Intent(Intent.ACTION_DIAL)
            norm.contains("navegador") || norm.contains("chrome") || norm.contains("web") ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            norm.contains("mapa") || norm.contains("maps") ->
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
            else -> null
        }

        if (systemIntent != null) {
            systemIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (systemIntent.resolveActivity(context.packageManager) != null) {
                return try {
                    context.startActivity(systemIntent)
                    DeviceActionResult.Executed("Aplicación abierta: $target")
                } catch (_: Exception) {
                    DeviceActionResult.Failed("No se pudo abrir $target.")
                }
            }
        }

        // Search installed launcher apps declared via <queries>
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val match = apps.firstOrNull { resolveInfo ->
            val label = normalize(resolveInfo.loadLabel(pm).toString())
            label.contains(norm) || norm.contains(label)
        }

        return if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(launchIntent)
                    DeviceActionResult.Executed("Abriendo ${match.loadLabel(pm)}")
                } catch (_: Exception) {
                    DeviceActionResult.Failed("No se pudo iniciar ${match.loadLabel(pm)}.")
                }
            } else {
                DeviceActionResult.Failed("No se encontró lanzador para $target.")
            }
        } else {
            DeviceActionResult.Failed("No encontré la aplicación «$target» instalada en este teléfono.")
        }
    }

    private fun createReminderOrAlarm(target: String, detail: String): DeviceActionResult {
        val timeRegex = Regex("(\\d{1,2})[:h.](\\d{2})")
        val match = timeRegex.find("$target $detail")
        return if (match != null) {
            val hour = match.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 8
            val minute = match.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, target.ifBlank { "Recordatorio DeiAI" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                DeviceActionResult.Executed("Alarma/Recordatorio preparado para las %02d:%02d".format(hour, minute))
            } catch (_: Exception) {
                launchCalendarReminder(target, detail)
            }
        } else {
            launchCalendarReminder(target, detail)
        }
    }

    private fun launchCalendarReminder(target: String, detail: String): DeviceActionResult {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, target.ifBlank { "Recordatorio DeiAI" })
            if (detail.isNotBlank()) {
                putExtra(CalendarContract.Events.DESCRIPTION, detail)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            DeviceActionResult.Executed("Recordatorio preparado en Calendario: ${target.ifBlank { "Recordatorio" }}")
        } catch (_: Exception) {
            DeviceActionResult.Failed("No hay una app de Calendario o Reloj disponible para crear el recordatorio.")
        }
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale("es", "ES")), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").trim()
    }
}
