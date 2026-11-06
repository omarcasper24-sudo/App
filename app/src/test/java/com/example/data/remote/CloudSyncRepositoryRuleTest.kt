package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.PlaceLocation
import com.example.data.model.TripRecord
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveTripAndObserveTrips_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore)
        val tripId = "trip_${UUID.randomUUID().toString().replace("-", "")}"

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveTrip(
                TripRecord(
                    id = tripId,
                    userId = uid,
                    dateKey = "2026-11-06",
                    transportMode = "WALKING",
                    startTimeEpochMs = 1700000000000L,
                    endTimeEpochMs = 1700001800000L,
                    durationMinutes = 30L,
                    distanceKm = 2.4,
                    startLabel = "Home Residence",
                    endLabel = "Tech Park",
                    startLat = 24.7136,
                    startLng = 46.6753,
                    endLat = 24.7236,
                    endLng = 46.6853,
                    routePointsSerialized = "24.7136,46.6753;24.7236,46.6853"
                )
            )
        }
        assertTrue(result.isSuccess)

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTrips(uid).first { list -> list.any { it.id == tripId } }
        }
        assertTrue(emitted.any { it.id == tripId })
    }

    @Test
    fun savePlaceAndObservePlaces_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore)
        val placeId = "place_${UUID.randomUUID().toString().replace("-", "")}"

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.savePlace(
                PlaceLocation(
                    id = placeId,
                    userId = uid,
                    name = "Central Office",
                    category = "WORK",
                    latitude = 24.7136,
                    longitude = 46.6753,
                    radiusMeters = 120L,
                    visitCount = 12L,
                    totalDurationMinutes = 5400L,
                    lastArrivalEpochMs = 1700000000000L,
                    lastDepartureEpochMs = 1700028800000L
                )
            )
        }
        assertTrue(result.isSuccess)

        val places = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePlaces(uid).first { list -> list.any { it.id == placeId } }
        }
        assertTrue(places.any { it.id == placeId })
    }

    @Test
    fun getTripById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSyncRepository(firestore)
        val tripId = "trip_${UUID.randomUUID().toString().replace("-", "")}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveTrip(
                TripRecord(
                    id = tripId,
                    userId = aliceUid,
                    dateKey = "2026-11-06",
                    transportMode = "DRIVING",
                    startTimeEpochMs = 1700000000000L,
                    endTimeEpochMs = 1700003600000L,
                    durationMinutes = 60L,
                    distanceKm = 25.0,
                    startLabel = "North Hub",
                    endLabel = "South Plaza",
                    startLat = 24.7136,
                    startLng = 46.6753,
                    endLat = 24.8136,
                    endLng = 46.7753,
                    routePointsSerialized = "24.7136,46.6753;24.8136,46.7753"
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudSyncRepository(firestore)
        var caught: Throwable? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getTripById(aliceUid, tripId)
            }
        } catch (e: Throwable) {
            caught = e
        }
        assertNotNull(caught)
        val firestoreEx = caught?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeTrips_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = CloudSyncRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeTrips(aliceUid).first()
            }
        } catch (e: Throwable) {
            caught = e
        }
        assertNotNull(caught)
        val firestoreEx = caught?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@lifetrack.test"
        const val BOB_EMAIL = "bob@lifetrack.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
