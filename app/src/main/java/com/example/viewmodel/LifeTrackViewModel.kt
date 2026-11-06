package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.R
import com.example.data.local.LifeTrackDao
import com.example.data.local.LifeTrackDatabase
import com.example.data.model.LatLngPoint
import com.example.data.model.LocalPlaceEntity
import com.example.data.model.LocalSleepEntity
import com.example.data.model.LocalTripEntity
import com.example.data.model.LocalWorkEntity
import com.example.data.model.PlaceLocation
import com.example.data.model.SleepLog
import com.example.data.model.TripRecord
import com.example.data.model.WorkSession
import com.example.data.model.serializeRoutePoints
import com.example.data.remote.CloudSyncRepository
import com.example.service.ActivityGeofenceReceiver
import com.example.service.ActivityRecognitionBroadcastEvent
import com.example.service.LifeTrackLocationService
import com.example.service.LiveTelemetryState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LifeTrackUiState(
    val userId: String = "local_offline_user",
    val isOfflineOnlyMode: Boolean = false,
    val isCloudSyncEnabled: Boolean = true,
    val isArabic: Boolean = false,
    val selectedDateKey: String = todayDateKey(),
    val trips: List<TripRecord> = emptyList(),
    val places: List<PlaceLocation> = emptyList(),
    val sleepLogs: List<SleepLog> = emptyList(),
    val workSessions: List<WorkSession> = emptyList(),
    val liveTelemetry: LiveTelemetryState = LiveTelemetryState(),
    val latestActivityBroadcast: ActivityRecognitionBroadcastEvent? = null,
    val statusBannerMessage: String? = null
)

fun todayDateKey(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}

