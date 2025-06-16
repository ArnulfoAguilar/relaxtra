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
import android.widget.Toast // Importación para el Toast en el servicio
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
    private var isPlaying = false // Este es el estado global de reproducción/pausa

    // --- Variables del temporizador ---
    private var timer: CountDownTimer? = null
    private var totalTimerDuration: Long = 0L
    private var timeLeftInMillis: Long = 0L
    private var isTimerRunning: Boolean = false
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
                    Log.e(
                        TAG,
                        "addSound: MediaPlayer.create returned null for $soundName, resourceId: $resourceId"
                    )
                    return
                }
                mp.isLooping = true
                activeMediaPlayers[soundName] = mp
                // REMOVIDO: la lógica de mp.start() se manejará en playSounds()
                updateNotification()
                Log.d(TAG, "addSound: Sound $soundName added successfully.")
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "addSound: Error creating MediaPlayer for $soundName, resourceId: $resourceId",
                    e
                )
            }
        } else {
            Log.d(TAG, "addSound: Sound $soundName already active.")
            // No necesitamos reiniciar el MediaPlayer aquí. playSounds() se encargará.
        }
    }

    fun removeSound(soundName: String) {
        Log.d(TAG, "removeSound: Attempting to remove sound $soundName")
        activeMediaPlayers[soundName]?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
                activeMediaPlayers.remove(soundName)
                soundVolumes.remove(soundName)
                updateNotification()
                Log.d(TAG, "removeSound: Sound $soundName removed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "removeSound: Error stopping/releasing MediaPlayer for $soundName", e)
            }
        } ?: Log.w(TAG, "removeSound: Sound $soundName not found in active players.")

        if (activeMediaPlayers.isEmpty()) {
            isPlaying = false // No hay más sonidos, el servicio ya no está "reproduciendo"
            updateNotification()
            Log.d(TAG, "removeSound: No active media players left. isPlaying set to false.")
            if (!isTimerRunning) {
                stopSelf() // Detener el servicio si ya no hay sonidos ni temporizador.
                Log.d(TAG, "removeSound: Stopping service as no sounds or timer remain.")
            }
        }
    }

    fun setVolume(soundName: String, volume: Int) {
        Log.d(TAG, "setVolume: Setting volume for $soundName to $volume")
        activeMediaPlayers[soundName]?.let { mp ->
            val vol = volume / 100f
            mp.setVolume(vol, vol)
            soundVolumes[soundName] = volume
            Log.d(TAG, "setVolume: Volume set for $soundName.")
        } ?: Log.w(
            TAG,
            "setVolume: Sound $soundName not found to set volume. It might not be added yet."
        )
    }

    fun getVolume(soundName: String): Int {
        val volume = soundVolumes[soundName] ?: 0
        Log.d(TAG, "getVolume: Returning volume $volume for $soundName.")
        return volume
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
        cancelTimer()
        if (duration <= 0) {
            Log.d(TAG, "startCountdownTimer: Duration is 0 or less, not starting timer.")
            isTimerRunning = false
            return
        }

        timer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
            }

            override fun onFinish() {
                Log.d(TAG, "Timer finished, calling stopSounds().")
                isTimerRunning = false
                totalTimerDuration = 0L
                timeLeftInMillis = 0L
                stopSounds()
            }
        }.start()
        isTimerRunning = true
        Log.d(TAG, "startCountdownTimer: New timer started with ${duration / 1000} seconds.")
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
            Log.d(
                TAG,
                "resumeTimer: Cannot resume timer. isTimerRunning: $isTimerRunning, timeLeftInMillis: $timeLeftInMillis."
            )
        }
    }

    private fun cancelTimer() {
        if (timer != null) {
            timer?.cancel()
            timer = null
            isTimerRunning = false
            Log.d(TAG, "cancelTimer: Existing timer cancelled.")
        } else {
            Log.d(TAG, "cancelTimer: No timer to cancel.")
        }
    }
    // --- Fin Métodos de Control del Temporizador ---

    // Este método asegura que TODOS los sonidos activos se reproduzcan
    fun playSounds() {
        Log.d(TAG, "playSounds: Attempting to play sounds. Current isPlaying state: $isPlaying")

        if (activeMediaPlayers.isEmpty() && totalTimerDuration == 0L && timeLeftInMillis == 0L) {
            Toast.makeText(
                this,
                "No hay sonidos seleccionados o temporizador configurado.",
                Toast.LENGTH_SHORT
            ).show()
            Log.w(TAG, "playSounds: No sounds and no timer set. Doing nothing.")
            return // No hay nada que reproducir, ni sonidos ni temporizador
        }

        activeMediaPlayers.values.forEach { mp ->
            try {
                if (!mp.isPlaying) { // Solo iniciar si no está ya reproduciendo
                    mp.start()
                    Log.d(TAG, "playSounds: Started playing an individual sound.")
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "playSounds: IllegalStateException trying to start MediaPlayer.", e)
            }
        }
        isPlaying = true // Establecer el estado global a reproduciendo
        updateNotification()
        Log.d(TAG, "playSounds: All active sounds started. isPlaying set to $isPlaying")

        // Lógica del temporizador
        if (totalTimerDuration > 0) {
            if (timeLeftInMillis > 0) {
                resumeTimer()
            } else {
                startCountdownTimer(totalTimerDuration)
            }
        } else {
            Log.d(TAG, "playSounds: No timer duration set.")
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
            isPlaying = false // Establecer el estado global a pausado
            updateNotification()
            Log.d(TAG, "pauseSounds: All sounds paused.")

            pauseTimer()

        } else {
            Log.d(TAG, "pauseSounds: Already paused or not playing.")
        }
    }

    fun stopSounds() {
        Log.d(TAG, "stopSounds: Attempting to stop all sounds.")
        activeMediaPlayers.values.forEach { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
                Log.d(TAG, "stopSounds: Stopped and released a MediaPlayer.")
            } catch (e: IllegalStateException) {
                Log.e(
                    TAG,
                    "stopSounds: IllegalStateException trying to stop/release MediaPlayer.",
                    e
                )
            }
        }
        activeMediaPlayers.clear()
        soundVolumes.clear()
        isPlaying = false // Establecer el estado global a no reproduciendo

        cancelTimer()
        totalTimerDuration = 0L
        timeLeftInMillis = 0L
        Log.d(TAG, "stopSounds: Timer fully reset.")

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

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: SoundService destroyed.")
        stopSounds()
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
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Sonidos de Relajación"
        val content = when {
            activeMediaPlayers.isEmpty() && !isTimerRunning -> "Detenido"
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