package dev.healthapp.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.healthapp.AppContainer
import dev.healthapp.data.db.ActivityGoal
import dev.healthapp.data.db.AppDatabase
import dev.healthapp.data.db.DailyMetric
import dev.healthapp.data.db.DayIntake
import dev.healthapp.data.db.ExerciseSession
import dev.healthapp.data.db.SleepSession
import dev.healthapp.data.db.WeightEntry
import dev.healthapp.data.Settings
import dev.healthapp.domain.Activities
import dev.healthapp.domain.Coach
import dev.healthapp.domain.HealthMath
import dev.healthapp.domain.Insights
import dev.healthapp.ui.activities.toDomain
import dev.healthapp.ui.trends.trackedBurn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

data class InsightsState(
    val tips: List<Coach.Tip> = emptyList(),
    val calibration: Insights.Calibration? = null,
    val sleep: Insights.SleepLink? = null,
    val loaded: Boolean = false,
)

/** Day windows used by the Insights tab and the widget; every window ends yesterday (today isn't over). */
object InsightWindows {
    const val CALIBRATION_DAYS = 28
    const val SLEEP_DAYS = 90
    const val WEEK = 7
    const val BASELINE_DAYS = 28
}

private class Raw(
    val intake: List<DayIntake>, val metrics: List<DailyMetric>, val sleep: List<SleepSession>, val weights: List<WeightEntry>,
    val settings: Settings,
)

class InsightsViewModel(app: AppContainer) : ViewModel() {
    val state: StateFlow<InsightsState> = insightsFlow(app.db, app.settings.settings, LocalDate.now(ZoneId.systemDefault()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState())
}

/** Everything the Insights tab shows, from the local database only. */
fun insightsFlow(db: AppDatabase, settings: Flow<Settings>, today: LocalDate): Flow<InsightsState> {
    val last = today.minusDays(1)
    val first = last.minusDays(InsightWindows.SLEEP_DAYS - 1L)
    val from = first.toString(); val to = last.toString()
    val raw = combine(
        db.food().intakeBetween(from, to), db.metrics().between(from, to), db.sleep().between(from, to),
        db.weight().between(first.minusDays(60).toString(), to), settings,
    ) { intake, metrics, sleep, weights, s -> Raw(intake, metrics, sleep, weights, s) }
    return combine(
        raw,
        db.exercise().between(today.minusDays(364).toString(), today.toString()),
        db.exercise().firstDay(),
        db.goals().all(),
    ) { r, sessions, firstDay, goals -> compute(r, sessions, firstDay, goals, today) }
}

private fun compute(r: Raw, sessions: List<ExerciseSession>, firstDay: String?, goals: List<ActivityGoal>, today: LocalDate): InsightsState {
    val last = today.minusDays(1)
    val intakeBy = r.intake.associateBy { it.day }
    val metricBy = r.metrics.associateBy { it.day }
    val sleepBy = r.sleep.groupBy { it.day }.mapValues { (_, s) -> s.sumOf { it.asleepMinutes } }
    fun days(n: Int, endingOn: LocalDate) = (n - 1 downTo 0).map { endingOn.minusDays(it.toLong()) }

    // Eaten vs burned over the last 28 days.
    val calDays = days(InsightWindows.CALIBRATION_DAYS, last)
    val calibration = Insights.calibrate(
        calDays.map { d -> Insights.EnergyDay(d, intakeBy[d.toString()]?.kcal?.takeIf { it > 0 }, metricBy[d.toString()]?.trackedBurn()) },
        r.weights.filter { it.day >= calDays.first().toString() }.groupBy { it.day }
            .map { (day, w) -> LocalDate.parse(day) to w.maxBy { it.time }.kg },
    )

    // Sleep vs the next morning's resting HR / HRV over 90 days.
    val sleepLink = Insights.sleepLink(days(InsightWindows.SLEEP_DAYS, last).mapNotNull { d ->
        val m = metricBy[d.toString()]
        sleepBy[d.toString()]?.let { Insights.SleepPair(d, it, m?.restingHr, m?.hrvMs) }
    })

    // Coach: last 7 days against the 28 before.
    val week = days(InsightWindows.WEEK, last)
    val base = days(InsightWindows.BASELINE_DAYS, week.first().minusDays(1))
    fun baseline(f: (DailyMetric) -> Double?): Double? =
        base.mapNotNull { metricBy[it.toString()]?.let(f) }.takeIf { it.size >= 7 }?.let(Insights::median)
    val daily = r.weights.groupBy { it.day }.mapValues { (_, w) -> w.maxBy { it.time }.kg }.toSortedMap()
    val trend = HealthMath.weightTrend(daily.values.toList())
    val recentWeights = daily.entries.filter { it.key >= last.minusDays(27).toString() }
    val rate = HealthMath.weeklyRate(recentWeights.map { (day, kg) ->
        java.time.temporal.ChronoUnit.DAYS.between(last.minusDays(27), LocalDate.parse(day)).toInt() to kg
    })

    val activities = Activities.dedupe(sessions.map {
        Activities.Session(it.hcId, LocalDate.parse(it.day), it.start, it.end, it.type, it.title, it.source)
    })
    val goalStatus = goals.mapNotNull { it.toDomain() }.map { g ->
        val p = Activities.progress(g, activities, today, firstDay?.let(LocalDate::parse))
        Coach.GoalStatus(g.category?.label ?: "Activity goal", g.weeklyTarget - p.thisWeek, g.metric.label, p.daysLeft, p.streak, p.met)
    }

    val tips = Coach.tips(Coach.Input(
        week = week.map { d ->
            val k = d.toString()
            val food = intakeBy[k]?.takeIf { it.kcal > 0 }
            val m = metricBy[k]
            Coach.Day(food?.kcal, food?.protein, m?.steps?.takeIf { it > 0 }, sleepBy[k], m?.restingHr, m?.hrvMs)
        },
        baselineRestingHr = baseline { it.restingHr },
        baselineHrv = baseline { it.hrvMs },
        baselineSteps = baseline { it.steps?.takeIf { s -> s > 0 }?.toDouble() },
        kcalTarget = r.settings.kcalTarget,
        proteinTarget = r.settings.proteinTarget,
        goalWeightKg = r.settings.goalWeightKg,
        weightTrendKg = trend.lastOrNull()?.takeIf { recentWeights.isNotEmpty() },
        weightRatePerWeek = rate?.takeIf { recentWeights.size >= 4 },
        goals = goalStatus,
    ))
    return InsightsState(tips, calibration, sleepLink, loaded = true)
}
