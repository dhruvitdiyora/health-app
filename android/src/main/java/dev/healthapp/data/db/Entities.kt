package dev.healthapp.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Days are stored as ISO strings (yyyy-MM-dd, local time) so SQL can group and range on them. */

enum class Meal(val label: String) {
    BREAKFAST("Breakfast"), LUNCH("Lunch"), DINNER("Dinner"), SNACK("Snack");

    companion object {
        /** A sensible default for "what meal is this" from the hour it was logged. */
        fun forHour(hour: Int): Meal = when (hour) {
            in 4..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 17..22 -> DINNER
            else -> SNACK
        }
    }
}

@Entity(tableName = "food_entry", indices = [Index("day"), Index("name")])
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,
    val time: Long,
    val name: String,
    val meal: Meal,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    /** Health Connect NutritionRecord id when the entry was shared there, so deletes can follow. */
    val hcRecordId: String? = null,
)

@Entity(tableName = "weight_entry", indices = [Index("day"), Index(value = ["hcRecordId"], unique = true)])
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,
    val time: Long,
    val kg: Double,
    /** "manual" or the package name of the app that wrote it to Health Connect. */
    val source: String,
    val hcRecordId: String? = null,
)

/** One row per day of device data pulled from Health Connect. Null means "no data", not zero. */
@Entity(tableName = "daily_metric")
data class DailyMetric(
    @PrimaryKey val day: String,
    val steps: Long? = null,
    val activeKcal: Double? = null,
    val totalKcal: Double? = null,
    val exerciseMinutes: Int? = null,
    val restingHr: Double? = null,
    val hrvMs: Double? = null,
    /** Comma-separated package names that contributed to this day. */
    val sources: String = "",
)

/** A night (or nap) of sleep, attributed to the day the person woke up. */
@Entity(tableName = "sleep_session", indices = [Index("day")])
data class SleepSession(
    @PrimaryKey val hcId: String,
    val day: String,
    val start: Long,
    val end: Long,
    val asleepMinutes: Int,
    val deepMinutes: Int,
    val remMinutes: Int,
    val lightMinutes: Int,
    val awakeMinutes: Int,
    val source: String,
)

/** A distinct food the person has logged before, for one-tap re-logging. */
data class RecentFood(
    val name: String,
    val meal: Meal,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val timesLogged: Int,
)

data class DayIntake(
    val day: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)
