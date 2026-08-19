package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AssistantStateRepository
import com.example.speech.SpeechRecognitionManager

class VoiceAssistantService : Service() {

    companion object {
        private const val TAG = "VoxoraService"

        const val CHANNEL_ID = "voxora_assistant_channel"
        const val CHANNEL_NAME = "Voxora Assistant Service"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.example.voxora.ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.voxora.ACTION_STOP_SERVICE"

        fun startService(context: Context) {
            AssistantStateRepository.setExplicitlyStopped(context, false)
            val intent = Intent(context, VoiceAssistantService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            AssistantStateRepository.setExplicitlyStopped(context, true)
            val intent = Intent(context, VoiceAssistantService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    private var speechManager: SpeechRecognitionManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Service restarted by Android OS after system kill
            if (AssistantStateRepository.isExplicitlyStopped(this)) {
                stopAssistantService()
                return START_NOT_STICKY
            } else {
                Log.d(TAG, "Service restarted by system")
                startForegroundAssistantService()
                return START_STICKY
            }
        }

        if (intent.action == ACTION_STOP_SERVICE) {
            Log.d(TAG, "Service stopped by user")
            AssistantStateRepository.setExplicitlyStopped(this, true)
            stopAssistantService()
            return START_NOT_STICKY
        }

        Log.d(TAG, "Service started")
        AssistantStateRepository.setExplicitlyStopped(this, false)
        startForegroundAssistantService()
        return START_STICKY
    }

    private fun startForegroundAssistantService() {
        val notification = createNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }

        AssistantStateRepository.setAssistantActive(true)

        if (speechManager == null) {
            speechManager = SpeechRecognitionManager(this)
        }
        speechManager?.startListening()
    }

    private fun stopAssistantService() {
        speechManager?.destroy()
        speechManager = null

        AssistantStateRepository.setAssistantActive(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }


    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed from recent apps")
        super.onTaskRemoved(rootIntent)
    }

    private fun createNotification(): Notification {
        // Pending intent to launch main screen on tap
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pending intent for notification STOP action button
        val stopIntent = Intent(this, VoiceAssistantService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voxora is Active")
            .setContentText("Voice assistant is running in the background")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setAutoCancel(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(mainPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "STOP",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build().apply {
                flags = flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
            }

    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for Voxora background voice assistant foreground service"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        speechManager?.destroy()
        speechManager = null
        AssistantStateRepository.setAssistantActive(false)
        super.onDestroy()
    }


    override fun onBind(intent: Intent?): IBinder? = null
}

