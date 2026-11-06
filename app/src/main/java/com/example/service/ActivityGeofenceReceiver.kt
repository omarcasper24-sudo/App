package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import java.util.UUID

data class ActivityConfidenceBreakdown(
    val walkingPercent: Int = 0,
    val drivingPercent: Int = 0,
    val stationaryPercent: Int = 100,
    val cyclingPercent: Int = 0
)

data class ActivityRecognitionBroadcastEvent(
    val id: String = UUID.randomUUID().toString(),
    val userState: String, // "WALKING", "DRIVING", "CYCLING", "STATIONARY"
    val rawDetectedType: Int,
    val confidence: Int,
    val transitionType: String, // "ENTER", "EXIT", "POLL_UPDATE"
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val breakdown: ActivityConfidenceBreakdown = ActivityConfidenceBreakdown(),
    val source: String = "ActivityRecognitionClient"
)

class ActivityGeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 1. Handle standard Google Play Services ActivityRecognitionResult (periodic confidence updates)
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            if (result != null) {
                handleActivityRecognitionResult(context, result)
            }
        }

        // 2. Handle Google Play Services ActivityTransitionResult (explicit ENTER/EXIT state transitions)
        if (ActivityTransitionResult.hasResult(intent)) {
            val transitionResult = ActivityTransitionResult.extractResult(intent)
            if (transitionResult != null) {
                handleActivityTransitionResult(context, transitionResult)
            }
        }

        // 3. Handle explicit app-level Activity State Broadcasts (from ActivityRecognitionClient wrapper or test triggers)
        if (intent.action == ACTION_USER_ACTIVITY_STATE_BROADCAST) {
            val detectedType = intent.getIntExtra(
                EXTRA_DETECTED_ACTIVITY_TYPE,
                DetectedActivity.STILL
            )
            val confidence = intent.getIntExtra(EXTRA_ACTIVITY_CONFIDENCE, 92).coerceIn(0, 100)
            val transitionLabel = intent.getStringExtra(EXTRA_TRANSITION_TYPE) ?: "ENTER"
            val mappedState = mapDetectedActivityToUserState(detectedType)
            val breakdown = buildConfidenceBreakdown(detectedType, confidence)

            val event = ActivityRecognitionBroadcastEvent(
                userState = mappedState,
                rawDetectedType = detectedType,
                confidence = confidence,
                transitionType = transitionLabel,
                timestampEpochMs = System.currentTimeMillis(),
                breakdown = breakdown,
                source = intent.getStringExtra(EXTRA_BROADCAST_SOURCE) ?: "ActivityRecognitionClient"
            )
            LifeTrackLocationService.onActivityRecognitionBroadcast(event)
        }

        // 4. Handle Geofencing transitions for frequently visited locations
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent != null && !geofencingEvent.hasError()) {
            val transition = geofencingEvent.geofenceTransition
            val triggeringIds = geofencingEvent.triggeringGeofences?.map { it.requestId }.orEmpty()
            if (transition == Geofence.GEOFENCE_TRANSITION_ENTER ||
                transition == Geofence.GEOFENCE_TRANSITION_DWELL ||
                transition == Geofence.GEOFENCE_TRANSITION_EXIT
            ) {
                LifeTrackLocationService.onGeofenceTransition(triggeringIds, transition)
            }
        }
    }

    private fun handleActivityRecognitionResult(
        context: Context,
        result: ActivityRecognitionResult
    ) {
        val mostProbable = result.mostProbableActivity
        val mappedState = mapDetectedActivityToUserState(mostProbable.type)

        val walkConf = maxOf(
            result.getActivityConfidence(DetectedActivity.WALKING),
            result.getActivityConfidence(DetectedActivity.ON_FOOT),
            result.getActivityConfidence(DetectedActivity.RUNNING)
        )
        val driveConf = result.getActivityConfidence(DetectedActivity.IN_VEHICLE)
        val stillConf = result.getActivityConfidence(DetectedActivity.STILL)
        val bikeConf = result.getActivityConfidence(DetectedActivity.ON_BICYCLE)

        val breakdown = ActivityConfidenceBreakdown(
            walkingPercent = walkConf.coerceIn(0, 100),
            drivingPercent = driveConf.coerceIn(0, 100),
            stationaryPercent = stillConf.coerceIn(0, 100),
            cyclingPercent = bikeConf.coerceIn(0, 100)
        )

        val event = ActivityRecognitionBroadcastEvent(
            userState = mappedState,
            rawDetectedType = mostProbable.type,
            confidence = mostProbable.confidence.coerceIn(0, 100),
            transitionType = "POLL_UPDATE",
            timestampEpochMs = result.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
            breakdown = breakdown,
            source = "ActivityRecognitionClient"
        )

        LifeTrackLocationService.onActivityRecognitionBroadcast(event)
        broadcastStateToAppReceivers(context, event)
    }

    private fun handleActivityTransitionResult(
        context: Context,
        transitionResult: ActivityTransitionResult
    ) {
        for (transitionEvent in transitionResult.transitionEvents) {
            val mappedState = mapDetectedActivityToUserState(transitionEvent.activityType)
            val transitionName = if (transitionEvent.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                "ENTER"
            } else {
                "EXIT"
            }

            // When exiting a movement state without a new enter yet, fall back to STATIONARY
            val effectiveState = if (transitionName == "EXIT" && mappedState != "STATIONARY") {
                "STATIONARY"
            } else {
                mappedState
            }

            val elapsedDeltaMs = SystemClock.elapsedRealtimeNanos() - transitionEvent.elapsedRealTimeNanos
            val eventEpochMs = (System.currentTimeMillis() - (elapsedDeltaMs / 1_000_000L))
                .coerceAtMost(System.currentTimeMillis())

            val breakdown = buildConfidenceBreakdown(
                detectedType = if (effectiveState == "STATIONARY") DetectedActivity.STILL else transitionEvent.activityType,
                primaryConfidence = 95
            )

            val event = ActivityRecognitionBroadcastEvent(
                userState = effectiveState,
                rawDetectedType = transitionEvent.activityType,
                confidence = 95,
                transitionType = transitionName,
                timestampEpochMs = eventEpochMs,
                breakdown = breakdown,
                source = "ActivityTransitionAPI"
            )

            LifeTrackLocationService.onActivityRecognitionBroadcast(event)
            broadcastStateToAppReceivers(context, event)
        }
    }

    private fun broadcastStateToAppReceivers(
        context: Context,
        event: ActivityRecognitionBroadcastEvent
    ) {
        val stateIntent = Intent(ACTION_APP_STATE_EVENT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_USER_STATE, event.userState)
            putExtra(EXTRA_DETECTED_ACTIVITY_TYPE, event.rawDetectedType)
            putExtra(EXTRA_ACTIVITY_CONFIDENCE, event.confidence)
            putExtra(EXTRA_TRANSITION_TYPE, event.transitionType)
            putExtra(EXTRA_EVENT_TIMESTAMP, event.timestampEpochMs)
        }
        context.sendBroadcast(stateIntent)
    }

    companion object {
        const val ACTION_ACTIVITY_RECOGNITION_UPDATES = "com.example.action.ACTIVITY_RECOGNITION_UPDATES"
        const val ACTION_ACTIVITY_TRANSITION_UPDATES = "com.example.action.ACTIVITY_TRANSITION_UPDATES"
        const val ACTION_USER_ACTIVITY_STATE_BROADCAST = "com.example.action.USER_ACTIVITY_STATE_BROADCAST"
        const val ACTION_APP_STATE_EVENT = "com.example.action.APP_STATE_EVENT"
        const val ACTION_GEOFENCE_UPDATES = "com.example.action.GEOFENCE_UPDATES"

        const val EXTRA_USER_STATE = "extra_user_state"
        const val EXTRA_DETECTED_ACTIVITY_TYPE = "extra_detected_activity_type"
        const val EXTRA_ACTIVITY_CONFIDENCE = "extra_activity_confidence"
        const val EXTRA_TRANSITION_TYPE = "extra_transition_type"
        const val EXTRA_EVENT_TIMESTAMP = "extra_event_timestamp"
        const val EXTRA_BROADCAST_SOURCE = "extra_broadcast_source"

        fun mapDetectedActivityToUserState(detectedActivityType: Int): String {
            return when (detectedActivityType) {
                DetectedActivity.IN_VEHICLE -> "DRIVING"
                DetectedActivity.ON_BICYCLE -> "CYCLING"
                DetectedActivity.WALKING,
                DetectedActivity.RUNNING,
                DetectedActivity.ON_FOOT -> "WALKING"
                DetectedActivity.STILL,
                DetectedActivity.TILTING -> "STATIONARY"
                else -> "STATIONARY"
            }
        }

        fun mapUserStateToDetectedActivity(userState: String): Int {
            return when (userState) {
                "DRIVING" -> DetectedActivity.IN_VEHICLE
                "CYCLING" -> DetectedActivity.ON_BICYCLE
                "WALKING" -> DetectedActivity.WALKING
                else -> DetectedActivity.STILL
            }
        }

        fun buildConfidenceBreakdown(detectedType: Int, primaryConfidence: Int): ActivityConfidenceBreakdown {
            val conf = primaryConfidence.coerceIn(50, 100)
            val remainder = (100 - conf).coerceAtLeast(0)
            return when (mapDetectedActivityToUserState(detectedType)) {
                "WALKING" -> ActivityConfidenceBreakdown(
                    walkingPercent = conf,
                    stationaryPercent = remainder / 2,
                    drivingPercent = remainder / 4,
                    cyclingPercent = remainder / 4
                )
                "DRIVING" -> ActivityConfidenceBreakdown(
                    drivingPercent = conf,
                    stationaryPercent = remainder / 2,
                    walkingPercent = remainder / 4,
                    cyclingPercent = remainder / 4
                )
                "CYCLING" -> ActivityConfidenceBreakdown(
                    cyclingPercent = conf,
                    walkingPercent = remainder / 2,
                    drivingPercent = remainder / 4,
                    stationaryPercent = remainder / 4
                )
                else -> ActivityConfidenceBreakdown(
                    stationaryPercent = conf,
                    walkingPercent = remainder / 2,
                    drivingPercent = remainder / 4,
                    cyclingPercent = remainder / 4
                )
            }
        }

        /**
         * Dispatches a broadcast Intent through Android's BroadcastReceiver pipeline to
         * ActivityGeofenceReceiver so both system ActivityRecognitionClient callbacks and
         * interactive state changes flow through the same broadcast architecture.
         */
        fun dispatchActivityStateBroadcast(
            context: Context,
            userState: String,
            confidence: Int = 94,
            transitionType: String = "ENTER",
            source: String = "ActivityRecognitionClient"
        ) {
            val detectedType = mapUserStateToDetectedActivity(userState)
            val intent = Intent(context, ActivityGeofenceReceiver::class.java).apply {
                action = ACTION_USER_ACTIVITY_STATE_BROADCAST
                putExtra(EXTRA_DETECTED_ACTIVITY_TYPE, detectedType)
                putExtra(EXTRA_ACTIVITY_CONFIDENCE, confidence)
                putExtra(EXTRA_TRANSITION_TYPE, transitionType)
                putExtra(EXTRA_BROADCAST_SOURCE, source)
            }
            context.sendBroadcast(intent)
        }
    }
}
