package com.screenmacro.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION
import java.io.File

/**
 * 主界面：权限引导 + 使用说明
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_SCREEN_RECORD = 100
        private const val REQUEST_CODE_OVERLAY = 101
        private const val REQUEST_CODE_NOTIFICATION = 102
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            setBackgroundColor(android.graphics.Color.parseColor("#FF121212"))
        }

        // 标题
        TextView(this).apply {
            text = "🎬 屏幕宏录制器"
            textSize = 26f
            setTextColor(android.graphics.Color.WHITE)
            textStyle = android.graphics.Typeface.BOLD
            gravity = Gravity.CENTER
            mainLayout.addView(this)
        }

        mainLayout.addView(createSpacer(8))

        TextView(this).apply {
            text = "录制屏幕操作并以可变速回放"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#FF888888"))
            gravity = Gravity.CENTER
            mainLayout.addView(this)
        }

        mainLayout.addView(createSpacer(32))

        // ===== 步骤 1: 悬浮窗权限 =====
        addSectionHeader(mainLayout, "步骤 1：悬浮窗权限")
        addSectionDesc(mainLayout, "用于显示录制/播放控制按钮")
        val overlayBtn = createActionButton("授予悬浮窗权限")
        overlayBtn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "✅ 已有悬浮窗权限", Toast.LENGTH_SHORT).show()
                } else {
                    startActivityForResult(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }, REQUEST_CODE_OVERLAY
                    )
                }
            } else {
                Toast.makeText(this, "✅ 无需额外权限", Toast.LENGTH_SHORT).show()
            }
        }
        mainLayout.addView(overlayBtn)

        mainLayout.addView(createSpacer(24))

        // ===== 步骤 2: 无障碍服务 =====
        addSectionHeader(mainLayout, "步骤 2：无障碍服务")
        addSectionDesc(mainLayout, "用于回放触摸操作（模拟点击/滑动）")
        val accessibilityBtn = createActionButton("打开无障碍设置")
        accessibilityBtn.setOnClickListener {
            if (isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "✅ 无障碍服务已开启", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        mainLayout.addView(accessibilityBtn)

        mainLayout.addView(createSpacer(24))

        // ===== 步骤 3: 通知权限 =====
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addSectionHeader(mainLayout, "步骤 3：通知权限")
            addSectionDesc(mainLayout, "后台录制时需要发送通知")
            val notifBtn = createActionButton("授予通知权限")
            notifBtn.setOnClickListener {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "✅ 已有通知权限", Toast.LENGTH_SHORT).show()
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_CODE_NOTIFICATION
                    )
                }
            }
            mainLayout.addView(notifBtn)
            mainLayout.addView(createSpacer(24))
        }

        // ===== 步骤 4: 启动服务 =====
        addSectionHeader(mainLayout, "🚀 启动控制面板")
        addSectionDesc(mainLayout, "启动悬浮按钮，开始录制和回放操作")
        mainLayout.addView(createSpacer(8))

        val startBtn = createActionButton("▶ 启动控制面板")
        startBtn.setTextColor(android.graphics.Color.WHITE)
        startBtn.setBackgroundColor(android.graphics.Color.parseColor("#FF1E88E5"))
        startBtn.setOnClickListener {
            checkPermissionsBeforeStart()
        }
        mainLayout.addView(startBtn)

        val stopBtn = createActionButton("⏹ 停止控制面板")
        stopBtn.setBackgroundColor(android.graphics.Color.parseColor("#FF555555"))
        stopBtn.setOnClickListener {
            stopService(Intent(this, FloatingControlService::class.java))
            Toast.makeText(this, "控制面板已停止", Toast.LENGTH_SHORT).show()
        }
        mainLayout.addView(stopBtn)

        mainLayout.addView(createSpacer(32))

        // ===== 使用说明 =====
        addSectionHeader(mainLayout, "📖 使用说明")
        mainLayout.addView(createSpacer(8))

        val instructions = arrayOf(
            "1. 完成以上所有权限设置",
            "2. 点击「启动控制面板」",
            "3. 点击悬浮小球打开控制面板",
            "4. 点「录制」开始记录你的操作",
            "5. 操作完成后点「停止录制」",
            "6. 点「播放录制」自动回放操作",
            "7. 在速度栏选择 0.25x ~ 5x 变速"
        )

        instructions.forEach { text ->
            TextView(this).apply {
                this.text = text
                textSize = 13f
                setTextColor(android.graphics.Color.parseColor("#FFCCCCCC"))
                setPadding(8, 4, 8, 4)
                mainLayout.addView(this)
            }
        }

        mainLayout.addView(createSpacer(24))

        // ===== 提示 =====
        TextView(this).apply {
            text = "⚠️ 录制时触摸会被捕获（不会穿透到下层应用）\n建议录制简单操作后立即停止播放测试"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#FFE53935"))
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER
            mainLayout.addView(this)
        }

        mainLayout.addView(createSpacer(16))

        scrollView.addView(mainLayout)
        setContentView(scrollView)

        // 首次启动检查权限状态
        checkInitialPermissions()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_SCREEN_RECORD -> {
                if (resultCode == RESULT_OK && data != null) {
                    ScreenRecordService.setupProjection(this, data, resultCode)
                    startService(Intent(this, ScreenRecordService::class.java)
                        .setAction(ScreenRecordService.ACTION_START))
                    Toast.makeText(this, "录制服务已启动", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "用户取消了屏幕录制授权", Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_CODE_OVERLAY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "✅ 悬浮窗权限已授予", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_NOTIFICATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "✅ 通知权限已授予", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkInitialPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                showPermissionHint("悬浮窗权限", "需要悬浮窗权限才能显示控制按钮")
            }
        }
        if (!isAccessibilityServiceEnabled()) {
            showPermissionHint("无障碍服务", "需要无障碍服务才能回放触摸操作")
        }
    }

    private fun checkPermissionsBeforeStart() {
        val missingPermissions = mutableListOf<String>()

        // 检查悬浮窗
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            missingPermissions.add("悬浮窗权限")
        }

        // 检查无障碍服务
        if (!isAccessibilityServiceEnabled()) {
            missingPermissions.add("无障碍服务")
        }

        if (missingPermissions.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("缺少权限")
                .setMessage("请先完成以下设置：\n\n${missingPermissions.joinToString("\n")}")
                .setPositiveButton("去设置") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        // 启动悬浮控制服务
        startService(Intent(this, FloatingControlService::class.java))
        Toast.makeText(this, "✅ 控制面板已启动", Toast.LENGTH_SHORT).show()
    }

    /**
     * 启动屏幕录制
     */
    private fun requestScreenRecording() {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_RECORD)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun showPermissionHint(title: String, message: String) {
        Toast.makeText(this, "⚠️ 需要 $title: $message", Toast.LENGTH_LONG).show()
    }

    // ---- UI 辅助方法 ----

    private fun addSectionHeader(layout: LinearLayout, text: String) {
        layout.addView(TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            textStyle = android.graphics.Typeface.BOLD
            setPadding(0, 8, 0, 4)
        })
    }

    private fun addSectionDesc(layout: LinearLayout, text: String) {
        layout.addView(TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#FF888888"))
            setPadding(0, 0, 0, 8)
        })
    }

    private fun createActionButton(text: String): Button {
        return Button(this).apply {
            setText(text)
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#FFCCCCCC"))
            setBackgroundColor(android.graphics.Color.parseColor("#FF2D2D2D"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * resources.displayMetrics.density).toInt()
            ).apply {
                setMargins(0, 4, 0, 4)
            }
            setPadding(16, 0, 16, 0)
            elevation = 2f
        }
    }

    private fun createSpacer(heightDp: Int): View {
        return android.widget.Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (heightDp * resources.displayMetrics.density).toInt()
            )
        }
    }
}
