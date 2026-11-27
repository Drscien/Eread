package com.eread.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryPlannerTest {

    @Test
    fun firstTimeIsFull() {
        assertEquals(SummaryMode.FULL, SummaryPlanner.mode(false, false, false, 100, 0))
    }

    @Test
    fun forceIsFull() {
        assertEquals(SummaryMode.FULL, SummaryPlanner.mode(true, true, false, 100, 50))
    }

    @Test
    fun backwardIsReread() {
        assertEquals(SummaryMode.REREAD, SummaryPlanner.mode(false, true, true, 100, 200))
    }

    @Test
    fun forwardIsIncremental() {
        assertEquals(SummaryMode.INCREMENTAL, SummaryPlanner.mode(false, true, false, 300, 200))
    }

    @Test
    fun samePositionIsFull() {
        assertEquals(SummaryMode.FULL, SummaryPlanner.mode(false, true, false, 200, 200))
    }
}
