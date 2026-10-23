package dev.healthapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FoodEntry::class, WeightEntry::class, DailyMetric::class, SleepSession::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun food(): FoodDao
    abstract fun weight(): WeightDao
    abstract fun metrics(): MetricDao
    abstract fun sleep(): SleepDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "health.db").build()
    }
}
