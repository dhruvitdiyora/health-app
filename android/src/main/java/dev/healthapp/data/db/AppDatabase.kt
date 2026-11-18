package dev.healthapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FoodEntry::class, WeightEntry::class, DailyMetric::class, SleepSession::class,
        ExerciseSession::class, ActivityGoal::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun food(): FoodDao
    abstract fun weight(): WeightDao
    abstract fun metrics(): MetricDao
    abstract fun sleep(): SleepDao
    abstract fun exercise(): ExerciseDao
    abstract fun goals(): GoalDao

    companion object {
        /** The food log is the user's only copy: every schema change gets a migration, never a destructive fallback. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_session` (`hcId` TEXT NOT NULL, `day` TEXT NOT NULL, " +
                        "`start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `type` INTEGER NOT NULL, `title` TEXT, " +
                        "`source` TEXT NOT NULL, PRIMARY KEY(`hcId`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_session_day` ON `exercise_session` (`day`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activity_goal` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`category` TEXT, `metric` TEXT NOT NULL, `weeklyTarget` INTEGER NOT NULL, `created` INTEGER NOT NULL)"
                )
            }
        }

        /** v3: photo entries keep the model's raw estimate, for the personal correction factor. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `food_entry` ADD COLUMN `photoEstimateKcal` REAL")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "health.db").addMigrations(*MIGRATIONS).build()
    }
}
