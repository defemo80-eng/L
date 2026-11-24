package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryItem
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.viewmodel.DataStreamState
import com.example.ui.viewmodel.DeiAiViewModel

@Composable
fun MemoryScreen(
    viewModel: DeiAiViewModel,
    modifier: Modifier = Modifier
) {
    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryItem?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingMemory = null
                    showEditorDialog = true
                },
                containerColor = ElectricCyan,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_memory_fab")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Agregar recuerdo"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 14.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Memoria inteligente",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tú decides qué información puede recordar DeiAI.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.testTag("clear_all_memories_button")
                        ) {
                            Text("Borrar todo", color = CoralAlert)
                        }
                    }
                }

                // Memory authorization controls
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                NeonPurple.copy(alpha = 0.4f),
                                RoundedCornerShape(20.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Permisos de memoria",
                                style = MaterialTheme.typography.titleMedium,
                                color = NeonPurple,
                                fontWeight = FontWeight.Bold
                            )
                            MemoryToggleRow(
                                title = "Recordar tu nombre",
                                checked = settings.rememberName,
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(rememberName = v) } }
                            )
                            MemoryToggleRow(
                                title = "Recordar preferencias y gustos",
                                checked = settings.rememberPreferences,
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(rememberPreferences = v) } }
                            )
                            MemoryToggleRow(
                                title = "Recordar datos e intereses",
                                checked = settings.rememberFacts,
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(rememberFacts = v) } }
                            )
                            MemoryToggleRow(
                                title = "Preguntar antes de guardar",
                                checked = settings.askBeforeRemembering,
                                onCheckedChange = { v -> viewModel.updateSettings { it.copy(askBeforeRemembering = v) } }
                            )
                        }
                    }
                }

                when (val state = memoriesState) {
                    is DataStreamState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = ElectricCyan)
                            }
                        }
                    }
                    is DataStreamState.Error -> {
                        item {
                            Text(
                                text = state.message,
                                color = CoralAlert,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    is DataStreamState.Success -> {
                        if (state.data.isEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Psychology,
                                            contentDescription = null,
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Text(
                                            text = "No hay recuerdos guardados aún",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Dile a DeiAI cómo te llamas o cuáles son tus gustos, o agrega un recuerdo con el botón +.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(state.data, key = { it.id }) { memory ->
                                MemoryItemCard(
                                    memory = memory,
                                    onToggleAuthorized = { auth ->
                                        viewModel.saveMemoryManual(
                                            existingId = memory.id,
                                            category = memory.categoryEnum,
                                            key = memory.key,
                                            value = memory.value,
                                            authorized = auth
                                        )
                                    },
                                    onEdit = {
                                        editingMemory = memory
                                        showEditorDialog = true
                                    },
                                    onDelete = { viewModel.deleteMemory(memory.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        MemoryEditorDialog(
            initial = editingMemory,
            onDismiss = { showEditorDialog = false },
            onSave = { category, key, value ->
                viewModel.saveMemoryManual(
                    existingId = editingMemory?.id,
                    category = category,
                    key = key,
                    value = value,
                    authorized = editingMemory?.authorized ?: true
                )
                showEditorDialog = false
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Eliminar todos los recuerdos") },
            text = { Text("DeiAI olvidará tu nombre, preferencias y datos guardados. ¿Deseas continuar?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllMemories()
                        showClearConfirm = false
                    }
                ) {
                    Text("Eliminar todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun MemoryToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MemoryItemCard(
    memory: MemoryItem,
    onToggleAuthorized: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(18.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = ElectricCyan.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = memory.categoryEnum.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = memory.authorized,
                        onCheckedChange = onToggleAuthorized
                    )
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Editar recuerdo"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Eliminar recuerdo",
                            tint = CoralAlert
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = memory.key,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = memory.value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MemoryEditorDialog(
    initial: MemoryItem?,
    onDismiss: () -> Unit,
    onSave: (MemoryCategory, String, String) -> Unit
) {
    var category by remember { mutableStateOf(initial?.categoryEnum ?: MemoryCategory.DATO) }
    var key by remember { mutableStateOf(initial?.key ?: "") }
    var value by remember { mutableStateOf(initial?.value ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo recuerdo" else "Editar recuerdo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MemoryCategory.entries.take(2).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MemoryCategory.entries.drop(2).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName) }
                        )
                    }
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { if (it.length <= 100) key = it },
                    label = { Text("Tema o etiqueta (ej. Nombre)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.length <= 500) value = it },
                    label = { Text("Información a recordar") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(category, key, value) },
                enabled = key.isNotBlank() && value.isNotBlank()
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
