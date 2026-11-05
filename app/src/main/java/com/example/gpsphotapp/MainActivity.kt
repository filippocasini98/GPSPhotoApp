package com.example.gpsphotapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.gpsphotapp.ui.AutoRecordingScreen
import com.example.gpsphotapp.ui.GalleryScreen
import com.example.gpsphotapp.ui.ManualSessionScreen
import com.example.gpsphotapp.ui.MapScreen
import com.example.gpsphotapp.ui.SessionsScreen
import com.example.gpsphotapp.ui.SettingsScreen
import com.example.gpsphotapp.ui.theme.GPSphotappTheme
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var locationHelper: LocationHelper

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraManager = CameraManager(this)
        locationHelper = LocationHelper(this)

        NotificationHelper.createChannel(this)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            GPSphotappTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(cameraManager, locationHelper, windowSizeClass)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasAllPermissions()) {
            locationHelper.start()
        }
    }

    override fun onStop() {
        super.onStop()
        locationHelper.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.unbind()
    }

    private fun hasAllPermissions(): Boolean = checkPermissions(this)
}

private data class BottomTab(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(
    cameraManager: CameraManager,
    locationHelper: LocationHelper,
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass
) {
    val context = LocalContext.current
    val viewModel: AppViewModel = viewModel()
    val nav = rememberNavController()

    var hasPermissions by remember { mutableStateOf(checkPermissions(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val essential = results.filterKeys {
            it == Manifest.permission.CAMERA ||
                    it == Manifest.permission.ACCESS_FINE_LOCATION ||
                    it == Manifest.permission.ACCESS_COARSE_LOCATION
        }
        hasPermissions = essential.values.all { it }
        if (hasPermissions) {
            cameraManager.onPermissionsChanged()
            locationHelper.start()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            launcher.launch(permissionList())
        } else {
            cameraManager.onPermissionsChanged()
            locationHelper.start()
        }
    }

    // Lo scatto per distanza deve funzionare da qualunque schermata:
    // il listener vive a livello di app, non della singola tab.
    DisposableEffect(Unit) {
        locationHelper.onLocationUpdate = {
            viewModel.checkDistanceCapture(cameraManager, locationHelper)
        }
        onDispose { locationHelper.onLocationUpdate = null }
    }

    val tabs = listOf(
        BottomTab("auto", R.string.tab_auto, Icons.Default.RadioButtonChecked),
        BottomTab("manual", R.string.tab_manual, Icons.Default.TouchApp),
        BottomTab("sessions", R.string.sessions, Icons.Default.Folder)
    )

    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTabRoute = tabs.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    Scaffold(
        bottomBar = {
            if (onTabRoute) {
                NavigationBar(
                    modifier = Modifier.height(64.dp),
                    tonalElevation = 0.dp
                ) {
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            alwaysShowLabel = false,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                val showBadge =
                                    (tab.route == "auto" && viewModel.isAutoRecording) ||
                                            (tab.route == "manual" && viewModel.isManualSession)
                                if (showBadge) {
                                    BadgedBox(badge = { Badge() }) {
                                        Icon(tab.icon, contentDescription = stringResource(tab.labelRes))
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = stringResource(tab.labelRes))
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "auto") {
            composable("auto") {
                if (hasPermissions) {
                    AutoRecordingScreen(
                        vm = viewModel,
                        cameraManager = cameraManager,
                        locationHelper = locationHelper,
                        windowSizeClass = windowSizeClass,
                        padding = padding,
                        onNavigateSettings = { nav.navigate("settings") }
                    )
                } else {
                    PermissionsRequired(
                        onGrant = { launcher.launch(permissionList()) },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
            composable("manual") {
                if (hasPermissions) {
                    ManualSessionScreen(
                        vm = viewModel,
                        cameraManager = cameraManager,
                        locationHelper = locationHelper,
                        windowSizeClass = windowSizeClass,
                        padding = padding,
                        onNavigateSettings = { nav.navigate("settings") }
                    )
                } else {
                    PermissionsRequired(
                        onGrant = { launcher.launch(permissionList()) },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
            composable("sessions") {
                SessionsScreen(
                    vm = viewModel,
                    padding = padding,
                    onOpenGallery = { dir ->
                        nav.navigate("gallery/${URLEncoder.encode(dir.absolutePath, "UTF-8")}")
                    },
                    onOpenMap = { dir ->
                        nav.navigate("map/${URLEncoder.encode(dir.absolutePath, "UTF-8")}")
                    },
                    onResumeSession = { session ->
                        val resumed = viewModel.resumeClosedSession(session, cameraManager, locationHelper)
                        if (resumed) {
                            nav.navigate(if (session.isAuto) "auto" else "manual") {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        resumed
                    }
                )
            }
            composable("gallery/{path}") { entry ->
                val pathEnc = entry.arguments?.getString("path") ?: return@composable
                val path = URLDecoder.decode(pathEnc, "UTF-8")
                GalleryScreen(
                    vm = viewModel,
                    sessionDir = File(path),
                    onBack = { nav.popBackStack() }
                )
            }
            composable("map/{path}") { entry ->
                val pathEnc = entry.arguments?.getString("path") ?: return@composable
                val path = URLDecoder.decode(pathEnc, "UTF-8")
                MapScreen(
                    sessionDir = File(path),
                    onBack = { nav.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    vm = viewModel,
                    rawSupported = cameraManager.rawSupported,
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun PermissionsRequired(onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.permissions_required_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.permissions_required_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        Button(onClick = onGrant) { Text(stringResource(R.string.grant_permissions)) }
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.padding(top = 8.dp)
        ) { Text(stringResource(R.string.open_app_settings)) }
    }
}

private fun permissionList(): Array<String> {
    val list = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        list.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    return list.toTypedArray()
}

private fun checkPermissions(context: android.content.Context): Boolean {
    val essential = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    return essential.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
