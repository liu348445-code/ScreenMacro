package com.screenmacro.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 屏幕录制后台服务
 * 使用 MediaProjection 录制屏幕为 MP4 视频
 */
class ScreenRecordService : Service() {

    companion object {
        private const val TAG = "ScreenRecordService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_record_channel"

        /** 录制状态 */
        var isRecording = false
            private set

        /** 录制状态回调 */
        var onStateChanged: ((Boolean) -> Unit)? = null
        var onError: ((String) -> Unit)? = null

        private var mediaProjection: MediaProjection? = null
        private var mediaRecorder: MediaRecorder? = null
        private var outputFilePath: String? = null

        /**
         * 初始化 MediaProjection（从 MainActivity 的结果获取）
         */
        fun setupProjection(context: Context, data: Intent, resultCode: Int) {
            val mgr = context.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mgr.getMediaProjection(resultCode, data)
        }

        /**
         * 获取最新录制的视频路径
         */
        fun getLastVideoPath(): String? = outputFilePath
    }

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    /**
     * 开始录制
     */
    private fun startRecording() {
        if (isRecording) return

        val projection = mediaProjection ?: run {
            onError?.invoke("MediaProjection 未初始化")
            return
        }

        try {
            val displayMetrics = DisplayMetrics()
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.defaultDisplay.getRealMetrics(displayMetrics)

            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val density = displayMetrics.densityDpi

            // 创建输出文件
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "ScreenRec_$timestamp.mp4"

            val outputDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES
            )
            if (!outputDir.exists()) outputDir.mkdirs()
            val outputFile = File(outputDir, fileName)
            outputFilePath = outputFile.absolutePath

            // 配置 MediaRecorder
            val recorder = MediaRecorder()
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncodingBitRate(12_000_000)
                setAudioEncodingBitRate(128_000)
                setVideoFrameRate(30)
                setVideoSize(width, height)
                setOutputFile(outputFile.absolutePath)
                prepare()
            }

            val surface = recorder.surface
            projection.createCapturedContentIntent(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
            ).also { displayIntent ->
                projection.createVirtualDisplay(
                    "ScreenMacroRecord",
                    width, height, density,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface, null, null
                )
            }

            mediaRecorder = recorder
            recorder.start()
            isRecording = true

            startForeground(NOTIFICATION_ID, createNotification("正在录制屏幕..."))
            onStateChanged?.invoke(true)

            Log.d(TAG, "Recording started: ${outputFile.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            onError?.invoke("录制启动失败: ${e.message}")
        }
    }

    /**
     * 停止录制
     */
    private fun stopRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }

        isRecording = false
        onStateChanged?.invoke(false)
        stopForeground(STOP_FOREGROUND_REMOVE)

        // 保存到媒体库
        outputFilePath?.let { path ->
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenMacro")
                put(MediaStore.Video.Media.TITLE, File(path).name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            }
            try {
                contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add to media store", e)
            }
        }

        Log.d(TAG, "Recording stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "屏幕录制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "屏幕录制服务通知"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
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
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.screenmacro.START_RECORDING"
        const val ACTION_STOP = "com.screenmacro.STOP_RECORDING"
    }
}
