package com.safjran.gdvictoryrecorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {
    private val captureRequest = 710
    private val audioPermissionRequest = 711
    private lateinit var status: TextView
    private lateinit var start: Button
    private lateinit var mark: Button
    private lateinit var stop: Button
    private lateinit var fpsSpinner: Spinner
    private lateinit var qualitySpinner: Spinner
    private lateinit var micSlider: Slider
    private lateinit var playbackSlider: Slider
    private lateinit var micValue: TextView
    private lateinit var playbackValue: TextView
    private val fpsOptions = listOf(24, 30, 60, 90, 120, 144, 240)
    private val bg = 0xFF10121B.toInt()
    private val surface = 0xFF1B1E2B.toInt()
    private val white = 0xFFF7F7FF.toInt()
    private val muted = 0xFFB9BED0.toInt()
    private val mint = 0xFF00D9A6.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("recorder", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
            setBackgroundColor(bg)
        }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(18))
        }
        hero.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            contentDescription = "Icono de GD Victory Recorder"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(68), dp(68)))
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(TextView(this@MainActivity).apply {
                text = "GD VICTORY"
                textSize = 23f; setTextColor(white); typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@MainActivity).apply {
                text = "RECORDER  •  GAME CAPTURE"
                textSize = 11f; setTextColor(0xFFB69CFF.toInt())
            })
        }
        hero.addView(titleColumn)
        root.addView(hero)
        root.addView(TextView(this).apply {
            text = "Tu partida. Tu victoria. Tu clip."
            textSize = 20f; setTextColor(white); typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(5))
        })
        root.addView(TextView(this).apply {
            text = "Graba la pantalla, mezcla el audio y recorta el comienzo después de la run."
            textSize = 14f; setTextColor(muted); setPadding(0, 0, 0, dp(18))
        })

        val videoCard = card()
        val videoContent = cardContent(videoCard, "VÍDEO", "Ajusta fluidez y tamaño del archivo")
        videoContent.addView(label("Fotogramas por segundo"))
        fpsSpinner = Spinner(this)
        fpsSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            fpsOptions.map { "$it FPS" + when (it) { 30 -> " · recomendado"; 60 -> " · fluido"; 240 -> " · máximo"; else -> "" } })
        fpsSpinner.setSelection(fpsOptions.indexOf(prefs.getInt("fps", 30)).takeIf { it >= 0 } ?: 1)
        videoContent.addView(fpsSpinner, matchWrap())
        videoContent.addView(label("Calidad de vídeo"))
        qualitySpinner = Spinner(this)
        qualitySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Ahorro · 540p aprox.", "Equilibrada · 720p aprox.", "Alta · resolución completa"))
        qualitySpinner.setSelection(prefs.getInt("quality", 1).coerceIn(0, 2))
        videoContent.addView(qualitySpinner, matchWrap())
        videoContent.addView(note("La tasa de FPS real depende del dispositivo y del codificador. 90–240 FPS son valores solicitados, no garantizados."))
        root.addView(videoCard)

        val audioCard = card()
        val audioContent = cardContent(audioCard, "MEZCLADOR DE AUDIO", "Controla cada fuente por separado")
        micSlider = Slider(this).apply {
            valueFrom = 0f; valueTo = 200f; stepSize = 5f
            value = prefs.getInt("mic_volume", 100).coerceIn(0, 200).toFloat()
            contentDescription = "Volumen del micrófono"
        }
        micValue = TextView(this).apply { setTextColor(mint); textSize = 13f }
        audioContent.addView(volumeHeader("Micrófono · audio externo", micValue))
        audioContent.addView(micSlider, matchWrap())
        micSlider.addOnChangeListener { _, value, _ -> micValue.text = value.toInt().toString() + " %" }
        micValue.text = micSlider.value.toInt().toString() + " %"

        playbackSlider = Slider(this).apply {
            valueFrom = 0f; valueTo = 200f; stepSize = 5f
            value = prefs.getInt("playback_volume", 100).coerceIn(0, 200).toFloat()
            contentDescription = "Volumen del audio interno"
        }
        playbackValue = TextView(this).apply { setTextColor(mint); textSize = 13f }
        audioContent.addView(volumeHeader("Audio interno del juego", playbackValue))
        audioContent.addView(playbackSlider, matchWrap())
        playbackSlider.addOnChangeListener { _, value, _ -> playbackValue.text = value.toInt().toString() + " %" }
        playbackValue.text = playbackSlider.value.toInt().toString() + " %"
        audioContent.addView(note("En Android 10 o posterior se captura el audio de aplicaciones que permiten compartirlo. En versiones anteriores solo se puede grabar el micrófono."))
        root.addView(audioCard)

        val controls = card()
        val controlsContent = cardContent(controls, "GRABACIÓN", "Marca el 100 % manualmente")
        status = TextView(this).apply {
            text = "●  LISTO PARA GRABAR"
            textSize = 13f; setTextColor(mint)
            setPadding(0, dp(2), 0, dp(14))
        }
        controlsContent.addView(status)
        start = actionButton("INICIAR GRABACIÓN", 0xFF7C4DFF.toInt()) { requestCapture() }
        controlsContent.addView(start, matchWrap())
        mark = actionButton("MARCAR VICTORIA · 10 s", 0xFF00A881.toInt()) {
            sendService(RecordingService.ACTION_MARK_VICTORY)
            status.text = "VICTORIA MARCADA · quedan 10 segundos"
            mark.isEnabled = false
        }
        mark.isEnabled = false
        controlsContent.addView(mark, matchWrap())
        stop = actionButton("DETENER Y ABRIR EDITOR", 0xFF33384B.toInt()) {
            sendService(RecordingService.ACTION_STOP_NOW)
            status.text = "DETENIENDO GRABACIÓN…"
            start.isEnabled = true; mark.isEnabled = false; stop.isEnabled = false
        }
        stop.isEnabled = false
        controlsContent.addView(stop, matchWrap())
        root.addView(controls)
        root.addView(TextView(this).apply {
            text = "GD Victory Recorder  •  Los clips se guardan en el almacenamiento privado de la app."
            textSize = 11f; setTextColor(0xFF7F859C.toInt()); gravity = Gravity.CENTER
            setPadding(dp(8), dp(18), dp(8), dp(4))
        })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }

    private fun card() = MaterialCardView(this).apply {
        radius = dp(18).toFloat()
        cardElevation = dp(2).toFloat()
        setCardBackgroundColor(surface)
        strokeWidth = dp(1)
        setStrokeColor(0xFF303449.toInt())
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    }

    private fun cardContent(card: MaterialCardView, heading: String, subheading: String): LinearLayout {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(16), dp(17), dp(16))
        }
        content.addView(TextView(this).apply {
            text = heading; textSize = 15f; setTextColor(white)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = subheading; textSize = 12f; setTextColor(muted)
            setPadding(0, dp(3), 0, dp(10))
        })
        card.addView(content)
        return content
    }

    private fun label(textValue: String) = TextView(this).apply {
        text = textValue; textSize = 13f; setTextColor(white)
        setPadding(0, dp(10), 0, dp(5))
    }

    private fun note(value: String) = TextView(this).apply {
        text = value; textSize = 11f; setTextColor(muted)
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun volumeHeader(title: String, value: TextView) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@MainActivity).apply {
            text = title; textSize = 13f; setTextColor(white)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(value)
    }

    private fun actionButton(title: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = title; isAllCaps = false; textSize = 13f
        setTextColor(white); backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        minHeight = dp(48)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun requestCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), audioPermissionRequest)
            return
        }
        val prefs = getSharedPreferences("recorder", MODE_PRIVATE)
        val fps = fpsOptions.getOrElse(fpsSpinner.selectedItemPosition) { 30 }
        val quality = qualitySpinner.selectedItemPosition.coerceIn(0, 2)
        prefs.edit().putInt("fps", fps).putInt("quality", quality)
            .putInt("mic_volume", micSlider.value.toInt())
            .putInt("playback_volume", playbackSlider.value.toInt()).apply()
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), captureRequest)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == audioPermissionRequest) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) requestCapture()
            else status.text = "Se necesita permiso de micrófono para grabar audio."
        }
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
                putExtra(RecordingService.EXTRA_MIC_VOLUME, prefs.getInt("mic_volume", 100) / 100f)
                putExtra(RecordingService.EXTRA_PLAYBACK_VOLUME, prefs.getInt("playback_volume", 100) / 100f)
            }
            startForegroundService(i)
            status.text = "●  GRABANDO PANTALLA Y AUDIO"
            status.setTextColor(mint)
            start.isEnabled = false; mark.isEnabled = true; stop.isEnabled = true
        } else if (requestCode == captureRequest) status.text = "Permiso de captura cancelado."
    }

    private fun sendService(action: String) {
        startService(Intent(this, RecordingService::class.java).setAction(action))
    }
}
