package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.WorkSession
import com.example.ui.theme.AmberTransit
import com.example.ui.theme.CoralWork
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldDrive
import com.example.ui.theme.VioletSleep
import com.example.util.LocalAppStrings
import com.example.viewmodel.LifeTrackUiState
import com.example.viewmodel.todayDateKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SleepAnalysisScreen(
    uiState: LifeTrackUiState,
    onSaveSleepLog: (
        dateKey: String,
        bedtimeHour: Int,
        bedtimeMinute: Int,
        wakeHour: Int,
        wakeMinute: Int,
        notes: String
    ) -> Unit
) {
    val strings = LocalAppStrings.current
    val latestSleep = uiState.sleepLogs.firstOrNull()
    val avgWeeklyMinutes = if (uiState.sleepLogs.isNotEmpty()) {
        uiState.sleepLogs.take(7).map { it.durationMinutes }.average()
    } else 455.0

    val irregularCount = uiState.sleepLogs.count { it.scheduleRegularity != "REGULAR" }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
    var showAdjustDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Mandatory Prominent Sleep Estimation Disclaimer Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = VioletSleep.copy(alpha = 0.16f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = VioletSleep
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = strings.sleepDisclaimer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Daily Sleep Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = strings.sleepDuration,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%.1f hrs",
                                    (latestSleep?.durationMinutes ?: 465L) / 60.0
                                ),
                                style = MaterialTheme.typography.displayMedium,
                                color = VioletSleep,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { showAdjustDialog = true },
                            modifier = Modifier.testTag("adjust_sleep_button")
                        ) {
                            Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (strings.isArabic) "تحديث التقدير" else "Log / Adjust")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bedtime, contentDescription = null, tint = VioletSleep, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(strings.bedtime, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = latestSleep?.let { timeFormat.format(Date(it.bedtimeEpochMs)) } ?: "22:45",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WbSunny, contentDescription = null, tint = AmberTransit, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(strings.wakeTime, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = latestSleep?.let { timeFormat.format(Date(it.wakeTimeEpochMs)) } ?: "06:30",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(strings.sleepRegularity, style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = latestSleep?.scheduleRegularity ?: "REGULAR",
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldDrive,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Weekly & Monthly Sleep Statistics Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (strings.isArabic) "متوسط النوم الأسبوعي" else "Weekly Average", style = MaterialTheme.typography.labelSmall)
                        Text(
                            String.format(Locale.US, "%.1f hrs", avgWeeklyMinutes / 60.0),
                            style = MaterialTheme.typography.titleLarge,
                            color = VioletSleep,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (strings.isArabic) "ليالٍ غير منتظمة" else "Irregular Nights", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "$irregularCount",
                            style = MaterialTheme.typography.titleLarge,
                            color = AmberTransit,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (strings.isArabic) "مؤشر السكون الليلي" else "Stationary Score", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${latestSleep?.sleepQualityScore ?: 90}%",
                            style = MaterialTheme.typography.titleLarge,
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        items(uiState.sleepLogs, key = { it.id }) { log ->
            val regColor = when (log.scheduleRegularity) {
                "REGULAR" -> EmeraldDrive
                "SLIGHT_SHIFT" -> AmberTransit
                else -> CoralWork
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(log.dateKey, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = regColor.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = log.scheduleRegularity,
                                style = MaterialTheme.typography.labelSmall,
                                color = regColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.1f hrs (%s - %s) • %s",
                            log.durationMinutes / 60.0,
                            timeFormat.format(Date(log.bedtimeEpochMs)),
                            timeFormat.format(Date(log.wakeTimeEpochMs)),
                            log.notes
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (log.durationMinutes / 540f).coerceIn(0.1f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = VioletSleep
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showAdjustDialog) {
        var bedHour by remember { mutableStateOf("23") }
        var WakeHour by remember { mutableStateOf("7") }
        var notes by remember { mutableStateOf("Stationary at Home Residence") }

        AlertDialog(
            onDismissRequest = { showAdjustDialog = false },
            title = { Text(if (strings.isArabic) "تعديل تقدير النوم" else "Update Sleep Estimate") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bedHour,
                            onValueChange = { bedHour = it },
                            label = { Text(if (strings.isArabic) "ساعة النوم (0-23)" else "Bed Hour (0-23)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = WakeHour,
                            onValueChange = { WakeHour = it },
                            label = { Text(if (strings.isArabic) "ساعة الاستيقاظ" else "Wake Hour (0-23)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(if (strings.isArabic) "ملاحظات السكون" else "Pattern Notes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveSleepLog(
                            todayDateKey(),
                            (bedHour.toIntOrNull() ?: 23).coerceIn(0, 23),
                            0,
                            (WakeHour.toIntOrNull() ?: 7).coerceIn(0, 23),
                            0,
                            notes
                        )
                        showAdjustDialog = false
                    }
                ) {
                    Text(if (strings.isArabic) "حفظ" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustDialog = false }) {
                    Text(if (strings.isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }
}

@Composable
fun WorkAnalysisScreen(
    uiState: LifeTrackUiState,
    onSaveWorkSession: (
        existingId: String?,
        dateKey: String,
        workplaceName: String,
        arrivalHour: Int,
        arrivalMinute: Int,
        departureHour: Int,
        departureMinute: Int,
        sessionType: String
    ) -> Unit
) {
    val strings = LocalAppStrings.current
    val workSessions = uiState.workSessions
    val detectedWorkplace = uiState.places.firstOrNull { it.category == "WORK" }?.name
        ?: workSessions.firstOrNull()?.workplaceName
        ?: "Innovation Tech Hub"

    val weeklyWorkMinutes = workSessions.filter { it.sessionType != "PERSONAL" }.take(7).sumOf { it.durationMinutes }
    val personalActivityMinutes = workSessions.filter { it.sessionType == "PERSONAL" }.sumOf { it.durationMinutes }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }

    var editingSession by remember { mutableStateOf<WorkSession?>(null) }
    var showCorrectionDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(CoralWork.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Work, contentDescription = null, tint = CoralWork)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = strings.workplaceDetected,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = detectedWorkplace,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Button(
                            onClick = {
                                editingSession = workSessions.firstOrNull()
                                showCorrectionDialog = true
                            },
                            modifier = Modifier.testTag("correct_work_schedule_button")
                        ) {
                            Text(strings.manualCorrection)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (strings.isArabic) "ساعات العمل الأسبوعية" else "Weekly Work Hours", style = MaterialTheme.typography.labelSmall)
                            Text(
                                String.format(Locale.US, "%.1f hrs", weeklyWorkMinutes / 60.0),
                                style = MaterialTheme.typography.titleLarge,
                                color = CoralWork,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (strings.isArabic) "ساعات الشهر التقديرية" else "Monthly Est. Hours", style = MaterialTheme.typography.labelSmall)
                            Text(
                                String.format(Locale.US, "%.1f hrs", (weeklyWorkMinutes / 60.0) * 4.2),
                                style = MaterialTheme.typography.titleLarge,
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (strings.isArabic) "أنشطة شخصية" else "Personal Activities", style = MaterialTheme.typography.labelSmall)
                            Text(
                                String.format(Locale.US, "%.1f hrs", (personalActivityMinutes.coerceAtLeast(180L)) / 60.0),
                                style = MaterialTheme.typography.titleLarge,
                                color = EmeraldDrive,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        items(workSessions, key = { it.id }) { session ->
            val badgeColor = when (session.sessionType) {
                "WORK" -> CoralWork
                "OVERTIME" -> AmberTransit
                else -> EmeraldDrive
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${session.dateKey} • ${session.workplaceName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (session.isManuallyCorrected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (strings.isArabic) "(مُعدّل)" else "(Corrected)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanAccent
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${strings.arrivalTime}: ${timeFormat.format(Date(session.arrivalEpochMs))} | ${strings.departureTime}: ${timeFormat.format(Date(session.departureEpochMs))}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format(Locale.US, "%.1f hrs", session.durationMinutes / 60.0),
                            style = MaterialTheme.typography.titleMedium,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = session.sessionType,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showCorrectionDialog) {
        var placeName by remember(editingSession) { mutableStateOf(editingSession?.workplaceName ?: detectedWorkplace) }
        var arrivalHr by remember { mutableStateOf("9") }
        var departureHr by remember { mutableStateOf("17") }
        var sessionType by remember(editingSession) { mutableStateOf(editingSession?.sessionType ?: "WORK") }

        AlertDialog(
            onDismissRequest = { showCorrectionDialog = false },
            title = { Text(strings.manualCorrection) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = placeName,
                        onValueChange = { placeName = it },
                        label = { Text(if (strings.isArabic) "موقع العمل" else "Workplace Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = arrivalHr,
                            onValueChange = { arrivalHr = it },
                            label = { Text(if (strings.isArabic) "ساعة الوصول" else "Arrival Hour (0-23)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = departureHr,
                            onValueChange = { departureHr = it },
                            label = { Text(if (strings.isArabic) "ساعة المغادرة" else "Departure Hour (0-23)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("WORK", "PERSONAL", "OVERTIME")) { type ->
                            FilterChip(
                                selected = sessionType == type,
                                onClick = { sessionType = type },
                                label = { Text(type) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveWorkSession(
                            editingSession?.id,
                            editingSession?.dateKey ?: todayDateKey(),
                            placeName,
                            (arrivalHr.toIntOrNull() ?: 9).coerceIn(0, 23),
                            0,
                            (departureHr.toIntOrNull() ?: 17).coerceIn(1, 23),
                            30,
                            sessionType
                        )
                        showCorrectionDialog = false
                    }
                ) {
                    Text(if (strings.isArabic) "حفظ" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCorrectionDialog = false }) {
                    Text(if (strings.isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }
}
