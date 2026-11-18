package dev.healthapp.domain

import androidx.health.connect.client.records.ExerciseSessionRecord as E
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Activities from Health Connect exercise sessions: categories, de-duplication of the same workout recorded by two
 * apps, and weekly goals with streaks. Pure functions; see docs/architecture.md "Activities".
 */
object Activities {

    enum class Category(val label: String) {
        RUN("Running"), WALK("Walking and hiking"), RIDE("Cycling"), SWIM("Swimming"), STRENGTH("Strength"),
        CARDIO("Cardio and HIIT"), MIND_BODY("Yoga and mobility"), SPORT("Sports"), OTHER("Other"),
    }

    fun categoryOf(type: Int): Category = when (type) {
        E.EXERCISE_TYPE_RUNNING, E.EXERCISE_TYPE_RUNNING_TREADMILL -> Category.RUN
        E.EXERCISE_TYPE_WALKING, E.EXERCISE_TYPE_HIKING -> Category.WALK
        E.EXERCISE_TYPE_BIKING, E.EXERCISE_TYPE_BIKING_STATIONARY -> Category.RIDE
        E.EXERCISE_TYPE_SWIMMING_POOL, E.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> Category.SWIM
        E.EXERCISE_TYPE_STRENGTH_TRAINING, E.EXERCISE_TYPE_WEIGHTLIFTING, E.EXERCISE_TYPE_CALISTHENICS -> Category.STRENGTH
        E.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, E.EXERCISE_TYPE_ROWING, E.EXERCISE_TYPE_ROWING_MACHINE,
        E.EXERCISE_TYPE_ELLIPTICAL, E.EXERCISE_TYPE_STAIR_CLIMBING, E.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
        E.EXERCISE_TYPE_BOOT_CAMP, E.EXERCISE_TYPE_EXERCISE_CLASS, E.EXERCISE_TYPE_DANCING -> Category.CARDIO
        E.EXERCISE_TYPE_YOGA, E.EXERCISE_TYPE_PILATES, E.EXERCISE_TYPE_STRETCHING -> Category.MIND_BODY
        E.EXERCISE_TYPE_SOCCER, E.EXERCISE_TYPE_TENNIS, E.EXERCISE_TYPE_BADMINTON, E.EXERCISE_TYPE_BASKETBALL,
        E.EXERCISE_TYPE_CRICKET, E.EXERCISE_TYPE_TABLE_TENNIS, E.EXERCISE_TYPE_VOLLEYBALL, E.EXERCISE_TYPE_SQUASH,
        E.EXERCISE_TYPE_MARTIAL_ARTS, E.EXERCISE_TYPE_BOXING, E.EXERCISE_TYPE_GOLF -> Category.SPORT
        else -> Category.OTHER
    }

    /** A readable name; the recording app's own title wins when it has one. */
    fun label(type: Int, title: String?): String = title?.takeIf { it.isNotBlank() } ?: when (type) {
        E.EXERCISE_TYPE_RUNNING, E.EXERCISE_TYPE_RUNNING_TREADMILL -> "Run"
        E.EXERCISE_TYPE_WALKING -> "Walk"
        E.EXERCISE_TYPE_HIKING -> "Hike"
        E.EXERCISE_TYPE_BIKING, E.EXERCISE_TYPE_BIKING_STATIONARY -> "Ride"
        E.EXERCISE_TYPE_SWIMMING_POOL, E.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swim"
        E.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength training"
        E.EXERCISE_TYPE_WEIGHTLIFTING -> "Weightlifting"
        E.EXERCISE_TYPE_CALISTHENICS -> "Calisthenics"
        E.EXERCISE_TYPE_YOGA -> "Yoga"
        E.EXERCISE_TYPE_PILATES -> "Pilates"
        E.EXERCISE_TYPE_STRETCHING -> "Stretching"
        E.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        E.EXERCISE_TYPE_ROWING, E.EXERCISE_TYPE_ROWING_MACHINE -> "Row"
        E.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
        E.EXERCISE_TYPE_STAIR_CLIMBING, E.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "Stairs"
        E.EXERCISE_TYPE_DANCING -> "Dance"
        E.EXERCISE_TYPE_SOCCER -> "Football"
        E.EXERCISE_TYPE_TENNIS -> "Tennis"
        E.EXERCISE_TYPE_BADMINTON -> "Badminton"
        E.EXERCISE_TYPE_BASKETBALL -> "Basketball"
        E.EXERCISE_TYPE_CRICKET -> "Cricket"
        E.EXERCISE_TYPE_MARTIAL_ARTS -> "Martial arts"
        E.EXERCISE_TYPE_BOXING -> "Boxing"
        else -> "Workout"
    }

    data class Session(
        val id: String,
        val day: LocalDate,
        val start: Long,
        val end: Long,
        val type: Int,
        val title: String?,
        val source: String,
    ) {
        val minutes: Int get() = ((end - start) / 60_000.0).roundToInt()
        val category: Category get() = categoryOf(type)
    }

