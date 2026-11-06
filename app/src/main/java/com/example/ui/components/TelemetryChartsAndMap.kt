package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.LatLngPoint
import com.example.data.model.PlaceLocation
import com.example.data.model.TripRecord
import com.example.data.model.parseRoutePoints
import com.example.ui.theme.AmberTransit
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldDrive
import com.example.ui.theme.VioletSleep
import kotlin.math.max
import kotlin.math.min

@Composable
fun InteractiveRouteMapCanvas(
    trips: List<TripRecord>,
    places: List<PlaceLocation>,
    livePoints: List<LatLngPoint> = emptyList(),
    currentLat: Double = 24.7136,
    currentLng: Double = 46.6753,
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val allPoints = remember(trips, places, livePoints, currentLat, currentLng) {
        buildList {
            trips.forEach { trip ->
                add(LatLngPoint(trip.startLat, trip.startLng))
                add(LatLngPoint(trip.endLat, trip.endLng))
                addAll(parseRoutePoints(trip.routePointsSerialized))
            }
            places.forEach { p ->
                add(LatLngPoint(p.latitude, p.longitude))
            }
            addAll(livePoints)
            if (isEmpty()) {
                add(LatLngPoint(currentLat, currentLng))
            }
        }
    }

    val minLat = allPoints.minOfOrNull { it.lat } ?: (currentLat - 0.02)
    val maxLat = allPoints.maxOfOrNull { it.lat } ?: (currentLat + 0.02)
    val minLng = allPoints.minOfOrNull { it.lng } ?: (currentLng - 0.02)
    val maxLng = allPoints.maxOfOrNull { it.lng } ?: (currentLng + 0.02)

    val latSpan = max(maxLat - minLat, 0.015)
    val lngSpan = max(maxLng - minLng, 0.015)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0A1228))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(0.6f, 4.5f)
                    panOffset += pan
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pad = 36.dp.toPx()

            // Draw subtle urban telemetry grid
            val gridStep = 36.dp.toPx()
            val gridColor = Color(0xFF1A2952)
            var gx = 0f
            while (gx < w) {
                drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                gx += gridStep
            }
            var gy = 0f
            while (gy < h) {
                drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                gy += gridStep
            }

            fun project(lat: Double, lng: Double): Offset {
                val nx = ((lng - minLng) / lngSpan).toFloat()
                val ny = 1f - ((lat - minLat) / latSpan).toFloat()
                val baseX = pad + nx * (w - 2 * pad)
                val baseY = pad + ny * (h - 2 * pad)
                val cx = w / 2f
                val cy = h / 2f
                return Offset(
                    x = cx + (baseX - cx) * zoom + panOffset.x,
                    y = cy + (baseY - cy) * zoom + panOffset.y
                )
            }

            // Draw Geofence Place circles and markers
            places.forEach { place ->
                val center = project(place.latitude, place.longitude)
                val placeColor = when (place.category) {
                    "HOME" -> CyanAccent
                    "WORK" -> AmberTransit
                    else -> VioletSleep
                }
                drawCircle(
                    color = placeColor.copy(alpha = 0.16f),
                    radius = 22.dp.toPx() * zoom.coerceAtMost(2f),
                    center = center
                )
                drawCircle(
                    color = placeColor,
                    radius = 6.dp.toPx(),
                    center = center
                )
            }

            // Draw Trip Routes
            trips.forEach { trip ->
                val routePts = parseRoutePoints(trip.routePointsSerialized).ifEmpty {
                    listOf(
                        LatLngPoint(trip.startLat, trip.startLng),
                        LatLngPoint(trip.endLat, trip.endLng)
                    )
                }
                val routeColor = when (trip.transportMode) {
                    "WALKING" -> CyanAccent
                    "DRIVING" -> EmeraldDrive
                    else -> AmberTransit
                }
                if (routePts.size >= 2) {
                    val path = Path()
                    routePts.forEachIndexed { idx, pt ->
                        val offset = project(pt.lat, pt.lng)
                        if (idx == 0) path.moveTo(offset.x, offset.y)
                        else path.lineTo(offset.x, offset.y)
                    }
                    // Outer glow
                    drawPath(
                        path = path,
                        color = routeColor.copy(alpha = 0.28f),
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // Core route line
                    drawPath(
                        path = path,
                        color = routeColor,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                val startOffset = project(trip.startLat, trip.startLng)
                val endOffset = project(trip.endLat, trip.endLng)
                drawCircle(Color.White, radius = 4.dp.toPx(), center = startOffset)
                drawCircle(routeColor, radius = 5.dp.toPx(), center = endOffset)
            }

            // Draw Live Active Route
            if (livePoints.size >= 2) {
                val livePath = Path()
                livePoints.forEachIndexed { idx, pt ->
                    val offset = project(pt.lat, pt.lng)
                    if (idx == 0) livePath.moveTo(offset.x, offset.y)
                    else livePath.lineTo(offset.x, offset.y)
                }
                drawPath(
                    path = livePath,
                    color = CyanAccent,
                    style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Current GPS position beacon
            val curOffset = project(currentLat, currentLng)
            drawCircle(CyanAccent.copy(alpha = 0.3f), radius = 14.dp.toPx(), center = curOffset)
            drawCircle(Color.White, radius = 6.dp.toPx(), center = curOffset)
            drawCircle(CyanAccent, radius = 4.dp.toPx(), center = curOffset)
        }

        // Map Legend at Top Start
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xCC0B132B)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = CyanAccent, label = "Walking / Home")
                LegendDot(color = EmeraldDrive, label = "Driving")
                LegendDot(color = AmberTransit, label = "Work / Transit")
            }
        }

        // Map Zoom Controls at Bottom End
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledIconButton(
                onClick = { zoom = (zoom * 1.25f).coerceAtMost(4.5f) },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xCC1C284E))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            FilledIconButton(
                onClick = { zoom = (zoom / 1.25f).coerceAtLeast(0.6f) },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xCC1C284E))
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            FilledIconButton(
                onClick = {
                    zoom = 1f
                    panOffset = Offset.Zero
                },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xCC1C284E))
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset Map Center", tint = CyanAccent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

