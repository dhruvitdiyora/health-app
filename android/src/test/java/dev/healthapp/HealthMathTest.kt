package dev.healthapp

import dev.healthapp.data.Export
import dev.healthapp.data.db.DailyMetric
import dev.healthapp.domain.HealthMath
import dev.healthapp.domain.HealthMath.Stage
import dev.healthapp.domain.HealthMath.StageSpan
import dev.healthapp.ui.components.compact
import dev.healthapp.ui.components.niceAxis
import dev.healthapp.ui.trends.trackedBurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthMathTest {
    private val min = 60_000L

    @Test fun kcalFromMacrosUsesAtwaterFactors() {
        assertEquals(410.0, HealthMath.kcalFromMacros(20.0, 60.0, 10.0), 1e-9)
    }

    @Test fun sleepWithoutStagesCountsWholeSession() {
        val s = HealthMath.summarizeSleep(0, 420 * min, emptyList())
        assertEquals(420, s.asleepMinutes)
        assertEquals(0, s.deepMinutes)
    }

    @Test fun sleepStagesExcludeAwakeTime() {
        val stages = listOf(
            StageSpan(Stage.LIGHT, 0, 60 * min),
            StageSpan(Stage.DEEP, 60 * min, 120 * min),
            StageSpan(Stage.AWAKE, 120 * min, 135 * min),
            StageSpan(Stage.REM, 135 * min, 180 * min),
        )
        val s = HealthMath.summarizeSleep(0, 180 * min, stages)
        assertEquals(165, s.asleepMinutes)
        assertEquals(60, s.deepMinutes)
        assertEquals(45, s.remMinutes)
        assertEquals(60, s.lightMinutes)
        assertEquals(15, s.awakeMinutes)
    }

    @Test fun sleepStagesAreClippedToTheSession() {
        val s = HealthMath.summarizeSleep(10 * min, 70 * min, listOf(StageSpan(Stage.LIGHT, 0, 100 * min)))
        assertEquals(60, s.lightMinutes)
    }

    @Test fun weightTrendSmoothsTowardNewValues() {
        val t = HealthMath.weightTrend(listOf(80.0, 80.0, 70.0))
        assertEquals(80.0, t[0], 1e-9)
        assertEquals(79.0, t[2], 1e-9)
    }

    @Test fun weeklyRateIsLeastSquaresSlope() {
        // -0.1 kg a day is -0.7 kg a week.
        val rate = HealthMath.weeklyRate(listOf(0 to 80.0, 1 to 79.9, 2 to 79.8, 3 to 79.7))!!
        assertEquals(-0.7, rate, 1e-9)
        assertNull(HealthMath.weeklyRate(listOf(0 to 80.0)))
    }

    @Test fun averageBalanceSkipsUnloggedAndUntrackedDays() {
        val eaten = mapOf("a" to 2000.0, "b" to 0.0, "c" to 1800.0)
        val burned = mapOf("a" to 2500.0, "b" to 2400.0)
        assertEquals(-500.0, HealthMath.averageBalance(eaten, burned)!!, 1e-9)
        assertNull(HealthMath.averageBalance(mapOf("x" to 1.0), emptyMap()))
    }

    @Test fun restingEstimateAloneIsNotTrackedBurn() {
        assertNull(DailyMetric("d", totalKcal = 1700.0, sources = "android").trackedBurn())
        assertNull(DailyMetric("d", totalKcal = 1700.0, sources = "").trackedBurn())
        assertEquals(2600.0, DailyMetric("d", totalKcal = 2600.0, sources = "android,com.fitbit.FitbitMobile").trackedBurn()!!, 1e-9)
    }

    @Test fun niceAxisRoundsAndStartsBarsAtZero() {
        val a = niceAxis(listOf(1646.0, 4291.0), zeroBased = true)
        assertEquals(0.0, a.min, 1e-9)
        // About four steps over 0-4,291 picks a step of 2,000.
        assertEquals(6000.0, a.max, 1e-9)
        assertEquals(listOf(0.0, 2000.0, 4000.0, 6000.0), a.ticks)
    }

    @Test fun niceAxisFitsLinesToTheirData() {
        val a = niceAxis(listOf(63.0, 70.0), zeroBased = false)
        assertEquals(62.0, a.min, 1e-9)
        assertEquals(70.0, a.max, 1e-9)
    }

    @Test fun compactLabels() {
        assertEquals("950", compact(950.0))
        assertEquals("2K", compact(2000.0))
        assertEquals("2.5K", compact(2500.0))
        assertEquals("12K", compact(12000.0))
    }

    @Test fun csvEscaping() {
        assertEquals("\"Oats, milk\"", Export.escape("Oats, milk"))
        assertEquals("\"5\"\" sub\"", Export.escape("5\" sub"))
        assertEquals("410", Export.escape(410.0))
        assertEquals("12.50", Export.escape(12.5))
        assertEquals("", Export.escape(null))
    }
}
