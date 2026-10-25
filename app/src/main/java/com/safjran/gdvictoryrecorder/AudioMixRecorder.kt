package com.safjran.gdvictoryrecorder

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Captures microphone and (Android 10+) capturable app playback, mixes the two PCM
 * streams with independent gains, and encodes a single AAC track for later muxing.
 */
class AudioMixRecorder(
    private val projection: MediaProjection,
    private val output: File,
    private val micGain: Float,
    private val playbackGain: Float
) {
    private val running = AtomicBoolean(false)
    private var mic: AudioRecord? = null
    private var playback: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var worker: Thread? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var samplesWritten = 0L

    fun start() {
        val sampleRate = 44100
        val channelMask = AudioFormat.CHANNEL_IN_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBytes = max(8192, AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding))
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate).setChannelMask(channelMask).setEncoding(encoding).build()

        mic = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(format).setBufferSizeInBytes(minBytes * 2).build()
        if (mic?.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("No se pudo iniciar el micrófono")

        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
                playback = AudioRecord.Builder().setAudioFormat(format)
                    .setAudioPlaybackCaptureConfig(config)
                    .setBufferSizeInBytes(minBytes * 2).build()
                if (playback?.state != AudioRecord.STATE_INITIALIZED) {
                    playback?.release()
                    playback = null
                }
            } catch (_: Exception) {
                playback?.release()
                playback = null
            }
        }

        val codec = MediaCodec.createEncoderByType("audio/mp4a-latm")
        val codecFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfoProfile.AAC_LC)
            setInteger(MediaFormat.KEY_BIT_RATE, 160000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        codec.configure(codecFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        encoder = codec
        if (output.exists()) output.delete()
        muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        mic!!.startRecording()
        playback?.startRecording()
        running.set(true)
        worker = Thread({ captureLoop(minBytes / 2) }, "GDVR-AudioCapture").also { it.start() }
    }

    fun stop() {
        running.set(false)
        try { worker?.join(2500) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        worker = null
        try { mic?.stop() } catch (_: Exception) {}
        try { playback?.stop() } catch (_: Exception) {}
        try { mic?.release() } catch (_: Exception) {}
        try { playback?.release() } catch (_: Exception) {}
        mic = null
        playback = null
        try { encoder?.stop() } catch (_: Exception) {}
        try { encoder?.release() } catch (_: Exception) {}
        encoder = null
        try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
        try { muxer?.release() } catch (_: Exception) {}
        muxer = null
    }

    private fun captureLoop(bufferSamples: Int) {
        val micData = ShortArray(bufferSamples)
        val playbackData = ShortArray(bufferSamples)
        val mixed = ShortArray(bufferSamples)
        val info = MediaCodec.BufferInfo()
        val codec = encoder ?: return
        try {
            while (running.get()) {
                val countMic = mic?.read(micData, 0, micData.size, AudioRecord.READ_NON_BLOCKING) ?: 0
                val countPlayback = playback?.read(playbackData, 0, playbackData.size, AudioRecord.READ_NON_BLOCKING) ?: 0
                val count = max(0, max(countMic, countPlayback))
                if (count == 0) {
                    drainEncoder(codec, info, false)
                    try { Thread.sleep(4) } catch (_: InterruptedException) { break }
                    continue
                }
                for (i in 0 until count) {
                    val m = if (i < countMic) micData[i].toFloat() * micGain else 0f
                    val p = if (i < countPlayback) playbackData[i].toFloat() * playbackGain else 0f
                    mixed[i] = (m + p).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                val inputIndex = codec.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val input = codec.getInputBuffer(inputIndex)
                    if (input != null) {
                        input.clear()
                        input.order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until count) input.putShort(mixed[i])
                        val pts = samplesWritten * 1_000_000L / 44100L
                        codec.queueInputBuffer(inputIndex, 0, count * 2, pts, 0)
                        samplesWritten += count / 2
                    } else codec.queueInputBuffer(inputIndex, 0, 0, 0, 0)
                }
                drainEncoder(codec, info, false)
            }
            val inputIndex = codec.dequeueInputBuffer(10000)
            if (inputIndex >= 0) codec.queueInputBuffer(inputIndex, 0, 0, samplesWritten * 1_000_000L / 44100L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drainEncoder(codec, info, true)
        } catch (_: Exception) {
            // Keep the screen recording usable if an OEM audio source stops unexpectedly.
        }
    }

    private fun drainEncoder(codec: MediaCodec, info: MediaCodec.BufferInfo, end: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (end) 10000 else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return else return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) throw IllegalStateException("Formato de audio cambiado")
                    trackIndex = muxer!!.addTrack(codec.outputFormat)
                    muxer!!.start()
                    muxerStarted = true
                }
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && info.size > 0 && muxerStarted) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer!!.writeSampleData(trackIndex, buffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private object MediaCodecInfoProfile {
        const val AAC_LC = 2
    }
}
