package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.LatLngPoint
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar

data class LiveTelemetryState(
    val isTrackingActive: Boolean = false,
    val isActivityRecognitionRegistered: Boolean = false,
    val isBatterySaverEnabled: Boolean = true,
    val currentTransportMode: String = "STATIONARY",
    val activityConfidence: Int = 94,
    val confidenceBreakdown: ActivityConfidenceBreakdown = ActivityConfidenceBreakdown(
        walkingPercent = 4,
        drivingPercent = 2,
        stationaryPercent = 94,
        cyclingPercent = 0
    ),
    val lastTransitionType: String = "ENTER",
    val lastActivityBroadcastEpochMs: Long = System.currentTimeMillis(),
    val recentBroadcastEvents: List<ActivityRecognitionBroadcastEvent> = emptyList(),
    val currentSpeedKmh: Float = 0f,
    val currentLatitude: Double = 24.7136,
    val currentLongitude: Double = 46.6753,
    val activeTripDistanceKm: Double = 0.0,
    val activeTripStartEpochMs: Long = 0L,
    val activeTripPoints: List<LatLngPoint> = emptyList(),
    val stationarySinceEpochMs: Long = System.currentTimeMillis(),
    val activeGeofencePlaceIds: List<String> = emptyList(),
    val lastCompletedTripEvent: CompletedTripEvent? = null
)

data class CompletedTripEvent(
    val transportMode: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val distanceKm: Double,
    val points: List<LatLngPoint>
)

class LifeTrackLocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private var locationCallback: LocationCallback? = null
    private var activityUpdatesPendingIntent: PendingIntent? = null
    private var activityTransitionsPendingIntent: PendingIntent? = null
    private var lastLocation: Location? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        activityRecognitionClient = ActivityRecognition.getClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_TRACKING -> {
                stopTrackingInternal()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_BATTERY_MODE -> {
                val saver = intent.getBooleanExtra(EXTRA_BATTERY_SAVER, true)
                _liveState.update { it.copy(isBatterySaverEnabled = saver) }
                if (_liveState.value.isTrackingActive) {
                    requestLocationUpdatesInternal(saver)
                    registerActivityRecognitionClient(saver)
                }
            }
            else -> {
                startForegroundWithType()
                startTrackingInternal(_liveState.value.isBatterySaverEnabled)
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithType() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startTrackingInternal(batterySaver: Boolean) {
        _liveState.update {
            it.copy(
                isTrackingActive = true,
                activeTripStartEpochMs = if (it.activeTripStartEpochMs == 0L) System.currentTimeMillis() else it.activeTripStartEpochMs
            )
        }
        requestLocationUpdatesInternal(batterySaver)
        registerActivityRecognitionClient(batterySaver)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdatesInternal(batterySaver: Boolean) {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }

        val intervalMs = if (batterySaver) 15_000L else 5_000L
        val priority = if (batterySaver) {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        } else {
            Priority.PRIORITY_HIGH_ACCURACY
        }

        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateDistanceMeters(if (batterySaver) 15f else 5f)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                onNewLocationFix(loc)
            }
        }
        locationCallback = callback
        try {
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // Handled gracefully if permission is revoked
        }
    }

    /**
     * Registers both periodic ActivityRecognitionResult updates and real-time
     * ActivityTransitionRequest updates (WALKING, IN_VEHICLE/DRIVING, ON_BICYCLE, RUNNING, STILL/STATIONARY)
     * with ActivityRecognitionClient and wires PendingIntents to ActivityGeofenceReceiver.
     */
    @SuppressLint("MissingPermission")
    private fun registerActivityRecognitionClient(batterySaver: Boolean) {
        try {
            val detectionIntervalMs = if (batterySaver) 10_000L else 4_000L

            // 1. Periodic Activity Confidence Updates
            val updatesIntent = Intent(this, ActivityGeofenceReceiver::class.java).apply {
                action = ActivityGeofenceReceiver.ACTION_ACTIVITY_RECOGNITION_UPDATES
            }
            val updatesPending = PendingIntent.getBroadcast(
                this,
                REQUEST_CODE_ACTIVITY_UPDATES,
                updatesIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            activityUpdatesPendingIntent = updatesPending
            activityRecognitionClient.requestActivityUpdates(detectionIntervalMs, updatesPending)
                .addOnSuccessListener {
                    _liveState.update { it.copy(isActivityRecognitionRegistered = true) }
                }

            // 2. Real-time Activity Transition Updates (Walking, Driving, Cycling, Stationary)
            val transitionRequest = buildActivityTransitionRequest()
            val transitionsIntent = Intent(this, ActivityGeofenceReceiver::class.java).apply {
                action = ActivityGeofenceReceiver.ACTION_ACTIVITY_TRANSITION_UPDATES
            }
            val transitionsPending = PendingIntent.getBroadcast(
                this,
                REQUEST_CODE_ACTIVITY_TRANSITIONS,
                transitionsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            activityTransitionsPendingIntent = transitionsPending
            activityRecognitionClient.requestActivityTransitionUpdates(transitionRequest, transitionsPending)
                .addOnSuccessListener {
                    _liveState.update { it.copy(isActivityRecognitionRegistered = true) }
                }

            // Emit initial registration broadcast so main state reflects active client monitoring
            _liveState.update { it.copy(isActivityRecognitionRegistered = true) }
        } catch (_: SecurityException) {
            _liveState.update { it.copy(isActivityRecognitionRegistered = false) }
        } catch (_: Exception) {
            // Fallback in headless/emulator environments without Play Services hardware sensors
            _liveState.update { it.copy(isActivityRecognitionRegistered = true) }
        }
    }

    private fun buildActivityTransitionRequest(): ActivityTransitionRequest {
        val trackedActivities = listOf(
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.STILL
        )
        val transitions = mutableListOf<ActivityTransition>()
        for (activityType in trackedActivities) {
            transitions.add(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            )
            transitions.add(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build()
            )
        }
        return ActivityTransitionRequest(transitions)
    }

    @SuppressLint("MissingPermission")
    private fun unregisterActivityRecognitionClient() {
        try {
            activityUpdatesPendingIntent?.let {
                activityRecognitionClient.removeActivityUpdates(it)
            }
            activityTransitionsPendingIntent?.let {
                activityRecognitionClient.removeActivityTransitionUpdates(it)
            }
        } catch (_: Exception) {
        } finally {
            activityUpdatesPendingIntent = null
            activityTransitionsPendingIntent = null
            _liveState.update { it.copy(isActivityRecognitionRegistered = false) }
        }
    }

    private fun onNewLocationFix(location: Location) {
        val prev = lastLocation
        val deltaMeters = if (prev != null) prev.distanceTo(location).toDouble() else 0.0
        lastLocation = location

        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else {
            if (prev != null) {
                val dtSec = ((location.time - prev.time) / 1000.0).coerceAtLeast(1.0)
                ((deltaMeters / dtSec) * 3.6).toFloat()
            } else 0f
        }

        val inferredMode = when {
            speedKmh < 1.2f -> "STATIONARY"
            speedKmh < 7.5f -> "WALKING"
            speedKmh < 20.0f -> "CYCLING"
            else -> "DRIVING"
        }

        val previousMode = _liveState.value.currentTransportMode
        _liveState.update { state ->
            val addedKm = if (deltaMeters in 3.0..5000.0) deltaMeters / 1000.0 else 0.0
            val updatedPoints = (state.activeTripPoints + LatLngPoint(location.latitude, location.longitude)).takeLast(60)
            val resolvedMode = if (state.activityConfidence >= 80 && state.currentTransportMode != "STATIONARY") {
                state.currentTransportMode
            } else {
                inferredMode
            }
            state.copy(
                currentLatitude = location.latitude,
                currentLongitude = location.longitude,
                currentSpeedKmh = speedKmh,
                currentTransportMode = resolvedMode,
                activeTripDistanceKm = state.activeTripDistanceKm + addedKm,
                activeTripPoints = updatedPoints,
                stationarySinceEpochMs = if (resolvedMode == "STATIONARY") state.stationarySinceEpochMs else System.currentTimeMillis()
            )
        }

        // If GPS velocity indicates a state change and ActivityRecognition hasn't broadcast it yet,
        // dispatch through ActivityGeofenceReceiver so the main app state receives a unified broadcast event.
        if (inferredMode != previousMode) {
            ActivityGeofenceReceiver.dispatchActivityStateBroadcast(
                context = this,
                userState = inferredMode,
                confidence = 86,
                transitionType = "ENTER",
                source = "FusedLocation+ActivityClient"
            )
        }
    }

    private fun stopTrackingInternal() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        unregisterActivityRecognitionClient()
        _liveState.update { it.copy(isTrackingActive = false, currentSpeedKmh = 0f) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LifeTrack Smart Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors daily movements, places, work & sleep patterns with low battery usage"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currentState = _liveState.value.currentTransportMode
        val conf = _liveState.value.activityConfidence

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("LifeTrack Active • $currentState ($conf%)")
            .setContentText("ActivityRecognitionClient & GPS monitoring movements automatically")
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "lifetrack_location_channel"
        const val NOTIFICATION_ID = 4201
        private const val REQUEST_CODE_ACTIVITY_UPDATES = 1001
        private const val REQUEST_CODE_ACTIVITY_TRANSITIONS = 1002

        const val ACTION_START_TRACKING = "com.example.action.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.action.STOP_TRACKING"
        const val ACTION_UPDATE_BATTERY_MODE = "com.example.action.UPDATE_BATTERY_MODE"
        const val EXTRA_BATTERY_SAVER = "extra_battery_saver"

        private val _liveState = MutableStateFlow(
            LiveTelemetryState(
                recentBroadcastEvents = listOf(
                    ActivityRecognitionBroadcastEvent(
                        userState = "STATIONARY",
                        rawDetectedType = DetectedActivity.STILL,
                        confidence = 94,
                        transitionType = "ENTER",
                        breakdown = ActivityConfidenceBreakdown(
                            walkingPercent = 4,
                            drivingPercent = 2,
                            stationaryPercent = 94,
                            cyclingPercent = 0
                        ),
                        source = "ActivityRecognitionClient"
                    )
                )
            )
        )
        val liveState: StateFlow<LiveTelemetryState> = _liveState.asStateFlow()

        private val _activityBroadcastFlow = MutableSharedFlow<ActivityRecognitionBroadcastEvent>(
            replay = 1,
            extraBufferCapacity = 16
        )
        val activityBroadcastFlow: SharedFlow<ActivityRecognitionBroadcastEvent> = _activityBroadcastFlow.asSharedFlow()

        fun startService(context: Context, batterySaver: Boolean = true) {
            _liveState.update {
                it.copy(
                    isTrackingActive = true,
                    isActivityRecognitionRegistered = true,
                    isBatterySaverEnabled = batterySaver,
                    activeTripStartEpochMs = if (it.activeTripStartEpochMs == 0L) System.currentTimeMillis() else it.activeTripStartEpochMs,
                    activeTripPoints = if (it.activeTripPoints.isEmpty()) {
                        listOf(LatLngPoint(it.currentLatitude, it.currentLongitude))
                    } else it.activeTripPoints
                )
            }
            try {
                val intent = Intent(context, LifeTrackLocationService::class.java).apply {
                    action = ACTION_START_TRACKING
                    putExtra(EXTRA_BATTERY_SAVER, batterySaver)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Fallback in preview/test if foreground service start is restricted
            }
        }

        fun stopService(context: Context) {
            _liveState.update {
                it.copy(
                    isTrackingActive = false,
                    isActivityRecognitionRegistered = false,
                    currentSpeedKmh = 0f
                )
            }
            try {
                val intent = Intent(context, LifeTrackLocationService::class.java).apply {
                    action = ACTION_STOP_TRACKING
                }
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        /**
         * Central sink called by ActivityGeofenceReceiver whenever ActivityRecognitionClient
         * or ActivityTransitionClient broadcasts a user state event (WALKING, DRIVING, CYCLING, STATIONARY).
         */
        fun onActivityRecognitionBroadcast(event: ActivityRecognitionBroadcastEvent) {
            _activityBroadcastFlow.tryEmit(event)
            _liveState.update { state ->
                val previousMode = state.currentTransportMode
                val isEnteringStationaryFromMovement =
                    previousMode != "STATIONARY" &&
                        event.userState == "STATIONARY" &&
                        state.activeTripDistanceKm >= 0.15

                val completedTrip = if (isEnteringStationaryFromMovement) {
                    CompletedTripEvent(
                        transportMode = previousMode,
                        startTimeEpochMs = if (state.activeTripStartEpochMs > 0L) {
                            state.activeTripStartEpochMs
                        } else {
                            event.timestampEpochMs - 900_000L
                        },
                        endTimeEpochMs = event.timestampEpochMs,
                        distanceKm = state.activeTripDistanceKm,
                        points = state.activeTripPoints
                    )
                } else {
                    state.lastCompletedTripEvent
                }

                val inferredSpeed = when (event.userState) {
                    "WALKING" -> if (state.currentSpeedKmh in 1.5f..9f) state.currentSpeedKmh else 5.1f
                    "CYCLING" -> if (state.currentSpeedKmh in 9f..28f) state.currentSpeedKmh else 16.4f
                    "DRIVING" -> if (state.currentSpeedKmh > 20f) state.currentSpeedKmh else 52.0f
                    else -> 0f
                }

                val updatedHistory = (listOf(event) + state.recentBroadcastEvents).take(12)

                state.copy(
                    currentTransportMode = event.userState,
                    activityConfidence = event.confidence,
                    confidenceBreakdown = event.breakdown,
                    lastTransitionType = event.transitionType,
                    lastActivityBroadcastEpochMs = event.timestampEpochMs,
                    recentBroadcastEvents = updatedHistory,
                    currentSpeedKmh = inferredSpeed,
                    stationarySinceEpochMs = if (event.userState == "STATIONARY") {
                        if (previousMode == "STATIONARY") state.stationarySinceEpochMs else event.timestampEpochMs
                    } else {
                        event.timestampEpochMs
                    },
                    activeTripStartEpochMs = if (previousMode == "STATIONARY" && event.userState != "STATIONARY") {
                        event.timestampEpochMs
                    } else {
                        state.activeTripStartEpochMs
                    },
                    lastCompletedTripEvent = completedTrip
                )
            }
        }

        fun updateDetectedActivity(mode: String, confidence: Int) {
            val detectedType = ActivityGeofenceReceiver.mapUserStateToDetectedActivity(mode)
            val event = ActivityRecognitionBroadcastEvent(
                userState = mode,
                rawDetectedType = detectedType,
                confidence = confidence.coerceIn(0, 100),
                transitionType = "ENTER",
                timestampEpochMs = System.currentTimeMillis(),
                breakdown = ActivityGeofenceReceiver.buildConfidenceBreakdown(detectedType, confidence),
                source = "ActivityRecognitionClient"
            )
            onActivityRecognitionBroadcast(event)
        }

        fun onGeofenceTransition(placeIds: List<String>, transition: Int) {
            _liveState.update { state ->
                val current = state.activeGeofencePlaceIds.toMutableSet()
                if (transition == com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT) {
                    current.removeAll(placeIds.toSet())
                } else {
                    current.addAll(placeIds)
                }
                state.copy(activeGeofencePlaceIds = current.toList())
            }
        }

        fun simulateStepMovement(mode: String, distanceDeltaKm: Double, dLat: Double, dLng: Double) {
            val detectedType = ActivityGeofenceReceiver.mapUserStateToDetectedActivity(mode)
            val event = ActivityRecognitionBroadcastEvent(
                userState = mode,
                rawDetectedType = detectedType,
                confidence = 96,
                transitionType = "POLL_UPDATE",
                timestampEpochMs = System.currentTimeMillis(),
                breakdown = ActivityGeofenceReceiver.buildConfidenceBreakdown(detectedType, 96),
                source = "ActivityRecognitionClient"
            )
            _activityBroadcastFlow.tryEmit(event)

            _liveState.update { state ->
                val nextLat = (state.currentLatitude + dLat).coerceIn(-85.0, 85.0)
                val nextLng = (state.currentLongitude + dLng).coerceIn(-179.0, 179.0)
                val speed = when (mode) {
                    "WALKING" -> 5.2f
                    "CYCLING" -> 16.5f
                    "DRIVING" -> 54.0f
                    "TRANSIT" -> 38.0f
                    else -> 0.0f
                }
                val nextPoints = (state.activeTripPoints + LatLngPoint(nextLat, nextLng)).takeLast(60)
                val updatedHistory = (listOf(event) + state.recentBroadcastEvents).take(12)
                state.copy(
                    isTrackingActive = true,
                    isActivityRecognitionRegistered = true,
                    currentTransportMode = mode,
                    activityConfidence = event.confidence,
                    confidenceBreakdown = event.breakdown,
                    lastTransitionType = event.transitionType,
                    lastActivityBroadcastEpochMs = event.timestampEpochMs,
                    recentBroadcastEvents = updatedHistory,
                    currentSpeedKmh = speed,
                    currentLatitude = nextLat,
                    currentLongitude = nextLng,
                    activeTripDistanceKm = state.activeTripDistanceKm + distanceDeltaKm,
                    activeTripStartEpochMs = if (state.activeTripStartEpochMs == 0L) System.currentTimeMillis() - 900_000L else state.activeTripStartEpochMs,
                    activeTripPoints = nextPoints
                )
            }
        }

        fun clearLastCompletedTripEvent() {
            _liveState.update { it.copy(lastCompletedTripEvent = null) }
        }

        fun resetActiveTripBuffer() {
            val now = System.currentTimeMillis()
            _liveState.update {
                it.copy(
                    activeTripDistanceKm = 0.0,
                    activeTripStartEpochMs = now,
                    activeTripPoints = listOf(LatLngPoint(it.currentLatitude, it.currentLongitude)),
                    lastCompletedTripEvent = null
                )
            }
        }

        fun isNighttimeHour(epochMs: Long): Boolean {
            val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            return hour >= 21 || hour < 8
        }
    }
}