@Composable
fun StackedTransportBarChart(
    labels: List<String>,
    walkingValues: List<Float>,
    drivingValues: List<Float>,
    modifier: Modifier = Modifier
) {
    val maxVal = remember(walkingValues, drivingValues) {
        val maxSum = labels.indices.maxOfOrNull { idx ->
            (walkingValues.getOrElse(idx) { 0f } + drivingValues.getOrElse(idx) { 0f })
        } ?: 1f
        max(maxSum, 1f)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val count = max(labels.size, 1)
            val slotWidth = size.width / count
            val barWidth = min(slotWidth * 0.48f, 28.dp.toPx())
            val chartHeight = size.height - 8.dp.toPx()

            for (i in 0 until count) {
                val walk = walkingValues.getOrElse(i) { 0f }
                val drive = drivingValues.getOrElse(i) { 0f }
                val walkH = (walk / maxVal) * chartHeight
                val driveH = (drive / maxVal) * chartHeight
                val x = i * slotWidth + (slotWidth - barWidth) / 2f

                // Background track
                drawRoundRect(
                    color = Color(0x22A7B6D4),
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, chartHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                // Driving segment at bottom
                if (driveH > 0f) {
                    drawRoundRect(
                        color = EmeraldDrive,
                        topLeft = Offset(x, chartHeight - driveH),
                        size = Size(barWidth, driveH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }

                // Walking segment stacked on top
                if (walkH > 0f) {
                    drawRoundRect(
                        color = CyanAccent,
                        topLeft = Offset(x, (chartHeight - driveH - walkH).coerceAtLeast(0f)),
                        size = Size(barWidth, walkH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
