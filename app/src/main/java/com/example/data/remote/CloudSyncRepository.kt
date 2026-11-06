package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.PlaceLocation
import com.example.data.model.SleepLog
import com.example.data.model.TripRecord
import com.example.data.model.WorkSession
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CloudSyncRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeTrips(userId: String = requireUserId()): Flow<List<TripRecord>> = flow {
        val path = "users/$userId/trips"
        emitAll(
            db.collection("users").document(userId).collection("trips")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(TripRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.startTimeEpochMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePlaces(userId: String = requireUserId()): Flow<List<PlaceLocation>> = flow {
        val path = "users/$userId/places"
        emitAll(
            db.collection("users").document(userId).collection("places")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PlaceLocation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.visitCount }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSleepLogs(userId: String = requireUserId()): Flow<List<SleepLog>> = flow {
        val path = "users/$userId/sleep_logs"
        emitAll(
            db.collection("users").document(userId).collection("sleep_logs")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SleepLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.bedtimeEpochMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeWorkSessions(userId: String = requireUserId()): Flow<List<WorkSession>> = flow {
        val path = "users/$userId/work_sessions"
        emitAll(
            db.collection("users").document(userId).collection("work_sessions")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(WorkSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.arrivalEpochMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveTrip(trip: TripRecord): Result<String> {
        val uid = requireUserId()
        val docId = trip.id.ifBlank { UUID.randomUUID().toString() }
        val docRef = db.collection("users").document(uid).collection("trips").document(docId)
        return try {
            val payload = mapOf(
                "userId" to uid,
                "dateKey" to trip.dateKey.take(10).padEnd(10, '0'),
                "transportMode" to trip.transportMode,
                "startTimeEpochMs" to trip.startTimeEpochMs,
                "endTimeEpochMs" to trip.endTimeEpochMs.coerceAtLeast(trip.startTimeEpochMs),
                "durationMinutes" to trip.durationMinutes.coerceIn(0L, 10080L),
                "distanceKm" to trip.distanceKm.coerceIn(0.0, 50000.0),
                "startLabel" to trip.startLabel.ifBlank { "Origin" }.take(120),
                "endLabel" to trip.endLabel.ifBlank { "Destination" }.take(120),
                "startLat" to trip.startLat.coerceIn(-90.0, 90.0),
                "startLng" to trip.startLng.coerceIn(-180.0, 180.0),
                "endLat" to trip.endLat.coerceIn(-90.0, 90.0),
                "endLng" to trip.endLng.coerceIn(-180.0, 180.0),
                "routePointsSerialized" to trip.routePointsSerialized.take(4000),
                "createdAt" to (trip.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getTripById(ownerUserId: String, tripId: String): TripRecord? {
        val docRef = db.collection("users").document(ownerUserId).collection("trips").document(tripId)
        return try {
            docRef.get().await().toObject(TripRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun deleteTrip(tripId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("trips").document(tripId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun savePlace(place: PlaceLocation): Result<String> {
        val uid = requireUserId()
        val docId = place.id.ifBlank { UUID.randomUUID().toString() }
        val docRef = db.collection("users").document(uid).collection("places").document(docId)
        return try {
            val payload = mapOf(
                "userId" to uid,
                "name" to place.name.ifBlank { "Location" }.take(100),
                "category" to place.category,
                "latitude" to place.latitude.coerceIn(-90.0, 90.0),
                "longitude" to place.longitude.coerceIn(-180.0, 180.0),
                "radiusMeters" to place.radiusMeters.coerceIn(10L, 10000L),
                "visitCount" to place.visitCount.coerceIn(0L, 1000000L),
                "totalDurationMinutes" to place.totalDurationMinutes.coerceIn(0L, 52560000L),
                "lastArrivalEpochMs" to place.lastArrivalEpochMs.coerceAtLeast(0L),
                "lastDepartureEpochMs" to place.lastDepartureEpochMs.coerceAtLeast(0L),
                "createdAt" to (place.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveSleepLog(sleepLog: SleepLog): Result<String> {
        val uid = requireUserId()
        val docId = sleepLog.id.ifBlank { UUID.randomUUID().toString() }
        val docRef = db.collection("users").document(uid).collection("sleep_logs").document(docId)
        return try {
            val payload = mapOf(
                "userId" to uid,
                "dateKey" to sleepLog.dateKey.take(10).padEnd(10, '0'),
                "bedtimeEpochMs" to sleepLog.bedtimeEpochMs.coerceAtLeast(0L),
                "wakeTimeEpochMs" to sleepLog.wakeTimeEpochMs.coerceAtLeast(sleepLog.bedtimeEpochMs),
                "durationMinutes" to sleepLog.durationMinutes.coerceIn(0L, 1440L),
                "sleepQualityScore" to sleepLog.sleepQualityScore.coerceIn(0L, 100L),
                "scheduleRegularity" to sleepLog.scheduleRegularity,
                "notes" to sleepLog.notes.take(300),
                "createdAt" to (sleepLog.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveWorkSession(workSession: WorkSession): Result<String> {
        val uid = requireUserId()
        val docId = workSession.id.ifBlank { UUID.randomUUID().toString() }
        val docRef = db.collection("users").document(uid).collection("work_sessions").document(docId)
        return try {
            val payload = mapOf(
                "userId" to uid,
                "dateKey" to workSession.dateKey.take(10).padEnd(10, '0'),
                "workplaceName" to workSession.workplaceName.ifBlank { "Workplace" }.take(100),
                "arrivalEpochMs" to workSession.arrivalEpochMs.coerceAtLeast(0L),
                "departureEpochMs" to workSession.departureEpochMs.coerceAtLeast(workSession.arrivalEpochMs),
                "durationMinutes" to workSession.durationMinutes.coerceIn(0L, 1440L),
                "sessionType" to workSession.sessionType,
                "isManuallyCorrected" to workSession.isManuallyCorrected,
                "createdAt" to (workSession.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }
}
