package com.dchernykh.trainingrecorder.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.dchernykh.trainingrecorder.localization.R
import com.dchernykh.trainingrecorder.wear.MainActivity

/**
 * Keeps a recording alive while the rider is not looking at the watch.
 *
 * Without a foreground service the system is free to stop the process the moment
 * the screen turns off, which on a long ride it will - and a workout that ends
 * when the wrist drops is worse than no workout at all. The ongoing activity is
 * what puts it on the watch face, so getting back to the ride is one tap rather
 * than a hunt through the launcher.
 */
class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // From API 34 the health type is only allowed when one of the sensor
        // permissions is actually granted, and starting without one throws.
        // Permission is explicitly optional in this app, so a rider who declined
        // gets a plain foreground service instead of a crash.
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasSensorPermission()) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            } else {
                0
            }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        // Restarted with its last intent if the system does kill it, so a
        // recording survives memory pressure rather than ending silently.
        return START_REDELIVER_INTENT
    }

    private fun hasSensorPermission(): Boolean =
        SENSOR_PERMISSIONS.any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun buildNotification(): Notification {
        createChannel()
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val builder =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.recording_notification_title))
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
        OngoingActivity
            .Builder(this, NOTIFICATION_ID, builder)
            .setStaticIcon(android.R.drawable.ic_media_play)
            .setTouchIntent(open)
            .setStatus(Status.Builder().addTemplate(getString(R.string.recording_notification_title)).build())
            .build()
            .apply(this)
        return builder.build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recording_channel_name),
                // Low: the rider already knows they are riding, and a noisy
                // channel on a watch is a wrist buzz every time it updates.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        /** Any one of these satisfies the health foreground service type. */
        private val SENSOR_PERMISSIONS =
            listOf(
                android.Manifest.permission.BODY_SENSORS,
                android.Manifest.permission.ACTIVITY_RECOGNITION,
                android.Manifest.permission.HIGH_SAMPLING_RATE_SENSORS,
            )

        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RecordingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }
}
