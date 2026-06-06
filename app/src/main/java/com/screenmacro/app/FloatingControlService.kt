package com.screenmacro.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import kotlinx.coroutines.flow.first

/**
 * 悬浮控制面板服务
 * 显示一个浮动按钮，点击后展开控制面板：
 * 录制 / 播放 / 停止 / 速度控制
 */
class FloatingControlService : Service() {

    companion object {
        private const val TAG = "FloatingControl"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "floating_channel"

        var isRunning = false
            private set

        private var panelExpanded = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var touchRecorder: TouchRecorder

    // 浮动按钮视图
    private var floatingView: View? = null
    // 控制面板视图
    private var controlPanel: View? = null

    private val displayMetrics: DisplayMetrics
        get() = resources.displayMetrics

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        touchRecorder = TouchRecorder(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification("控制面板已启动"))
            showFloatingButton()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideFloatingButton()
        hideControlPanel()
        if (touchRecorder.isRecording.first()) {
            touchRecorder.stopRecording()
        }
        isRunning = false
        super.onDestroy()
    }

    /**
     * 创建悬浮小球按钮
     */
    private fun showFloatingButton() {
        if (floatingView != null) return

        val density = resources.displayMetrics.density
        val size = (56 * density).toInt()

        val imageView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setBackgroundColor(android.graphics.Color.parseColor("#33000000"))
            layoutParams = ViewGroup.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(12, 12, 12, 12)
            elevation = 6f
            setOnClickListener {
                toggleControlPanel()
            }
        }

        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        } else {
            WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        }.apply {
            gravity = Gravity.START or Gravity.TOP
            x = displayMetrics.widthPixels - size - 20
            y = displayMetrics.heightPixels / 3
        }

        // 添加拖拽支持
        imageView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    params.x = (event.rawX - size / 2).toInt()
                    params.y = (event.rawY - 50).toInt()
                    windowManager.updateViewLayout(imageView, params)
                }
            }
            false
        }

        try {
            windowManager.addView(imageView, params)
            floatingView = imageView
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to show floating button", e)
        }
    }

    private fun hideFloatingButton() {
        floatingView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            floatingView = null
        }
    }

    /**
     * 展开/收起控制面板
     */
    private fun toggleControlPanel() {
        if (panelExpanded) {
            hideControlPanel()
        } else {
            showControlPanel()
        }
    }

    /**
     * 控制面板
     */
    private fun showControlPanel() {
        if (controlPanel != null) return
        panelExpanded = true

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val panel = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#E0000000"))
            setOnClickListener { hideControlPanel() }
        }

        val panelContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#FF1C1C1E"))
                setCornerRadius(24f)
                setStroke(1, android.graphics.Color.parseColor("#FF3A3A3C"))
            })
        }

        // 标题
        TextView(this).apply {
            text = "屏幕宏控制"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            panelContent.addView(this)
        }

        panelContent.addView(createSpacer(16))

        // ---- 录制按钮 ----
        val recordBtn = createButton("🎬  开始录制", android.graphics.Color.parseColor("#FFE53935"))
        recordBtn.setOnClickListener {
            if (!touchRecorder.isRecording.first()) {
                touchRecorder.startRecording()
                recordBtn.text = "🔴  停止录制"
                recordBtn.setBackgroundColor(android.graphics.Color.parseColor("#FF1E88E5"))
            } else {
                touchRecorder.stopRecording()
                recordBtn.text = "✅  录制完成"
                recordBtn.isEnabled = false
                android.os.Handler(mainLooper).postDelayed({
                    recordBtn.text = "🎬  开始录制"
                    recordBtn.setBackgroundColor(android.graphics.Color.parseColor("#FFE53935"))
                    recordBtn.isEnabled = true
                }, 1500)
            }
        }
        panelContent.addView(recordBtn)

        panelContent.addView(createSpacer(8))

        // ---- 播放按钮 ----
        val playBtn = createButton("▶  播放录制", android.graphics.Color.parseColor("#FF43A047"))
        playBtn.setOnClickListener {
            val recording = touchRecorder.stopRecording()
            if (recording.events.isEmpty()) {
                Toast.makeText(this, "没有录制数据", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val speed = getCurrentSpeed()
            MacroAccessibilityService.instance?.let { service ->
                service.playRecording(recording, speed)
                Toast.makeText(this, "正在以 ${speed}x 速度播放", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "无障碍服务未启动", Toast.LENGTH_LONG).show()
                // 打开无障碍设置
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        panelContent.addView(playBtn)

        panelContent.addView(createSpacer(8))

        // ---- 停止按钮 ----
        val stopBtn = createButton("⏹  停止", android.graphics.Color.parseColor("#FF757575"))
        stopBtn.setOnClickListener {
            MacroAccessibilityService.instance?.stopPlayback()
            touchRecorder.stopRecording()
            Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show()
        }
        panelContent.addView(stopBtn)

        panelContent.addView(createSpacer(16))

        // ---- 速度控制 ----
        TextView(this).apply {
            text = "播放速度"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#FFAAAAAA"))
            gravity = Gravity.CENTER
            panelContent.addView(this)
        }

        panelContent.addView(createSpacer(8))

        val speedLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val speeds = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 5.0f)
        val speedTexts = listOf("0.25x", "0.5x", "1x", "1.5x", "2x", "3x", "5x")

        var selectedSpeed = 1.0f

        speedTexts.forEachIndexed { index, text ->
            val btn = Button(this).apply {
                setText(text)
                textSize = 12f
                setPadding(8, 4, 8, 4)
                val isSelected = speeds[index] == 1.0f
                setTextColor(if (isSelected) android.graphics.Color.WHITE
                    else android.graphics.Color.parseColor("#FF888888"))
                setBackgroundColor(if (isSelected) android.graphics.Color.parseColor("#FF333333")
                    else android.graphics.Color.TRANSPARENT)
                setOnClickListener {
                    selectedSpeed = speeds[index]
                    MacroAccessibilityService.instance?.setSpeed(selectedSpeed)
                    Toast.makeText(this@FloatingControlService, "${text}", Toast.LENGTH_SHORT).show()
                    // 高亮选中的按钮
                    for (i in 0 until speedLayout.childCount) {
                        val b = speedLayout.getChildAt(i) as? Button ?: continue
                        val isSel = i == index
                        b.setTextColor(if (isSel) android.graphics.Color.WHITE
                            else android.graphics.Color.parseColor("#FF888888"))
                        b.setBackgroundColor(if (isSel) android.graphics.Color.parseColor("#FF333333")
                            else android.graphics.Color.TRANSPARENT)
                    }
                }
            }
            speedLayout.addView(btn)

            if (index < speedTexts.size - 1) {
                speedLayout.addView(createSpacerHorizontal(4))
            }
        }
        panelContent.addView(speedLayout)

        panelContent.addView(createSpacer(16))

        // ---- 关闭按钮 ----
        val closeBtn = createButton("✕  关闭面板", android.graphics.Color.parseColor("#555555"))
        closeBtn.setOnClickListener { hideControlPanel() }
        panelContent.addView(closeBtn)

        panel.addView(panelContent, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        })

        // 显示为全屏覆盖
        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        } else {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        }

        try {
            windowManager.addView(panel, params)
            controlPanel = panel
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to show control panel", e)
        }
    }

    private fun hideControlPanel() {
        controlPanel?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            controlPanel = null
        }
        panelExpanded = false
    }

    private fun getCurrentSpeed(): Float {
        return 1.0f // 默认速度，实际通过面板选择
    }

    private fun createButton(text: String, bgColor: Int): Button {
        return Button(this).apply {
            setText(text)
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(bgColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                height = (48 * resources.displayMetrics.density).toInt()
            }
            setPadding(16, 0, 16, 0)
            elevation = 2f
        }
    }

    private fun createSpacer(heightDp: Int): View {
        return Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (heightDp * resources.displayMetrics.density).toInt()
            )
        }
    }

    private fun createSpacerHorizontal(widthDp: Int): View {
        return Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (widthDp * resources.displayMetrics.density).toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "浮动控制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "浮动控制面板通知"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("屏幕宏录制器")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }
}
