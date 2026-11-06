package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.LocalPlaceEntity
import com.example.data.model.LocalSleepEntity
import com.example.data.model.LocalTripEntity
import com.example.data.model.LocalWorkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeTrackDao {
    @Query("SELECT * FROM local_trips WHERE userId = :userId ORDER BY startTimeEpochMs DESC")
    fun observeTrips(userId: String): Flow<List<LocalTripEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrip(trip: LocalTripEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrips(trips: List<LocalTripEntity>)

    @Query("DELETE FROM local_trips WHERE id = :id AND userId = :userId")
    suspend fun deleteTripById(id: String, userId: String)

    @Query("DELETE FROM local_trips WHERE userId = :userId")
    suspend fun clearAllTripsForUser(userId: String)

    @Query("SELECT * FROM local_places WHERE userId = :userId ORDER BY visitCount DESC")
    fun observePlaces(userId: String): Flow<List<LocalPlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlace(place: LocalPlaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaces(places: List<LocalPlaceEntity>)

    @Query("DELETE FROM local_places WHERE id = :id AND userId = :userId")
    suspend fun deletePlaceById(id: String, userId: String)

    @Query("SELECT * FROM local_sleep_logs WHERE userId = :userId ORDER BY bedtimeEpochMs DESC")
    fun observeSleepLogs(userId: String): Flow<List<LocalSleepEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSleepLog(sleepLog: LocalSleepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSleepLogs(sleepLogs: List<LocalSleepEntity>)

    @Query("SELECT * FROM local_work_sessions WHERE userId = :userId ORDER BY arrivalEpochMs DESC")
    fun observeWorkSessions(userId: String): Flow<List<LocalWorkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkSession(workSession: LocalWorkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkSessions(workSessions: List<LocalWorkEntity>)
}

@Database(
    entities = [
        LocalTripEntity::class,
        LocalPlaceEntity::class,
        LocalSleepEntity::class,
        LocalWorkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LifeTrackDatabase : RoomDatabase() {
    abstract fun lifeTrackDao(): LifeTrackDao

    companion object {
        @Volatile
        private var INSTANCE: LifeTrackDatabase? = null

        fun getInstance(context: Context): LifeTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LifeTrackDatabase::class.java,
                    "lifetrack_local.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}
