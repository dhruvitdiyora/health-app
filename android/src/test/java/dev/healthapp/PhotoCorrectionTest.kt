package dev.healthapp

import dev.healthapp.domain.PhotoCorrection
import dev.healthapp.domain.PhotoCorrection.Pair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCorrectionTest {
    @Test fun needsFiveMeals() {
        assertNull(PhotoCorrection.learn(List(4) { Pair(500.0, 650.0) }))
    }

    @Test fun learnsTheTypicalRatio() {
        val pairs = listOf(1.1, 1.2, 1.3, 1.25, 1.4, 1.2).map { Pair(500.0, 500.0 * it) }
        val f = PhotoCorrection.learn(pairs)!!
        assertEquals(1.225, f.factor, 0.01)
        assertEquals(6, f.meals)
        assertTrue(f.low <= f.factor && f.factor <= f.high)
    }

    @Test fun ignoresTyposAndOtherFoods() {
        // One entry saved at 10× (a typo) and one at 0.1× (a different food) don't move the factor.
        val pairs = List(6) { Pair(400.0, 520.0) } + Pair(400.0, 4000.0) + Pair(400.0, 40.0)
        val f = PhotoCorrection.learn(pairs)!!
        assertEquals(1.3, f.factor, 1e-9)
        assertEquals(6, f.meals)
    }

    @Test fun nothingToOfferWhenTheModelAlreadyMatches() {
        assertNull(PhotoCorrection.learn(List(10) { Pair(500.0, 510.0) }))
    }

    @Test fun onlyTheMostRecentMealsCount() {
        val recent = List(PhotoCorrection.MAX_MEALS) { Pair(500.0, 750.0) }
        val old = List(20) { Pair(500.0, 400.0) }
        assertEquals(1.5, PhotoCorrection.learn(recent + old)!!.factor, 1e-9)
    }

    @Test fun factorIsBounded() {
        assertEquals(PhotoCorrection.MAX_FACTOR, PhotoCorrection.learn(List(8) { Pair(100.0, 350.0) })!!.factor, 1e-9)
    }
}
