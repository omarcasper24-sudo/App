package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.service.ActivityConfidenceBreakdown
import com.example.service.ActivityRecognitionBroadcastEvent
import com.example.service.LifeTrackLocationService
import com.example.ui.components.InteractiveRouteMapCanvas
import com.example.ui.components.StackedTransportBarChart
import com.example.ui.theme.AmberTransit
import com.example.ui.theme.CoralWork
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldDrive
import com.example.ui.theme.VioletSleep
import com.example.util.LocalAppStrings
import com.example.viewmodel.LifeTrackUiState
import com.example.viewmodel.dateKeyDaysAgo
import com.example.viewmodel.todayDateKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    uiState: LifeTrackUiState,
    onNavigateToSection: (Int) -> Unit,
    onToggleTracking: (Boolean) -> Unit,
    onBroadcastActivityState: (String) -> Unit = {}
) {
    val strings = LocalAppStrings.current
    val todayKey = todayDateKey()
    val todayTrips = remember(uiState.trips, todayKey) {
        uiState.trips.filter { it.dateKey == todayKey }.ifEmpty { uiState.trips.take(4) }
    }

    val totalDistanceKm = todayTrips.sumOf { it.distanceKm } + uiState.liveTelemetry.activeTripDistanceKm
    val walkingDistanceKm = todayTrips.filter { it.transportMode == "WALKING" }.sumOf { it.distanceKm } +
        if (uiState.liveTelemetry.currentTransportMode == "WALKING") uiState.liveTelemetry.activeTripDistanceKm else 0.0
    val drivingDistanceKm = todayTrips.filter { it.transportMode == "DRIVING" }.sumOf { it.distanceKm } +
        if (uiState.liveTelemetry.currentTransportMode == "DRIVING") uiState.liveTelemetry.activeTripDistanceKm else 0.0

    val outsideHomeMinutes = todayTrips.sumOf { it.durationMinutes } +
        uiState.workSessions.firstOrNull()?.durationMinutes.let { it ?: 480L }
    val latestSleepHrs = (uiState.sleepLogs.firstOrNull()?.durationMinutes ?: 460L) / 60.0
    val latestWorkHrs = (uiState.workSessions.firstOrNull { it.sessionType != "PERSONAL" }?.durationMinutes ?: 495L) / 60.0
    val topPlace = uiState.places.maxByOrNull { it.visitCount }?.name ?: "Home Residence"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            HeroTelemetryBanner(
                isTrackingActive = uiState.liveTelemetry.isTrackingActive,
                currentMode = uiState.liveTelemetry.currentTransportMode,
                confidence = uiState.liveTelemetry.activityConfidence,
                totalDistanceKm = totalDistanceKm,
                onToggleTracking = onToggleTracking,
                onOpenLive = { onNavigateToSection(1) }
            )
        }

        // Live ActivityRecognitionClient Status & Broadcast Summary Card
        item {
            ActivityRecognitionLiveStatusCard(
                currentMode = uiState.liveTelemetry.currentTransportMode,
                confidence = uiState.liveTelemetry.activityConfidence,
                transitionType = uiState.liveTelemetry.lastTransitionType,
                breakdown = uiState.liveTelemetry.confidenceBreakdown,
                latestBroadcast = uiState.latestActivityBroadcast,
                onBroadcastState = onBroadcastActivityState
            )
        }

        // Primary 2x2 Metric Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardStatCard(
                    title = strings.walkingDistance,
                    value = String.format(Locale.US, "%.2f km", walkingDistanceKm),
                    subtitle = "${todayTrips.count { it.transportMode == "WALKING" }} walks",
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    accentColor = CyanAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToSection(2) }
                )
                DashboardStatCard(
                    title = strings.drivingDistance,
                    value = String.format(Locale.US, "%.2f km", drivingDistanceKm),
                    subtitle = "${todayTrips.count { it.transportMode == "DRIVING" }} drives",
                    icon = Icons.Default.DirectionsCar,
                    accentColor = EmeraldDrive,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToSection(2) }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardStatCard(
                    title = strings.estSleepingHours,
                    value = String.format(Locale.US, "%.1f hrs", latestSleepHrs),
                    subtitle = uiState.sleepLogs.firstOrNull()?.scheduleRegularity ?: "REGULAR",
                    icon = Icons.Default.Bedtime,
                    accentColor = VioletSleep,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToSection(4) }
                )
                DashboardStatCard(
                    title = strings.estWorkingHours,
                    value = String.format(Locale.US, "%.1f hrs", latestWorkHrs),
                    subtitle = uiState.workSessions.firstOrNull()?.workplaceName ?: "Workplace",
                    icon = Icons.Default.Work,
                    accentColor = CoralWork,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToSection(5) }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardStatCard(
                    title = strings.totalTrips,
                    value = "${todayTrips.size}",
                    subtitle = String.format(Locale.US, "%.1f hrs outside home", outsideHomeMinutes / 60.0),
                    icon = Icons.Default.Route,
                    accentColor = AmberTransit,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToSection(2) }
                )
                DashboardStatCard(
                    title = strings.mostVisitedPlace,
                    value = topPlace,
                    subtitle = "${uiState.places.size} ${strings.placesVisitedCount}",
                    icon = Icons.Default.Place,
                    accentColor = CyanAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToSection(3) }
                )
            }
        }

        // Interactive 7-Day Movement Chart Card
        item {
            val days = remember { (6 downTo 0).map { dateKeyDaysAgo(it) } }
            val dayLabels = remember(days) { days.map { it.takeLast(5) } }
            val walkVals = remember(uiState.trips, days) {
                days.mapIndexed { idx, d ->
                    val sum = uiState.trips.filter { it.dateKey == d && it.transportMode == "WALKING" }.sumOf { it.distanceKm }.toFloat()
                    if (sum > 0f) sum else listOf(2.4f, 3.1f, 1.9f, 3.8f, 2.7f, 4.2f, 3.9f)[idx % 7]
                }
            }
            val driveVals = remember(uiState.trips, days) {
                days.mapIndexed { idx, d ->
                    val sum = uiState.trips.filter { it.dateKey == d && it.transportMode == "DRIVING" }.sumOf { it.distanceKm }.toFloat()
                    if (sum > 0f) sum else listOf(18.0f, 24.5f, 15.2f, 28.4f, 22.0f, 12.5f, 30.8f)[idx % 7]
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (strings.isArabic) "تحليل الحركة الأسبوعي (مشي مقابل قيادة)" else "7-Day Movement Trend (Walking vs Driving)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (strings.isArabic) "كم/يوم" else "km / day",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    StackedTransportBarChart(
                        labels = dayLabels,
                        walkingValues = walkVals,
                        drivingValues = driveVals
                    )
                }
            }
        }

        // Interactive Map Preview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (strings.isArabic) "خريطة المسارات والأماكن اليومية" else "Today's Movement Route & Places Map",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (strings.isArabic) "عرض السجل الكامل" else "Full History →",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onNavigateToSection(2) }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    InteractiveRouteMapCanvas(
                        trips = todayTrips,
                        places = uiState.places,
                        livePoints = uiState.liveTelemetry.activeTripPoints,
                        currentLat = uiState.liveTelemetry.currentLatitude,
                        currentLng = uiState.liveTelemetry.currentLongitude,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActivityRecognitionLiveStatusCard(
    currentMode: String,
    confidence: Int,
    transitionType: String,
    breakdown: ActivityConfidenceBreakdown,
    latestBroadcast: ActivityRecognitionBroadcastEvent?,
    onBroadcastState: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("activity_recognition_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = CyanAccent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (strings.isArabic) "محرك التعرف على النشاط (ActivityRecognitionClient)" else "ActivityRecognitionClient Live State",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = latestBroadcast?.let {
                                "${it.source} • ${it.transitionType} @ ${timeFormat.format(Date(it.timestampEpochMs))}"
                            } ?: "Broadcast pipeline active • Transition: $transitionType",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CyanAccent.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "$currentMode ($confidence%)",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4-state confidence bars (Walking, Driving, Stationary, Cycling)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ConfidenceMiniBar(
                    label = strings.walking,
                    percent = breakdown.walkingPercent,
                    color = CyanAccent,
                    modifier = Modifier.weight(1f)
                )
                ConfidenceMiniBar(
                    label = strings.driving,
                    percent = breakdown.drivingPercent,
                    color = EmeraldDrive,
                    modifier = Modifier.weight(1f)
                )
                ConfidenceMiniBar(
                    label = strings.stationary,
                    percent = breakdown.stationaryPercent,
                    color = VioletSleep,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Broadcast Dispatch Chips (triggers real Intent -> ActivityGeofenceReceiver -> App State)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "WALKING" to strings.walking,
                    "DRIVING" to strings.driving,
                    "STATIONARY" to strings.stationary
                ).forEach { (stateKey, label) ->
                    FilterChip(
                        selected = currentMode == stateKey,
                        onClick = { onBroadcastState(stateKey) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("dashboard_broadcast_$stateKey")
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfidenceMiniBar(
    label: String,
    percent: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(text = "$percent%", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0.04f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color
        )
    }
}

@Composable
private fun HeroTelemetryBanner(
    isTrackingActive: Boolean,
    currentMode: String,
    confidence: Int,
    totalDistanceKm: Double,
    onToggleTracking: (Boolean) -> Unit,
    onOpenLive: () -> Unit
) {
    val strings = LocalAppStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenLive() },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_map_banner_1793963804864),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xE60B132B),
                                Color(0xB30B132B),
                                Color(0xCC131D3B)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isTrackingActive) EmeraldDrive.copy(alpha = 0.2f) else AmberTransit.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isTrackingActive) EmeraldDrive else AmberTransit)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isTrackingActive) strings.trackingActive else strings.trackingPaused,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = { onToggleTracking(!isTrackingActive) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTrackingActive) Color(0xFF283556) else CyanAccent,
                            contentColor = if (isTrackingActive) Color.White else Color(0xFF002A32)
                        ),
                        modifier = Modifier.testTag("hero_toggle_tracking_button")
                    ) {
                        Icon(
                            imageVector = if (isTrackingActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTrackingActive) strings.pauseTracking else strings.resumeTracking,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = strings.totalDistanceToday,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB8C7E0)
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f km", totalDistanceKm),
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyanAccent.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "$currentMode • $confidence%",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyanAccent,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun LiveTrackingScreen(
    uiState: LifeTrackUiState,
    onToggleTracking: (Boolean) -> Unit,
    onBroadcastActivityState: (userState: String, transitionType: String) -> Unit = { _, _ -> },
    onSaveCompletedTrip: (
        transportMode: String,
        distanceKm: Double,
        durationMinutes: Long,
        startLabel: String,
        endLabel: String
    ) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val live = uiState.liveTelemetry
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    var hasForegroundLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasBackgroundLocation by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val fgPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasForegroundLocation = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasForegroundLocation) {
            onToggleTracking(true)
        }
    }

    val bgPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBackgroundLocation = granted
    }

    var startLabelInput by remember { mutableStateOf("Home Residence") }
    var endLabelInput by remember { mutableStateOf("Innovation Tech Hub") }
    var selectedMode by remember(live.currentTransportMode) {
        mutableStateOf(if (live.currentTransportMode == "STATIONARY") "WALKING" else live.currentTransportMode)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Permission & Background Tracking Control Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = null,
                                tint = if (live.isTrackingActive) EmeraldDrive else CyanAccent
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (live.isTrackingActive) strings.trackingActive else strings.trackingPaused,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "GPS: %.4f, %.4f • %.1f km/h • %s (%d%%)",
                                        live.currentLatitude,
                                        live.currentLongitude,
                                        live.currentSpeedKmh,
                                        live.currentTransportMode,
                                        live.activityConfidence
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (!hasForegroundLocation) {
                                    val perms = mutableListOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    fgPermLauncher.launch(perms.toTypedArray())
                                } else {
                                    onToggleTracking(!live.isTrackingActive)
                                }
                            },
                            modifier = Modifier.testTag("live_toggle_tracking_button")
                        ) {
                            Text(if (live.isTrackingActive) strings.pauseTracking else strings.resumeTracking)
                        }
                    }

                    if (hasForegroundLocation && !hasBackgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                bgPermLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (strings.isArabic) "تفعيل إذن التتبع في الخلفية عند إغلاق الشاشة"
                                else "Grant Background Location (Screen-Off Tracking)"
                            )
                        }
                    }
                }
            }
        }

        // Live Interactive Route Map
        item {
            InteractiveRouteMapCanvas(
                trips = uiState.trips.take(3),
                places = uiState.places,
                livePoints = live.activeTripPoints,
                currentLat = live.currentLatitude,
                currentLng = live.currentLongitude,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            )
        }

        // Live ActivityRecognitionClient Broadcast & Trip Recorder Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (strings.isArabic) "بث حالات النشاط (ActivityRecognitionClient)" else "ActivityRecognitionClient Broadcast Controls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${live.currentTransportMode} • ${live.activityConfidence}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    val modes = listOf(
                        "WALKING" to strings.walking,
                        "DRIVING" to strings.driving,
                        "CYCLING" to strings.cycling,
                        "STATIONARY" to strings.stationary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(modes) { (modeKey, modeLabel) ->
                            FilterChip(
                                selected = live.currentTransportMode == modeKey,
                                onClick = {
                                    selectedMode = modeKey
                                    onBroadcastActivityState(modeKey, "ENTER")
                                },
                                label = { Text(modeLabel) },
                                leadingIcon = {
                                    val icon = when (modeKey) {
                                        "WALKING" -> Icons.AutoMirrored.Filled.DirectionsWalk
                                        "DRIVING" -> Icons.Default.DirectionsCar
                                        "CYCLING" -> Icons.AutoMirrored.Filled.DirectionsBike
                                        else -> Icons.Default.Home
                                    }
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("broadcast_chip_$modeKey")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedMode = "WALKING"
                                LifeTrackLocationService.simulateStepMovement("WALKING", 0.45, 0.0032, 0.0024)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("simulate_walk_step_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (strings.isArabic) "+0.45 كم مشي" else "+0.45 km Walk")
                        }
                        OutlinedButton(
                            onClick = {
                                selectedMode = "DRIVING"
                                LifeTrackLocationService.simulateStepMovement("DRIVING", 3.20, 0.014, -0.011)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("simulate_drive_step_button")
                        ) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (strings.isArabic) "+3.2 كم قيادة" else "+3.2 km Drive")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = startLabelInput,
                            onValueChange = { startLabelInput = it },
                            label = { Text(if (strings.isArabic) "نقطة الانطلاق" else "Origin") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endLabelInput,
                            onValueChange = { endLabelInput = it },
                            label = { Text(if (strings.isArabic) "الوجهة" else "Destination") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val dist = if (live.activeTripDistanceKm > 0.05) live.activeTripDistanceKm else {
                                if (selectedMode == "DRIVING") 8.5 else 1.4
                            }
                            val durationMins = if (live.activeTripStartEpochMs > 0L) {
                                ((System.currentTimeMillis() - live.activeTripStartEpochMs) / 60_000L).coerceIn(5L, 180L)
                            } else 20L
                            onSaveCompletedTrip(
                                selectedMode,
                                dist,
                                durationMins,
                                startLabelInput,
                                endLabelInput
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("finish_save_trip_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.StopCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "%s (%.2f km)",
                                strings.finishCurrentTrip,
                                live.activeTripDistanceKm.coerceAtLeast(0.0)
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Recent ActivityRecognition Broadcast Event Stream Log
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("activity_broadcast_event_log_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (strings.isArabic) "سجل بث أحداث النشاط المباشر" else "ActivityRecognition Broadcast Event Log",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    live.recentBroadcastEvents.take(6).forEach { ev ->
                        val badgeColor = when (ev.userState) {
                            "WALKING" -> CyanAccent
                            "DRIVING" -> EmeraldDrive
                            "CYCLING" -> AmberTransit
                            else -> VioletSleep
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(badgeColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${ev.userState} (${ev.transitionType})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "${ev.confidence}% • ${timeFormat.format(Date(ev.timestampEpochMs))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
