package dev.healthapp.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.healthapp.AppContainer
import dev.healthapp.data.Settings
import dev.healthapp.data.db.DailyMetric
import dev.healthapp.domain.HealthMath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class Range(val days: Int, val label: String) { WEEK(7, "7 days"), MONTH(30, "30 days"), QUARTER(90, "90 days"), YEAR(365, "1 year") }

/** Everything index-aligned with [days]; null means no data for that day. */
data class TrendsState(
    val range: Range = Range.MONTH,
    val days: List<LocalDate> = emptyList(),
    val eaten: List<Double?> = emptyList(),
    val protein: List<Double?> = emptyList(),
    val burned: List<Double?> = emptyList(),
    val steps: List<Double?> = emptyList(),
    val sleepHours: List<Double?> = emptyList(),
    val restingHr: List<Double?> = emptyList(),
    val hrv: List<Double?> = emptyList(),
    val weight: List<Double?> = emptyList(),
    val weightTrend: List<Double?> = emptyList(),
    val settings: Settings = Settings(),
    val loaded: Boolean = false,
) {
    val loggedDays get() = eaten.count { it != null }
    val trackedDays get() = burned.count { it != null }
    val avgEaten get() = HealthMath.meanOrNull(eaten)
    val avgProtein get() = HealthMath.meanOrNull(protein)
    val avgBurned get() = HealthMath.meanOrNull(burned)
    val avgSleep get() = HealthMath.meanOrNull(sleepHours)
    val avgSteps get() = HealthMath.meanOrNull(steps)

    /** Mean daily balance over days with both food logged and device-tracked burn. */
    val avgBalance: Double?
        get() {
            val e = days.indices.filter { eaten[it] != null }.associate { days[it].toString() to eaten[it]!! }
            val b = days.indices.filter { burned[it] != null }.associate { days[it].toString() to burned[it]!! }
            return HealthMath.averageBalance(e, b)
        }

    val weightRatePerWeek: Double?
        get() = HealthMath.weeklyRate(weight.mapIndexedNotNull { i, w -> w?.let { i to it } })
}

/**
 * A day's calorie burn counts as tracked only if a real source (a device or app) contributed.
 * Health Connect fills every other day with a resting-burn estimate attributed to "android".
 */
fun DailyMetric.trackedBurn(): Double? {
    val real = sources.split(',').filter { it.isNotBlank() && it != "android" }
    return if (real.isEmpty()) null else totalKcal
}

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModel(private val app: AppContainer) : ViewModel() {
    private val range = MutableStateFlow(Range.MONTH)

    val state: StateFlow<TrendsState> = range.flatMapLatest { r ->
        // Today is partial (half a day of burn, breakfast only), so ranges end yesterday.
        val last = LocalDate.now().minusDays(1)
        val first = last.minusDays(r.days.toLong() - 1)
        val from = first.toString()
        val to = last.toString()
        // Weights start earlier so the smoothed trend has warmed up by the first visible day.
        val weightFrom = first.minusDays(60).toString()
        combine(
            app.db.food().intakeBetween(from, to),
            app.db.metrics().between(from, to),
            app.db.sleep().between(from, to),
            app.db.weight().between(weightFrom, to),
            app.settings.settings,
        ) { intake, metrics, sleep, weights, settings ->
            val days = (0 until r.days).map { first.plusDays(it.toLong()) }
            val intakeBy = intake.associateBy { it.day }
            val metricBy = metrics.associateBy { it.day }
            val sleepBy = sleep.groupBy { it.day }.mapValues { (_, s) -> s.sumOf { it.asleepMinutes } / 60.0 }

            // Last weigh-in of each day, and the smoothed trend at that point.
            val daily = weights.groupBy { it.day }.mapValues { (_, w) -> w.maxBy { it.time }.kg }.toSortedMap()
            val trend = daily.keys.zip(HealthMath.weightTrend(daily.values.toList())).toMap()

            TrendsState(
                range = r,
                days = days,
                eaten = days.map { d -> intakeBy[d.toString()]?.kcal?.takeIf { it > 0 } },
                protein = days.map { d -> intakeBy[d.toString()]?.protein?.takeIf { (intakeBy[d.toString()]?.kcal ?: 0.0) > 0 } },
                burned = days.map { d -> metricBy[d.toString()]?.trackedBurn() },
                steps = days.map { d -> metricBy[d.toString()]?.steps?.takeIf { it > 0 }?.toDouble() },
                sleepHours = days.map { d -> sleepBy[d.toString()] },
                restingHr = days.map { d -> metricBy[d.toString()]?.restingHr },
                hrv = days.map { d -> metricBy[d.toString()]?.hrvMs },
                weight = days.map { d -> daily[d.toString()] },
                weightTrend = days.map { d -> trend[d.toString()] },
                settings = settings,
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendsState())

    fun setRange(r: Range) { range.value = r }
}
