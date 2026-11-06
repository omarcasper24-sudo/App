package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.ActivityGeofenceReceiver
import com.example.service.LifeTrackLocationService
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LiveTrackingScreen
import com.example.ui.screens.MovementHistoryScreen
import com.example.ui.screens.PlacesLocationsScreen
import com.example.ui.screens.ReportsAnalyticsScreen
import com.example.ui.screens.SettingsPrivacyScreen
import com.example.ui.screens.SleepAnalysisScreen
import com.example.ui.screens.WorkAnalysisScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.LocalAppStrings
import com.example.util.getBilingualStrings
import com.example.viewmodel.LifeTrackViewModel
import com.google.android.gms.location.DetectedActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                LifeTrackRootApp()
            }
        }
    }
}

@Composable
fun LifeTrackRootApp() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isOfflineOnlySession by rememberSaveable { mutableStateOf(false) }
    var unauthArabic by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null && !isOfflineOnlySession) {
        val strings = getBilingualStrings(unauthArabic)
        val layoutDir = if (unauthArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(
            LocalAppStrings provides strings,
            LocalLayoutDirection provides layoutDir
        ) {
            AuthScreen(
                isArabic = unauthArabic,
                onToggleLanguage = { unauthArabic = !unauthArabic },
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onContinueOffline = { isOfflineOnlySession = true }
            )
        }
    } else {
        val activeUid = currentUser?.uid ?: "local_offline_user"
        LifeTrackMainFeatureShell(
            currentUserId = activeUid,
            isOfflineOnly = isOfflineOnlySession && currentUser == null,
            onSignOutToAuth = {
                isOfflineOnlySession = false
                currentUser = Firebase.auth.currentUser
            }
        )
    }
}

