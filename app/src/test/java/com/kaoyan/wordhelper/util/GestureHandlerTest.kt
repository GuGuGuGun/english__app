package com.kaoyan.wordhelper.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureHandlerTest {

    @Test
    fun triggerThreshold_and_edgeReservedWidth_followConfiguredRatios() {
        val width = 1000f
        val threshold = GestureHandler.triggerThreshold(width, 0.3f)
        val edgeReserved = GestureHandler.edgeReservedWidth(width, 0.8f)

        assertEquals(300f, threshold, 0.0001f)
        assertEquals(100f, edgeReserved, 0.0001f)
    }

    @Test
    fun isStartInActiveZone_blocksEdges_andAllowsMiddle() {
        val width = 1000f

        assertFalse(GestureHandler.isStartInActiveZone(50f, width, 0.8f))
        assertTrue(GestureHandler.isStartInActiveZone(500f, width, 0.8f))
        assertFalse(GestureHandler.isStartInActiveZone(980f, width, 0.8f))
    }

    @Test
    fun resolveSwipeAction_matchesThresholdBoundaries() {
        val threshold = 300f

        assertEquals(SwipeAction.NONE, GestureHandler.resolveSwipeAction(-299f, threshold))
        assertEquals(SwipeAction.NONE, GestureHandler.resolveSwipeAction(299f, threshold))
        assertEquals(SwipeAction.TOO_EASY, GestureHandler.resolveSwipeAction(-300f, threshold))
        assertEquals(SwipeAction.ADD_TO_NOTEBOOK, GestureHandler.resolveSwipeAction(300f, threshold))
    }
}
