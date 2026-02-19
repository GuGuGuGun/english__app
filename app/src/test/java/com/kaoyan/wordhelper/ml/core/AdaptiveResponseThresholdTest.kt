package com.kaoyan.wordhelper.ml.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveResponseThresholdTest {

    @Test
    fun `ML关闭应返回默认阈值6000ms`() {
        val threshold = AdaptiveResponseThreshold.computeThreshold(
            avgResponseTime = 3000f,
            stdResponseTime = 1000f,
            mlEnabled = false
        )
        assertEquals(6000L, threshold)
    }

    @Test
    fun `零平均响应时间应返回默认阈值`() {
        val threshold = AdaptiveResponseThreshold.computeThreshold(
            avgResponseTime = 0f,
            stdResponseTime = 0f,
            mlEnabled = true
        )
        assertEquals(6000L, threshold)
    }

    @Test
    fun `正常响应时间应返回均值加1_5倍标准差`() {
        val threshold = AdaptiveResponseThreshold.computeThreshold(
            avgResponseTime = 4000f,
            stdResponseTime = 1000f,
            mlEnabled = true
        )
        // 4000 + 1.5 * 1000 = 5500
        assertEquals(5500L, threshold)
    }

    @Test
    fun `阈值应不低于3000ms`() {
        val threshold = AdaptiveResponseThreshold.computeThreshold(
            avgResponseTime = 1000f,
            stdResponseTime = 500f,
            mlEnabled = true
        )
        assertTrue("阈值不应低于3000ms: $threshold", threshold >= 3000L)
    }

    @Test
    fun `阈值应不超过15000ms`() {
        val threshold = AdaptiveResponseThreshold.computeThreshold(
            avgResponseTime = 12000f,
            stdResponseTime = 5000f,
            mlEnabled = true
        )
        assertTrue("阈值不应超过15000ms: $threshold", threshold <= 15000L)
    }

    @Test
    fun `快速用户应获得较短阈值`() {
        val fastThreshold = AdaptiveResponseThreshold.computeThreshold(
            avgResponseTime = 2000f,
            stdResponseTime = 500f,
            mlEnabled = true
        )
        val slowThreshold = AdaptiveResponseThreshold.computeThreshold(
            avgResponseTime = 6000f,
            stdResponseTime = 2000f,
            mlEnabled = true
        )
        assertTrue(
            "快速用户阈值应更短: fast=$fastThreshold, slow=$slowThreshold",
            fastThreshold < slowThreshold
        )
    }
}
