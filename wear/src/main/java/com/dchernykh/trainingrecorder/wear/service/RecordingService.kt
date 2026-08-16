package com.dchernykh.trainingrecorder.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
        // Always the health type. An earlier version fell back to
        // FOREGROUND_SERVICE_TYPE_NONE when no sensor permission was granted,
        // which is worse than the problem it was avoiding: on API 34 and above a
        // NONE-type foreground service is itself rejected, so the branch written
        // to prevent a crash was the crash. The type is safe to use
        // unconditionally because ACTIVITY_RECOGNITION covers it and
        // HIGH_SAMPLING_RATE_SENSORS is a normal permission granted at install,
        // so the requirement is met even by a rider who declined everything
        // they were asked.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            // Health *and* location. Health alone keeps the process alive and
            // says nothing about what it needs, so the platform withdraws
            // positions as soon as the app leaves the screen - the ride goes on
            // recording, with the distance frozen and the satellite indicator
            // still green, because availability keeps being reported while the
            // fixes stop.
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        // Restarted with its last intent if the system does kill it, so a
        // recording survives memory pressure rather than ending silently.
        return START_REDELIVER_INTENT
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
