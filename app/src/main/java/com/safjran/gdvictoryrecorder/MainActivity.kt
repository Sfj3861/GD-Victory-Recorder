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
    private lateinit var fpsSpinner: Spinner
    private lateinit var qualitySpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("recorder", MODE_PRIVATE)
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
            text = "Graba tu run, marca la victoria y recorta el inicio antes de exportar."
            textSize = 15f; setTextColor(0xFFB9BED0.toInt())
        })

        root.addView(label("Fotogramas por segundo (FPS)"))
        fpsSpinner = Spinner(this)
        fpsSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("24 FPS · menor consumo", "30 FPS · recomendado", "60 FPS · más fluido"))
        fpsSpinner.setSelection(when (prefs.getInt("fps", 30)) { 24 -> 0; 60 -> 2; else -> 1 })
        root.addView(fpsSpinner)

        root.addView(label("Calidad de grabación"))
        qualitySpinner = Spinner(this)
        qualitySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Ahorro · 540p aprox. / menor RAM", "Equilibrada · 720p aprox.", "Alta · resolución completa"))
        qualitySpinner.setSelection(prefs.getInt("quality", 1).coerceIn(0, 2))
        root.addView(qualitySpinner)
        root.addView(TextView(this).apply {
            text = "El modo Ahorro reduce resolución y bitrate para consumir menos memoria y espacio. La calidad final depende de la pantalla del dispositivo."
            textSize = 13f; setTextColor(0xFFB9BED0.toInt()); setPadding(0,8,0,8)
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
            text = "\nEl 100 % se marca manualmente. El grabador usa MediaRecorder directamente, sin cargar fotogramas de vídeo en memoria."
            textSize = 13f; setTextColor(0xFFFFD180.toInt())
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun label(value: String) = TextView(this).apply {
        text = value; textSize = 15f; setTextColor(0xFFFFFFFF.toInt()); setPadding(0,18,0,6)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }
    }

    private fun requestCapture() {
        val prefs = getSharedPreferences("recorder", MODE_PRIVATE)
        val fps = when (fpsSpinner.selectedItemPosition) { 0 -> 24; 2 -> 60; else -> 30 }
        val quality = qualitySpinner.selectedItemPosition.coerceIn(0, 2)
        prefs.edit().putInt("fps", fps).putInt("quality", quality).apply()
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("Deprecated API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == Activity.RESULT_OK && data != null) {
            val prefs = getSharedPreferences("recorder", MODE_PRIVATE)
            val i = Intent(this, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START
                putExtra(RecordingService.EXTRA_RESULT_CODE, resultCode)
                putExtra(RecordingService.EXTRA_RESULT_DATA, data)
                putExtra(RecordingService.EXTRA_FPS, prefs.getInt("fps", 30))
                putExtra(RecordingService.EXTRA_QUALITY, prefs.getInt("quality", 1))
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
