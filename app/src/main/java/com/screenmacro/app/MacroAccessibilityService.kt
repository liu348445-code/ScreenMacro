package com.screenmacro.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.min

/**
 * 无障碍服务：用于回放触摸操作（dispatchGesture）
 */
class MacroAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MacroAccessibility"
        var instance: MacroAccessibilityService? = null
            private set

        /** 当前播放状态 */
        var isPlaying = false
            private set

        /** 当前播放速度倍率 */
        var playbackSpeed = 1.0f
            private set

        /** 当前播放进度 0~1 */
        var playbackProgress = 0f
            private set

        /** 播放状态变化回调 */
        var onStateChanged: ((Boolean) -> Unit)? = null
        var onProgressChanged: ((Float) -> Unit)? = null
        var onFinished: (() -> Unit)? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var playbackJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理 UI 事件
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
        instance = null
        Log.d(TAG, "AccessibilityService destroyed")
    }

    /**
     * 开始播放宏录制
     */
    fun playRecording(recording: MacroRecording, speed: Float = 1.0f) {
        if (isPlaying) stopPlayback()
        if (recording.events.isEmpty()) {
            onFinished?.invoke()
            return
        }

        playbackSpeed = speed.coerceIn(0.1f, 10.0f)
        isPlaying = true
        playbackProgress = 0f
        onStateChanged?.invoke(true)

        var lastEventTime = recording.events.first().eventTimeMs
        val totalDuration = recording.durationMs
        var dispatchedCount = 0
        val totalEvents = recording.events.size

        playbackJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                for (event in recording.events) {
                    if (!isPlaying) break

                    val delayMs = ((event.eventTimeMs - lastEventTime) / playbackSpeed).toLong()
                    if (delayMs > 2) {
                        delay(delayMs.coerceAtMost(1000L))
                    }
                    lastEventTime = event.eventTimeMs

                    // 将百分比坐标转换为实际像素
                    val displayMetrics = resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val screenHeight = displayMetrics.heightPixels
                    val px = event.x * screenWidth
                    val py = event.y * screenHeight

                    dispatchTouch(event.action, px.toInt(), py.toInt())

                    dispatchedCount++
                    playbackProgress = event.eventTimeMs.toFloat() / totalDuration.coerceAtLeast(1)
                    onProgressChanged?.invoke(playbackProgress)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
            } finally {
                finishPlayback()
            }
        }
    }

    /**
     * 停止播放
     */
    fun stopPlayback() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        finishPlayback()
    }

    /**
     * 更新播放速度（实时调整）
     */
    fun setSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.1f, 10.0f)
    }

    private fun finishPlayback() {
        if (isPlaying || playbackProgress > 0f) {
            isPlaying = false
            playbackProgress = 0f
            onStateChanged?.invoke(false)
            onProgressChanged?.invoke(0f)
            onFinished?.invoke()
        }
    }

    /**
     * 使用 AccessibilityService 分发手势
     */
    private fun dispatchTouch(action: Int, x: Int, y: Int) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())

        val duration: Long = when (action) {
            android.view.MotionEvent.ACTION_DOWN -> 1L
            android.view.MotionEvent.ACTION_MOVE -> 5L
            android.view.MotionEvent.ACTION_UP -> 30L
            else -> 1L
        }

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Gesture cancelled at ($x, $y)")
            }
        }, null)
    }
}
