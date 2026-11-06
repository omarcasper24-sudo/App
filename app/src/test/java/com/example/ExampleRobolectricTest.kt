package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.service.ActivityGeofenceReceiver
import com.example.service.LifeTrackLocationService
import com.google.android.gms.location.DetectedActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {

    @Test
    fun activityGeofenceReceiver_broadcastsWalkingDrivingAndStationaryToMainState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = ActivityGeofenceReceiver()

        // 1. Broadcast WALKING state
        val walkingIntent = Intent(context, ActivityGeofenceReceiver::class.java).apply {
            action = ActivityGeofenceReceiver.ACTION_USER_ACTIVITY_STATE_BROADCAST
            putExtra(ActivityGeofenceReceiver.EXTRA_DETECTED_ACTIVITY_TYPE, DetectedActivity.WALKING)
            putExtra(ActivityGeofenceReceiver.EXTRA_ACTIVITY_CONFIDENCE, 93)
            putExtra(ActivityGeofenceReceiver.EXTRA_TRANSITION_TYPE, "ENTER")
        }
        receiver.onReceive(context, walkingIntent)

        val stateAfterWalk = LifeTrackLocationService.liveState.value
        assertEquals("WALKING", stateAfterWalk.currentTransportMode)
        assertEquals(93, stateAfterWalk.activityConfidence)
        assertTrue(stateAfterWalk.confidenceBreakdown.walkingPercent >= 90)

        // 2. Broadcast DRIVING (IN_VEHICLE) state
        val drivingIntent = Intent(context, ActivityGeofenceReceiver::class.java).apply {
            action = ActivityGeofenceReceiver.ACTION_USER_ACTIVITY_STATE_BROADCAST
            putExtra(ActivityGeofenceReceiver.EXTRA_DETECTED_ACTIVITY_TYPE, DetectedActivity.IN_VEHICLE)
            putExtra(ActivityGeofenceReceiver.EXTRA_ACTIVITY_CONFIDENCE, 97)
            putExtra(ActivityGeofenceReceiver.EXTRA_TRANSITION_TYPE, "ENTER")
        }
        receiver.onReceive(context, drivingIntent)

        val stateAfterDrive = LifeTrackLocationService.liveState.value
        assertEquals("DRIVING", stateAfterDrive.currentTransportMode)
        assertEquals(97, stateAfterDrive.activityConfidence)
        assertTrue(stateAfterDrive.confidenceBreakdown.drivingPercent >= 90)

        // 3. Broadcast STATIONARY (STILL) state
        val stationaryIntent = Intent(context, ActivityGeofenceReceiver::class.java).apply {
            action = ActivityGeofenceReceiver.ACTION_USER_ACTIVITY_STATE_BROADCAST
            putExtra(ActivityGeofenceReceiver.EXTRA_DETECTED_ACTIVITY_TYPE, DetectedActivity.STILL)
            putExtra(ActivityGeofenceReceiver.EXTRA_ACTIVITY_CONFIDENCE, 98)
            putExtra(ActivityGeofenceReceiver.EXTRA_TRANSITION_TYPE, "ENTER")
        }
        receiver.onReceive(context, stationaryIntent)

        val stateAfterStationary = LifeTrackLocationService.liveState.value
        assertEquals("STATIONARY", stateAfterStationary.currentTransportMode)
        assertEquals(98, stateAfterStationary.activityConfidence)
        assertTrue(stateAfterStationary.confidenceBreakdown.stationaryPercent >= 90)
    }
}
