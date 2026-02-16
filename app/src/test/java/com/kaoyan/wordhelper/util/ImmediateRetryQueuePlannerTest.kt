package com.kaoyan.wordhelper.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ImmediateRetryQueuePlannerTest {

    @Test
    fun legacyPolicy_insertsAtTail_whenQueueIsShort() {
        val queueSize = 5
        val index = ImmediateRetryQueuePlanner.resolveInsertIndex(
            queueSize = queueSize,
            policy = ImmediateRetryQueuePlanner.LegacyPolicy,
            randomSource = RetryRandomSource { _, _ -> error("should not use random") }
        )
        assertEquals(5, index)
    }

    @Test
    fun legacyPolicy_usesConfiguredRandomRange_whenQueueIsLong() {
        val queueSize = 8
        var capturedFrom = -1
        var capturedUntil = -1
        val index = ImmediateRetryQueuePlanner.resolveInsertIndex(
            queueSize = queueSize,
            policy = ImmediateRetryQueuePlanner.LegacyPolicy,
            randomSource = RetryRandomSource { from, until ->
                capturedFrom = from
                capturedUntil = until
                4
            }
        )
        assertEquals(2, capturedFrom)
        assertEquals(8, capturedUntil)
        assertEquals(4, index)
    }

    @Test
    fun v4Policy_usesExpectedRandomRange() {
        val queueSize = 7
        var capturedFrom = -1
        var capturedUntil = -1
        val index = ImmediateRetryQueuePlanner.resolveInsertIndex(
            queueSize = queueSize,
            policy = ImmediateRetryQueuePlanner.V4Policy,
            randomSource = RetryRandomSource { from, until ->
                capturedFrom = from
                capturedUntil = until
                3
            }
        )
        assertEquals(1, capturedFrom)
        assertEquals(7, capturedUntil)
        assertEquals(3, index)
    }
}
