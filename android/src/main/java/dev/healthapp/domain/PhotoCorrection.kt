package dev.healthapp.domain

import kotlin.math.exp
import kotlin.math.ln

/**
 * A personal correction for photo estimates, learned from what the user actually saved. Each photo entry keeps the
 * model's raw estimate; the ratio saved / estimate captures both portion choices and edits. The factor is only ever
 * offered as a portion choice ("Your usual"), never applied on its own. See docs/photo-estimates.md.
 */
object PhotoCorrection {

    /** [saved] kcal the user stored for an entry whose raw model estimate was [estimate] kcal. */
    data class Pair(val estimate: Double, val saved: Double)

    data class Factor(
        /** Typical saved / estimate, as a multiplier. */
        val factor: Double,
        /** The middle half of the ratios: a quarter of meals were below [low], a quarter above [high]. */
        val low: Double,
        val high: Double,
        val meals: Int,
    )

    /** Fewer meals than this and the factor is mostly noise. */
    const val MIN_MEALS = 5
    /** Only the most recent meals count, so the factor follows changes in habits. */
    const val MAX_MEALS = 30
    /** A ratio outside this is a different food or a typo, not a correction. */
    const val MIN_RATIO = 0.25
    const val MAX_RATIO = 4.0
    /** The offered factor stays inside this, however skewed the history. */
    const val MIN_FACTOR = 0.5
    const val MAX_FACTOR = 2.0
    /** Within this of 1.0 there is nothing worth offering. */
    const val NEUTRAL_BAND = 0.08

    /**
     * Geometric median of saved / estimate over the most recent meals ([pairs] newest first). Null with too few meals
     * or when the factor is close to 1 (the model already matches what this person saves).
     */
    fun learn(pairs: List<Pair>): Factor? {
        val ratios = pairs.asSequence().filter { it.estimate > 0 && it.saved > 0 }.take(MAX_MEALS)
            .map { it.saved / it.estimate }.filter { it in MIN_RATIO..MAX_RATIO }.map { ln(it) }.sorted().toList()
        if (ratios.size < MIN_MEALS) return null
        val f = exp(quantile(ratios, 0.5)).coerceIn(MIN_FACTOR, MAX_FACTOR)
        if (kotlin.math.abs(f - 1.0) < NEUTRAL_BAND) return null
        return Factor(f, exp(quantile(ratios, 0.25)), exp(quantile(ratios, 0.75)), ratios.size)
    }

    /** Linear-interpolated quantile of an already sorted list. */
    fun quantile(sorted: List<Double>, q: Double): Double {
        val pos = q * (sorted.size - 1)
        val i = pos.toInt()
        val frac = pos - i
        return if (i + 1 < sorted.size) sorted[i] * (1 - frac) + sorted[i + 1] * frac else sorted[i]
    }
}
