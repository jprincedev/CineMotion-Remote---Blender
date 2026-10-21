package com.example

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.screens.LiveScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.TakesScreen
import com.example.ui.theme.CineAmber
import com.example.ui.theme.CineBlack
import com.example.ui.theme.CineBorder
import com.example.ui.theme.CineSurface
import com.example.ui.theme.CineSurfaceVariant
import com.example.ui.theme.CineTextSecondary
import com.example.ui.theme.CineTextTertiary
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppTab
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainContent(
                    viewModel = viewModel,
                    onSetOrientation = { landscape ->
                        requestedOrientation = if (landscape) {
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MainContent(
    viewModel: MainViewModel,
    onSetOrientation: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Orientation lock: Live screen is locked to Landscape; others to Portrait
    LaunchedEffect(currentTab) {
        if (currentTab == AppTab.LIVE) {
            onSetOrientation(true)
        } else {
            onSetOrientation(false)
        }
    }

    // Collect UI event toasts/snackbars
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CineBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Hide bottom navigation in Live camera operator mode for full immersion
            if (currentTab != AppTab.LIVE) {
                NavigationBar(
                    containerColor = CineSurface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == AppTab.SETUP,
                        onClick = { viewModel.setTab(AppTab.SETUP) },
                        icon = { Icon(Icons.Default.Tune, contentDescription = "Setup") },
                        label = { Text("Setup", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CineBlack,
                            selectedTextColor = CineAmber,
                            indicatorColor = CineAmber,
                            unselectedIconColor = CineTextSecondary,
                            unselectedTextColor = CineTextTertiary
                        ),
                        modifier = Modifier.testTag("tab_setup")
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.LIVE,
                        onClick = {
                            val activity = context as? ComponentActivity
                            if (activity != null) {
                                viewModel.startCamera(activity)
                            }
                        },
                        icon = { Icon(Icons.Default.Videocam, contentDescription = "Live") },
                        label = { Text("Live", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CineBlack,
                            selectedTextColor = CineAmber,
                            indicatorColor = CineAmber,
                            unselectedIconColor = CineTextSecondary,
                            unselectedTextColor = CineTextTertiary
                        ),
                        modifier = Modifier.testTag("tab_live")
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.TAKES,
                        onClick = { viewModel.setTab(AppTab.TAKES) },
                        icon = { Icon(Icons.Default.Movie, contentDescription = "Takes") },
                        label = { Text("Takes", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CineBlack,
                            selectedTextColor = CineAmber,
                            indicatorColor = CineAmber,
                            unselectedIconColor = CineTextSecondary,
                            unselectedTextColor = CineTextTertiary
                        ),
                        modifier = Modifier.testTag("tab_takes")
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.SETTINGS,
                        onClick = { viewModel.setTab(AppTab.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CineBlack,
                            selectedTextColor = CineAmber,
                            indicatorColor = CineAmber,
                            unselectedIconColor = CineTextSecondary,
                            unselectedTextColor = CineTextTertiary
                        ),
                        modifier = Modifier.testTag("tab_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.SETUP -> SetupScreen(viewModel = viewModel)
                AppTab.LIVE -> LiveScreen(viewModel = viewModel)
                AppTab.TAKES -> TakesScreen(viewModel = viewModel)
                AppTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
