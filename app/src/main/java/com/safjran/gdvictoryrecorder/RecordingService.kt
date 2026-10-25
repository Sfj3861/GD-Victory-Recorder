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
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import java.io.File
import java.nio.ByteBuffer

class RecordingService : Service() {
    private var recorder: MediaRecorder? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var output: File? = null
    private var videoOnly: File? = null
    private var audioOnly: File? = null
    private var audioMixer: AudioMixRecorder? = null
    private var stopping = false
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
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(22, notification("Preparando grabación…"),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else startForeground(22, notification("Preparando grabación…"))
        stopping = false
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
            val quality = intent.getIntExtra(EXTRA_QUALITY, 1).coerceIn(0, 2)
            val requestedFps = intent.getIntExtra(EXTRA_FPS, 30)
            val fps = if (requestedFps in setOf(24, 30, 60, 90, 120, 144, 240)) requestedFps else 30
            val maxWidth = when (quality) { 0 -> 960; 1 -> 1280; else -> metrics.widthPixels }
            val maxHeight = when (quality) { 0 -> 540; 1 -> 720; else -> metrics.heightPixels }
            val scale = minOf(1.0, maxWidth.toDouble() / metrics.widthPixels, maxHeight.toDouble() / metrics.heightPixels)
            val width = ((metrics.widthPixels * scale).toInt() / 2) * 2
            val height = ((metrics.heightPixels * scale).toInt() / 2) * 2
            val bitRate = when (quality) { 0 -> 2_500_000; 1 -> 5_000_000; else -> 10_000_000 }
            val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)!!
            if (!dir.exists()) dir.mkdirs()
            val stamp = System.currentTimeMillis()
            output = File(dir, "run_$stamp.mp4")
            videoOnly = File(dir, "run_${stamp}_video.mp4")
            audioOnly = File(dir, "run_${stamp}_audio.mp4")
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            recorder = r
            r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setOutputFile(videoOnly!!.absolutePath)
            r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            r.setVideoSize(width, height)
            r.setVideoFrameRate(fps)
            r.setVideoEncodingBitRate(bitRate)
            r.prepare()
            display = projection!!.createVirtualDisplay("GDVictoryRecorder", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler)
            r.start()

            val prefs = getSharedPreferences("recorder", MODE_PRIVATE)
            val micGain = intent.getFloatExtra(EXTRA_MIC_VOLUME, prefs.getInt("mic_volume", 100) / 100f).coerceIn(0f, 2f)
            val playbackGain = intent.getFloatExtra(EXTRA_PLAYBACK_VOLUME, prefs.getInt("playback_volume", 100) / 100f).coerceIn(0f, 2f)
            try {
                audioMixer = AudioMixRecorder(projection!!, audioOnly!!, micGain, playbackGain).also { it.start() }
            } catch (e: Exception) {
                prefs.edit().putString("last_audio_error", e.message ?: "No se pudo iniciar el audio").apply()
                audioMixer = null
            }
            notifyStatus("Grabando · $fps FPS solicitados · " + when (quality) { 0 -> "Ahorro"; 1 -> "Equilibrada"; else -> "Alta" })
        } catch (e: Exception) {
            getSharedPreferences("recorder", MODE_PRIVATE).edit().putString("last_error", e.message).apply()
            stopRecording(openEditor = false)
        }
    }

    @Synchronized
    private fun stopRecording(openEditor: Boolean) {
        if (stopping) return
        stopping = true
        handler.removeCallbacks(finishTask)
        try { display?.release() } catch (_: Exception) {}; display = null
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.reset(); recorder?.release() } catch (_: Exception) {}; recorder = null
        try { audioMixer?.stop() } catch (_: Exception) {}; audioMixer = null

        val video = videoOnly
        val audio = audioOnly
        val finalOutput = output
        var finalFile: File? = null
        if (video?.exists() == true && video.length() > 0) {
            finalFile = if (audio?.exists() == true && audio.length() > 0 && finalOutput != null) {
                try { muxTracks(video, audio, finalOutput); finalOutput } catch (_: Exception) { video }
            } else video
        }
        try { projection?.unregisterCallback(projectionCallback) } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}; projection = null
        finalFile?.let {
            getSharedPreferences("recorder", MODE_PRIVATE).edit()
                .putString("last_video", it.absolutePath)
                .putBoolean("editor_pending", openEditor && it.exists() && it.length() > 0).apply()
        }
        if (finalFile != video) try { video?.delete() } catch (_: Exception) {}
        try { audio?.delete() } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (openEditor && finalFile?.exists() == true && finalFile.length() > 0) {
            startActivity(Intent(this, EditorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        stopSelf()
    }

    private fun muxTracks(videoFile: File, audioFile: File, destination: File) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)
            val videoTrack = (0 until videoExtractor.trackCount).firstOrNull {
                videoExtractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: throw IllegalStateException("No se encontró la pista de vídeo")
            val audioTrack = (0 until audioExtractor.trackCount).firstOrNull {
                audioExtractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalStateException("No se encontró la pista de audio")
            if (destination.exists()) destination.delete()
            muxer = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outVideo = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
            val outAudio = muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
            muxer.start()
            copyTrack(videoExtractor, videoTrack, muxer, outVideo)
            copyTrack(audioExtractor, audioTrack, muxer, outAudio)
            muxer.stop()
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            videoExtractor.release()
            audioExtractor.release()
        }
    }

    private fun copyTrack(extractor: MediaExtractor, sourceTrack: Int, muxer: MediaMuxer, destinationTrack: Int) {
        extractor.selectTrack(sourceTrack)
        val buffer = ByteBuffer.allocate(1 shl 20)
        val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(destinationTrack, buffer, info)
            extractor.advance()
        }
        extractor.unselectTrack(sourceTrack)
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
        const val EXTRA_MIC_VOLUME = "recording_mic_volume"
        const val EXTRA_PLAYBACK_VOLUME = "recording_playback_volume"
    }
}