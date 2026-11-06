package com.example.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.model.PlaceLocation
import com.example.data.model.TripRecord
import com.example.ui.components.InteractiveRouteMapCanvas
import com.example.ui.theme.AmberTransit
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldDrive
import com.example.ui.theme.VioletSleep
import com.example.util.LocalAppStrings
import com.example.viewmodel.LifeTrackUiState
import com.example.viewmodel.dateKeyDaysAgo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MovementHistoryScreen(
    uiState: LifeTrackUiState,
    onSelectDate: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val availableDates = remember(uiState.trips) {
        val recent = (0..6).map { dateKeyDaysAgo(it) }
        (recent + uiState.trips.map { it.dateKey }).distinct()
    }

    val filteredTrips = remember(uiState.trips, uiState.selectedDateKey) {
        val exact = uiState.trips.filter { it.dateKey == uiState.selectedDateKey }
        exact.ifEmpty { uiState.trips }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (strings.isArabic) "اختر التاريخ لمراجعة سجل الحركة" else "Select Date to Review Movement History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableDates) { dateKey ->
                    FilterChip(
                        selected = uiState.selectedDateKey == dateKey,
                        onClick = { onSelectDate(dateKey) },
                        label = { Text(dateKey) }
                    )
                }
            }
        }

        item {
            InteractiveRouteMapCanvas(
                trips = filteredTrips,
                places = uiState.places,
                currentLat = uiState.liveTelemetry.currentLatitude,
                currentLng = uiState.liveTelemetry.currentLongitude,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(235.dp)
            )
        }

        item {
            val walkSum = filteredTrips.filter { it.transportMode == "WALKING" }.sumOf { it.distanceKm }
            val driveSum = filteredTrips.filter { it.transportMode == "DRIVING" }.sumOf { it.distanceKm }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(strings.walkingDistance, style = MaterialTheme.typography.labelSmall)
                        Text(
                            String.format(Locale.US, "%.2f km", walkSum),
                            style = MaterialTheme.typography.titleMedium,
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(strings.drivingDistance, style = MaterialTheme.typography.labelSmall)
                        Text(
                            String.format(Locale.US, "%.2f km", driveSum),
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldDrive,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(strings.totalTrips, style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${filteredTrips.size}",
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberTransit,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        items(filteredTrips, key = { it.id }) { trip ->
            TripTimelineCard(trip = trip, timeFormat = timeFormat)
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun TripTimelineCard(
    trip: TripRecord,
    timeFormat: SimpleDateFormat
) {
    val isWalk = trip.transportMode == "WALKING"
    val accent = when (trip.transportMode) {
        "WALKING" -> CyanAccent
        "DRIVING" -> EmeraldDrive
        else -> AmberTransit
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isWalk) Icons.AutoMirrored.Filled.DirectionsWalk else Icons.Default.DirectionsCar,
                    contentDescription = trip.transportMode,
                    tint = accent
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${trip.startLabel} → ${trip.endLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f km", trip.distanceKm),
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${timeFormat.format(Date(trip.startTimeEpochMs))} - ${timeFormat.format(Date(trip.endTimeEpochMs))} • ${trip.durationMinutes} min • ${trip.transportMode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PlacesLocationsScreen(
    uiState: LifeTrackUiState,
    onSaveOrUpdatePlace: (
        existingId: String?,
        name: String,
        category: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Long,
        visitCount: Long,
        totalDurationMinutes: Long
    ) -> Unit
) {
    val strings = LocalAppStrings.current
    val top10Places = remember(uiState.places) {
        uiState.places.sortedByDescending { it.visitCount }.take(10)
    }
    val maxVisits = (top10Places.maxOfOrNull { it.visitCount } ?: 1L).coerceAtLeast(1L)

    var editingPlace by remember { mutableStateOf<PlaceLocation?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = strings.top10PlacesTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (strings.isArabic) "تصنيف تلقائي للمنزل والعمل والأماكن المتكررة" else "Auto-clustered Home, Workplace & Frequent Geofences",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        editingPlace = null
                        showEditDialog = true
                    },
                    modifier = Modifier.testTag("add_place_button")
                ) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (strings.isArabic) "إضافة" else "Add")
                }
            }
        }

        itemsIndexed(top10Places, key = { _, p -> p.id }) { index, place ->
            val rank = index + 1
            val progress = (place.visitCount.toFloat() / maxVisits.toFloat()).coerceIn(0.08f, 1f)
            val categoryColor = when (place.category) {
                "HOME" -> CyanAccent
                "WORK" -> AmberTransit
                "GYM" -> EmeraldDrive
                else -> VioletSleep
            }
            val categoryIcon = when (place.category) {
                "HOME" -> Icons.Default.Home
                "WORK" -> Icons.Default.Work
                "GYM" -> Icons.Default.FitnessCenter
                "CAFE" -> Icons.Default.LocalCafe
                "SHOPPING" -> Icons.Default.ShoppingBag
                else -> Icons.Default.Place
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        editingPlace = place
                        showEditDialog = true
                    },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = categoryColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "#$rank",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = categoryColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = categoryIcon,
                                    contentDescription = null,
                                    tint = categoryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = place.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%s • %d visits • %.1f hrs total • Arrival %s / Dep %s",
                                    place.category,
                                    place.visitCount,
                                    place.totalDurationMinutes / 60.0,
                                    timeFormat.format(Date(place.lastArrivalEpochMs)),
                                    timeFormat.format(Date(place.lastDepartureEpochMs))
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                editingPlace = place
                                showEditDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = strings.renameCategorizePlace)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = categoryColor
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showEditDialog) {
        var nameInput by remember(editingPlace) { mutableStateOf(editingPlace?.name ?: "") }
        var categoryInput by remember(editingPlace) { mutableStateOf(editingPlace?.category ?: "HOME") }
        var visitsInput by remember(editingPlace) { mutableStateOf((editingPlace?.visitCount ?: 5L).toString()) }
        var hoursInput by remember(editingPlace) {
            mutableStateOf(((editingPlace?.totalDurationMinutes ?: 180L) / 60).toString())
        }

        val categories = listOf("HOME", "WORK", "GYM", "CAFE", "SHOPPING", "OTHER")

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(strings.renameCategorizePlace) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(if (strings.isArabic) "اسم المكان" else "Location Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = categoryInput == cat,
                                onClick = { categoryInput = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = visitsInput,
                            onValueChange = { visitsInput = it },
                            label = { Text(if (strings.isArabic) "عدد الزيارات" else "Visits") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = hoursInput,
                            onValueChange = { hoursInput = it },
                            label = { Text(if (strings.isArabic) "إجمالي الساعات" else "Total Hours") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveOrUpdatePlace(
                            editingPlace?.id,
                            nameInput,
                            categoryInput,
                            editingPlace?.latitude ?: 24.7136,
                            editingPlace?.longitude ?: 46.6753,
                            editingPlace?.radiusMeters ?: 120L,
                            visitsInput.toLongOrNull() ?: 1L,
                            (hoursInput.toLongOrNull() ?: 2L) * 60L
                        )
                        showEditDialog = false
                    }
                ) {
                    Text(if (strings.isArabic) "حفظ" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(if (strings.isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }
}
