package com.safjran.gdvictoryrecorder

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import java.io.File

class RecordingService : Service() {
    private var recorder: MediaRecorder? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var output: File? = null
    private val handler = Handler(Looper.getMainLooper())
    private val finishTask = Runnable { stopRecording(openEditor = true) }
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stopRecording(openEditor = true) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_MARK_VICTORY -> {
                handler.removeCallbacks(finishTask)
                handler.postDelayed(finishTask, 10_000)
                notifyStatus("Victoria marcada: quedan 10 segundos")
            }
            ACTION_STOP_NOW -> stopRecording(openEditor = true)
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {
        createChannel()
        startForeground(22, notification("Preparando grabación…"))
        try {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            @Suppress("DEPRECATION")
            val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else intent.getParcelableExtra(EXTRA_RESULT_DATA)
            if (data == null) throw IllegalStateException("Falta el permiso de captura")
            projection = mgr.getMediaProjection(resultCode, data)
            projection!!.registerCallback(projectionCallback, handler)

            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            // Downscale before encoding to reduce capture buffers on lower-memory devices.
            val quality = intent.getIntExtra(EXTRA_QUALITY, 1).coerceIn(0, 2)
            val fps = intent.getIntExtra(EXTRA_FPS, 30).let { if (it == 24 || it == 60) it else 30 }
            val maxWidth = when (quality) { 0 -> 960; 1 -> 1280; else -> metrics.widthPixels }
            val maxHeight = when (quality) { 0 -> 540; 1 -> 720; else -> metrics.heightPixels }
            val scale = minOf(1.0, maxWidth.toDouble() / metrics.widthPixels, maxHeight.toDouble() / metrics.heightPixels)
            val width = ((metrics.widthPixels * scale).toInt() / 2) * 2
            val height = ((metrics.heightPixels * scale).toInt() / 2) * 2
            val bitRate = when (quality) { 0 -> 2_500_000; 1 -> 5_000_000; else -> 10_000_000 }
            val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)!!
            if (!dir.exists()) dir.mkdirs()
            output = File(dir, "run_${System.currentTimeMillis()}.mp4")
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            recorder = r
            r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setOutputFile(output!!.absolutePath)
            r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            r.setVideoSize(width, height)
            r.setVideoFrameRate(fps)
            r.setVideoEncodingBitRate(bitRate)
            r.prepare()
            display = projection!!.createVirtualDisplay("GDVictoryRecorder", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler)
            r.start()
            notifyStatus("Grabando · " + fps + " FPS · " + when (quality) { 0 -> "Ahorro"; 1 -> "Equilibrada"; else -> "Alta" })
        } catch (e: Exception) {
            getSharedPreferences("recorder", MODE_PRIVATE).edit().putString("last_error", e.message).apply()
            stopRecording(openEditor = false)
        }
    }

    private fun stopRecording(openEditor: Boolean) {
        handler.removeCallbacks(finishTask)
        try { display?.release() } catch (_: Exception) {}; display = null
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.reset(); recorder?.release() } catch (_: Exception) {}; recorder = null
        try { projection?.unregisterCallback(projectionCallback) } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}; projection = null
        output?.let {
            getSharedPreferences("recorder", MODE_PRIVATE).edit()
                .putString("last_video", it.absolutePath)
                .putBoolean("editor_pending", openEditor && it.exists() && it.length() > 0).apply()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (openEditor && output?.exists() == true && output!!.length() > 0) {
            startActivity(Intent(this, EditorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Grabación de pantalla", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    private fun notification(text: String): Notification =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
            .setContentTitle("GD Victory Recorder").setContentText(text)
            .setSmallIcon(android.R.drawable.presence_video_online).build()
        else @Suppress("DEPRECATION") Notification.Builder(this)
            .setContentTitle("GD Victory Recorder").setContentText(text)
            .setSmallIcon(android.R.drawable.presence_video_online).build()
    private fun notifyStatus(text: String) {
        getSystemService(NotificationManager::class.java).notify(22, notification(text))
    }

    companion object {
        const val CHANNEL = "gd_victory_recorder"
        const val ACTION_START = "com.safjran.gdvictoryrecorder.START"
        const val ACTION_MARK_VICTORY = "com.safjran.gdvictoryrecorder.MARK_VICTORY"
        const val ACTION_STOP_NOW = "com.safjran.gdvictoryrecorder.STOP_NOW"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_FPS = "recording_fps"
        const val EXTRA_QUALITY = "recording_quality"
    }
}
