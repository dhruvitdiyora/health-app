package dev.healthapp.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.healthapp.AppContainer
import dev.healthapp.HealthStatus
import dev.healthapp.data.Settings
import dev.healthapp.data.db.DailyMetric
import dev.healthapp.data.db.FoodEntry
import dev.healthapp.data.db.RecentFood
import dev.healthapp.data.db.SleepSession
import dev.healthapp.data.db.WeightEntry
import dev.healthapp.domain.HealthMath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class DayData(
    val food: List<FoodEntry> = emptyList(),
    val metric: DailyMetric? = null,
    val sleep: SleepSession? = null,
    /** The 7 days before the selected one, for "vs. usual" comparisons. */
    val baseline: List<DailyMetric> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
    /** Most recent weigh-in on or before the day, however old. */
    val latestWeight: WeightEntry? = null,
)

data class TodayState(
    val day: LocalDate = LocalDate.now(),
    val data: DayData = DayData(),
    val settings: Settings = Settings(),
    val recent: List<RecentFood> = emptyList(),
    val health: HealthStatus = HealthStatus(),
    val syncing: Boolean = false,
    val loaded: Boolean = false,
) {
    val eaten get() = data.food.sumOf { it.kcal }
    val protein get() = data.food.sumOf { it.protein }
    val carbs get() = data.food.sumOf { it.carbs }
    val fat get() = data.food.sumOf { it.fat }
    val burned get() = data.metric?.totalKcal
    val isToday get() = day == LocalDate.now()

    /** Latest weigh-in on or before the selected day. */
    val weight: WeightEntry? get() = data.latestWeight

    /** Smoothed trend over the last 30 days, only once there are at least two weigh-ins to smooth. */
    val weightTrend: Double? get() =
        if (data.weights.size < 2) null else HealthMath.weightTrend(data.weights.map { it.kg }).lastOrNull()

    val rhrBaseline get() = HealthMath.meanOrNull(data.baseline.map { it.restingHr })
    val hrvBaseline get() = HealthMath.meanOrNull(data.baseline.map { it.hrvMs })
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val app: AppContainer) : ViewModel() {
    private val day = MutableStateFlow(LocalDate.now())

    private val dayData = day.flatMapLatest { d ->
        val s = d.toString()
        combine(
            app.db.food().forDay(s),
            app.db.metrics().forDay(s),
            app.db.sleep().forDay(s),
            app.db.metrics().between(d.minusDays(7).toString(), d.minusDays(1).toString()),
            combine(app.db.weight().between(d.minusDays(30).toString(), s), app.db.weight().latestOnOrBefore(s), ::Pair),
        ) { food, metric, sleep, baseline, (weights, latest) ->
            DayData(food, metric, sleep.firstOrNull(), baseline, weights, latest)
        }
    }

    val state: StateFlow<TodayState> = combine(
        day, dayData, app.settings.settings, app.db.food().recent(), combine(app.sync.status, app.sync.syncing, ::Pair),
    ) { d, data, settings, recent, (health, syncing) ->
        TodayState(d, data, settings, recent, health, syncing, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    fun previousDay() { day.value = day.value.minusDays(1) }
    fun nextDay() { if (day.value < LocalDate.now()) day.value = day.value.plusDays(1) }
    fun goToToday() { day.value = LocalDate.now() }

    private var today = LocalDate.now()

    /** If the app sat open past midnight on "today", move on to the new today; then sync if stale. */
    fun onResume() {
        val now = LocalDate.now()
        if (now != today) {
            if (day.value == today) day.value = now
            today = now
        }
        viewModelScope.launch { app.sync.sync() }
    }

    fun refresh() {
        viewModelScope.launch { app.sync.sync(force = true) }
    }

    /** Entries logged on a past day get a midday timestamp so they land on that day. */
    fun timestampFor(d: LocalDate): Long {
        val zone = ZoneId.systemDefault()
        return if (d == LocalDate.now()) System.currentTimeMillis()
        else d.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    }

    fun saveFood(entry: FoodEntry) = viewModelScope.launch {
        if (entry.id == 0L) app.food.add(entry) else app.food.update(entry)
    }

    fun deleteFood(entry: FoodEntry) = viewModelScope.launch {
        app.food.delete(entry)
        _message.value = "Deleted ${entry.name}"
    }

    fun logWeight(kg: Double) = viewModelScope.launch {
        val d = day.value
        app.db.weight().insert(WeightEntry(day = d.toString(), time = timestampFor(d), kg = kg, source = "manual"))
    }

    fun deleteWeight(entry: WeightEntry) = viewModelScope.launch { app.db.weight().delete(entry) }
}
