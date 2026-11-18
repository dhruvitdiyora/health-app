package dev.healthapp

import androidx.health.connect.client.records.ExerciseSessionRecord as E
import dev.healthapp.domain.Activities
import dev.healthapp.domain.Activities.Category
import dev.healthapp.domain.Activities.Goal
import dev.healthapp.domain.Activities.Metric
import dev.healthapp.domain.Activities.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ActivitiesTest {
    private val min = 60_000L
    // 2026-03-02 is a Monday.
    private val mon = LocalDate.of(2026, 3, 2)

    private fun s(id: String, startMin: Long, endMin: Long, source: String = "a", type: Int = E.EXERCISE_TYPE_RUNNING, day: LocalDate = mon) =
        Session(id, day, startMin * min, endMin * min, type, null, source)

    @Test fun categoriesAndLabels() {
        assertEquals(Category.RUN, Activities.categoryOf(E.EXERCISE_TYPE_RUNNING_TREADMILL))
        assertEquals(Category.STRENGTH, Activities.categoryOf(E.EXERCISE_TYPE_WEIGHTLIFTING))
        assertEquals(Category.OTHER, Activities.categoryOf(E.EXERCISE_TYPE_OTHER_WORKOUT))
        assertEquals("Ride", Activities.label(E.EXERCISE_TYPE_BIKING, null))
        assertEquals("Morning loop", Activities.label(E.EXERCISE_TYPE_BIKING, "Morning loop"))
    }

    @Test fun sameRunFromTwoAppsIsMergedKeepingTheLongest() {
        val watch = s("w", 0, 40, "com.fitbit.FitbitMobile")
        val phone = s("p", 2, 38, "com.strava")
        val out = Activities.dedupe(listOf(phone, watch))
        assertEquals(1, out.size)
        assertEquals("w", out.single().session.id)
        assertEquals(listOf("com.strava"), out.single().alsoRecordedBy)
    }

    @Test fun backToBackOrSlightlyOverlappingSessionsStaySeparate() {
        // A 30-minute run, then a 30-minute walk that started 5 minutes before the run ended.
        val out = Activities.dedupe(listOf(s("r", 0, 30), s("w", 25, 55, type = E.EXERCISE_TYPE_WALKING), s("x", 55, 70)))
        assertEquals(3, out.size)
        assertEquals(listOf("x", "w", "r"), out.map { it.session.id })
        assertFalse(Activities.sameWorkout(s("r", 0, 30), s("w", 25, 55)))
        assertTrue(Activities.sameWorkout(s("r", 0, 30), s("w", 10, 25)))
    }

    @Test fun goalProgressAndStreaks() {
        val goal = Goal(1, Category.RUN, Metric.SESSIONS, 2)
        fun run(day: LocalDate) = Activities.Activity(s(day.toString(), 0, 30, day = day))
        fun walk(day: LocalDate) = Activities.Activity(s("w$day", 0, 30, type = E.EXERCISE_TYPE_WALKING, day = day))
        val acts = listOf(
            // Four weeks ago: met. Three weeks ago: missed. Two and one week ago: met. This week: 1 so far.
            run(mon.minusWeeks(4)), run(mon.minusWeeks(4).plusDays(2)),
            run(mon.minusWeeks(3)), walk(mon.minusWeeks(3).plusDays(1)),
            run(mon.minusWeeks(2)), run(mon.minusWeeks(2).plusDays(6)),
            run(mon.minusWeeks(1).plusDays(3)), run(mon.minusWeeks(1).plusDays(4)),
            run(mon),
        )
        val today = mon.plusDays(2)
        val p = Activities.progress(goal, acts, today, historyStart = mon.minusWeeks(4))
        assertEquals(1, p.thisWeek)
        assertFalse(p.met)
        // The unfinished week does not break the streak.
        assertEquals(2, p.streak)
        assertEquals(2, p.best)
        assertEquals(Activities.WEEKS_SHOWN, p.weeks.size)
        assertEquals(mon, p.weeks.last().start)
        assertEquals(4, p.daysLeft)

        val done = Activities.progress(goal, acts + run(mon.plusDays(1)), today, mon.minusWeeks(4))
        assertTrue(done.met)
        assertEquals(3, done.streak)
        assertEquals(3, done.best)
    }

    @Test fun minutesGoalForAnyActivity() {
        val goal = Goal(2, null, Metric.MINUTES, 150)
        val separate = listOf(
            Activities.Activity(s("a", 0, 60, day = mon)),
            Activities.Activity(s("b", 0, 45, type = E.EXERCISE_TYPE_YOGA, day = mon.plusDays(1))),
            Activities.Activity(s("c", 0, 50, type = E.EXERCISE_TYPE_STRENGTH_TRAINING, day = mon.plusDays(2))),
        )
        val p = Activities.progress(goal, separate, mon.plusDays(6), historyStart = null)
        assertEquals(155, p.thisWeek)
        assertTrue(p.met)
        assertEquals(1, p.streak)
        assertEquals(0, p.daysLeft)
    }

    @Test fun weeksBeforeHistoryDoNotCountAsMissed() {
        val goal = Goal(3, null, Metric.SESSIONS, 1)
        val acts = listOf(Activities.Activity(s("a", 0, 30, day = mon.minusWeeks(1))))
        val p = Activities.progress(goal, acts, mon, historyStart = mon.minusWeeks(1).plusDays(3))
        assertEquals(1, p.streak)
        assertEquals(1, p.best)
    }

    @Test fun totalsPerCategory() {
        val acts = Activities.dedupe(listOf(s("a", 0, 30), s("b", 100, 160, type = E.EXERCISE_TYPE_YOGA), s("c", 200, 220)))
        val t = Activities.totals(acts)
        assertEquals(listOf(Category.MIND_BODY, Category.RUN), t.map { it.category })
        assertEquals(2, t[1].sessions)
        assertEquals(50, t[1].minutes)
    }
}
