package com.example.relaxtra

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
        const val SOUND_BIRDS = "bird_sound" // Añade tus constantes aquí
        const val SOUND_WAVES = "wave_sound"
        const val SOUND_ROAD = "road_sound"
    }

    private val activeMediaPlayers = mutableMapOf<String, MediaPlayer>()
    private val soundVolumes = mutableMapOf<String, Int>() // Para almacenar los volúmenes
    private val binder = LocalBinder()
    private var isPlaying = false
    private var timer: CountDownTimer? = null

    inner class LocalBinder : Binder() {
        fun getService(): SoundService = this@SoundService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Servicio creado.")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "onStartCommand: Comando de inicio recibido.")
        return START_NOT_STICKY // O START_STICKY si quieres que el servicio se reinicie automáticamente
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // Métodos para controlar la reproducción de sonidos
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
                mp.isLooping = true // Para que se reproduzca en bucle
                activeMediaPlayers[soundName] = mp
                soundVolumes[soundName] = 50 // Volumen por defecto al añadir
                if (isPlaying) { // Si ya estamos reproduciendo, inicia este nuevo sonido
                    mp.start()
                    Log.d(
                        TAG,
                        "addSound: Started playing new sound $soundName as service is already playing."
                    )
                }
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

            } catch (e: Exception) {
                Log.e(TAG, "removeSound: Error stopping/releasing MediaPlayer for $soundName", e)
            }
        } ?: Log.w(TAG, "removeSound: Sound $soundName not found in active players.")
    }

    fun setVolume(soundName: String, volume: Int) {
        Log.d(TAG, "setVolume: Setting volume for $soundName to $volume")
        activeMediaPlayers[soundName]?.let { mp ->
            // El volumen de MediaPlayer va de 0.0f a 1.0f
            val vol = volume / 100f
            mp.setVolume(vol, vol)
            soundVolumes[soundName] = volume // Guarda el volumen
            Log.d(TAG, "setVolume: Volume set for $soundName.")
        } ?: Log.w(TAG, "setVolume: Sound $soundName not found to set volume.")
    }

    fun getVolume(soundName: String): Int {
        return soundVolumes[soundName] ?: 50 // Devuelve el volumen guardado o 50 por defecto
    }

    fun isSoundActive(soundName: String): Boolean {
        return activeMediaPlayers.containsKey(soundName)
    }

    fun playSounds() {
        Log.d(TAG, "playSounds: Attempting to play sounds. isPlaying: $isPlaying")
        if (!isPlaying) {
            if (activeMediaPlayers.isEmpty()) {
                Log.w(TAG, "playSounds: No sounds added to play.")
                // Puedes decidir si detener el servicio o mostrar un Toast aquí
                return
            }
            activeMediaPlayers.values.forEach { mp ->
                activeMediaPlayers.values.forEach { mp ->
                    if (!mp.isPlaying) {
                        mp.start()
                    }
                }
            }
            isPlaying = true
            updateNotification()
            Log.d(TAG, "playSounds: All sounds started.")
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
        } else {
            Log.d(TAG, "pauseSounds: Already paused or not playing.")
        }
    }

    fun stopSounds() {
        Log.d(TAG, "stopSounds: Attempting to stop all sounds.")
        activeMediaPlayers.values.forEach { mp ->
            try {
                mp.stop()
                mp.release() // Libera los recursos
                Log.d(TAG, "stopSounds: Stopped and released a MediaPlayer.")
            } catch (e: IllegalStateException) {
                Log.e(TAG, "stopSounds: IllegalStateException trying to stop/release MediaPlayer.", e)
            }
        }
        activeMediaPlayers.clear()
        soundVolumes.clear()
        isPlaying = false
        cancelTimer() // Detener el temporizador si está activo

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            Log.d(TAG, "stopSounds: stopForeground(STOP_FOREGROUND_REMOVE) called.")
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
            Log.d(TAG, "stopSounds: stopForeground(true) called (deprecated).")
        }

        stopSelf() // Detiene el servicio completamente
        Log.d(TAG, "stopSounds: Service stopping itself.")
    }

    fun isPlaying(): Boolean = isPlaying

    fun setTimer(durationMillis: Long) {
        Log.d(TAG, "setTimer: Setting timer for $durationMillis ms.")
        cancelTimer()
        if (durationMillis > 0) {
            timer = object : CountDownTimer(durationMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    Log.d(TAG, "Timer remaining: ${millisUntilFinished / 1000} seconds")
                }

                override fun onFinish() {
                    Log.d(TAG, "Timer finished, stopping sounds.")
                    stopSounds()
                }
            }.start()
            Log.d(TAG, "Timer started.")
        } else {
            Log.d(TAG, "setTimer: Timer set to 0 (no timer).")
        }
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
        Log.d(TAG, "Timer cancelled.")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSounds() // Asegúrate de liberar los recursos al destruir el servicio
        cancelTimer()
        Log.d(TAG, "onDestroy: SoundService destroyed.")
    }

    /// --- Notificaciones para Foreground Service ---
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
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Asegúrate de tener un ícono
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