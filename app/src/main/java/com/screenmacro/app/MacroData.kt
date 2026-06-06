package com.screenmacro.app

/**
 * 单次触摸事件
 */
data class TouchEvent(
    val action: Int,       // MotionEvent.ACTION_DOWN / UP / MOVE
    val x: Float,          // 触摸 X 坐标（屏幕百分比 0~1）
    val y: Float,          // 触摸 Y 坐标（屏幕百分比 0~1）
    val eventTimeMs: Long,  // 距离录制开始的时间偏移（毫秒）
    val pointerId: Int = 0, // 多点触控的手指ID
    val pressure: Float = 1.0f
)

/**
 * 一次完整的宏录制
 */
data class MacroRecording(
    var name: String = "未命名录制",
    var timestamp: Long = System.currentTimeMillis(),
    var screenWidthPx: Int = 0,
    var screenHeightPx: Int = 0,
    val events: MutableList<TouchEvent> = mutableListOf()
) {
    val durationMs: Long
        get() = if (events.isEmpty()) 0L else events.last().eventTimeMs

    val totalEvents: Int
        get() = events.size
}
