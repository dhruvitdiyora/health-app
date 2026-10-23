package dev.healthapp.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import dev.healthapp.data.db.FoodEntry
import dev.healthapp.data.db.Meal
import java.time.Instant
import java.time.ZoneId
import kotlin.reflect.KClass

/** Data types this app reads, with the label shown in Settings. */
enum class ReadType(val label: String, val recordType: KClass<out Record>) {
    STEPS("Steps", StepsRecord::class),
    ACTIVE_KCAL("Active calories", ActiveCaloriesBurnedRecord::class),
    TOTAL_KCAL("Total calories burned", TotalCaloriesBurnedRecord::class),
    SLEEP("Sleep", SleepSessionRecord::class),
    RESTING_HR("Resting heart rate", RestingHeartRateRecord::class),
    HRV("Heart rate variability", HeartRateVariabilityRmssdRecord::class),
    WEIGHT("Weight", WeightRecord::class),
    EXERCISE("Workouts", ExerciseSessionRecord::class);

    val permission: String get() = HealthPermission.getReadPermission(recordType)
}

class HealthConnectManager(private val context: Context) {

    val providerPackage = "com.google.android.apps.healthdata"

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context, providerPackage)

    val isAvailable: Boolean get() = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val readPermissions: Set<String> = ReadType.entries.map { it.permission }.toSet()
    val historyPermission: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
    val shareFoodPermission: String = HealthPermission.getWritePermission(NutritionRecord::class)

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    suspend fun grantedPermissions(): Set<String> =
        if (isAvailable) client.permissionController.getGrantedPermissions() else emptySet()

    suspend fun historyFeatureAvailable(): Boolean = isAvailable &&
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
        HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    /** Opens the Play Store page to install or update Health Connect (Android 13 and below). */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = Uri.parse("market://details?id=$providerPackage&url=healthconnect%3A%2F%2Fonboarding")
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
    }

    /** This app's own permission page in Health Connect (Android 14+), else Health Connect's settings. */
    fun manageAccessIntent(): Intent =
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
                .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        } else {
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        }

    /** Reads every page of a record type in the window. */
    suspend fun <T : Record> readAll(type: KClass<T>, filter: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, filter, pageToken = token))
            out += response.records
            token = response.pageToken?.takeIf { it.isNotEmpty() }
        } while (token != null)
        return out
    }

    /** Writes a food entry as a NutritionRecord. Returns the Health Connect record id. */
    suspend fun writeFood(entry: FoodEntry): String? {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(entry.time)
        val end = start.plusSeconds(60)
        val record = NutritionRecord(
            startTime = start,
            startZoneOffset = zone.rules.getOffset(start),
            endTime = end,
            endZoneOffset = zone.rules.getOffset(end),
            metadata = Metadata.manualEntry(),
            name = entry.name,
            energy = Energy.kilocalories(entry.kcal),
            protein = Mass.grams(entry.protein),
            totalCarbohydrate = Mass.grams(entry.carbs),
            totalFat = Mass.grams(entry.fat),
            mealType = entry.meal.toHealthConnect(),
        )
        return client.insertRecords(listOf(record)).recordIdsList.firstOrNull()
    }

    suspend fun deleteFood(recordId: String) {
        client.deleteRecords(NutritionRecord::class, listOf(recordId), emptyList())
    }

    private fun Meal.toHealthConnect(): Int = when (this) {
        Meal.BREAKFAST -> MealType.MEAL_TYPE_BREAKFAST
        Meal.LUNCH -> MealType.MEAL_TYPE_LUNCH
        Meal.DINNER -> MealType.MEAL_TYPE_DINNER
        Meal.SNACK -> MealType.MEAL_TYPE_SNACK
    }
}

/** Friendly names for apps that commonly write to Health Connect. */
fun sourceLabel(packageName: String): String = when {
    packageName == "manual" -> "Manual"
    packageName.startsWith("com.whoop") -> "WHOOP"
    packageName.startsWith("com.fitbit") -> "Fitbit"
    packageName == "com.google.android.apps.fitness" -> "Google Fit"
    packageName.startsWith("com.google.android.apps.healthdata") -> "Health Connect"
    // Android 16+ counts steps on the phone itself and reports it under a per-device id.
    packageName.startsWith("com.android.healthconnect.phone") -> "Phone"
    // Health Connect fills gaps in total calories with a resting-burn estimate under "android".
    packageName == "android" -> "Resting estimate"
    packageName.startsWith("com.google.android.apps.wear") -> "Pixel Watch"
    packageName.startsWith("com.sec.android.app.shealth") -> "Samsung Health"
    packageName.startsWith("com.ouraring") -> "Oura"
    packageName.startsWith("com.garmin") -> "Garmin"
    packageName.startsWith("dev.healthapp") -> "This app"
    else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
