package dev.healthapp.data.health

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.healthapp.data.db.AppDatabase
import dev.healthapp.data.db.DailyMetric
import dev.healthapp.data.db.SleepSession
import dev.healthapp.data.db.WeightEntry
import dev.healthapp.domain.HealthMath
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

data class SyncResult(val days: Int, val typesRead: Int, val sleepSessions: Int, val weights: Int) {
    val message: String
        get() = if (typesRead == 0) "No Health Connect permissions granted"
        else "Synced $days days: $typesRead data types, $sleepSessions sleeps, $weights weigh-ins"
}

/**
 * Copies a window of Health Connect data into the local database. Health Connect stays the
 * source of truth for device data; the local copy makes charts fast and keeps history if an
 * app later deletes or stops sharing its data.
 */
class HealthSync(private val hc: HealthConnectManager, private val db: AppDatabase) {

    suspend fun sync(days: Int): SyncResult {
        val granted = hc.grantedPermissions()
        val can = ReadType.entries.filter { it.permission in granted }.toSet()
        if (can.isEmpty()) return SyncResult(days, 0, 0, 0)

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val firstDay = today.minusDays(days.toLong() - 1)
        val from = firstDay.toString()
        val to = today.toString()

        val metrics = dailyAggregates(can, firstDay, today).toMutableMap()

        if (ReadType.HRV in can) {
            val filter = TimeRangeFilter.between(firstDay.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant())
            hc.readAll(HeartRateVariabilityRmssdRecord::class, filter)
                .groupBy { it.time.atZone(zone).toLocalDate().toString() }
                .forEach { (day, records) ->
                    val m = metrics[day] ?: DailyMetric(day)
                    val sources = (m.sources.split(',') + records.map { it.metadata.dataOrigin.packageName })
                        .filter { it.isNotBlank() }.distinct().joinToString(",")
                    metrics[day] = m.copy(hrvMs = records.map { it.heartRateVariabilityMillis }.average(), sources = sources)
                }
        }
        db.metrics().upsert(metrics.values.toList())

        var sleepCount = 0
        if (ReadType.SLEEP in can) {
            // Start a day early so a night that began before the window still counts for its wake-up day.
            val filter = TimeRangeFilter.between(firstDay.minusDays(1).atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant())
            val sessions = hc.readAll(SleepSessionRecord::class, filter).mapNotNull { r ->
                val day = r.endTime.atZone(zone).toLocalDate()
                if (day < firstDay || day > today) return@mapNotNull null
                val summary = HealthMath.summarizeSleep(
                    r.startTime.toEpochMilli(), r.endTime.toEpochMilli(),
                    r.stages.map { HealthMath.StageSpan(it.stage, it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) },
                )
                SleepSession(
                    hcId = r.metadata.id,
                    day = day.toString(),
                    start = r.startTime.toEpochMilli(),
                    end = r.endTime.toEpochMilli(),
                    asleepMinutes = summary.asleepMinutes,
                    deepMinutes = summary.deepMinutes,
                    remMinutes = summary.remMinutes,
                    lightMinutes = summary.lightMinutes,
                    awakeMinutes = summary.awakeMinutes,
                    source = r.metadata.dataOrigin.packageName,
                )
            }
            db.sleep().replaceBetween(from, to, sessions)
            sleepCount = sessions.size
        }

        var weightCount = 0
        if (ReadType.WEIGHT in can) {
            val filter = TimeRangeFilter.between(firstDay.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant())
            val weights = hc.readAll(WeightRecord::class, filter).map { r ->
                WeightEntry(
                    day = r.time.atZone(zone).toLocalDate().toString(),
                    time = r.time.toEpochMilli(),
                    kg = r.weight.inKilograms,
                    source = r.metadata.dataOrigin.packageName,
                    hcRecordId = r.metadata.id,
                )
            }
            db.weight().replaceSyncedBetween(from, to, weights)
            weightCount = weights.size
        }

        return SyncResult(days, can.size, sleepCount, weightCount)
    }

    /** One Health Connect aggregate call covering every summable metric, sliced per local day. */
    private suspend fun dailyAggregates(can: Set<ReadType>, firstDay: LocalDate, lastDay: LocalDate): Map<String, DailyMetric> {
        val wanted = buildSet<AggregateMetric<*>> {
            if (ReadType.STEPS in can) add(StepsRecord.COUNT_TOTAL)
            if (ReadType.ACTIVE_KCAL in can) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (ReadType.TOTAL_KCAL in can) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            if (ReadType.RESTING_HR in can) add(RestingHeartRateRecord.BPM_AVG)
            if (ReadType.EXERCISE in can) add(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
        }
        if (wanted.isEmpty()) return emptyMap()

        val groups = hc.client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = wanted,
                timeRangeFilter = TimeRangeFilter.between(firstDay.atStartOfDay(), lastDay.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        return groups.associate { g ->
            val day = g.startTime.toLocalDate().toString()
            val r = g.result
            day to DailyMetric(
                day = day,
                steps = r[StepsRecord.COUNT_TOTAL],
                activeKcal = r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
                totalKcal = r[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories,
                exerciseMinutes = r[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes()?.toInt(),
                restingHr = r[RestingHeartRateRecord.BPM_AVG]?.toDouble(),
                sources = r.dataOrigins.joinToString(",") { it.packageName },
            )
        }
    }
}
