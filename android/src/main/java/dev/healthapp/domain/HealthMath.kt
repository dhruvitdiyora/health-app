package dev.healthapp.domain

import kotlin.math.roundToInt

/** Pure functions over plain values: no Android, no database, so they can be unit tested. */
object HealthMath {

    /** Atwater factors: kcal implied by grams of protein, carbs and fat. */
    fun kcalFromMacros(protein: Double, carbs: Double, fat: Double): Double =
        protein * 4 + carbs * 4 + fat * 9

    /** About 7,700 kcal per kg of body fat; the usual rule of thumb, rough at the individual level. */
    const val KCAL_PER_KG = 7700.0

    fun kgFromEnergyBalance(kcal: Double): Double = kcal / KCAL_PER_KG

    /** Health Connect sleep stage codes (SleepSessionRecord.STAGE_TYPE_*). */
    object Stage {
        const val UNKNOWN = 0
        const val AWAKE = 1
        const val SLEEPING = 2
        const val OUT_OF_BED = 3
        const val LIGHT = 4
        const val DEEP = 5
        const val REM = 6
        const val AWAKE_IN_BED = 7
    }

    data class StageSpan(val stage: Int, val startMs: Long, val endMs: Long)

    data class SleepSummary(
        val asleepMinutes: Int,
        val deepMinutes: Int,
        val remMinutes: Int,
        val lightMinutes: Int,
        val awakeMinutes: Int,
    )

    /**
     * Minutes asleep and per stage. With no stage data the whole session counts as sleep,
     * which is what sources that only write a start and end time mean.
     */
    fun summarizeSleep(startMs: Long, endMs: Long, stages: List<StageSpan>): SleepSummary {
        fun minutes(ms: Long) = (ms / 60_000.0).roundToInt()
        if (stages.isEmpty()) return SleepSummary(minutes(endMs - startMs), 0, 0, 0, 0)
        var deep = 0L; var rem = 0L; var light = 0L; var asleepOther = 0L; var awake = 0L
        for (s in stages) {
            val d = (minOf(s.endMs, endMs) - maxOf(s.startMs, startMs)).coerceAtLeast(0)
            when (s.stage) {
                Stage.DEEP -> deep += d
                Stage.REM -> rem += d
                Stage.LIGHT -> light += d
                Stage.SLEEPING -> asleepOther += d
                Stage.AWAKE, Stage.AWAKE_IN_BED, Stage.OUT_OF_BED -> awake += d
                else -> Unit
            }
        }
        return SleepSummary(
            asleepMinutes = minutes(deep + rem + light + asleepOther),
            deepMinutes = minutes(deep),
            remMinutes = minutes(rem),
            lightMinutes = minutes(light),
            awakeMinutes = minutes(awake),
        )
    }

    /**
     * Exponentially smoothed weight trend (the "Hacker's Diet" moving average), which hides
     * day-to-day water swings. Input must be ordered oldest first.
     */
    fun weightTrend(weights: List<Double>, smoothing: Double = 0.1): List<Double> {
        if (weights.isEmpty()) return emptyList()
        val out = ArrayList<Double>(weights.size)
        var t = weights.first()
        for (w in weights) {
            t += smoothing * (w - t)
            out += t
        }
        return out
    }

    /** Least-squares slope in kg per week, from (dayIndex, kg) points. Null with fewer than 2 days. */
    fun weeklyRate(points: List<Pair<Int, Double>>): Double? {
        if (points.map { it.first }.distinct().size < 2) return null
        val n = points.size
        val mx = points.sumOf { it.first.toDouble() } / n
        val my = points.sumOf { it.second } / n
        val num = points.sumOf { (it.first - mx) * (it.second - my) }
        val den = points.sumOf { (it.first - mx) * (it.first - mx) }
        return num / den * 7
    }

    /** Mean of the non-null values, or null if there are none. */
    fun meanOrNull(values: List<Double?>): Double? {
        val present = values.filterNotNull()
        return if (present.isEmpty()) null else present.average()
    }

    /**
     * Calories in minus calories out, averaged only over days that have both (a day with no food
     * logged is "not tracked", not "ate nothing").
     */
    fun averageBalance(eaten: Map<String, Double>, burned: Map<String, Double>): Double? {
        val days = eaten.keys.filter { (eaten[it] ?: 0.0) > 0 && burned[it] != null }
        if (days.isEmpty()) return null
        return days.sumOf { eaten.getValue(it) - burned.getValue(it) } / days.size
    }

    fun formatMinutes(minutes: Int): String = "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"
}
