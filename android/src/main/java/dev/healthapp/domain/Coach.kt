package dev.healthapp.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The on-device coach: a fixed set of plain rules over the last week, each with the numbers that triggered it. No
 * model and no network. Tips are suggestions, never diagnoses. See docs/insights.md "Coach rules".
 */
object Coach {

    enum class Kind { DATA, FOOD, SLEEP, RECOVERY, ACTIVITY, WEIGHT, GOOD }

    data class Tip(val id: String, val kind: Kind, val title: String, val body: String, val priority: Int)

    /** One complete day; null = not recorded. [eaten]/[protein] are null when no food was logged. */
    data class Day(
        val eaten: Double? = null,
        val protein: Double? = null,
        val steps: Long? = null,
        val asleepMinutes: Int? = null,
        val restingHr: Double? = null,
        val hrvMs: Double? = null,
    )

    data class GoalStatus(val title: String, val left: Int, val unit: String, val daysLeft: Int, val streak: Int, val met: Boolean)

    data class Input(
        /** The last 7 complete days, oldest first. */
        val week: List<Day>,
        /** Medians over the 28 days before [week]; null without enough data. */
        val baselineRestingHr: Double? = null,
        val baselineHrv: Double? = null,
        val baselineSteps: Double? = null,
        val kcalTarget: Int,
        val proteinTarget: Int,
        val goalWeightKg: Double? = null,
        val weightTrendKg: Double? = null,
        val weightRatePerWeek: Double? = null,
        val goals: List<GoalStatus> = emptyList(),
    )

    const val MIN_LOGGED_DAYS = 4
    const val SLEEP_FLOOR_MINUTES = 420
    const val RHR_RAISED_BPM = 3.0
    const val HRV_LOW_SHARE = 0.85
    const val STEPS_LOW_SHARE = 0.7

    /** Every rule that fires, most important first. When nothing needs attention, one "steady week" tip. */
    fun tips(i: Input): List<Tip> = buildList {
        val logged = i.week.filter { it.eaten != null && it.eaten > 0 }

        if (logged.size < MIN_LOGGED_DAYS) {
            add(Tip("log-more", Kind.DATA, "Log a few more days",
                "Food was logged on ${logged.size} of the last 7 days. Insights about eating need at least $MIN_LOGGED_DAYS; " +
                    "a rough entry is more useful than none.", 50))
        } else {
            val lowProtein = logged.count { (it.protein ?: 0.0) < 0.9 * i.proteinTarget }
            if (lowProtein >= MIN_LOGGED_DAYS) {
                val avg = logged.mapNotNull { it.protein }.average().roundToInt()
                add(Tip("protein", Kind.FOOD, "Protein ran low",
                    "Under your ${i.proteinTarget} g target on $lowProtein of ${logged.size} logged days (about $avg g a day). " +
                        "A protein food at each meal is the simplest fix.", 40))
            }
            val avgKcal = logged.mapNotNull { it.eaten }.average()
            val diff = avgKcal - i.kcalTarget
            if (diff > 0.15 * i.kcalTarget) {
                add(Tip("kcal-high", Kind.FOOD, "Above your calorie target",
                    "About ${avgKcal.roundToInt()} kcal a day on logged days, ${diff.roundToInt()} over your ${i.kcalTarget} target.", 35))
            } else if (diff < -0.25 * i.kcalTarget) {
                add(Tip("kcal-low", Kind.FOOD, "Well under your calorie target",
                    "About ${avgKcal.roundToInt()} kcal a day on logged days, ${(-diff).roundToInt()} under your ${i.kcalTarget} target. " +
                        "If that's not on purpose, check for meals that weren't logged.", 30))
            }
        }

        val nights = i.week.mapNotNull { it.asleepMinutes }
        if (nights.size >= 4) {
            val avg = nights.average().roundToInt()
            if (avg < SLEEP_FLOOR_MINUTES) {
                add(Tip("sleep-short", Kind.SLEEP, "Short on sleep",
                    "You averaged ${HealthMath.formatMinutes(avg)} asleep over ${nights.size} nights. Most adults need 7 hours or more; " +
                        "an earlier, steady bedtime is the usual lever.", 45))
            }
        }

        val last3 = i.week.takeLast(3)
        val rhrUp = i.baselineRestingHr?.let { b -> last3.all { d -> d.restingHr != null && d.restingHr >= b + RHR_RAISED_BPM } } == true
        val hrvDown = i.baselineHrv?.let { b -> last3.all { d -> d.hrvMs != null && d.hrvMs <= b * HRV_LOW_SHARE } } == true
        if (rhrUp || hrvDown) {
            val parts = buildList {
                if (rhrUp) add("resting heart rate has been ${(last3.mapNotNull { it.restingHr }.average() - i.baselineRestingHr!!).roundToInt()} bpm above your usual")
                if (hrvDown) add("HRV has been ${(100 * (1 - last3.mapNotNull { it.hrvMs }.average() / i.baselineHrv!!)).roundToInt()}% below your usual")
            }
            add(Tip("recovery", Kind.RECOVERY, "Your body may be under extra load",
                "For 3 days your ${parts.joinToString(" and ")}. Common reasons are short sleep, alcohol, a hard training block, " +
                    "stress or a cold coming on. An easier day can help. This is not a diagnosis.", 48))
        }

        val steps = i.week.mapNotNull { it.steps }
        if (i.baselineSteps != null && i.baselineSteps > 0 && steps.size >= 4 && steps.average() < STEPS_LOW_SHARE * i.baselineSteps) {
            add(Tip("steps-low", Kind.ACTIVITY, "Fewer steps than usual",
                "About ${steps.average().roundToInt()} a day this week against your usual ${i.baselineSteps.roundToInt()}.", 20))
        }

        i.goals.filter { !it.met && it.left > 0 && it.daysLeft <= 2 }.forEach { g ->
            val keep = if (g.streak > 0) " to keep your ${g.streak}-week streak" else ""
            add(Tip("goal-${g.title}", Kind.ACTIVITY, "${g.title}: ${g.left} ${g.unit} to go",
                "${g.left} more ${g.unit} in ${if (g.daysLeft == 0) "today" else "the next ${g.daysLeft + 1} days"}$keep.", 38))
        }

        if (i.goalWeightKg != null && i.weightTrendKg != null && i.weightRatePerWeek != null) {
            val toGo = i.goalWeightKg - i.weightTrendKg
            val rate = i.weightRatePerWeek
            when {
                abs(toGo) < 0.5 -> {}
                toGo * rate < 0 && abs(rate) >= 0.1 -> add(Tip("weight-away", Kind.WEIGHT, "Weight is moving away from your goal",
                    "Your trend is ${"%+.1f".format(rate)} kg a week; your goal is ${"%.1f".format(abs(toGo))} kg ${if (toGo < 0) "lower" else "higher"}.", 32))
                toGo * rate > 0 && abs(rate) >= 0.1 -> {
                    val weeks = (abs(toGo) / abs(rate)).roundToInt()
                    add(Tip("weight-toward", Kind.GOOD, "On track for your weight goal",
                        "At ${"%+.1f".format(rate)} kg a week, the trend reaches your goal in roughly $weeks weeks if nothing changes.", 10))
                }
            }
        }

        if (none { it.kind != Kind.GOOD }) {
            add(Tip("steady", Kind.GOOD, "A steady week", "Nothing stood out in the last 7 days. Keep doing what you're doing.", 1))
        }
    }.sortedByDescending { it.priority }
}
