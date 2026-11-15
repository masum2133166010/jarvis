package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.components.HolographicBackground
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.JarvisTheme
import com.example.ui.theme.LocalJarvisHudColors

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_CHAT = "chat"
const val ROUTE_TOOLS = "tools"
const val ROUTE_SETTINGS = "settings"

private data class JarvisNavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JarvisAppRoot()
        }
    }
}

@Composable
fun JarvisAppRoot(viewModel: MainViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    JarvisTheme(themeMode = settings.themeMode) {
        val context = LocalContext.current
        val hud = LocalJarvisHudColors.current
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        var pendingWakeAction by remember { mutableStateOf(false) }

        val micPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                if (pendingWakeAction) viewModel.toggleWakeListening()
                else viewModel.toggleVoiceListening()
            } else {
                viewModel.emitSnackbar("Microphone permission is needed for JARVIS voice input.")
            }
        }

        val requestMicThenListen: () -> Unit = {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.toggleVoiceListening()
            } else {
                pendingWakeAction = false
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        val requestMicThenWake: () -> Unit = {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.toggleWakeListening()
            } else {
                pendingWakeAction = true
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        LaunchedEffect(Unit) {
            viewModel.snackbarMessage.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }

        LaunchedEffect(Unit) {
            viewModel.navigateToChatSignal.collect {
                navController.navigate(ROUTE_CHAT) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }

        val destinations = remember {
            listOf(
                JarvisNavDestination(ROUTE_DASHBOARD, R.string.nav_dashboard, Icons.Filled.Hub, Icons.Outlined.Hub, "nav_core"),
                JarvisNavDestination(ROUTE_CHAT, R.string.nav_chat, Icons.Filled.Forum, Icons.Outlined.Forum, "nav_chat"),
                JarvisNavDestination(ROUTE_TOOLS, R.string.nav_tools, Icons.Filled.Build, Icons.Outlined.Build, "nav_tools"),
                JarvisNavDestination(ROUTE_SETTINGS, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
            )
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination

        val navigateTo: (String) -> Unit = { route ->
            viewModel.playTapSound()
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        HolographicBackground {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 780.dp

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar(
                                containerColor = hud.panelGlass,
                                windowInsets = WindowInsets.navigationBars
                            ) {
                                destinations.forEach { item ->
                                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                    val label = stringResource(item.labelRes)
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTo(item.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = label
                                            )
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag(item.testTag)
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
                        if (useRail) {
                            NavigationRail(
                                containerColor = hud.panelGlass,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                            ) {
                                destinations.forEach { item ->
                                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                    val label = stringResource(item.labelRes)
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateTo(item.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = label
                                            )
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag(item.testTag)
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = ROUTE_DASHBOARD
                            ) {
                                composable(ROUTE_DASHBOARD) {
                                    DashboardScreen(
                                        viewModel = viewModel,
                                        onOpenChat = { navigateTo(ROUTE_CHAT) },
                                        onOpenSettings = { navigateTo(ROUTE_SETTINGS) },
                                        onRequestMicPermissionThenListen = requestMicThenListen,
                                        onRequestMicPermissionThenWake = requestMicThenWake
                                    )
                                }
                                composable(ROUTE_CHAT) {
                                    ChatScreen(
                                        viewModel = viewModel,
                                        onOpenSettings = { navigateTo(ROUTE_SETTINGS) },
                                        onRequestMicPermissionThenListen = requestMicThenListen
                                    )
                                }
                                composable(ROUTE_TOOLS) {
                                    ToolsScreen(viewModel = viewModel)
                                }
                                composable(ROUTE_SETTINGS) {
                                    SettingsScreen(
                                        viewModel = viewModel,
                                        onRequestMicPermissionThenWake = requestMicThenWake
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
