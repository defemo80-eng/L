package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.actions.DeviceActionExecutor
import com.example.data.ai.DeiAiEngine
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.SettingsRepository
import com.example.security.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DeiAiTheme
import com.example.ui.theme.ElectricCyan
import com.example.ui.viewmodel.DeiAiViewModel
import com.example.voice.DeiAiVoiceService
import com.example.voice.VoiceManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class DeiAiTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    ASISTENTE("asistente", "DeiAI", Icons.Rounded.Mic, Icons.Outlined.Mic),
    HISTORIAL("historial", "Historial", Icons.Rounded.History, Icons.Outlined.History),
    MEMORIA("memoria", "Memoria", Icons.Rounded.Psychology, Icons.Outlined.Psychology),
    AJUSTES("ajustes", "Ajustes", Icons.Rounded.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    private var pendingWakeCommand by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)
        handleWakeIntent(intent)

        enableEdgeToEdge()

        setContent {
            AppRoot(
                pendingWakeCommand = pendingWakeCommand,
                onWakeCommandConsumed = { pendingWakeCommand = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWakeIntent(intent)
    }

    private fun handleWakeIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(DeiAiVoiceService.EXTRA_WAKE_TRIGGERED, false) == true) {
            val cmd = intent.getStringExtra(DeiAiVoiceService.EXTRA_WAKE_COMMAND).orEmpty()
            pendingWakeCommand = cmd
            intent.removeExtra(DeiAiVoiceService.EXTRA_WAKE_TRIGGERED)
        }
    }
}

@Composable
private fun AppRoot(
    pendingWakeCommand: String?,
    onWakeCommandConsumed: () -> Unit
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    if (currentUser == null) {
        DeiAiTheme(darkTheme = true) {
            Surface(modifier = Modifier.fillMaxSize()) {
                AuthScreen(
                    onAuthSuccess = {
                        currentUser = Firebase.auth.currentUser
                    }
                )
            }
        }
    } else {
        val user = currentUser!!
        val uid = user.uid
        key(uid) {
            val vm: DeiAiViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        DeiAiViewModel(
                            appContext = app,
                            currentUserId = uid,
                            displayName = user.displayName,
                            firestoreRepo = FirestoreRepository(db),
                            settingsRepo = SettingsRepository(app),
                            aiEngine = DeiAiEngine(app),
                            voiceManager = VoiceManager(app),
                            actionExecutor = DeviceActionExecutor(app)
                        )
                    }
                }
            )

            val settings by vm.settings.collectAsStateWithLifecycle()

            LaunchedEffect(pendingWakeCommand) {
                if (pendingWakeCommand != null) {
                    vm.handleWakeWordTriggered(pendingWakeCommand)
                    onWakeCommandConsumed()
                }
            }

            DeiAiTheme(darkTheme = settings.darkMode) {
                DeiAiMainShell(
                    viewModel = vm,
                    onSignedOut = { currentUser = Firebase.auth.currentUser }
                )
            }
        }
    }
}

@Composable
private fun DeiAiMainShell(
    viewModel: DeiAiViewModel,
    onSignedOut: () -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(DeiAiTab.ASISTENTE) }

    if (currentTab != DeiAiTab.ASISTENTE) {
        BackHandler {
            currentTab = DeiAiTab.ASISTENTE
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DeiAiTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DeiAiTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentTab) {
                        DeiAiTab.ASISTENTE -> AssistantScreen(
                            viewModel = viewModel,
                            onOpenSettings = { currentTab = DeiAiTab.AJUSTES }
                        )
                        DeiAiTab.HISTORIAL -> HistoryScreen(
                            viewModel = viewModel,
                            onOpenConversation = { convId ->
                                viewModel.selectConversation(convId)
                                currentTab = DeiAiTab.ASISTENTE
                            },
                            onNewConversation = {
                                currentTab = DeiAiTab.ASISTENTE
                            }
                        )
                        DeiAiTab.MEMORIA -> MemoryScreen(viewModel = viewModel)
                        DeiAiTab.AJUSTES -> SettingsScreen(
                            viewModel = viewModel,
                            onSignedOut = onSignedOut
                        )
                    }
                }
            }
        }
    }
}
