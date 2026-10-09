package com.example.clipsync.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.clipsync.MainActivity
import com.example.clipsync.data.ClipSyncRepository
import com.example.clipsync.data.ServerConfig
import com.example.clipsync.data.SyncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClipSyncService : Service() {

    companion object {
        const val CHANNEL_ID = "clipsync_foreground_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.clipsync.action.START"
        const val ACTION_STOP = "com.example.clipsync.action.STOP"
        const val ACTION_PASTE_AND_SEND = "com.example.clipsync.action.PASTE_AND_SEND"
        const val ACTION_TOGGLE_LIVE = "com.example.clipsync.action.TOGGLE_LIVE"
        const val ACTION_CLEAR = "com.example.clipsync.action.CLEAR"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _lastNotificationStatus = MutableStateFlow("Listo en segundo plano")
        val lastNotificationStatus: StateFlow<String> = _lastNotificationStatus.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, ClipSyncService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ClipSyncService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ClipSyncRepository
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        repository = ClipSyncRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                _isRunning.value = true
                val config = repository.getServerConfig()
                val notification = buildNotification("Conectado a ${config.host}:${config.port}", config)
                startForeground(NOTIFICATION_ID, notification)
            }

            ACTION_STOP -> {
                _isRunning.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_PASTE_AND_SEND -> {
                handlePasteAndSend()
            }

            ACTION_TOGGLE_LIVE -> {
                handleToggleLive()
            }

            ACTION_CLEAR -> {
                handleClear()
            }
        }

        return START_STICKY
    }

    private fun handlePasteAndSend() {
        mainHandler.post {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (text.isNotBlank()) {
                    serviceScope.launch {
                        val config = repository.getServerConfig()
                        val result = if (config.syncMode == SyncMode.LIVE_TYPING) {
                            repository.sendDirectType(text, config)
                        } else {
                            repository.sendClip(text, config)
                        }

                        mainHandler.post {
                            if (result.isSuccess) {
                                val preview = if (text.length > 25) text.take(22) + "..." else text
                                val msg = "✓ Enviado a PC: \"$preview\""
                                _lastNotificationStatus.value = msg
                                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                                updateNotification(msg, config)
                            } else {
                                val msg = "✕ Error al enviar al PC"
                                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                                updateNotification(msg, config)
                            }
                        }
                    }
                } else {
                    Toast.makeText(applicationContext, "El portapapeles está vacío", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(applicationContext, "No hay texto en el portapapeles", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleToggleLive() {
        val currentConfig = repository.getServerConfig()
        val nextMode = if (currentConfig.syncMode == SyncMode.CLIPBOARD) SyncMode.LIVE_TYPING else SyncMode.CLIPBOARD
        val updatedConfig = currentConfig.copy(syncMode = nextMode)
        repository.saveServerConfig(updatedConfig)

        val modeLabel = if (nextMode == SyncMode.LIVE_TYPING) "Modo En Vivo ⚡" else "Modo Portapapeles 📋"
        val msg = "Modo cambiado a: $modeLabel"
        _lastNotificationStatus.value = msg
        mainHandler.post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
            updateNotification(msg, updatedConfig)
        }
    }

    private fun handleClear() {
        serviceScope.launch {
            val config = repository.getServerConfig()
            repository.sendClip("", config)
            val msg = "🧹 Portapapeles de la PC limpiado"
            _lastNotificationStatus.value = msg
            mainHandler.post {
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                updateNotification(msg, config)
            }
        }
    }

    private fun buildNotification(statusText: String, config: ServerConfig): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: Paste & Send
        val pasteIntent = Intent(this, ClipSyncService::class.java).apply { action = ACTION_PASTE_AND_SEND }
        val pastePendingIntent = PendingIntent.getService(
            this, 1, pasteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Toggle Live Mode
        val toggleIntent = Intent(this, ClipSyncService::class.java).apply { action = ACTION_TOGGLE_LIVE }
        val togglePendingIntent = PendingIntent.getService(
            this, 2, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: Clear
        val clearIntent = Intent(this, ClipSyncService::class.java).apply { action = ACTION_CLEAR }
        val clearPendingIntent = PendingIntent.getService(
            this, 3, clearIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 4: Stop
        val stopIntent = Intent(this, ClipSyncService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 4, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isLive = config.syncMode == SyncMode.LIVE_TYPING
        val modeTitle = if (isLive) "⚡ Escritura en Vivo" else "📋 Portapapeles PC"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoClip • $modeTitle")
            .setContentText(statusText)
            .setSmallIcon(com.example.clipsync.R.drawable.ic_bolt)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(com.example.clipsync.R.drawable.ic_paste, "Pegar y Enviar", pastePendingIntent)
            .addAction(
                if (isLive) com.example.clipsync.R.drawable.ic_paste else com.example.clipsync.R.drawable.ic_bolt,
                if (isLive) "📋 Portapapeles" else "⚡ En Vivo",
                togglePendingIntent
            )
            .addAction(com.example.clipsync.R.drawable.ic_trash, "Limpiar", clearPendingIntent)
            .addAction(com.example.clipsync.R.drawable.ic_stop, "Cerrar", stopPendingIntent)
            .build()
    }

    private fun updateNotification(statusText: String, config: ServerConfig) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(statusText, config))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoClip Sincronizador en Segundo Plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene los controles de acceso rápido en la barra de notificaciones para enviar textos a tu PC."
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        _isRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }
}
