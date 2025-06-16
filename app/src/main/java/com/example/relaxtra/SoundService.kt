package com.example.relaxtra // Asegúrate de que este sea tu package name

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class SoundService : Service() {

    private val TAG = "SoundService"

    private val CHANNEL_ID = "RelaxationSoundsChannel"
    private val NOTIFICATION_ID = 1

    companion object {
        const val SOUND_WHITE_NOISE = "white_noise"
        const val SOUND_RAIN = "rain_sound"
        const val SOUND_BIRDS = "bird_sound"
        const val SOUND_WAVES = "wave_sound"
        const val SOUND_ROAD = "road_sound"
    }

    private val activeMediaPlayers = mutableMapOf<String, MediaPlayer>()
    private val soundVolumes = mutableMapOf<String, Int>()
    private val binder = LocalBinder()
    private var isPlaying = false

    // --- Variables del temporizador ---
    private var timer: CountDownTimer? = null
    private var totalTimerDuration: Long = 0L // La duración total seleccionada (ej. 15 min)
    private var timeLeftInMillis: Long = 0L // El tiempo restante cuando se pausa o se detiene
    private var isTimerRunning: Boolean = false // Nuevo estado para el temporizador
    // --- Fin variables del temporizador ---

    inner class LocalBinder : Binder() {
        fun getService(): SoundService = this@SoundService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Servicio creado.")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Comando de inicio recibido.")
        startForeground(NOTIFICATION_ID, createNotification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind: Servicio vinculado.")
        return binder
    }

    fun addSound(soundName: String, resourceId: Int) {
        Log.d(TAG, "addSound: Attempting to add sound $soundName from resource $resourceId")
        if (!activeMediaPlayers.containsKey(soundName)) {
            try {
                val mp = MediaPlayer.create(this, resourceId)
                if (mp == null) {
                    Log.e(TAG, "addSound: MediaPlayer.create returned null for $soundName, resourceId: $resourceId")
                    return
                }
                mp.isLooping = true
                activeMediaPlayers[soundName] = mp
                soundVolumes[soundName] = 50 // Volumen por defecto al añadir
                // No iniciar aquí, se iniciará con playSounds()
                updateNotification()
                Log.d(TAG, "addSound: Sound $soundName added successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "addSound: Error creating MediaPlayer for $soundName, resourceId: $resourceId", e)
            }
        } else {
            Log.d(TAG, "addSound: Sound $soundName already active.")
        }
    }

    fun removeSound(soundName: String) {
        Log.d(TAG, "removeSound: Attempting to remove sound $soundName")
        activeMediaPlayers[soundName]?.let { mp ->
            try {
                mp.stop()
                mp.release()
                activeMediaPlayers.remove(soundName)
                soundVolumes.remove(soundName)
                updateNotification()
                Log.d(TAG, "removeSound: Sound $soundName removed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "removeSound: Error stopping/releasing MediaPlayer for $soundName", e)
            }
        } ?: Log.w(TAG, "removeSound: Sound $soundName not found in active players.")
    }

    fun setVolume(soundName: String, volume: Int) {
        Log.d(TAG, "setVolume: Setting volume for $soundName to $volume")
        activeMediaPlayers[soundName]?.let { mp ->
            val vol = volume / 100f
            mp.setVolume(vol, vol)
            soundVolumes[soundName] = volume
            Log.d(TAG, "setVolume: Volume set for $soundName.")
        } ?: Log.w(TAG, "setVolume: Sound $soundName not found to set volume.")
    }

    fun getVolume(soundName: String): Int {
        return soundVolumes[soundName] ?: 50
    }

    fun isSoundActive(soundName: String): Boolean {
        return activeMediaPlayers.containsKey(soundName)
    }

    // --- Métodos de Control del Temporizador ---
    fun setInitialTimerDuration(durationMillis: Long) {
        totalTimerDuration = durationMillis
        timeLeftInMillis = durationMillis
        Log.d(TAG, "setInitialTimerDuration: Total timer duration set to $totalTimerDuration ms.")
    }

    private fun startCountdownTimer(duration: Long) {
        cancelTimer() // Cancela cualquier temporizador existente
        if (duration <= 0) {
            Log.d(TAG, "startCountdownTimer: Duration is 0 or less, not starting timer.")
            isTimerRunning = false
            return
        }

        timer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                val secondsLeft = millisUntilFinished / 1000
                Log.d(TAG, "Timer remaining: $secondsLeft seconds.")
            }

            override fun onFinish() {
                Log.d(TAG, "Timer finished, calling stopSounds().")
                isTimerRunning = false
                totalTimerDuration = 0L // Reiniciar la duración total
                timeLeftInMillis = 0L   // Reiniciar el tiempo restante
                stopSounds()
            }
        }.start()
        isTimerRunning = true
        Log.d(TAG, "startCountdownTimer: Timer started with $duration ms.")
    }

    fun pauseTimer() {
        if (isTimerRunning) {
            timer?.cancel()
            isTimerRunning = false
            Log.d(TAG, "pauseTimer: Timer paused. Time left: $timeLeftInMillis ms.")
        } else {
            Log.d(TAG, "pauseTimer: Timer not running, nothing to pause.")
        }
    }

    fun resumeTimer() {
        if (!isTimerRunning && timeLeftInMillis > 0) {
            startCountdownTimer(timeLeftInMillis)
            Log.d(TAG, "resumeTimer: Timer resumed from $timeLeftInMillis ms.")
        } else {
            Log.d(TAG, "resumeTimer: Cannot resume timer. isTimerRunning: $isTimerRunning, timeLeftInMillis: $timeLeftInMillis.")
        }
    }

    private fun cancelTimer() {
        if (timer != null) {
            timer?.cancel()
            timer = null
            isTimerRunning = false // Asegúrate de que el estado también se reinicie
            Log.d(TAG, "cancelTimer: Existing timer cancelled.")
        } else {
            Log.d(TAG, "cancelTimer: No timer to cancel.")
        }
    }
    // --- Fin Métodos de Control del Temporizador ---


    fun playSounds() {
        Log.d(TAG, "playSounds: Attempting to play sounds. isPlaying: $isPlaying")
        if (!isPlaying) {
            if (activeMediaPlayers.isEmpty()) {
                Log.w(TAG, "playSounds: No sounds added to play.")
                // Puedes decidir si detener el servicio o mostrar un Toast aquí
                return
            }
            activeMediaPlayers.values.forEach { mp ->
                try {
                    if (!mp.isPlaying) {
                        mp.start()
                        Log.d(TAG, "playSounds: Started playing a sound.")
                    }
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "playSounds: IllegalStateException trying to start MediaPlayer. Is it prepared?", e)
                }
            }
            isPlaying = true
            updateNotification()
            Log.d(TAG, "playSounds: All sounds started.")

            // --- INICIAR O REANUDAR EL TEMPORIZADOR AL REPRODUCIR ---
            if (totalTimerDuration > 0) { // Solo si hay una duración de temporizador establecida
                if (timeLeftInMillis > 0) {
                    resumeTimer() // Reanudar desde donde se quedó
                } else {
                    // Esto es para la primera vez que se reproduce después de seleccionar un tiempo
                    startCountdownTimer(totalTimerDuration)
                }
            }
            // --- FIN INICIO/REANUDACIÓN TEMPORIZADOR ---

        } else {
            Log.d(TAG, "playSounds: Already playing.")
        }
    }

    fun pauseSounds() {
        Log.d(TAG, "pauseSounds: Attempting to pause sounds. isPlaying: $isPlaying")
        if (isPlaying) {
            activeMediaPlayers.values.forEach { mp ->
                try {
                    if (mp.isPlaying) {
                        mp.pause()
                        Log.d(TAG, "pauseSounds: Paused a sound.")
                    }
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "pauseSounds: IllegalStateException trying to pause MediaPlayer.", e)
                }
            }
            isPlaying = false
            updateNotification()
            Log.d(TAG, "pauseSounds: All sounds paused.")

            // --- PAUSAR EL TEMPORIZADOR AL PAUSAR SONIDOS ---
            pauseTimer()
            // --- FIN PAUSA TEMPORIZADOR ---

        } else {
            Log.d(TAG, "pauseSounds: Already paused or not playing.")
        }
    }

    fun stopSounds() {
        Log.d(TAG, "stopSounds: Attempting to stop all sounds.")
        activeMediaPlayers.values.forEach { mp ->
            try {
                mp.stop()
                mp.release()
                Log.d(TAG, "stopSounds: Stopped and released a MediaPlayer.")
            } catch (e: IllegalStateException) {
                Log.e(TAG, "stopSounds: IllegalStateException trying to stop/release MediaPlayer.", e)
            }
        }
        activeMediaPlayers.clear()
        soundVolumes.clear()
        isPlaying = false

        // --- DETENER Y RESETEAR TEMPORIZADOR AL DETENER SONIDOS ---
        cancelTimer()
        totalTimerDuration = 0L // Resetear la duración total
        timeLeftInMillis = 0L   // Resetear el tiempo restante
        Log.d(TAG, "stopSounds: Timer fully reset.")
        // --- FIN DETENER TEMPORIZADOR ---

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            Log.d(TAG, "stopSounds: stopForeground(STOP_FOREGROUND_REMOVE) called.")
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
            Log.d(TAG, "stopSounds: stopForeground(true) called (deprecated).")
        }

        stopSelf()
        Log.d(TAG, "stopSounds: Service stopping itself.")
    }

    fun isPlaying(): Boolean = isPlaying

    // Remover este método, ahora la lógica de inicio/pausa/reanudación está en playSounds/pauseSounds
    /*
    fun setTimer(durationMillis: Long) {
        Log.d(TAG, "setTimer: Received request to set timer for $durationMillis ms.")
        cancelTimer()
        if (durationMillis > 0) {
            timer = object : CountDownTimer(durationMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsLeft = millisUntilFinished / 1000
                    Log.d(TAG, "Timer remaining: $secondsLeft seconds.")
                }

                override fun onFinish() {
                    Log.d(TAG, "Timer finished, calling stopSounds().")
                    stopSounds()
                }
            }.start()
            Log.d(TAG, "setTimer: New timer started for ${durationMillis / 1000} seconds.")
        } else {
            Log.d(TAG, "setTimer: Timer set to 0 (no timer requested or cancelled).")
        }
    }
    */

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: SoundService destroyed.")
        stopSounds() // Asegúrate de liberar los recursos al destruir el servicio
        // cancelTimer() // Ya se llama dentro de stopSounds()
    }

    // --- Notificaciones para Foreground Service ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Sonidos de Relajación",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            Log.d(TAG, "Notification channel created.")
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this,
            0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val title = "Sonidos de Relajación"
        val content = when {
            activeMediaPlayers.isEmpty() -> "Detenido"
            !isPlaying -> "Pausado"
            else -> "Reproduciendo ${activeMediaPlayers.size} sonido(s)"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        Log.d(TAG, "Notification created with content: '$content'")
        return notification
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "Notification updated.")
    }
}