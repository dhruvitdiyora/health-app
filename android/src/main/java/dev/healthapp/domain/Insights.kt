package dev.healthapp.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Phase 5 insights, all pure and rule-based (no model, no network). See docs/insights.md for the reasoning and
 * sources. Every result here is a suggestion with its uncertainty, never a fact or a medical statement.
 */
object Insights {

    // ----------------------------------------------------------------------------- eaten vs burned

    /** One complete day. [eaten] is null when no food was logged; [burned] is device-tracked burn only. */
    data class EnergyDay(val day: LocalDate, val eaten: Double?, val burned: Double?)

    enum class Verdict {
        /** The weight trend agrees with logged food minus tracked burn, within the uncertainty. */
        CONSISTENT,
        /** Weight went up more (or down less) than the logs say: food under-logged, or burn over-estimated. */
        LOGS_UNDERSTATE_BALANCE,
        /** Weight went down more (or up less) than the logs say: food over-logged, or burn under-estimated. */
        LOGS_OVERSTATE_BALANCE,
    }

    sealed interface Calibration {
        data class Missing(val reason: String) : Calibration
        data class Result(
            val days: Int,
            val loggedDays: Int,
            val avgEaten: Double,
            val avgBurned: Double,
            /** Mean of eaten − burned over days that have both, kcal/day. */
            val loggedBalance: Double,
            /** From the weight trend: slope × [HealthMath.KCAL_PER_KG], kcal/day, with a ±2 SE range. */
            val weightBalance: Double,
            val weightBalanceLow: Double,
            val weightBalanceHigh: Double,
            /** What you'd eat to hold weight, judged from your own logs and scale: avg eaten − weight balance. */
            val maintenanceLow: Double,
            val maintenanceHigh: Double,
            val verdict: Verdict,
        ) : Calibration {
            /** How far the logs are from the scale, kcal/day; positive = the logs claim more surplus than the scale. */
            val gap: Double get() = loggedBalance - weightBalance
        }
    }

    const val CALIBRATION_MIN_PAIRED_DAYS = 14
    const val CALIBRATION_MIN_WEIGHINS = 4
    const val CALIBRATION_MIN_SPAN_DAYS = 14
    /** Body composition and water make the scale noisy: never claim better than this, kcal/day. */
    const val CALIBRATION_FLOOR_KCAL = 150.0

    /**
     * Compares logged food minus tracked burn with what the scale did over the same days.
     * [weighIns]: (day, kg) inside the same window, any order.
     */
    fun calibrate(days: List<EnergyDay>, weighIns: List<Pair<LocalDate, Double>>): Calibration {
        val paired = days.filter { it.eaten != null && it.eaten > 0 && it.burned != null }
        if (paired.size < CALIBRATION_MIN_PAIRED_DAYS) {
            return Calibration.Missing("Needs $CALIBRATION_MIN_PAIRED_DAYS days with both food logged and burn tracked (have ${paired.size}).")
        }
        if (weighIns.size < CALIBRATION_MIN_WEIGHINS) {
            return Calibration.Missing("Needs at least $CALIBRATION_MIN_WEIGHINS weigh-ins in the period (have ${weighIns.size}).")
        }
        val first = weighIns.minOf { it.first }
        val last = weighIns.maxOf { it.first }
        if (ChronoUnit.DAYS.between(first, last) < CALIBRATION_MIN_SPAN_DAYS) {
            return Calibration.Missing("Weigh-ins need to span at least $CALIBRATION_MIN_SPAN_DAYS days.")
        }
        val fit = slope(weighIns.map { ChronoUnit.DAYS.between(first, it.first).toDouble() to it.second })
            ?: return Calibration.Missing("Weigh-ins need to be on different days.")

        val avgEaten = days.mapNotNull { it.eaten?.takeIf { e -> e > 0 } }.average()
        val avgBurned = days.mapNotNull { it.burned }.average()
        val logged = paired.map { it.eaten!! - it.burned!! }.average()
        val weight = fit.slope * HealthMath.KCAL_PER_KG
        val spread = maxOf(2 * fit.se * HealthMath.KCAL_PER_KG, CALIBRATION_FLOOR_KCAL)
        val verdict = when {
            abs(logged - weight) <= spread -> Verdict.CONSISTENT
            logged < weight -> Verdict.LOGS_UNDERSTATE_BALANCE
            else -> Verdict.LOGS_OVERSTATE_BALANCE
        }
        return Calibration.Result(
            days = days.size, loggedDays = paired.size, avgEaten = avgEaten, avgBurned = avgBurned,
            loggedBalance = logged, weightBalance = weight, weightBalanceLow = weight - spread, weightBalanceHigh = weight + spread,
            maintenanceLow = avgEaten - (weight + spread), maintenanceHigh = avgEaten - (weight - spread),
            verdict = verdict,
        )
    }

