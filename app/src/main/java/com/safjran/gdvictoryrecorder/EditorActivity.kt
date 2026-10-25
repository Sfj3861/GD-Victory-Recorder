package com.safjran.gdvictoryrecorder

import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

@OptIn(UnstableApi::class)
class EditorActivity : AppCompatActivity() {
    private var durationMs = 0L
    private var startMs = 0L
    private lateinit var info: TextView
    private lateinit var seek: SeekBar
    private lateinit var export: Button
    private var input: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        input = getSharedPreferences("recorder", MODE_PRIVATE).getString("last_video", null)?.let(::File)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24,28,24,24); setBackgroundColor(0xFF10121B.toInt())
        }
        root.addView(TextView(this).apply {
            text = "EDITOR DE INICIO OFICIAL"; textSize = 24f; setTextColor(0xFFFFFFFF.toInt())
        })
        info = TextView(this).apply {
            text = "Selecciona el inicio del vídeo antes de exportar."
            textSize = 15f; setTextColor(0xFFB9BED0.toInt()); setPadding(0,18,0,18)
        }
        root.addView(info)
        seek = SeekBar(this).apply { max = 1000; progress = 0 }; root.addView(seek)
        root.addView(TextView(this).apply {
            text = "Desliza para elegir el punto de inicio. La exportación requiere una grabación válida."
            setTextColor(0xFFB9BED0.toInt())
        })
        export = Button(this).apply {
            text = "EXPORTAR VÍDEO RECORTADO"; isEnabled = false; setOnClickListener { exportTrimmed() }
        }; root.addView(export)
        root.addView(Button(this).apply { text = "Cerrar"; setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { addView(root) })
        val source = input
        if (source == null || !source.exists() || source.length() == 0L) {
            info.text = "No se encontró una grabación válida. Vuelve a grabar."; return
        }
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(source.absolutePath)
            durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            retriever.release()
            if (durationMs <= 1000L) throw IllegalStateException("El vídeo es demasiado corto")
            seek.max = durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            info.text = "Duración: ${durationMs / 1000}s · inicio seleccionado: 0s"
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                    startMs = progress.toLong()
                    info.text = "Duración: ${durationMs / 1000}s · inicio seleccionado: ${startMs / 1000}s"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            export.isEnabled = true
        } catch (e: Exception) { info.text = "No se pudo leer el vídeo: ${e.message}" }
    }

    private fun exportTrimmed() {
        val source = input ?: return
        val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)!!
        val dest = File(dir, "official_${System.currentTimeMillis()}.mp4")
        export.isEnabled = false; info.text = "Exportando… no cierres la aplicación."
        val item = MediaItem.Builder().setUri(Uri.fromFile(source)).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).build()
        ).build()
        val edited = EditedMediaItem.Builder(item).build()
        Transformer.Builder(this).setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    info.text = "Exportación completada: ${dest.name}\nGuardado en Movies de la aplicación."
                    export.isEnabled = true
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    info.text = "Error al exportar: ${exportException.message}"; export.isEnabled = true
                }
            }).build().start(edited, dest.absolutePath)
    }
}
