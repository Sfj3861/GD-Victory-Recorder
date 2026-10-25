package com.safjran.gdvictoryrecorder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val captureRequest = 710
    private lateinit var status: TextView
    private lateinit var start: Button
    private lateinit var mark: Button
    private lateinit var stop: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
            setBackgroundColor(0xFF10121B.toInt())
        }
        root.addView(TextView(this).apply {
            text = "GD VICTORY\nRECORDER"; textSize = 28f
            setTextColor(0xFFFFFFFF.toInt()); setPadding(0,0,0,12)
        })
        root.addView(TextView(this).apply {
            text = "Graba tu run, conserva la victoria y recorta el inicio antes de exportar."
            textSize = 15f; setTextColor(0xFFB9BED0.toInt())
        })
        status = TextView(this).apply {
            text = "Estado: listo para grabar"; textSize = 16f
            setTextColor(0xFF00D9A6.toInt()); setPadding(0,24,0,20)
        }
        root.addView(status)
        start = button("●  INICIAR GRABACIÓN") { requestCapture() }; root.addView(start)
        mark = button("🏁  MARCAR VICTORIA (100 %) · 10 s") {
            sendService(RecordingService.ACTION_MARK_VICTORY)
            status.text = "Victoria marcada. Se detendrá en 10 segundos…"; mark.isEnabled = false
        }
        mark.isEnabled = false; root.addView(mark)
        stop = button("■  DETENER Y ABRIR EDITOR") {
            sendService(RecordingService.ACTION_STOP_NOW)
            status.text = "Deteniendo…"; start.isEnabled = true; mark.isEnabled = false; stop.isEnabled = false
        }
        stop.isEnabled = false; root.addView(stop)
        root.addView(TextView(this).apply {
            text = "\nNOTA: el 100 % se marca manualmente en esta versión; la detección visual automática todavía no está integrada."
            textSize = 13f; setTextColor(0xFFFFD180.toInt())
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }
    }
    private fun requestCapture() {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), captureRequest)
    }
    @Deprecated("Deprecated API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == Activity.RESULT_OK && data != null) {
            val i = Intent(this, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START
                putExtra(RecordingService.EXTRA_RESULT_CODE, resultCode)
                putExtra(RecordingService.EXTRA_RESULT_DATA, data)
            }
            startForegroundService(i)
            status.text = "Grabando pantalla…"
            start.isEnabled = false; mark.isEnabled = true; stop.isEnabled = true
        } else status.text = "Permiso de captura cancelado."
    }
    private fun sendService(action: String) {
        startService(Intent(this, RecordingService::class.java).setAction(action))
    }
}