    data class Fit(val slope: Double, val se: Double)

    /** Least-squares slope with its standard error; null without two distinct x values. */
    fun slope(points: List<Pair<Double, Double>>): Fit? {
        val n = points.size
        if (n < 2) return null
        val mx = points.sumOf { it.first } / n
        val my = points.sumOf { it.second } / n
        val sxx = points.sumOf { (it.first - mx) * (it.first - mx) }
        if (sxx == 0.0) return null
        val b = points.sumOf { (it.first - mx) * (it.second - my) } / sxx
        val a = my - b * mx
        val se = if (n > 2) sqrt(points.sumOf { val r = it.second - (a + b * it.first); r * r } / (n - 2) / sxx) else 0.0
        return Fit(b, se)
    }

    // ------------------------------------------------------------------ sleep vs next-day heart

    /** A night's sleep (attributed to the wake-up day) and that morning's resting HR / HRV. */
    data class SleepPair(val day: LocalDate, val asleepMinutes: Int, val restingHr: Double?, val hrvMs: Double?)

    data class Comparison(
        val shortMean: Double,
        val longMean: Double,
        val shortNights: Int,
        val longNights: Int,
        /** long − short */
        val difference: Double,
        /** |difference| > 2 × its standard error (Welch). */
        val clear: Boolean,
        /** Pearson correlation between minutes asleep and the metric. */
        val r: Double?,
    )

    sealed interface SleepLink {
        data class Missing(val reason: String) : SleepLink
        data class Result(val nights: Int, val splitMinutes: Int, val restingHr: Comparison?, val hrv: Comparison?) : SleepLink
    }

    const val SLEEP_MIN_NIGHTS = 14
    const val SLEEP_MIN_PER_HALF = 5

    /** Splits your nights at your own median and compares the mornings after the shorter and the longer half. */
    fun sleepLink(pairs: List<SleepPair>): SleepLink {
        val usable = pairs.filter { it.asleepMinutes > 0 && (it.restingHr != null || it.hrvMs != null) }
        if (usable.size < SLEEP_MIN_NIGHTS) return SleepLink.Missing("Needs $SLEEP_MIN_NIGHTS nights with a resting HR or HRV the next morning (have ${usable.size}).")
        val split = median(usable.map { it.asleepMinutes.toDouble() })
        fun compare(f: (SleepPair) -> Double?): Comparison? {
            val withValue = usable.filter { f(it) != null }
            val short = withValue.filter { it.asleepMinutes < split }.map { f(it)!! }
            val long = withValue.filter { it.asleepMinutes >= split }.map { f(it)!! }
            if (short.size < SLEEP_MIN_PER_HALF || long.size < SLEEP_MIN_PER_HALF) return null
            val diff = long.average() - short.average()
            val se = sqrt(variance(short) / short.size + variance(long) / long.size)
            return Comparison(short.average(), long.average(), short.size, long.size, diff,
                clear = se > 0 && abs(diff) > 2 * se, r = pearson(withValue.map { it.asleepMinutes.toDouble() to f(it)!! }))
        }
        return SleepLink.Result(usable.size, split.roundToInt(), compare { it.restingHr }, compare { it.hrvMs })
    }

    fun median(xs: List<Double>): Double {
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private fun variance(xs: List<Double>): Double {
        if (xs.size < 2) return 0.0
        val m = xs.average()
        return xs.sumOf { (it - m) * (it - m) } / (xs.size - 1)
    }

    fun pearson(points: List<Pair<Double, Double>>): Double? {
        if (points.size < 3) return null
        val mx = points.sumOf { it.first } / points.size
        val my = points.sumOf { it.second } / points.size
        val sxy = points.sumOf { (it.first - mx) * (it.second - my) }
        val sxx = points.sumOf { (it.first - mx) * (it.first - mx) }
        val syy = points.sumOf { (it.second - my) * (it.second - my) }
        return if (sxx == 0.0 || syy == 0.0) null else sxy / sqrt(sxx * syy)
    }
}