fun dateKeyDaysAgo(daysAgo: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

class LifeTrackViewModel(
    private val dao: LifeTrackDao,
    private val cloudRepository: CloudSyncRepository?,
    private val activeUserId: String,
    initialOfflineMode: Boolean
) : ViewModel() {

    private val _isArabic = MutableStateFlow(Locale.getDefault().language == "ar")
    private val _selectedDateKey = MutableStateFlow(todayDateKey())
    private val _isCloudSyncEnabled = MutableStateFlow(!initialOfflineMode && cloudRepository != null)
    private val _statusBanner = MutableStateFlow<String?>(null)
    private val _latestActivityBroadcast = MutableStateFlow<ActivityRecognitionBroadcastEvent?>(null)

    private val localTripsFlow = dao.observeTrips(activeUserId)
    private val localPlacesFlow = dao.observePlaces(activeUserId)
    private val localSleepFlow = dao.observeSleepLogs(activeUserId)
    private val localWorkFlow = dao.observeWorkSessions(activeUserId)

    val uiState: StateFlow<LifeTrackUiState> = combine(
        localTripsFlow,
        localPlacesFlow,
        localSleepFlow,
        localWorkFlow,
        LifeTrackLocationService.liveState
    ) { localTrips, localPlaces, localSleeps, localWorks, liveState ->
        LifeTrackUiState(
            userId = activeUserId,
            isOfflineOnlyMode = initialOfflineMode,
            isCloudSyncEnabled = _isCloudSyncEnabled.value,
            isArabic = _isArabic.value,
            selectedDateKey = _selectedDateKey.value,
            trips = localTrips.map { it.toDomain() },
            places = localPlaces.map { it.toDomain() },
            sleepLogs = localSleeps.map { it.toDomain() },
            workSessions = localWorks.map { it.toDomain() },
            liveTelemetry = liveState,
            latestActivityBroadcast = _latestActivityBroadcast.value,
            statusBannerMessage = _statusBanner.value
        )
    }.combine(
        combine(
            _isArabic,
            _selectedDateKey,
            _isCloudSyncEnabled,
            _statusBanner,
            _latestActivityBroadcast
        ) { ar, date, sync, msg, latestBroadcast ->
            Quint(ar, date, sync, msg, latestBroadcast)
        }
    ) { base, meta ->
        base.copy(
            isArabic = meta.first,
            selectedDateKey = meta.second,
            isCloudSyncEnabled = meta.third,
            statusBannerMessage = meta.fourth,
            latestActivityBroadcast = meta.fifth ?: base.liveTelemetry.recentBroadcastEvents.firstOrNull()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = LifeTrackUiState(
            userId = activeUserId,
            isOfflineOnlyMode = initialOfflineMode,
            isCloudSyncEnabled = !initialOfflineMode && cloudRepository != null,
            isArabic = _isArabic.value
        )
    )

    init {
        viewModelScope.launch {
            seedInitialRealisticDataIfEmpty()
        }
        if (!initialOfflineMode && cloudRepository != null) {
            observeCloudChangesAndSyncToRoom(cloudRepository)
        }
        observeActivityRecognitionBroadcasts()
    }

    /**
     * Collects broadcast events emitted from ActivityRecognitionClient -> ActivityGeofenceReceiver ->
     * LifeTrackLocationService and wires them directly into the main application state, including
     * automatic trip completion when transitioning from movement (WALKING/DRIVING) into STATIONARY.
     */
    private fun observeActivityRecognitionBroadcasts() {
        viewModelScope.launch {
            LifeTrackLocationService.activityBroadcastFlow.collect { broadcastEvent ->
                _latestActivityBroadcast.value = broadcastEvent

                val completedTrip = LifeTrackLocationService.liveState.value.lastCompletedTripEvent
                if (completedTrip != null && broadcastEvent.userState == "STATIONARY") {
                    val durationMins = ((completedTrip.endTimeEpochMs - completedTrip.startTimeEpochMs) / 60_000L)
                        .coerceAtLeast(5L)
                    val firstPt = completedTrip.points.firstOrNull()
                    val lastPt = completedTrip.points.lastOrNull()
                    val sLat = firstPt?.lat ?: 24.7136
                    val sLng = firstPt?.lng ?: 46.6753
                    val eLat = lastPt?.lat ?: (sLat + 0.008)
                    val eLng = lastPt?.lng ?: (sLng + 0.006)

                    LifeTrackLocationService.clearLastCompletedTripEvent()
                    recordTrip(
                        transportMode = completedTrip.transportMode,
                        distanceKm = completedTrip.distanceKm,
                        durationMinutes = durationMins,
                        startLabel = "Auto-Detected Origin",
                        endLabel = "Auto-Detected Stop",
                        startLat = sLat,
                        startLng = sLng,
                        endLat = eLat,
                        endLng = eLng,
                        points = completedTrip.points
                    )
                }
            }
        }
    }

    fun dispatchActivityStateBroadcast(
        context: Context,
        userState: String,
        confidence: Int = 95,
        transitionType: String = "ENTER"
    ) {
        ActivityGeofenceReceiver.dispatchActivityStateBroadcast(
            context = context,
            userState = userState,
            confidence = confidence,
            transitionType = transitionType,
            source = "ActivityRecognitionClient"
        )
        _statusBanner.value = if (_isArabic.value) {
            "تم بث حالة النشاط: $userState ($confidence%)"
        } else {
            "ActivityRecognition broadcast: $userState ($confidence% • $transitionType)"
        }
    }

    fun onExternalBroadcastReceived(
        userState: String,
        detectedType: Int,
        confidence: Int,
        transitionType: String,
        timestampEpochMs: Long
    ) {
        val currentLatest = _latestActivityBroadcast.value
        if (currentLatest == null || currentLatest.timestampEpochMs != timestampEpochMs) {
            val event = ActivityRecognitionBroadcastEvent(
                userState = userState,
                rawDetectedType = detectedType,
                confidence = confidence,
                transitionType = transitionType,
                timestampEpochMs = timestampEpochMs,
                breakdown = ActivityGeofenceReceiver.buildConfidenceBreakdown(detectedType, confidence),
                source = "ActivityRecognitionBroadcast"
            )
            _latestActivityBroadcast.value = event
        }
    }

    private fun observeCloudChangesAndSyncToRoom(repo: CloudSyncRepository) {
        viewModelScope.launch {
            repo.observeTrips(activeUserId)
                .catch { e -> handleStreamException(e) }
                .collect { remoteTrips ->
                    if (remoteTrips.isNotEmpty()) {
                        dao.upsertTrips(remoteTrips.map { it.toLocal(activeUserId) })
                    }
                }
        }
        viewModelScope.launch {
            repo.observePlaces(activeUserId)
                .catch { e -> handleStreamException(e) }
                .collect { remotePlaces ->
                    if (remotePlaces.isNotEmpty()) {
                        dao.upsertPlaces(remotePlaces.map { it.toLocal(activeUserId) })
                    }
                }
        }
        viewModelScope.launch {
            repo.observeSleepLogs(activeUserId)
                .catch { e -> handleStreamException(e) }
                .collect { remoteSleeps ->
                    if (remoteSleeps.isNotEmpty()) {
                        dao.upsertSleepLogs(remoteSleeps.map { it.toLocal(activeUserId) })
                    }
                }
        }
        viewModelScope.launch {
            repo.observeWorkSessions(activeUserId)
                .catch { e -> handleStreamException(e) }
                .collect { remoteWorks ->
                    if (remoteWorks.isNotEmpty()) {
                        dao.upsertWorkSessions(remoteWorks.map { it.toLocal(activeUserId) })
                    }
                }
        }
    }

    private fun handleStreamException(error: Throwable) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
    }

    fun toggleLanguage() {
        _isArabic.update { !it }
    }

    fun setLanguageArabic(arabic: Boolean) {
        _isArabic.value = arabic
    }

    fun selectDateKey(dateKey: String) {
        _selectedDateKey.value = dateKey
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        _isCloudSyncEnabled.value = enabled && cloudRepository != null
        if (enabled && cloudRepository != null) {
            syncAllLocalToCloud()
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun postStatusMessage(msg: String) {
        _statusBanner.value = msg
    }

    fun recordTrip(
        transportMode: String,
        distanceKm: Double,
        durationMinutes: Long,
        startLabel: String,
        endLabel: String,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        points: List<LatLngPoint>
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val startEpoch = now - (durationMinutes * 60_000L)
            val id = "trip_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val serialized = serializeRoutePoints(
                points.ifEmpty {
                    listOf(
                        LatLngPoint(startLat, startLng),
                        LatLngPoint((startLat + endLat) / 2 + 0.002, (startLng + endLng) / 2 - 0.001),
                        LatLngPoint(endLat, endLng)
                    )
                }
            )
            val trip = TripRecord(
                id = id,
                userId = activeUserId,
                dateKey = _selectedDateKey.value,
                transportMode = transportMode,
                startTimeEpochMs = startEpoch,
                endTimeEpochMs = now,
                durationMinutes = durationMinutes.coerceAtLeast(1L),
                distanceKm = distanceKm.coerceAtLeast(0.1),
                startLabel = startLabel.ifBlank { "Origin" },
                endLabel = endLabel.ifBlank { "Destination" },
                startLat = startLat,
                startLng = startLng,
                endLat = endLat,
                endLng = endLng,
                routePointsSerialized = serialized
            )
            dao.upsertTrip(trip.toLocal(activeUserId))
            clusterPlaceVisitFromTrip(trip)
            if (_isCloudSyncEnabled.value && cloudRepository != null) {
                cloudRepository.saveTrip(trip)
            }
            LifeTrackLocationService.resetActiveTripBuffer()
            _statusBanner.value = if (_isArabic.value) "تم حفظ الرحلة وتحديث التحليلات" else "Trip saved & analytics updated"
        }
    }

    private suspend fun clusterPlaceVisitFromTrip(trip: TripRecord) {
        val currentPlaces = uiState.value.places
        val matched = currentPlaces.firstOrNull { place ->
            haversineMeters(place.latitude, place.longitude, trip.endLat, trip.endLng) <= place.radiusMeters
        }
        val updatedPlace = if (matched != null) {
            matched.copy(
                visitCount = matched.visitCount + 1L,
                totalDurationMinutes = matched.totalDurationMinutes + 45L,
                lastArrivalEpochMs = trip.endTimeEpochMs,
                lastDepartureEpochMs = trip.endTimeEpochMs + 2700_000L
            )
        } else {
            PlaceLocation(
                id = "place_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                userId = activeUserId,
                name = trip.endLabel,
                category = inferCategoryFromLabel(trip.endLabel),
                latitude = trip.endLat,
                longitude = trip.endLng,
                radiusMeters = 120L,
                visitCount = 1L,
                totalDurationMinutes = 45L,
                lastArrivalEpochMs = trip.endTimeEpochMs,
                lastDepartureEpochMs = trip.endTimeEpochMs + 2700_000L
            )
        }
        dao.upsertPlace(updatedPlace.toLocal(activeUserId))
        if (_isCloudSyncEnabled.value && cloudRepository != null) {
            cloudRepository.savePlace(updatedPlace)
        }
    }

    fun saveOrUpdatePlace(
        existingId: String?,
        name: String,
        category: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Long,
        visitCount: Long,
        totalDurationMinutes: Long
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val place = PlaceLocation(
                id = existingId ?: "place_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                userId = activeUserId,
                name = name.ifBlank { "Saved Place" },
                category = category,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters.coerceIn(25L, 5000L),
                visitCount = visitCount.coerceAtLeast(1L),
                totalDurationMinutes = totalDurationMinutes.coerceAtLeast(15L),
                lastArrivalEpochMs = now - 3600_000L,
                lastDepartureEpochMs = now
            )
            dao.upsertPlace(place.toLocal(activeUserId))
            if (_isCloudSyncEnabled.value && cloudRepository != null) {
                cloudRepository.savePlace(place)
            }
            _statusBanner.value = if (_isArabic.value) "تم تحديث بيانات المكان" else "Location updated"
        }
    }

    fun saveOrUpdateSleepLog(
        dateKey: String,
        bedtimeHour: Int,
        bedtimeMinute: Int,
        wakeHour: Int,
        wakeMinute: Int,
        notes: String
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val durationMins = (((24 - bedtimeHour + wakeHour) % 24) * 60 + (wakeMinute - bedtimeMinute)).let {
                if (it <= 0) it + 720 else it
            }.toLong().coerceIn(60L, 960L)
            val regularity = when {
                bedtimeHour in 22..23 && durationMins in 400..520 -> "REGULAR"
                durationMins in 330..570 -> "SLIGHT_SHIFT"
                else -> "IRREGULAR"
            }
            val score = when (regularity) {
                "REGULAR" -> 90L
                "SLIGHT_SHIFT" -> 76L
                else -> 58L
            }
            val log = SleepLog(
                id = "sleep_${dateKey.replace("-", "")}",
                userId = activeUserId,
                dateKey = dateKey,
                bedtimeEpochMs = now - (durationMins * 60_000L),
                wakeTimeEpochMs = now,
                durationMinutes = durationMins,
                sleepQualityScore = score,
                scheduleRegularity = regularity,
                notes = notes
            )
            dao.upsertSleepLog(log.toLocal(activeUserId))
            if (_isCloudSyncEnabled.value && cloudRepository != null) {
                cloudRepository.saveSleepLog(log)
            }
            _statusBanner.value = if (_isArabic.value) "تم تحديث تقدير النوم" else "Estimated sleep log saved"
        }
    }

    fun saveOrUpdateWorkSession(
        existingId: String?,
        dateKey: String,
        workplaceName: String,
        arrivalHour: Int,
        arrivalMinute: Int,
        departureHour: Int,
        departureMinute: Int,
        sessionType: String
    ) {
        viewModelScope.launch {
            val durationMins = ((departureHour * 60 + departureMinute) - (arrivalHour * 60 + arrivalMinute))
                .coerceIn(30, 960).toLong()
            val now = System.currentTimeMillis()
            val session = WorkSession(
                id = existingId ?: "work_${dateKey.replace("-", "")}",
                userId = activeUserId,
                dateKey = dateKey,
                workplaceName = workplaceName.ifBlank { "Primary Workplace" },
                arrivalEpochMs = now - (durationMins * 60_000L),
                departureEpochMs = now,
                durationMinutes = durationMins,
                sessionType = sessionType,
                isManuallyCorrected = true
            )
            dao.upsertWorkSession(session.toLocal(activeUserId))
            if (_isCloudSyncEnabled.value && cloudRepository != null) {
                cloudRepository.saveWorkSession(session)
            }
            _statusBanner.value = if (_isArabic.value) "تم حفظ جدول العمل بنجاح" else "Work schedule saved"
        }
    }

    fun deleteAllHistory() {
        viewModelScope.launch {
            val currentTrips = uiState.value.trips
            dao.clearAllTripsForUser(activeUserId)
            if (_isCloudSyncEnabled.value && cloudRepository != null) {
                currentTrips.forEach { cloudRepository.deleteTrip(it.id) }
            }
            _statusBanner.value = if (_isArabic.value) "تم حذف سجل الحركة بالكامل" else "All movement history deleted"
        }
    }

    fun syncAllLocalToCloud() {
        val repo = cloudRepository ?: return
        viewModelScope.launch {
            val s = uiState.value
            s.trips.forEach { repo.saveTrip(it) }
            s.places.forEach { repo.savePlace(it) }
            s.sleepLogs.forEach { repo.saveSleepLog(it) }
            s.workSessions.forEach { repo.saveWorkSession(it) }
            _statusBanner.value = if (_isArabic.value) "تمت المزامنة السحابية بنجاح" else "Cloud synchronization complete"
        }
    }

    fun seedDemoMultiDayTelemetry() {
        viewModelScope.launch {
            populateSampleTelemetryData()
            if (_isCloudSyncEnabled.value && cloudRepository != null) {
                syncAllLocalToCloud()
            }
            _statusBanner.value = if (_isArabic.value) "تم تحميل بيانات 7 أيام تحليلية" else "Loaded 7-day smart telemetry dataset"
        }
    }

    private suspend fun seedInitialRealisticDataIfEmpty() {
        val sampleTripId = "trip_day0_morning"
        val now = System.currentTimeMillis()
        val day0 = todayDateKey()
        val day1 = dateKeyDaysAgo(1)
        val day2 = dateKeyDaysAgo(2)
        val day3 = dateKeyDaysAgo(3)
        val day4 = dateKeyDaysAgo(4)
        val day5 = dateKeyDaysAgo(5)
        val day6 = dateKeyDaysAgo(6)

        val defaultTrips = listOf(
            LocalTripEntity(
                id = sampleTripId,
                userId = activeUserId,
                dateKey = day0,
                transportMode = "DRIVING",
                startTimeEpochMs = now - 8 * 3600_000L,
                endTimeEpochMs = now - 7 * 3600_000L - 1800_000L,
                durationMinutes = 28L,
                distanceKm = 14.6,
                startLabel = "Home Residence",
                endLabel = "Innovation Tech Hub",
                startLat = 24.7136,
                startLng = 46.6753,
                endLat = 24.7650,
                endLng = 46.6430,
                routePointsSerialized = "24.7136,46.6753;24.7310,46.6620;24.7490,46.6510;24.7650,46.6430"
            ),
            LocalTripEntity(
                id = "trip_day0_walk1",
                userId = activeUserId,
                dateKey = day0,
                transportMode = "WALKING",
                startTimeEpochMs = now - 4 * 3600_000L,
                endTimeEpochMs = now - 4 * 3600_000L + 1320_000L,
                durationMinutes = 22L,
                distanceKm = 1.85,
                startLabel = "Innovation Tech Hub",
                endLabel = "Specialty Roasters Cafe",
                startLat = 24.7650,
                startLng = 46.6430,
                endLat = 24.7720,
                endLng = 46.6495,
                routePointsSerialized = "24.7650,46.6430;24.7685,46.6462;24.7720,46.6495"
            ),
            LocalTripEntity(
                id = "trip_day0_evening",
                userId = activeUserId,
                dateKey = day0,
                transportMode = "DRIVING",
                startTimeEpochMs = now - 2 * 3600_000L,
                endTimeEpochMs = now - 2 * 3600_000L + 1920_000L,
                durationMinutes = 32L,
                distanceKm = 16.2,
                startLabel = "Innovation Tech Hub",
                endLabel = "Pulse Fitness Club",
                startLat = 24.7650,
                startLng = 46.6430,
                endLat = 24.7280,
                endLng = 46.6910,
                routePointsSerialized = "24.7650,46.6430;24.7450,46.6690;24.7280,46.6910"
            ),
            LocalTripEntity(
                id = "trip_day0_walk2",
                userId = activeUserId,
                dateKey = day0,
                transportMode = "WALKING",
                startTimeEpochMs = now - 3600_000L,
                endTimeEpochMs = now - 1800_000L,
                durationMinutes = 25L,
                distanceKm = 2.1,
                startLabel = "Pulse Fitness Club",
                endLabel = "Home Residence",
                startLat = 24.7280,
                startLng = 46.6910,
                endLat = 24.7136,
                endLng = 46.6753,
                routePointsSerialized = "24.7280,46.6910;24.7205,46.6830;24.7136,46.6753"
            ),
            LocalTripEntity(
                id = "trip_day1_commute",
                userId = activeUserId,
                dateKey = day1,
                transportMode = "DRIVING",
                startTimeEpochMs = now - 32 * 3600_000L,
                endTimeEpochMs = now - 31 * 3600_000L,
                durationMinutes = 52L,
                distanceKm = 28.4,
                startLabel = "Home Residence",
                endLabel = "Innovation Tech Hub",
                startLat = 24.7136,
                startLng = 46.6753,
                endLat = 24.7650,
                endLng = 46.6430,
                routePointsSerialized = "24.7136,46.6753;24.7410,46.6580;24.7650,46.6430"
            ),
            LocalTripEntity(
                id = "trip_day1_walk",
                userId = activeUserId,
                dateKey = day1,
                transportMode = "WALKING",
                startTimeEpochMs = now - 27 * 3600_000L,
                endTimeEpochMs = now - 26 * 3600_000L,
                durationMinutes = 38L,
                distanceKm = 3.2,
                startLabel = "Central Park Trail",
                endLabel = "Home Residence",
                startLat = 24.7350,
                startLng = 46.6810,
                endLat = 24.7136,
                endLng = 46.6753,
                routePointsSerialized = "24.7350,46.6810;24.7240,46.6780;24.7136,46.6753"
            ),
            LocalTripEntity(
                id = "trip_day2_drive",
                userId = activeUserId,
                dateKey = day2,
                transportMode = "DRIVING",
                startTimeEpochMs = now - 54 * 3600_000L,
                endTimeEpochMs = now - 53 * 3600_000L,
                durationMinutes = 48L,
                distanceKm = 24.8,
                startLabel = "Home Residence",
                endLabel = "Innovation Tech Hub",
                startLat = 24.7136,
                startLng = 46.6753,
                endLat = 24.7650,
                endLng = 46.6430,
                routePointsSerialized = "24.7136,46.6753;24.7650,46.6430"
            ),
            LocalTripEntity(
                id = "trip_day2_walk",
                userId = activeUserId,
                dateKey = day2,
                transportMode = "WALKING",
                startTimeEpochMs = now - 50 * 3600_000L,
                endTimeEpochMs = now - 49 * 3600_000L,
                durationMinutes = 34L,
                distanceKm = 2.9,
                startLabel = "Innovation Tech Hub",
                endLabel = "Green Market",
                startLat = 24.7650,
                startLng = 46.6430,
                endLat = 24.7520,
                endLng = 46.6550,
                routePointsSerialized = "24.7650,46.6430;24.7520,46.6550"
            )
        )
        dao.upsertTrips(defaultTrips)

        val defaultPlaces = listOf(
            LocalPlaceEntity("place_1", activeUserId, "Home Residence", "HOME", 24.7136, 46.6753, 120L, 42L, 18400L, now - 1800_000L, now),
            LocalPlaceEntity("place_2", activeUserId, "Innovation Tech Hub", "WORK", 24.7650, 46.6430, 150L, 28L, 12840L, now - 8 * 3600_000L, now - 2 * 3600_000L),
            LocalPlaceEntity("place_3", activeUserId, "Pulse Fitness Club", "GYM", 24.7280, 46.6910, 100L, 16L, 1440L, now - 2 * 3600_000L, now - 3600_000L),
            LocalPlaceEntity("place_4", activeUserId, "Specialty Roasters Cafe", "CAFE", 24.7720, 46.6495, 80L, 14L, 680L, now - 4 * 3600_000L, now - 3 * 3600_000L),
            LocalPlaceEntity("place_5", activeUserId, "Green Organic Market", "SHOPPING", 24.7520, 46.6550, 110L, 11L, 520L, now - 50 * 3600_000L, now - 49 * 3600_000L),
            LocalPlaceEntity("place_6", activeUserId, "Central Park Trail", "OTHER", 24.7350, 46.6810, 200L, 9L, 610L, now - 27 * 3600_000L, now - 26 * 3600_000L),
            LocalPlaceEntity("place_7", activeUserId, "Downtown Library", "OTHER", 24.7010, 46.6620, 90L, 7L, 840L, now - 72 * 3600_000L, now - 70 * 3600_000L),
            LocalPlaceEntity("place_8", activeUserId, "Panorama Mall", "SHOPPING", 24.6920, 46.6710, 180L, 6L, 590L, now - 96 * 3600_000L, now - 94 * 3600_000L),
            LocalPlaceEntity("place_9", activeUserId, "Blue Wave Lounge", "CAFE", 24.7420, 46.6380, 75L, 5L, 310L, now - 110 * 3600_000L, now - 109 * 3600_000L),
            LocalPlaceEntity("place_10", activeUserId, "Metro Transit Hub", "OTHER", 24.7580, 46.6820, 130L, 4L, 140L, now - 130 * 3600_000L, now - 129 * 3600_000L)
        )
        dao.upsertPlaces(defaultPlaces)

        val defaultSleeps = listOf(
            LocalSleepEntity("sleep_$day0", activeUserId, day0, now - 16 * 3600_000L, now - 8 * 3600_000L - 1800_000L, 465L, 91L, "REGULAR", "Stationary at Home Residence 22:45 - 06:30"),
            LocalSleepEntity("sleep_$day1", activeUserId, day1, now - 40 * 3600_000L, now - 32 * 3600_000L, 450L, 88L, "REGULAR", "Consistent nighttime stationary window"),
            LocalSleepEntity("sleep_$day2", activeUserId, day2, now - 63 * 3600_000L, now - 56 * 3600_000L, 410L, 76L, "SLIGHT_SHIFT", "Late bedtime detected at 23:50"),
            LocalSleepEntity("sleep_$day3", activeUserId, day3, now - 88 * 3600_000L, now - 80 * 3600_000L, 480L, 93L, "REGULAR", "Deep 8.0h stationary interval"),
            LocalSleepEntity("sleep_$day4", activeUserId, day4, now - 111 * 3600_000L, now - 105 * 3600_000L, 355L, 62L, "IRREGULAR", "Short sleep schedule shift"),
            LocalSleepEntity("sleep_$day5", activeUserId, day5, now - 136 * 3600_000L, now - 128 * 3600_000L, 470L, 89L, "REGULAR", "Optimal recovery night"),
            LocalSleepEntity("sleep_$day6", activeUserId, day6, now - 160 * 3600_000L, now - 152 * 3600_000L, 455L, 86L, "REGULAR", "Steady sleep pattern")
        )
        dao.upsertSleepLogs(defaultSleeps)

        val defaultWorks = listOf(
            LocalWorkEntity("work_$day0", activeUserId, day0, "Innovation Tech Hub", now - 7 * 3600_000L - 1800_000L, now - 2 * 3600_000L, 510L, "WORK", false),
            LocalWorkEntity("work_$day1", activeUserId, day1, "Innovation Tech Hub", now - 31 * 3600_000L, now - 23 * 3600_000L, 495L, "WORK", false),
            LocalWorkEntity("work_$day2", activeUserId, day2, "Innovation Tech Hub", now - 55 * 3600_000L, now - 46 * 3600_000L, 540L, "OVERTIME", false),
            LocalWorkEntity("work_$day3", activeUserId, day3, "Innovation Tech Hub", now - 79 * 3600_000L, now - 71 * 3600_000L, 480L, "WORK", false),
            LocalWorkEntity("work_$day4", activeUserId, day4, "Innovation Tech Hub", now - 103 * 3600_000L, now - 95 * 3600_000L, 465L, "WORK", true),
            LocalWorkEntity("work_$day5", activeUserId, day5, "Specialty Roasters Cafe", now - 126 * 3600_000L, now - 123 * 3600_000L, 180L, "PERSONAL", false)
        )
        dao.upsertWorkSessions(defaultWorks)
    }

    private suspend fun populateSampleTelemetryData() {
        seedInitialRealisticDataIfEmpty()
    }

    companion object {
        fun provideFactory(
            context: Context,
            userId: String,
            offlineOnly: Boolean
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val appContext = context.applicationContext
                val db = LifeTrackDatabase.getInstance(appContext)
                val cloudRepo = if (offlineOnly) {
                    null
                } else {
                    try {
                        val databaseId = appContext.getString(R.string.firestore_database_id)
                        CloudSyncRepository(FirebaseFirestore.getInstance(databaseId))
                    } catch (_: Exception) {
                        null
                    }
                }
                return LifeTrackViewModel(
                    dao = db.lifeTrackDao(),
                    cloudRepository = cloudRepo,
                    activeUserId = userId,
                    initialOfflineMode = offlineOnly
                ) as T
            }
        }
    }
}

