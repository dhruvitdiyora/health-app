package dev.healthapp

import dev.healthapp.domain.Coach
import dev.healthapp.domain.Insights
import dev.healthapp.domain.Insights.EnergyDay
import dev.healthapp.domain.Insights.SleepPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {
    private val d0 = LocalDate.of(2026, 3, 1)

    @Test fun slopeAndStandardError() {
        val f = Insights.slope(listOf(0.0 to 1.0, 1.0 to 3.0, 2.0 to 5.0))!!
        assertEquals(2.0, f.slope, 1e-9)
        assertEquals(0.0, f.se, 1e-9)
    }

    @Test fun calibrationNeedsEnoughData() {
        val days = (0 until 10).map { EnergyDay(d0.plusDays(it.toLong()), 2000.0, 2200.0) }
        assertTrue(Insights.calibrate(days, emptyList()) is Insights.Calibration.Missing)
    }

    @Test fun steadyWeightButLoggedDeficitMeansLogsUnderstate() {
        // Logs say a 500 kcal/day deficit, the scale is flat: food under-logged or burn over-estimated.
        val days = (0 until 28).map { EnergyDay(d0.plusDays(it.toLong()), 2000.0, 2500.0) }
        val w = (0 until 28 step 3).map { d0.plusDays(it.toLong()) to 80.0 + (if (it % 2 == 0) 0.1 else -0.1) }
        val r = Insights.calibrate(days, w) as Insights.Calibration.Result
        assertEquals(-500.0, r.loggedBalance, 1e-9)
        assertEquals(0.0, r.weightBalance, 30.0)
        assertEquals(Insights.Verdict.LOGS_UNDERSTATE_BALANCE, r.verdict)
        assertTrue(r.maintenanceLow < 2000 && r.maintenanceHigh > 2000)
    }

    @Test fun matchingWeightLossIsConsistent() {
        // 500 kcal/day deficit ≈ 0.065 kg/day.
        val days = (0 until 28).map { EnergyDay(d0.plusDays(it.toLong()), 2000.0, 2500.0) }
        val w = (0 until 28 step 2).map { d0.plusDays(it.toLong()) to 80.0 - it * 500.0 / 7700.0 }
        val r = Insights.calibrate(days, w) as Insights.Calibration.Result
        assertEquals(Insights.Verdict.CONSISTENT, r.verdict)
        assertEquals(-500.0, r.weightBalance, 1.0)
        assertEquals(2500.0, (r.maintenanceLow + r.maintenanceHigh) / 2, 1.0)
    }

    @Test fun sleepLinkComparesShortAndLongNights() {
        val pairs = (0 until 20).map { i ->
            val short = i % 2 == 0
            SleepPair(d0.plusDays(i.toLong()), if (short) 360 + i else 480 + i,
                restingHr = if (short) 60.0 + (i % 3) else 55.0 + (i % 3), hrvMs = 50.0 + (i % 5))
        }
        val r = Insights.sleepLink(pairs) as Insights.SleepLink.Result
        val rhr = r.restingHr!!
        assertEquals(10, rhr.shortNights)
        assertTrue(rhr.difference < -4)
        assertTrue(rhr.clear)
        assertTrue(rhr.r!! < -0.8)
        assertNotNull(r.hrv)
        assertFalse(r.hrv!!.clear)
    }

    @Test fun sleepLinkNeedsNights() {
        assertTrue(Insights.sleepLink(listOf(SleepPair(d0, 400, 55.0, null))) is Insights.SleepLink.Missing)
    }

    private fun input(week: List<Coach.Day>, vararg goals: Coach.GoalStatus, base: Coach.Input.() -> Coach.Input = { this }) =
        Coach.Input(week = week, kcalTarget = 2200, proteinTarget = 140, goals = goals.toList()).base()

    @Test fun coachAsksForMoreLoggingFirst() {
        val tips = Coach.tips(input(List(7) { Coach.Day() }))
        assertEquals(listOf("log-more"), tips.map { it.id })
    }

    @Test fun coachFlagsLowProteinShortSleepAndRaisedRestingHr() {
        val week = List(7) { Coach.Day(eaten = 2100.0, protein = 90.0, asleepMinutes = 380, restingHr = 61.0, hrvMs = 50.0, steps = 8000) }
        val tips = Coach.tips(input(week) { copy(baselineRestingHr = 56.0, baselineHrv = 52.0, baselineSteps = 8000.0) })
        assertEquals(listOf("recovery", "sleep-short", "protein"), tips.map { it.id })
        assertTrue(tips.first().body.contains("5 bpm above"))
        assertTrue(tips.first().body.contains("not a diagnosis"))
    }

    @Test fun coachGoalNudgeAndWeightDirection() {
        val week = List(7) { Coach.Day(eaten = 2200.0, protein = 150.0) }
        val tips = Coach.tips(input(week, Coach.GoalStatus("Running", 1, "sessions", 1, 3, false)) {
            copy(goalWeightKg = 75.0, weightTrendKg = 80.0, weightRatePerWeek = 0.3)
        })
        assertEquals(listOf("goal-Running", "weight-away"), tips.map { it.id })
        assertTrue(tips.first().body.contains("3-week streak"))
    }

    @Test fun quietWeekGetsOneSteadyTip() {
        val week = List(7) { Coach.Day(eaten = 2200.0, protein = 150.0, asleepMinutes = 460) }
        assertEquals(listOf("steady"), Coach.tips(input(week)).map { it.id })
    }
}
