package com.example.relaxtra // Asegúrate de que este sea tu package name

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var whiteNoiseSeekBar: SeekBar
    private lateinit var rainSoundSeekBar: SeekBar
    private lateinit var playPauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var timerSpinner: Spinner
    private lateinit var soundSelectionSpinner: Spinner
    private lateinit var backgroundGifImageView: ImageView

    private var soundService: SoundService? = null
    private var isBound = false

    // Conexión al servicio
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected: Servicio vinculado.")
            val binder = service as SoundService.LocalBinder
            soundService = binder.getService()
            isBound = true
            updateUIBasedOnServiceState()
            val selectedDuration = getDurationFromSpinnerPosition(timerSpinner.selectedItemPosition)
            soundService?.setInitialTimerDuration(selectedDuration)
            Log.d(TAG, "onServiceConnected: Sent initial timer duration: $selectedDuration ms.")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected: Servicio desvinculado.")
            isBound = false
            soundService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Actividad creada.")
        setContentView(R.layout.activity_main)

        // Inicializar vistas
        whiteNoiseSeekBar = findViewById(R.id.whiteNoiseSeekBar)
        rainSoundSeekBar = findViewById(R.id.rainSoundSeekBar)
        playPauseButton = findViewById(R.id.playPauseButton)
        stopButton = findViewById(R.id.stopButton)
        timerSpinner = findViewById(R.id.timerSpinner)
        backgroundGifImageView = findViewById(R.id.backgroundGifImageView)

        soundSelectionSpinner = findViewById(R.id.soundSelectionSpinner)
        setupSoundSelectionSpinner()

        Log.d(TAG, "onCreate: Vistas inicializadas.")

        // --- Listener para SeekBar de Ruido Blanco (controla activación y volumen) ---
        whiteNoiseSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && isBound) {
                    if (progress > 0) {
                        Log.d(TAG, "whiteNoiseSeekBar: Progress > 0. Adding/Setting volume for White Noise to $progress")
                        soundService?.addSound(SoundService.SOUND_WHITE_NOISE, R.raw.birs_chirping)
                        soundService?.setVolume(SoundService.SOUND_WHITE_NOISE, progress)
                        soundService?.playSounds()
                        // ¡CORRECCIÓN CLAVE AQUÍ!
                        // Si el servicio no está reproduciendo globalmente, iniciar la reproducción
                        if (soundService?.isPlaying() == false) {

                            playPauseButton.text = "Pausa" // Actualizar el texto del botón
                            Log.d(TAG, "whiteNoiseSeekBar: Triggered global play because service was paused.")
                        }
                    } else { // progress == 0
                        Log.d(TAG, "whiteNoiseSeekBar: Progress is 0. Removing White Noise.")
                        soundService?.removeSound(SoundService.SOUND_WHITE_NOISE)
                        // Si no quedan sonidos activos, el servicio se detendrá, y el botón se actualizará
                        if (soundService?.isPlaying() == false) { // Si al remover, ya no queda nada sonando
                            playPauseButton.text = "Reproducir"
                        }
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { Log.d(TAG, "whiteNoiseSeekBar tracking started.") }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { Log.d(TAG, "whiteNoiseSeekBar tracking stopped.") }
        })

        // --- Listener para SeekBar de Lluvia (controla activación y volumen) ---
        rainSoundSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && isBound) {
                    if (progress > 0) {
                        Log.d(TAG, "rainSoundSeekBar: Progress > 0. Adding/Setting volume for Rain Sound to $progress")
                        soundService?.addSound(SoundService.SOUND_RAIN, R.raw.rain_sound)
                        soundService?.setVolume(SoundService.SOUND_RAIN, progress)
                        soundService?.playSounds()
                        // ¡CORRECCIÓN CLAVE AQUÍ!9
                        if (soundService?.isPlaying() == false) {

                            playPauseButton.text = "Pausa"
                            Log.d(TAG, "rainSoundSeekBar: Triggered global play because service was paused.")
                        }
                    } else { // progress == 0
                        Log.d(TAG, "rainSoundSeekBar: Progress is 0. Removing Rain Sound.")
                        soundService?.removeSound(SoundService.SOUND_RAIN)
                        if (soundService?.isPlaying() == false) {
                            playPauseButton.text = "Reproducir"
                        }
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { Log.d(TAG, "rainSoundSeekBar tracking started.") }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { Log.d(TAG, "rainSoundSeekBar tracking stopped.") }
        })

        // Botón Play/Pause
        playPauseButton.setOnClickListener {
            Log.d(TAG, "Play/Pause button clicked. isBound: $isBound")
            if (isBound) {
                if (soundService?.isPlaying() == true) {
                    soundService?.pauseSounds()
                    playPauseButton.text = "Reproducir"
                    Log.d(TAG, "Sounds paused.")
                } else {
                    val hasActiveSounds = whiteNoiseSeekBar.progress > 0 || rainSoundSeekBar.progress > 0
                    val hasTimer = getDurationFromSpinnerPosition(timerSpinner.selectedItemPosition) > 0

                    if (hasActiveSounds || hasTimer) {
                        soundService?.playSounds()
                        playPauseButton.text = "Pausa"
                        Log.d(TAG, "Sounds/Timer started.")
                    } else {
                        Toast.makeText(this, "Ajusta el volumen de un sonido o selecciona un temporizador para iniciar.", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Play clicked but no sounds active (seekbars are at 0) and no timer set.")
                    }
                }
            } else {
                Toast.makeText(this, "Servicio no conectado, intenta de nuevo.", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Play/Pause clicked but service not bound.")
            }
        }

        // Botón Detener
        stopButton.setOnClickListener {
            Log.d(TAG, "Stop button clicked. isBound: $isBound")
            if (isBound) {
                soundService?.stopSounds()
                playPauseButton.text = "Reproducir"
                whiteNoiseSeekBar.progress = 0
                rainSoundSeekBar.progress = 0
                timerSpinner.setSelection(0)
                Log.d(TAG, "Sounds stopped and UI reset.")
            } else {
                Log.e(TAG, "Stop clicked but service not bound.")
            }
        }

        // Spinner del Temporizador
        timerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val durationMillis = getDurationFromSpinnerPosition(position)
                Log.d(TAG, "Timer selected: ${parent?.getItemAtPosition(position)} (duration: $durationMillis ms)")
                if (isBound) {
                    soundService?.setInitialTimerDuration(durationMillis)
                    Log.d(TAG, "Sent timer duration to service: $durationMillis ms.")
                } else {
                    Log.w(TAG, "Timer selected but service not bound.")
                    Toast.makeText(this@MainActivity, "Servicio de sonido no conectado. El temporizador no se configuró.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { Log.d(TAG, "Timer nothing selected.") }
        }
    } // Fin de onCreate

    private fun getDurationFromSpinnerPosition(position: Int): Long {
        return when (position) {
            0 -> 0L // Sin temporizador
            1 -> 15 * 60 * 1000L // 15 minutos
            2 -> 30 * 60 * 1000L // 30 minutos
            3 -> 45 * 60 * 1000L // 45 minutos
            4 -> 60 * 60 * 1000L // 1 hora
            else -> 0L
        }
    }

    private fun setupSoundSelectionSpinner() {
        val soundOptions = arrayOf("Seleccionar Fondo", "Lluvia", "Bosque", "Olas", "Carretera")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, soundOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        soundSelectionSpinner.adapter = adapter

        soundSelectionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> {
                        Glide.with(this@MainActivity)
                            .load(android.R.color.black)
                            .into(backgroundGifImageView)
                        Log.d(TAG, "Background set to default (black).")
                    }
                    1 -> {
                        Glide.with(this@MainActivity)
                            .asGif()
                            .load(R.drawable.rain_background)
                            .into(backgroundGifImageView)
                        Log.d(TAG, "Background set to rain_background.gif")
                    }
                    2 -> {
                        Glide.with(this@MainActivity)
                            .asGif()
                            .load(R.drawable.birds_background)
                            .into(backgroundGifImageView)
                        Log.d(TAG, "Background set to birds_background.gif")
                    }
                    3 -> {
                        Glide.with(this@MainActivity)
                            .asGif()
                            .load(R.drawable.waves_background)
                            .into(backgroundGifImageView)
                        Log.d(TAG, "Background set to waves_background.gif")
                    }
                    4 -> {
                        Glide.with(this@MainActivity)
                            .asGif()
                            .load(R.drawable.road_background)
                            .into(backgroundGifImageView)
                        Log.d(TAG, "Background set to road_background.gif")
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                Log.d(TAG, "No background sound selected.")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Iniciando servicio y vinculándolo.")
        val intent = Intent(this, SoundService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: Desvinculando servicio.")
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Actividad destruida.")
    }

    private fun updateUIBasedOnServiceState() {
        soundService?.let { service ->
            Log.d(TAG, "updateUIBasedOnServiceState: Actualizando UI.")

            whiteNoiseSeekBar.progress = service.getVolume(SoundService.SOUND_WHITE_NOISE)
            rainSoundSeekBar.progress = service.getVolume(SoundService.SOUND_RAIN)

            if (service.isPlaying()) {
                playPauseButton.text = "Pausa"
            } else {
                playPauseButton.text = "Reproducir"
            }
        } ?: run {
            Log.w(TAG, "updateUIBasedOnServiceState: Service is null, cannot update UI.")
            whiteNoiseSeekBar.progress = 0
            rainSoundSeekBar.progress = 0
            playPauseButton.text = "Reproducir"
        }
    }
}