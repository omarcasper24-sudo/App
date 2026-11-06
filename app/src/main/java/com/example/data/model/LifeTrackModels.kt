package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class TripRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val transportMode: String = "WALKING",
    val startTimeEpochMs: Long = 0L,
    val endTimeEpochMs: Long = 0L,
    val durationMinutes: Long = 0L,
    val distanceKm: Double = 0.0,
    val startLabel: String = "Start",
    val endLabel: String = "Destination",
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val endLat: Double = 0.0,
    val endLng: Double = 0.0,
    val routePointsSerialized: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PlaceLocation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = "OTHER",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Long = 100L,
    val visitCount: Long = 1L,
    val totalDurationMinutes: Long = 0L,
    val lastArrivalEpochMs: Long = 0L,
    val lastDepartureEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SleepLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val bedtimeEpochMs: Long = 0L,
    val wakeTimeEpochMs: Long = 0L,
    val durationMinutes: Long = 0L,
    val sleepQualityScore: Long = 80L,
    val scheduleRegularity: String = "REGULAR",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WorkSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val workplaceName: String = "",
    val arrivalEpochMs: Long = 0L,
    val departureEpochMs: Long = 0L,
    val durationMinutes: Long = 0L,
    val sessionType: String = "WORK",
    val isManuallyCorrected: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Entity(tableName = "local_trips")
data class LocalTripEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val dateKey: String,
    val transportMode: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val durationMinutes: Long,
    val distanceKm: Double,
    val startLabel: String,
    val endLabel: String,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val routePointsSerialized: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_places")
data class LocalPlaceEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Long,
    val visitCount: Long,
    val totalDurationMinutes: Long,
    val lastArrivalEpochMs: Long,
    val lastDepartureEpochMs: Long,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_sleep_logs")
data class LocalSleepEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val dateKey: String,
    val bedtimeEpochMs: Long,
    val wakeTimeEpochMs: Long,
    val durationMinutes: Long,
    val sleepQualityScore: Long,
    val scheduleRegularity: String,
    val notes: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_work_sessions")
data class LocalWorkEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val dateKey: String,
    val workplaceName: String,
    val arrivalEpochMs: Long,
    val departureEpochMs: Long,
    val durationMinutes: Long,
    val sessionType: String,
    val isManuallyCorrected: Boolean,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class LatLngPoint(
    val lat: Double,
    val lng: Double
)

fun serializeRoutePoints(points: List<LatLngPoint>): String {
    return points.take(60).joinToString(";") {
        String.format(java.util.Locale.US, "%.5f,%.5f", it.lat, it.lng)
    }
}

fun parseRoutePoints(serialized: String): List<LatLngPoint> {
    if (serialized.isBlank()) return emptyList()
    return serialized.split(";").mapNotNull { token ->
        val parts = token.split(",")
        if (parts.size == 2) {
            val lat = parts[0].toDoubleOrNull()
            val lng = parts[1].toDoubleOrNull()
            if (lat != null && lng != null) LatLngPoint(lat, lng) else null
        } else null
    }
}
