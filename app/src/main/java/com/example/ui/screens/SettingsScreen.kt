package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PersonalityStyle
import com.example.ui.auth.signOutUser
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.NeonPurple
import com.example.ui.viewmodel.DeiAiViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: DeiAiViewModel,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val availableVoices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val isWakeServiceRunning by viewModel.isWakeServiceRunning.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.setWakeWordEnabled(true)
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    var showVoiceDropdown by remember { mutableStateOf(false) }
    var showLanguageDropdown by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshVoices()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Configuración",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Personaliza la voz, emociones, memoria, activación y privacidad de DeiAI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1. Voice & Audio
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.RecordVoiceOver,
                    title = "Voz y audio"
                ) {
                    SettingsSwitchRow(
                        title = "Respuestas habladas automáticas",
                        subtitle = "DeiAI leerá en voz alta cada respuesta.",
                        checked = settings.autoSpeak,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(autoSpeak = v) } }
                    )

                    SettingsSwitchRow(
                        title = "Conversación continua",
                        subtitle = "Vuelve a escuchar tras responder sin tocar el micrófono.",
                        checked = settings.continuousConversation,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(continuousConversation = v) } }
                    )

                    // Voice selector
                    Box {
                        val currentVoiceLabel = availableVoices
                            .firstOrNull { it.name == settings.voiceName }
                            ?.displayLabel ?: "Voz predeterminada del sistema"

                        OutlinedButton(
                            onClick = {
                                viewModel.refreshVoices()
                                showVoiceDropdown = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Voz: $currentVoiceLabel")
                        }
                        DropdownMenu(
                            expanded = showVoiceDropdown,
                            onDismissRequest = { showVoiceDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Predeterminada del sistema") },
                                onClick = {
                                    viewModel.updateSettings { it.copy(voiceName = "") }
                                    showVoiceDropdown = false
                                }
                            )
                            availableVoices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice.displayLabel) },
                                    onClick = {
                                        viewModel.updateSettings { it.copy(voiceName = voice.name) }
                                        showVoiceDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Speech rate
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Velocidad de voz", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${((settings.speechRate * 10).roundToInt() / 10.0)}x",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan
                            )
                        }
                        Slider(
                            value = settings.speechRate,
                            onValueChange = { rate -> viewModel.updateSettings { it.copy(speechRate = rate) } },
                            valueRange = 0.6f..1.8f,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan
                            )
                        )
                    }

                    // Volume
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Volumen de DeiAI", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${(settings.volume * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = ElectricCyan
                            )
                        }
                        Slider(
                            value = settings.volume,
                            onValueChange = { vol -> viewModel.updateSettings { it.copy(volume = vol) } },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonPurple,
                                activeTrackColor = NeonPurple
                            )
                        )
                    }

                    Button(
                        onClick = { viewModel.testVoice() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "Probar voz"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Probar voz actual")
                    }
                }
            }

            // 2. Wake Word («Hola DeiAI»)
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Mic,
                    title = "Activación por voz («Hola DeiAI»)"
                ) {
                    SettingsSwitchRow(
                        title = "Escucha en segundo plano",
                        subtitle = if (isWakeServiceRunning) {
                            "Servicio activo • Di «Hola DeiAI» incluso con la pantalla bloqueada si Android lo permite."
                        } else {
                            "Mantiene una notificación visible para responder al decir «Hola DeiAI»."
                        },
                        checked = settings.wakeWordEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasMicPermission) {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.setWakeWordEnabled(enabled)
                            }
                        }
                    )
                }
            }

            // 3. Personality & Simulated Emotions
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Psychology,
                    title = "Personalidad y emociones"
                ) {
                    SettingsSwitchRow(
                        title = "Emociones simuladas",
                        subtitle = "Cambia expresiones del avatar, tono de voz y respuestas.",
                        checked = settings.emotionsEnabled,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(emotionsEnabled = v) } }
                    )

                    Text(
                        text = "Estilo de personalidad:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    PersonalityStyle.entries.chunked(2).forEach { rowStyles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowStyles.forEach { style ->
                                FilterChip(
                                    selected = settings.personalityStyle == style,
                                    onClick = { viewModel.updateSettings { it.copy(personalityStyle = style) } },
                                    label = { Text(style.displayName) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Nota: Las emociones de DeiAI son una simulación de personalidad, no sentimientos reales.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. Language, Appearance & Notifications
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Palette,
                    title = "Idioma, apariencia y notificaciones"
                ) {
                    SettingsSwitchRow(
                        title = "Modo oscuro futurista",
                        subtitle = "Colores negro espacial, azul eléctrico y morado.",
                        checked = settings.darkMode,
                        onCheckedChange = { v -> viewModel.updateSettings { it.copy(darkMode = v) } }
                    )

                    SettingsSwitchRow(
                        title = "Notificaciones del asistente",
                        subtitle = "Alertas del servicio de escucha y confirmaciones.",
                        checked = settings.notificationsEnabled,
                        onCheckedChange = { v ->
                            if (v && !hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.updateSettings { it.copy(notificationsEnabled = v) }
                        }
                    )

                    val languages = listOf(
                        "es-ES" to "Español (España)",
                        "es-MX" to "Español (México)",
                        "es-AR" to "Español (Argentina)",
                        "es-US" to "Español (Latinoamérica / EE. UU.)"
                    )
                    val currentLangLabel = languages.firstOrNull { it.first == settings.languageTag }?.second
                        ?: settings.languageTag

                    Box {
                        OutlinedButton(
                            onClick = { showLanguageDropdown = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Idioma de voz: $currentLangLabel")
                        }
                        DropdownMenu(
                            expanded = showLanguageDropdown,
                            onDismissRequest = { showLanguageDropdown = false }
                        ) {
                            languages.forEach { (tag, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.updateSettings { it.copy(languageTag = tag) }
                                        showLanguageDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 5. Permissions & Privacy
            item {
                SettingsSectionCard(
                    icon = Icons.Rounded.Security,
                    title = "Permisos y privacidad"
                ) {
                    PermissionStatusRow(
                        name = "Micrófono (voz y «Hola DeiAI»)",
                        granted = hasMicPermission,
                        onRequest = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermissionStatusRow(
                            name = "Notificaciones (servicio en 2.º plano)",
                            granted = hasNotifPermission,
                            onRequest = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Rounded.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Abrir permisos del sistema Android")
                    }

                    Text(
                        text = "Privacidad: DeiAI no guarda grabaciones de audio sin tu autorización. Todas las llamadas a Gemini están protegidas por Firebase App Check.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { showClearHistoryDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralAlert),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_clear_history_button")
                    ) {
                        Text("Eliminar todo el historial de conversaciones")
                    }

                    Button(
                        onClick = {
                            if (isWakeServiceRunning) {
                                viewModel.setWakeWordEnabled(false)
                            }
                            signOutUser(credentialManager, scope, onSignedOut)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = CoralAlert
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Cerrar sesión"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cerrar sesión de Google")
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Eliminar todo el historial") },
            text = { Text("¿Seguro que deseas borrar todas tus conversaciones? Los recuerdos autorizados se mantendrán salvo que los borres en Memoria.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllConversations()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text("Eliminar todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                RoundedCornerShape(22.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = ElectricCyan
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionStatusRow(
    name: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (granted) "Concedido" else "Pendiente",
                style = MaterialTheme.typography.labelSmall,
                color = if (granted) EmeraldPulse else CoralAlert
            )
        }
        if (!granted) {
            TextButton(onClick = onRequest) {
                Text("Permitir")
            }
        }
    }
}
