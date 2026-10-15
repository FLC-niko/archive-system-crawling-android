package com.topviewclub.crawling.wechat.official

import com.topviewclub.crawling.wechat.official.OfficialListProgressGuard.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class OfficialListProgressGuardTest {

    @Test
    fun fiveUnchangedScrolledViewportsRequestOneReentry() {
        val guard = OfficialListProgressGuard()

        assertEquals(Decision.CONTINUE, guard.observe("articles A", 0))
        for (generation in 1..4) {
            assertEquals(Decision.CONTINUE, guard.observe("articles A", generation))
        }
        assertEquals(Decision.REENTER, guard.observe("articles A", 5))

        // Re-entry may restart the scroll counter, but cannot renew its budget.
        assertEquals(Decision.CONTINUE, guard.observe("articles A", 0))
        for (generation in 1..4) {
            assertEquals(Decision.CONTINUE, guard.observe("articles A", generation))
        }
        assertEquals(Decision.FAIL, guard.observe("articles A", 5))
        assertEquals(Decision.FAIL, guard.observe("different viewport", 6))
    }

    @Test
    fun repeatedOrStaleOcrCallbacksDoNotCountAsScrolling() {
        val guard = OfficialListProgressGuard()
        guard.observe("articles A", 0)

        for (generation in 1..4) {
            assertEquals(Decision.CONTINUE, guard.observe("articles A", generation))
            repeat(20) {
                assertEquals(Decision.CONTINUE, guard.observe("articles A", generation))
                assertEquals(Decision.CONTINUE, guard.observe("articles A", generation - 1))
            }
        }
        assertEquals(Decision.REENTER, guard.observe("articles A", 5))
    }

    @Test
    fun actualViewportProgressRestartsTheConsecutiveStallCount() {
        val guard = OfficialListProgressGuard()
        guard.observe("articles A", 0)
        for (generation in 1..4) guard.observe("articles A", generation)

        assertEquals(Decision.CONTINUE, guard.observe("articles B", 5))
        for (generation in 6..9) {
            assertEquals(Decision.CONTINUE, guard.observe("articles B", generation))
        }
        assertEquals(Decision.REENTER, guard.observe("articles B", 10))
    }

    @Test
    fun missingScreensDoNotCountOrPreventALaterValidObservation() {
        val guard = OfficialListProgressGuard()
        assertEquals(Decision.CONTINUE, guard.observe("", 0))
        guard.observe("articles A", 0)

        for (generation in 1..4) {
            assertEquals(Decision.CONTINUE, guard.observe(" \n\t", generation))
            assertEquals(Decision.CONTINUE, guard.observe("articles A", generation))
        }
        assertEquals(Decision.CONTINUE, guard.observe("", 5))
        assertEquals(Decision.REENTER, guard.observe("articles A", 5))
    }

    @Test
    fun skippedGenerationsCountOnlyObservedStableViewports() {
        val guard = OfficialListProgressGuard()
        guard.observe("articles A", 0)

        for (generation in listOf(10, 20, 30, 40)) {
            assertEquals(Decision.CONTINUE, guard.observe("articles A", generation))
        }
        assertEquals(Decision.REENTER, guard.observe("articles A", 50))
    }

    @Test
    fun resetGivesTheNextTaskItsOwnReentryBudget() {
        val guard = OfficialListProgressGuard()
        guard.observe("articles A", 0)
        for (generation in 1..5) guard.observe("articles A", generation)
        guard.observe("articles A", 0)
        for (generation in 1..5) guard.observe("articles A", generation)

        guard.reset()
        assertEquals(Decision.CONTINUE, guard.observe("articles A", 0))
        for (generation in 1..4) {
            assertEquals(Decision.CONTINUE, guard.observe("articles A", generation))
        }
        assertEquals(Decision.REENTER, guard.observe("articles A", 5))
    }
}
