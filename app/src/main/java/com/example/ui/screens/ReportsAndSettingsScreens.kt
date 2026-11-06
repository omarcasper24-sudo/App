package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.StackedTransportBarChart
import com.example.ui.theme.AmberTransit
import com.example.ui.theme.CoralWork
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldDrive
import com.example.ui.theme.VioletSleep
import com.example.util.LocalAppStrings
import com.example.util.ReportExportHelper
import com.example.viewmodel.LifeTrackUiState
import java.util.Locale

@Composable
fun ReportsAnalyticsScreen(
    uiState: LifeTrackUiState,
    onPostStatusMessage: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var selectedPeriodTab by remember { mutableIntStateOf(0) } // 0 = Daily, 1 = Weekly, 2 = Monthly

    val periodLabels = listOf(strings.dailyReport, strings.weeklyReport, strings.monthlyReport)
    val multiplier = when (selectedPeriodTab) {
        0 -> 1.0
        1 -> 1.0
        else -> 4.2
    }

    val baseWalkKm = uiState.trips.filter { it.transportMode == "WALKING" }.sumOf { it.distanceKm }
    val baseDriveKm = uiState.trips.filter { it.transportMode == "DRIVING" }.sumOf { it.distanceKm }
    val totalKm = (baseWalkKm + baseDriveKm) * multiplier
    val avgDailyKm = if (selectedPeriodTab == 0) totalKm else totalKm / (if (selectedPeriodTab == 1) 7.0 else 30.0)
    val avgSleepHrs = (uiState.sleepLogs.map { it.durationMinutes }.average().takeIf { !it.isNaN() } ?: 455.0) / 60.0
    val avgWorkHrs = (uiState.workSessions.filter { it.sessionType != "PERSONAL" }.map { it.durationMinutes }.average().takeIf { !it.isNaN() } ?: 490.0) / 60.0
    val topPlaces = uiState.places.sortedByDescending { it.visitCount }.take(3).joinToString(", ") { it.name }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                periodLabels.forEachIndexed { idx, title ->
                    FilterChip(
                        selected = selectedPeriodTab == idx,
                        onClick = { selectedPeriodTab = idx },
                        label = { Text(title) },
                        modifier = Modifier.testTag("report_tab_$idx")
                    )
                }
            }
        }

        // Executive Report Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = periodLabels[selectedPeriodTab],
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    ReportRowItem(
                        label = if (strings.isArabic) "إجمالي المسافة المقطوعة" else "Total Distance Traveled",
                        value = String.format(Locale.US, "%.2f km", totalKm),
                        color = CyanAccent
                    )
                    ReportRowItem(
                        label = if (strings.isArabic) "مسافة المشي مقابل القيادة" else "Walking vs Driving Distance",
                        value = String.format(Locale.US, "%.1f km / %.1f km", baseWalkKm * multiplier, baseDriveKm * multiplier),
                        color = EmeraldDrive
                    )
                    ReportRowItem(
                        label = if (strings.isArabic) "متوسط الحركة اليومية" else "Average Daily Movement",
                        value = String.format(Locale.US, "%.2f km/day", avgDailyKm),
                        color = AmberTransit
                    )
                    ReportRowItem(
                        label = if (strings.isArabic) "متوسط ساعات العمل اليومية" else "Average Working Hours",
                        value = String.format(Locale.US, "%.1f hrs/day", avgWorkHrs),
                        color = CoralWork
                    )
                    ReportRowItem(
                        label = if (strings.isArabic) "متوسط ساعات النوم التقديرية" else "Average Estimated Sleep",
                        value = String.format(Locale.US, "%.1f hrs/night", avgSleepHrs),
                        color = VioletSleep
                    )
                    ReportRowItem(
                        label = if (strings.isArabic) "الأماكن الأكثر زيارة" else "Most Visited Locations",
                        value = topPlaces.ifBlank { "Home, Workplace" },
                        color = CyanAccent
                    )
                }
            }
        }

        // Behavioral Trends Chart Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = if (strings.isArabic) "الأنماط والاتجاهات السلوكية" else "Behavioral Patterns & Mobility Trends",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StackedTransportBarChart(
                        labels = listOf("W1", "W2", "W3", "W4", "W5", "W6", "W7"),
                        walkingValues = listOf(2.8f, 3.4f, 2.1f, 4.0f, 3.6f, 4.8f, 3.9f),
                        drivingValues = listOf(16.0f, 25.0f, 22.0f, 28.0f, 19.5f, 12.0f, 26.4f)
                    )
                }
            }
        }

        // Export PDF & Excel CSV Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val res = ReportExportHelper.exportPdfReport(
                            context = context,
                            periodTitle = periodLabels[selectedPeriodTab],
                            trips = uiState.trips,
                            places = uiState.places,
                            sleepLogs = uiState.sleepLogs,
                            workSessions = uiState.workSessions
                        )
                        if (res.isSuccess) {
                            onPostStatusMessage("PDF exported: ${res.getOrNull()?.name}")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("export_pdf_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.exportPdf, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val res = ReportExportHelper.exportExcelCsvReport(
                            context = context,
                            periodTitle = periodLabels[selectedPeriodTab],
                            trips = uiState.trips,
                            places = uiState.places,
                            sleepLogs = uiState.sleepLogs,
                            workSessions = uiState.workSessions
                        )
                        if (res.isSuccess) {
                            onPostStatusMessage("Excel CSV exported: ${res.getOrNull()?.name}")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("export_excel_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.exportExcel, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReportRowItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SettingsPrivacyScreen(
    uiState: LifeTrackUiState,
    onToggleLanguage: () -> Unit,
    onToggleTracking: (Boolean) -> Unit,
    onToggleBatterySaver: (Boolean) -> Unit,
    onToggleCloudSync: (Boolean) -> Unit,
    onSeedDemoData: () -> Unit,
    onDeleteAllHistory: () -> Unit,
    onSignOutOrSwitchMode: () -> Unit
) {
    val strings = LocalAppStrings.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Arabic RTL / English Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = CyanAccent)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    if (uiState.isArabic) "الواجهة العربية (RTL) مفعّلة" else "English (LTR) — Tap to switch to Arabic RTL",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.isArabic,
                            onCheckedChange = { onToggleLanguage() },
                            modifier = Modifier.testTag("settings_language_switch")
                        )
                    }

                    // Pause / Resume Background Tracking
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = EmeraldDrive)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    if (uiState.isArabic) "التتبع التلقائي في الخلفية" else "Automatic Background Tracking",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (uiState.liveTelemetry.isTrackingActive) strings.trackingActive else strings.trackingPaused,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.liveTelemetry.isTrackingActive,
                            onCheckedChange = onToggleTracking,
                            modifier = Modifier.testTag("settings_tracking_switch")
                        )
                    }

                    // Battery Saver Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = AmberTransit)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(strings.batterySaverLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    if (uiState.isArabic) "فترات تحديث متوازنة لتقليل استهلاك البطارية" else "Adaptive intervals for minimal battery consumption",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.liveTelemetry.isBatterySaverEnabled,
                            onCheckedChange = onToggleBatterySaver
                        )
                    }

                    // Cloud Backup & Auto-Synchronization vs Offline Local-Only
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                if (uiState.isCloudSyncEnabled) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = VioletSleep
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(strings.cloudSyncLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    if (uiState.isOfflineOnlyMode) strings.offlineModeLabel
                                    else "Firestore Cloud Sync (${uiState.userId.take(12)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.isCloudSyncEnabled,
                            onCheckedChange = onToggleCloudSync,
                            enabled = !uiState.isOfflineOnlyMode
                        )
                    }
                }
            }
        }

        // Action Buttons (Seed Sample Data, Delete History, Sign Out)
        item {
            OutlinedButton(
                onClick = onSeedDemoData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("seed_demo_data_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.seedDemoTelemetry)
            }
        }

        item {
            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("delete_history_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.deleteHistoryLabel, fontWeight = FontWeight.Bold)
            }
        }

        item {
            OutlinedButton(
                onClick = onSignOutOrSwitchMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (uiState.isOfflineOnlyMode) strings.signInWithGoogle else strings.signOutLabel,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(strings.deleteHistoryLabel) },
            text = {
                Text(
                    if (strings.isArabic) "هل أنت متأكد من حذف جميع سجلات الحركة؟ لا يمكن التراجع عن هذا الإجراء."
                    else "Are you sure you want to permanently delete all recorded movement history?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllHistory()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (strings.isArabic) "حذف نهائي" else "Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(if (strings.isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }
}
