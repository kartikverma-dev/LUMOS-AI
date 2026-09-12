package com.lumos.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lumos.ai.R

/**
 * Minimal foreground service that keeps LUMOS's response generation alive
 * when the app is backgrounded.
 *
 * Android's background execution limits (Doze, App Standby, and per-app
 * network restrictions once the process leaves the foreground) can throttle
 * or outright kill the OkHttp callback carrying streamed tokens. A foreground
 * service with a visible notification is the documented way to avoid that.
 *
 * The service does no work itself — ChatViewModel still owns the OkHttp call
 * and the coroutine. This is purely "keep the process's priority elevated and
 * tell the user why", started right before streaming begins and stopped as
 * soon as it ends (onDone / onError / user Stop / ViewModel cleared).
 */
class GenerationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "LUMOS generation", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shown while LUMOS is generating a response" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val stopPending = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_STOP).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LUMOS is thinking…")
            .setContentText("Generating a response")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop", stopPending)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "lumos_generation"
        const val NOTIFICATION_ID = 4201

        /** Broadcast by the notification's Stop action; ChatViewModel listens for this. */
        const val ACTION_STOP = "com.lumos.ai.action.STOP_GENERATION"
    }
}