private data class NavSectionItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeTrackMainFeatureShell(
    currentUserId: String,
    isOfflineOnly: Boolean,
    onSignOutToAuth: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: LifeTrackViewModel = viewModel(
        key = "${currentUserId}_$isOfflineOnly",
        factory = LifeTrackViewModel.provideFactory(context, currentUserId, isOfflineOnly)
    )

    // Register dynamic BroadcastReceiver for ActivityGeofenceReceiver.ACTION_APP_STATE_EVENT
    // so broadcast events from ActivityRecognitionClient update the main application state directly.
    DisposableEffect(context, viewModel) {
        val appStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == ActivityGeofenceReceiver.ACTION_APP_STATE_EVENT) {
                    val userState = intent.getStringExtra(ActivityGeofenceReceiver.EXTRA_USER_STATE) ?: "STATIONARY"
                    val detectedType = intent.getIntExtra(
                        ActivityGeofenceReceiver.EXTRA_DETECTED_ACTIVITY_TYPE,
                        DetectedActivity.STILL
                    )
                    val confidence = intent.getIntExtra(ActivityGeofenceReceiver.EXTRA_ACTIVITY_CONFIDENCE, 92)
                    val transition = intent.getStringExtra(ActivityGeofenceReceiver.EXTRA_TRANSITION_TYPE) ?: "ENTER"
                    val timestamp = intent.getLongExtra(
                        ActivityGeofenceReceiver.EXTRA_EVENT_TIMESTAMP,
                        System.currentTimeMillis()
                    )
                    viewModel.onExternalBroadcastReceived(
                        userState = userState,
                        detectedType = detectedType,
                        confidence = confidence,
                        transitionType = transition,
                        timestampEpochMs = timestamp
                    )
                }
            }
        }
        val filter = IntentFilter(ActivityGeofenceReceiver.ACTION_APP_STATE_EVENT)
        ContextCompat.registerReceiver(
            context,
            appStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            try {
                context.unregisterReceiver(appStateReceiver)
            } catch (_: Exception) {
            }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = remember(uiState.isArabic) { getBilingualStrings(uiState.isArabic) }
    val layoutDirection = if (uiState.isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    var currentSection by rememberSaveable { mutableIntStateOf(0) }

    if (currentSection != 0) {
        BackHandler {
            currentSection = 0
        }
    }

    val allSections = remember(strings) {
        listOf(
            NavSectionItem(0, strings.navDashboard, Icons.Default.Dashboard, "nav_dashboard"),
            NavSectionItem(1, strings.navLive, Icons.Default.GpsFixed, "nav_live"),
            NavSectionItem(2, strings.navHistory, Icons.Default.History, "nav_history"),
            NavSectionItem(3, strings.navPlaces, Icons.Default.Place, "nav_places"),
            NavSectionItem(4, strings.navSleep, Icons.Default.Bedtime, "nav_sleep"),
            NavSectionItem(5, strings.navWork, Icons.Default.Work, "nav_work"),
            NavSectionItem(6, strings.navReports, Icons.Default.Analytics, "nav_reports"),
            NavSectionItem(7, strings.navSettings, Icons.Default.Settings, "nav_settings")
        )
    }

    val bottomSections = remember(allSections) {
        listOf(
            allSections[0], // Dashboard
            allSections[1], // Live
            allSections[2], // History
            allSections[3], // Places
            allSections[6]  // Reports
        )
    }

    CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalLayoutDirection provides layoutDirection
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = strings.appTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${allSections.getOrNull(currentSection)?.label ?: strings.appSubtitle} • ${uiState.liveTelemetry.currentTransportMode} (${uiState.liveTelemetry.activityConfidence}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.toggleLanguage() },
                                modifier = Modifier.testTag("top_language_toggle")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "Toggle Arabic / English"
                                )
                            }
                            IconButton(
                                onClick = { currentSection = 7 },
                                modifier = Modifier.testTag("top_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = strings.navSettings
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(allSections) { idx, item ->
                            FilterChip(
                                selected = currentSection == idx,
                                onClick = { currentSection = idx },
                                label = { Text(item.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("chip_${item.tag}")
                            )
                        }
                    }

                    AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
                        uiState.statusBannerMessage?.let { msg ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "✕",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier
                                            .clickable { viewModel.clearStatusBanner() }
                                            .padding(4.dp)
                                            .testTag("dismiss_banner")
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    windowInsets = WindowInsets.navigationBars
                ) {
                    bottomSections.forEach { item ->
                        NavigationBarItem(
                            selected = currentSection == item.index,
                            onClick = { currentSection = item.index },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, maxLines = 1) },
                            modifier = Modifier.testTag("bottom_${item.tag}")
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
                when (currentSection) {
                    0 -> DashboardScreen(
                        uiState = uiState,
                        onNavigateToSection = { currentSection = it },
                        onToggleTracking = { enable ->
                            if (enable) {
                                LifeTrackLocationService.startService(context, uiState.liveTelemetry.isBatterySaverEnabled)
                            } else {
                                LifeTrackLocationService.stopService(context)
                            }
                        },
                        onBroadcastActivityState = { stateName ->
                            viewModel.dispatchActivityStateBroadcast(context, stateName, 96, "ENTER")
                        }
                    )
                    1 -> LiveTrackingScreen(
                        uiState = uiState,
                        onToggleTracking = { enable ->
                            if (enable) {
                                LifeTrackLocationService.startService(context, uiState.liveTelemetry.isBatterySaverEnabled)
                            } else {
                                LifeTrackLocationService.stopService(context)
                            }
                        },
                        onBroadcastActivityState = { stateName, transition ->
                            viewModel.dispatchActivityStateBroadcast(context, stateName, 95, transition)
                        },
                        onSaveCompletedTrip = { mode, dist, mins, startLbl, endLbl ->
                            val pts = uiState.liveTelemetry.activeTripPoints
                            val sLat = pts.firstOrNull()?.lat ?: uiState.liveTelemetry.currentLatitude
                            val sLng = pts.firstOrNull()?.lng ?: uiState.liveTelemetry.currentLongitude
                            val eLat = pts.lastOrNull()?.lat ?: (sLat + 0.012)
                            val eLng = pts.lastOrNull()?.lng ?: (sLng + 0.009)
                            viewModel.recordTrip(
                                transportMode = mode,
                                distanceKm = dist,
                                durationMinutes = mins,
                                startLabel = startLbl,
                                endLabel = endLbl,
                                startLat = sLat,
                                startLng = sLng,
                                endLat = eLat,
                                endLng = eLng,
                                points = pts
                            )
                        }
                    )
                    2 -> MovementHistoryScreen(
                        uiState = uiState,
                        onSelectDate = { viewModel.selectDateKey(it) }
                    )
                    3 -> PlacesLocationsScreen(
                        uiState = uiState,
                        onSaveOrUpdatePlace = { id, name, cat, lat, lng, rad, visits, mins ->
                            viewModel.saveOrUpdatePlace(id, name, cat, lat, lng, rad, visits, mins)
                        }
                    )
                    4 -> SleepAnalysisScreen(
                        uiState = uiState,
                        onSaveSleepLog = { dateKey, bH, bM, wH, wM, notes ->
                            viewModel.saveOrUpdateSleepLog(dateKey, bH, bM, wH, wM, notes)
                        }
                    )
                    5 -> WorkAnalysisScreen(
                        uiState = uiState,
                        onSaveWorkSession = { id, dateKey, workplace, aH, aM, dH, dM, type ->
                            viewModel.saveOrUpdateWorkSession(id, dateKey, workplace, aH, aM, dH, dM, type)
                        }
                    )
                    6 -> ReportsAnalyticsScreen(
                        uiState = uiState,
                        onPostStatusMessage = { viewModel.postStatusMessage(it) }
                    )
                    7 -> SettingsPrivacyScreen(
                        uiState = uiState,
                        onToggleLanguage = { viewModel.toggleLanguage() },
                        onToggleTracking = { enable ->
                            if (enable) {
                                LifeTrackLocationService.startService(context, uiState.liveTelemetry.isBatterySaverEnabled)
                            } else {
                                LifeTrackLocationService.stopService(context)
                            }
                        },
                        onToggleBatterySaver = { saver ->
                            val intent = Intent(context, LifeTrackLocationService::class.java).apply {
                                action = LifeTrackLocationService.ACTION_UPDATE_BATTERY_MODE
                                putExtra(LifeTrackLocationService.EXTRA_BATTERY_SAVER, saver)
                            }
                            try {
                                context.startService(intent)
                            } catch (_: Exception) {
                            }
                        },
                        onToggleCloudSync = { viewModel.setCloudSyncEnabled(it) },
                        onSeedDemoData = { viewModel.seedDemoMultiDayTelemetry() },
                        onDeleteAllHistory = { viewModel.deleteAllHistory() },
                        onSignOutOrSwitchMode = {
                            val credentialManager = CredentialManager.create(context)
                            signOut(
                                context = context,
                                credentialManager = credentialManager,
                                onSignOutComplete = onSignOutToAuth,
                                scope = scope
                            )
                        }
                    )
                }
            }
        }
    }
}
