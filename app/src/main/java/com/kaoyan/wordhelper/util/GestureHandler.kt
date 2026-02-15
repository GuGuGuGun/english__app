package com.kaoyan.wordhelper.util

enum class SwipeAction {
    NONE,
    TOO_EASY,
    ADD_TO_NOTEBOOK
}

object GestureHandler {
    const val DEFAULT_TRIGGER_RATIO = 0.3f
    const val DEFAULT_ACTIVE_ZONE_RATIO = 0.8f

    fun triggerThreshold(widthPx: Float, triggerRatio: Float = DEFAULT_TRIGGER_RATIO): Float {
        return widthPx.coerceAtLeast(1f) * triggerRatio.coerceIn(0f, 1f)
    }

    fun edgeReservedWidth(widthPx: Float, activeZoneRatio: Float = DEFAULT_ACTIVE_ZONE_RATIO): Float {
        val safeWidth = widthPx.coerceAtLeast(1f)
        val safeActiveZone = activeZoneRatio.coerceIn(0f, 1f)
        return safeWidth * ((1f - safeActiveZone) / 2f)
    }

    fun isStartInActiveZone(
        startX: Float,
        widthPx: Float,
        activeZoneRatio: Float = DEFAULT_ACTIVE_ZONE_RATIO
    ): Boolean {
        val safeWidth = widthPx.coerceAtLeast(1f)
        val edgeReserved = edgeReservedWidth(safeWidth, activeZoneRatio)
        return startX in edgeReserved..(safeWidth - edgeReserved)
    }

    fun resolveSwipeAction(offsetX: Float, threshold: Float): SwipeAction {
        val safeThreshold = threshold.coerceAtLeast(1f)
        return when {
            offsetX <= -safeThreshold -> SwipeAction.TOO_EASY
            offsetX >= safeThreshold -> SwipeAction.ADD_TO_NOTEBOOK
            else -> SwipeAction.NONE
        }
    }
}
