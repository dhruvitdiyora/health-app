package dev.healthapp.ui.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.healthapp.AppContainer
import dev.healthapp.data.db.ActivityGoal
import dev.healthapp.data.health.ReadType
import dev.healthapp.domain.Activities
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class GoalProgress(val entity: ActivityGoal, val progress: Activities.Progress)

data class ActivitiesState(
    val hasAccess: Boolean = true,
    val goals: List<GoalProgress> = emptyList(),
    /** De-duplicated, newest first, last [RECENT_DAYS] days. */
    val recent: List<Activities.Activity> = emptyList(),
    val totals: List<Activities.CategoryTotal> = emptyList(),
    /** Copies of the same workout merged away in the recent window. */
    val merged: Int = 0,
    val loaded: Boolean = false,
)

const val RECENT_DAYS = 30

class ActivitiesViewModel(private val app: AppContainer) : ViewModel() {
    private val today = LocalDate.now(ZoneId.systemDefault())

    val state: StateFlow<ActivitiesState> = combine(
        app.sync.status,
        // A year of sessions is enough for streaks; the sync backfills up to that.
        app.db.exercise().between(today.minusDays(364).toString(), today.toString()),
        app.db.exercise().firstDay(),
        app.db.goals().all(),
    ) { status, sessions, firstDay, goals ->
        val activities = Activities.dedupe(sessions.map {
            Activities.Session(it.hcId, LocalDate.parse(it.day), it.start, it.end, it.type, it.title, it.source)
        })
        val since = today.minusDays(RECENT_DAYS - 1L)
        val recent = activities.filter { it.day >= since }
        val history = firstDay?.let(LocalDate::parse)
        ActivitiesState(
            hasAccess = !status.available || ReadType.EXERCISE.permission in status.granted || sessions.isNotEmpty(),
            goals = goals.mapNotNull { g -> g.toDomain()?.let { GoalProgress(g, Activities.progress(it, activities, today, history)) } },
            recent = recent,
            totals = Activities.totals(recent),
            merged = recent.sumOf { it.alsoRecordedBy.size },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivitiesState())

    fun saveGoal(existing: ActivityGoal?, category: Activities.Category?, metric: Activities.Metric, target: Int) {
        viewModelScope.launch {
            if (existing == null) {
                app.db.goals().insert(ActivityGoal(category = category?.name, metric = metric.name, weeklyTarget = target, created = System.currentTimeMillis()))
            } else {
                app.db.goals().update(existing.copy(category = category?.name, metric = metric.name, weeklyTarget = target))
            }
        }
    }

    fun deleteGoal(goal: ActivityGoal) {
        viewModelScope.launch { app.db.goals().delete(goal) }
    }
}

fun ActivityGoal.toDomain(): Activities.Goal? {
    val m = Activities.Metric.entries.firstOrNull { it.name == metric } ?: return null
    val c = category?.let { name -> Activities.Category.entries.firstOrNull { it.name == name } ?: return null }
    return Activities.Goal(id, c, m, weeklyTarget)
}
