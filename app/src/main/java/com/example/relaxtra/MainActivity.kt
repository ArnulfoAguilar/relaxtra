package com.example.relaxtra

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var whiteNoiseCheckBox: CheckBox
    private lateinit var whiteNoiseSeekBar: SeekBar
    private lateinit var rainSoundCheckBox: CheckBox
    private lateinit var rainSoundSeekBar: SeekBar
    private lateinit var playPauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var timerSpinner: Spinner

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
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected: Servicio desvinculado.")
            isBound = false
            soundService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity creada")
        setContentView(R.layout.activity_main)

        // Inicializar vistas
        whiteNoiseCheckBox = findViewById(R.id.whiteNoiseCheckBox)
        whiteNoiseSeekBar = findViewById(R.id.whiteNoiseSeekBar)
        rainSoundCheckBox = findViewById(R.id.rainSoundCheckBox)
        rainSoundSeekBar = findViewById(R.id.rainSoundSeekBar)
        playPauseButton = findViewById(R.id.playPauseButton)
        stopButton = findViewById(R.id.stopButton)
        timerSpinner = findViewById(R.id.timerSpinner)
        Log.d(TAG, "onCreate: Vistas inicializadas.")

        // Listener para CheckBox de Ruido Blanco
        whiteNoiseCheckBox.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "whiteNoiseCheckBox checked: $isChecked")
            if (isBound) {
                if (isChecked) {
                    soundService?.addSound(SoundService.SOUND_WHITE_NOISE, R.raw.birs_chirping) //ToDo R.raw.birs_chirping
                    soundService?.setVolume(SoundService.SOUND_WHITE_NOISE, whiteNoiseSeekBar.progress)
                } else {
                    soundService?.removeSound(SoundService.SOUND_WHITE_NOISE)
                }
            }else{
                Log.w(TAG, "whiteNoiseCheckBox changed but service not bound.")
            }
        }

        // Listener para SeekBar de Ruido Blanco
        whiteNoiseSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (isBound && whiteNoiseCheckBox.isChecked) {
                    Log.d(TAG, "whiteNoiseSeekBar progress: $progress")
                    soundService?.setVolume(SoundService.SOUND_WHITE_NOISE, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {Log.d(TAG, "whiteNoiseSeekBar tracking started.")}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {Log.d(TAG, "whiteNoiseSeekBar tracking stopped.")}
        })

        // Listener para CheckBox de Lluvia
        rainSoundCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isBound) {
                if (isChecked) {
                    Log.d(TAG, "rainSoundCheckBox checked: $isChecked")
                    soundService?.addSound(SoundService.SOUND_RAIN, R.raw.rain_sound)//ToDo R.raw.rain_sound
                    soundService?.setVolume(SoundService.SOUND_RAIN, rainSoundSeekBar.progress)
                } else {
                    soundService?.removeSound(SoundService.SOUND_RAIN)
                }
            }
        }

        // Listener para SeekBar de Lluvia
        rainSoundSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (isBound && rainSoundCheckBox.isChecked) {
                    Log.d(TAG, "rainSoundSeekBar progress: $progress")
                    soundService?.setVolume(SoundService.SOUND_RAIN, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Botón Play/Pause
        playPauseButton.setOnClickListener {
            if (isBound) {
                if (soundService?.isPlaying() == true) {
                    soundService?.pauseSounds()
                    playPauseButton.text = "Reproducir"
                } else {
                    soundService?.playSounds()
                    playPauseButton.text = "Pausa"
                }
            } else {
                Toast.makeText(this, "Servicio no conectado, intenta de nuevo.", Toast.LENGTH_SHORT).show()
            }
        }

        // Botón Detener
        stopButton.setOnClickListener {
            if (isBound) {
                soundService?.stopSounds()
                playPauseButton.text = "Reproducir"
                // Resetear checkboxes y seekbars
                whiteNoiseCheckBox.isChecked = false
                rainSoundCheckBox.isChecked = false
                whiteNoiseSeekBar.progress = 50
                rainSoundSeekBar.progress = 50
            }
        }

        // Spinner del Temporizador
        timerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val durationMillis = when (position) {
                    0 -> 0L // Sin temporizador
                    1 -> 15 * 60 * 1000L // 15 minutos
                    2 -> 30 * 60 * 1000L // 30 minutos
                    3 -> 45 * 60 * 1000L // 45 minutos
                    4 -> 60 * 60 * 1000L // 1 hora
                    else -> 0L
                }
                if (isBound) {
                    soundService?.setTimer(durationMillis)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Iniciando servicio y vinculándolo.")
        // Iniciar y vincular el servicio cuando la actividad se inicia
        val intent = Intent(this, SoundService::class.java)
        startService(intent) // Inicia el servicio
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE) // Vincula al servicio
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: Desvinculando servicio.")
        // Desvincular el servicio cuando la actividad se detiene
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
            // Actualiza el estado de los checkboxes y seekbars basado en lo que el servicio está reproduciendo
            whiteNoiseCheckBox.isChecked = service.isSoundActive(SoundService.SOUND_WHITE_NOISE)
            rainSoundCheckBox.isChecked = service.isSoundActive(SoundService.SOUND_RAIN)

            // Asumiendo que SoundService.getVolume ahora devuelve el valor correcto
            whiteNoiseSeekBar.progress = service.getVolume(SoundService.SOUND_WHITE_NOISE)
            rainSoundSeekBar.progress = service.getVolume(SoundService.SOUND_RAIN)

            if (service.isPlaying()) {
                playPauseButton.text = "Pausa"
            } else {
                playPauseButton.text = "Reproducir"
            }
        }
            ?: run {
                Log.w(TAG, "updateUIBasedOnServiceState: Service is null, cannot update UI.")
            }
    }
}