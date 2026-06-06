package com.screenmacro.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 触摸事件录制器
 * 通过悬浮透明覆盖层捕获用户的触摸操作
 */
class TouchRecorder(private val context: Context) {

    companion object {
        private const val TAG = "TouchRecorder"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val displayMetrics: DisplayMetrics
        get() = context.resources.displayMetrics

    /** 当前录制数据 */
    private val currentRecording = MacroRecording()

    /** 录制开始时的系统时间 */
    private var recordingStartTime = 0L

    /** 录制覆盖层视图 */
    private var overlayView: View? = null

    /** 录制状态 */
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    /** 当前录制的帧数 */
    private val _eventCount = MutableStateFlow(0)
    val eventCount: StateFlow<Int> = _eventCount

    /** 录制时长（毫秒） */
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    /** 录制状态回调 */
    var onRecordingStart: (() -> Unit)? = null
    var onRecordingStop: ((MacroRecording) -> Unit)? = null

    /**
     * 开始录制
     */
    fun startRecording() {
        if (_isRecording.value) return

        currentRecording.apply {
            events.clear()
            timestamp = System.currentTimeMillis()
            screenWidthPx = displayMetrics.widthPixels
            screenHeightPx = displayMetrics.heightPixels
        }

        recordingStartTime = System.currentTimeMillis()

        showTouchOverlay()
        _isRecording.value = true
        _eventCount.value = 0
        _durationMs.value = 0L

        onRecordingStart?.invoke()
    }

    /**
     * 停止录制
     */
    fun stopRecording(): MacroRecording {
        if (!_isRecording.value) return currentRecording

        _isRecording.value = false
        hideTouchOverlay()

        currentRecording.name = "录制_${System.currentTimeMillis() % 10000}"
        onRecordingStop?.invoke(currentRecording)

        return currentRecording
    }

    /**
     * 创建并显示触摸覆盖层
     */
    private fun showTouchOverlay() {
        val overlay = View(context).apply {
            setOnTouchListener { _, event -> captureTouch(event); true }
            setBackgroundColor(android.graphics.Color.parseColor("#01000000")) // 几乎完全透明
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                TYPE_APPLICATION_OVERLAY
            else
                TYPE_PHONE,
            FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_IN_SCREEN or FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
        }

        try {
            windowManager.addView(overlay, params)
            overlayView = overlay
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to show overlay", e)
        }
    }

    /**
     * 隐藏覆盖层
     */
    private fun hideTouchOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                // 忽略已移除的情况
            }
            overlayView = null
        }
    }

    /**
     * 捕获触摸事件
     */
    private fun captureTouch(event: MotionEvent): Boolean {
        if (!_isRecording.value) return false

        val now = System.currentTimeMillis()
        val offsetMs = now - recordingStartTime
        val screenW = displayMetrics.widthPixels.toFloat()
        val screenH = displayMetrics.heightPixels.toFloat()

        // 记录百分比坐标（适配不同屏幕）
        val touchEvent = TouchEvent(
            action = event.actionMasked,
            x = event.x / screenW,
            y = event.y / screenH,
            eventTimeMs = offsetMs,
            pointerId = event.getPointerId(event.actionIndex),
            pressure = event.pressure
        )

        currentRecording.events.add(touchEvent)
        _eventCount.value = currentRecording.events.size
        _durationMs.value = offsetMs

        return true // 消费事件，不传递到下层
    }

    /**
     * 清空录制数据
     */
    fun clear() {
        currentRecording.events.clear()
        _eventCount.value = 0
        _durationMs.value = 0L
    }
}
