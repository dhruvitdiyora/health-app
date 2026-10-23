package dev.healthapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_entry WHERE day = :day ORDER BY time")
    fun forDay(day: String): Flow<List<FoodEntry>>

    @Query(
        """SELECT day, SUM(kcal) AS kcal, SUM(protein) AS protein, SUM(carbs) AS carbs, SUM(fat) AS fat
           FROM food_entry WHERE day BETWEEN :from AND :to GROUP BY day ORDER BY day"""
    )
    fun intakeBetween(from: String, to: String): Flow<List<DayIntake>>

    /** Latest values for each distinct food name, most-logged first. */
    @Query(
        """SELECT f.name, f.meal, f.kcal, f.protein, f.carbs, f.fat, g.n AS timesLogged
           FROM food_entry f
           JOIN (SELECT LOWER(name) AS k, COUNT(*) AS n, MAX(time) AS t FROM food_entry GROUP BY LOWER(name)) g
             ON LOWER(f.name) = g.k AND f.time = g.t
           ORDER BY g.n DESC, g.t DESC LIMIT :limit"""
    )
    fun recent(limit: Int = 30): Flow<List<RecentFood>>

    @Query("SELECT * FROM food_entry WHERE id = :id")
    suspend fun byId(id: Long): FoodEntry?

    @Query("SELECT * FROM food_entry ORDER BY time")
    suspend fun all(): List<FoodEntry>

    @Insert
    suspend fun insert(entry: FoodEntry): Long

    @Update
    suspend fun update(entry: FoodEntry)

    @Delete
    suspend fun delete(entry: FoodEntry)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_entry ORDER BY time DESC LIMIT 1")
    fun latest(): Flow<WeightEntry?>

    @Query("SELECT * FROM weight_entry WHERE day <= :day ORDER BY time DESC LIMIT 1")
    fun latestOnOrBefore(day: String): Flow<WeightEntry?>

    @Query("SELECT * FROM weight_entry WHERE day BETWEEN :from AND :to ORDER BY time")
    fun between(from: String, to: String): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entry ORDER BY time")
    suspend fun all(): List<WeightEntry>

    @Insert
    suspend fun insert(entry: WeightEntry): Long

    @Delete
    suspend fun delete(entry: WeightEntry)

    @Query("DELETE FROM weight_entry WHERE hcRecordId IS NOT NULL AND source != 'manual' AND day BETWEEN :from AND :to")
    suspend fun deleteSyncedBetween(from: String, to: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<WeightEntry>)

    /** Synced (non-manual) weights mirror Health Connect exactly inside the window. */
    @Transaction
    suspend fun replaceSyncedBetween(from: String, to: String, entries: List<WeightEntry>) {
        deleteSyncedBetween(from, to)
        insertAll(entries)
    }
}

@Dao
interface MetricDao {
    @Query("SELECT * FROM daily_metric WHERE day = :day")
    fun forDay(day: String): Flow<DailyMetric?>

    @Query("SELECT * FROM daily_metric WHERE day BETWEEN :from AND :to ORDER BY day")
    fun between(from: String, to: String): Flow<List<DailyMetric>>

    @Query("SELECT * FROM daily_metric ORDER BY day")
    suspend fun all(): List<DailyMetric>

    @Upsert
    suspend fun upsert(metrics: List<DailyMetric>)
}

@Dao
interface SleepDao {
    /** The main sleep for a day is the longest session that ended on it. */
    @Query("SELECT * FROM sleep_session WHERE day = :day ORDER BY asleepMinutes DESC")
    fun forDay(day: String): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_session WHERE day BETWEEN :from AND :to ORDER BY start")
    fun between(from: String, to: String): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_session ORDER BY start")
    suspend fun all(): List<SleepSession>

    @Query("DELETE FROM sleep_session WHERE day BETWEEN :from AND :to")
    suspend fun deleteBetween(from: String, to: String)

    @Upsert
    suspend fun upsert(sessions: List<SleepSession>)

    /** Replace a window atomically so sessions deleted in Health Connect disappear here too. */
    @Transaction
    suspend fun replaceBetween(from: String, to: String, sessions: List<SleepSession>) {
        deleteBetween(from, to)
        upsert(sessions)
    }
}
