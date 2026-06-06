package com.screenmacro.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import java.io.File

/**
 * 播放叠加层服务
 * 录制完成后，在屏幕上显示一个可拖动的视频回放窗口
 */
class PlaybackOverlayService : Service() {

    companion object {
        private const val TAG = "PlaybackOverlay"
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "playback_channel"

        var isShowing = false
            private set

        var currentVideoPath: String? = null

        const val ACTION_SHOW = "com.screenmacro.SHOW_PLAYBACK"
        const val ACTION_HIDE = "com.screenmacro.HIDE_PLAYBACK"
        const val EXTRA_VIDEO_PATH = "video_path"
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playbackActive = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH)
                if (videoPath != null) {
                    currentVideoPath = videoPath
                    showOverlay(videoPath)
                }
            }
            ACTION_HIDE -> hideOverlay()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay(videoPath: String) {
        if (isShowing) hideOverlay()

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val density = resources.displayMetrics.density

        // 创建小窗口（1/4 屏幕宽度）
        val width = (resources.displayMetrics.widthPixels * 0.4).toInt()
        val height = (width * 9 / 16)

        val frameLayout = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#DD000000"))
            setOnClickListener { togglePlayback() }
        }

        val videoView = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setVideoPath(videoPath)
            setOnPreparedListener { mp ->
                mediaPlayer = mp
                mp.setLooping(true)
                mp.start()
                this.playbackActive = true
            }
            setOnErrorListener { _, _, _ ->
                Toast.makeText(this@PlaybackOverlayService, "视频播放失败", Toast.LENGTH_SHORT).show()
                true
            }
        }
        frameLayout.addView(videoView)

        // 控制栏（顶部）
        val controlsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(8, 4, 8, 4)
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
        }

        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(4, 4, 4, 4)
            setOnClickListener { hideOverlay() }
        }
        controlsLayout.addView(closeBtn)

        val playPauseBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(4, 4, 4, 4)
            setOnClickListener { togglePlayback() }
        }
        controlsLayout.addView(playPauseBtn)

        frameLayout.addView(controlsLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (36 * density).toInt()
        ).apply {
            gravity = Gravity.TOP
        })

        // 参数
        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
        } else {
            WindowManager.LayoutParams(
                width, height,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
        }.apply {
            gravity = Gravity.START or Gravity.TOP
            x = 20
            y = (resources.displayMetrics.heightPixels * 0.15).toInt()
        }

        // 拖拽
        frameLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    params.x = (event.rawX - width / 2).toInt()
                    params.y = (event.rawY - 50).toInt()
                    windowManager.updateViewLayout(frameLayout, params)
                }
            }
            false
        }

        try {
            windowManager.addView(frameLayout, params)
            overlayView = frameLayout
            isShowing = true

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification("回放窗口已打开"))

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to show overlay", e)
        }
    }

    private fun hideOverlay() {
        mediaPlayer?.apply {
            if (playbackActive) stop()
            release()
        }
        mediaPlayer = null
        playbackActive = false

        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
        isShowing = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun togglePlayback() {
        mediaPlayer?.let { mp ->
            if (mp.playbackActive) {
                mp.pause()
                playbackActive = false
            } else {
                mp.start()
                playbackActive = true
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "回放控制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "回放叠加层通知"
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
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

}