    /** One real workout; [alsoRecordedBy] lists the other apps' copies that were merged into it. */
    data class Activity(val session: Session, val alsoRecordedBy: List<String> = emptyList()) {
        val day get() = session.day
        val minutes get() = session.minutes
        val category get() = session.category
    }

    /** Two sessions are the same workout when their overlap is at least this share of the shorter one. */
    const val SAME_WORKOUT_OVERLAP = 0.5

    /**
     * Merges copies of the same workout (for example Fitbit and Strava both writing a run). The longest copy is kept,
     * because a watch usually starts before the phone app and stops after it; the others are listed as "also
     * recorded by". Newest first.
     */
    fun dedupe(sessions: List<Session>): List<Activity> {
        val kept = mutableListOf<Pair<Session, MutableList<String>>>()
        for (s in sessions.sortedWith(compareByDescending<Session> { it.end - it.start }.thenBy { it.start }.thenBy { it.id })) {
            val twin = kept.firstOrNull { (k, _) -> sameWorkout(k, s) }
            if (twin != null) {
                if (s.source != twin.first.source && s.source !in twin.second) twin.second += s.source
            } else kept += s to mutableListOf()
        }
        return kept.map { (s, others) -> Activity(s, others) }.sortedByDescending { it.session.start }
    }

    fun sameWorkout(a: Session, b: Session): Boolean {
        val overlap = min(a.end, b.end) - max(a.start, b.start)
        if (overlap <= 0) return false
        val shorter = min(a.end - a.start, b.end - b.start)
        return shorter > 0 && overlap >= SAME_WORKOUT_OVERLAP * shorter
    }

    // ------------------------------------------------------------------------------------------- goals

    enum class Metric(val label: String) { SESSIONS("sessions"), MINUTES("minutes") }

    /** A weekly goal; [category] null means any activity. */
    data class Goal(val id: Long, val category: Category?, val metric: Metric, val weeklyTarget: Int)

    data class Week(val start: LocalDate, val value: Int, val met: Boolean)

    data class Progress(
        val goal: Goal,
        val thisWeek: Int,
        val met: Boolean,
        /** Weeks in a row the goal was met, ending with the current week if it's already met, else last week. */
        val streak: Int,
        val best: Int,
        /** The last [WEEKS_SHOWN] weeks, oldest first; the last one is the current, unfinished week. */
        val weeks: List<Week>,
        val daysLeft: Int,
    )

    const val WEEKS_SHOWN = 8

    /** Weeks run Monday to Sunday. */
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun valueOf(goal: Goal, activities: List<Activity>): Int {
        val matching = activities.filter { goal.category == null || it.category == goal.category }
        return when (goal.metric) {
            Metric.SESSIONS -> matching.size
            Metric.MINUTES -> matching.sumOf { it.minutes }
        }
    }

    /**
     * Weekly progress and streaks. [historyStart] is the first day we have exercise data for; weeks before it are
     * unknown, not missed, so they neither break nor extend a streak. The current week never breaks a streak:
     * it isn't over yet.
     */
    fun progress(goal: Goal, activities: List<Activity>, today: LocalDate, historyStart: LocalDate?): Progress {
        val current = weekStart(today)
        val byWeek = activities.groupBy { weekStart(it.day) }
        val first = historyStart?.let(::weekStart)?.takeIf { it <= current } ?: current
        val weeks = generateSequence(first) { it.plusWeeks(1) }.takeWhile { it <= current }.map { w ->
            val v = valueOf(goal, byWeek[w].orEmpty())
            Week(w, v, v >= goal.weeklyTarget)
        }.toList()

        val now = weeks.last()
        val finished = weeks.dropLast(1)
        val streakBefore = finished.asReversed().takeWhile { it.met }.size
        val streak = streakBefore + if (now.met) 1 else 0

        var best = 0; var run = 0
        for (w in weeks) { run = if (w.met) run + 1 else 0; best = max(best, run) }

        val shown = (WEEKS_SHOWN - 1 downTo 0).map { current.minusWeeks(it.toLong()) }
            .map { w -> weeks.firstOrNull { it.start == w } ?: Week(w, valueOf(goal, byWeek[w].orEmpty()), false) }
        return Progress(goal, now.value, now.met, streak, best, shown, daysLeft = 6 - (today.dayOfWeek.value - 1))
    }

    data class CategoryTotal(val category: Category, val sessions: Int, val minutes: Int)

    /** Sessions and minutes per category, most minutes first. */
    fun totals(activities: List<Activity>): List<CategoryTotal> = activities.groupBy { it.category }
        .map { (c, list) -> CategoryTotal(c, list.size, list.sumOf { it.minutes }) }
        .sortedByDescending { it.minutes }
}