private data class Quint<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

private fun inferCategoryFromLabel(label: String): String {
    val lower = label.lowercase(Locale.ROOT)
    return when {
        "home" in lower || "منزل" in lower -> "HOME"
        "work" in lower || "office" in lower || "hub" in lower || "عمل" in lower -> "WORK"
        "gym" in lower || "fitness" in lower || "نادي" in lower -> "GYM"
        "cafe" in lower || "coffee" in lower || "مقهى" in lower -> "CAFE"
        "mall" in lower || "market" in lower || "مول" in lower -> "SHOPPING"
        else -> "OTHER"
    }
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun LocalTripEntity.toDomain() = TripRecord(
    id = id,
    userId = userId,
    dateKey = dateKey,
    transportMode = transportMode,
    startTimeEpochMs = startTimeEpochMs,
    endTimeEpochMs = endTimeEpochMs,
    durationMinutes = durationMinutes,
    distanceKm = distanceKm,
    startLabel = startLabel,
    endLabel = endLabel,
    startLat = startLat,
    startLng = startLng,
    endLat = endLat,
    endLng = endLng,
    routePointsSerialized = routePointsSerialized
)

private fun TripRecord.toLocal(uid: String) = LocalTripEntity(
    id = id.ifBlank { UUID.randomUUID().toString() },
    userId = uid,
    dateKey = dateKey,
    transportMode = transportMode,
    startTimeEpochMs = startTimeEpochMs,
    endTimeEpochMs = endTimeEpochMs,
    durationMinutes = durationMinutes,
    distanceKm = distanceKm,
    startLabel = startLabel,
    endLabel = endLabel,
    startLat = startLat,
    startLng = startLng,
    endLat = endLat,
    endLng = endLng,
    routePointsSerialized = routePointsSerialized
)

private fun LocalPlaceEntity.toDomain() = PlaceLocation(
    id = id,
    userId = userId,
    name = name,
    category = category,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    visitCount = visitCount,
    totalDurationMinutes = totalDurationMinutes,
    lastArrivalEpochMs = lastArrivalEpochMs,
    lastDepartureEpochMs = lastDepartureEpochMs
)

private fun PlaceLocation.toLocal(uid: String) = LocalPlaceEntity(
    id = id.ifBlank { UUID.randomUUID().toString() },
    userId = uid,
    name = name,
    category = category,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    visitCount = visitCount,
    totalDurationMinutes = totalDurationMinutes,
    lastArrivalEpochMs = lastArrivalEpochMs,
    lastDepartureEpochMs = lastDepartureEpochMs
)

private fun LocalSleepEntity.toDomain() = SleepLog(
    id = id,
    userId = userId,
    dateKey = dateKey,
    bedtimeEpochMs = bedtimeEpochMs,
    wakeTimeEpochMs = wakeTimeEpochMs,
    durationMinutes = durationMinutes,
    sleepQualityScore = sleepQualityScore,
    scheduleRegularity = scheduleRegularity,
    notes = notes
)

private fun SleepLog.toLocal(uid: String) = LocalSleepEntity(
    id = id.ifBlank { UUID.randomUUID().toString() },
    userId = uid,
    dateKey = dateKey,
    bedtimeEpochMs = bedtimeEpochMs,
    wakeTimeEpochMs = wakeTimeEpochMs,
    durationMinutes = durationMinutes,
    sleepQualityScore = sleepQualityScore,
    scheduleRegularity = scheduleRegularity,
    notes = notes
)

private fun LocalWorkEntity.toDomain() = WorkSession(
    id = id,
    userId = userId,
    dateKey = dateKey,
    workplaceName = workplaceName,
    arrivalEpochMs = arrivalEpochMs,
    departureEpochMs = departureEpochMs,
    durationMinutes = durationMinutes,
    sessionType = sessionType,
    isManuallyCorrected = isManuallyCorrected
)

private fun WorkSession.toLocal(uid: String) = LocalWorkEntity(
    id = id.ifBlank { UUID.randomUUID().toString() },
    userId = uid,
    dateKey = dateKey,
    workplaceName = workplaceName,
    arrivalEpochMs = arrivalEpochMs,
    departureEpochMs = departureEpochMs,
    durationMinutes = durationMinutes,
    sessionType = sessionType,
    isManuallyCorrected = isManuallyCorrected
)
