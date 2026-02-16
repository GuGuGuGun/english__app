package com.kaoyan.wordhelper.util

import kotlin.random.Random

data class RetryInsertPolicy(
    val tailInsertMaxSize: Int,
    val randomInsertStartIndex: Int,
    val excludeTailInRandom: Boolean
)

fun interface RetryRandomSource {
    fun nextInt(from: Int, until: Int): Int

    companion object {
        val Default: RetryRandomSource = RetryRandomSource { from, until ->
            Random.nextInt(from, until)
        }
    }
}

object ImmediateRetryQueuePlanner {
    val LegacyPolicy = RetryInsertPolicy(
        tailInsertMaxSize = 5,
        randomInsertStartIndex = 2,
        excludeTailInRandom = true
    )

    val V4Policy = RetryInsertPolicy(
        tailInsertMaxSize = 3,
        randomInsertStartIndex = 1,
        excludeTailInRandom = true
    )

    fun resolveInsertIndex(
        queueSize: Int,
        policy: RetryInsertPolicy,
        randomSource: RetryRandomSource = RetryRandomSource.Default
    ): Int {
        val safeQueueSize = queueSize.coerceAtLeast(0)
        if (safeQueueSize == 0) return 0
        if (safeQueueSize <= policy.tailInsertMaxSize) return safeQueueSize

        val from = policy.randomInsertStartIndex.coerceIn(0, safeQueueSize - 1)
        val until = if (policy.excludeTailInRandom) safeQueueSize else safeQueueSize + 1
        if (from >= until) return safeQueueSize
        return randomSource.nextInt(from, until)
    }
}
