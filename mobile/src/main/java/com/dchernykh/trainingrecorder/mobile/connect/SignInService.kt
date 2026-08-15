package com.dchernykh.trainingrecorder.mobile.connect

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
import com.dchernykh.trainingrecorder.localization.R
import com.dchernykh.trainingrecorder.mobile.MainActivity

/**
 * Keeps the app running while the rider is signing in inside a browser.
 *
 * This exists because of a failure that looks nothing like its cause. Strava's
 * redirect comes back to a loopback port this app is listening on - and by then
 * the app is not in front of the rider, the browser is. Android freezes cached
 * processes, and a frozen process does not merely stop accepting connections:
 * its inbound traffic is dropped rather than refused, so the browser retransmits
 * until it gives up and shows ERR_CONNECTION_TIMED_OUT against the phone's own
 * address. A rider reads that as the app being broken, which is fair, and no log
 * on either side says otherwise. Phones that manage background processes
 * aggressively - which is most of them - make it the normal outcome rather than
 * the unlucky one.
 *
 * A foreground service is the documented way to say "this process is doing
 * something the user asked for and is waiting on". It runs for the length of one
 * sign-in and stops itself.
 *
 * `dataSync` is the type: this is a network exchange the rider started. It is
 * not a `shortService` - that caps at three minutes, and a rider hunting for a
 * password and then a two-factor code can easily take longer than the sign-in
 * would otherwise allow.
 */
class SignInService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        // Not restarted if the system does stop it: the authorization it was
        // guarding is gone with the process, and a service holding a port open
        // for a sign-in nobody is doing is worse than nothing.
        return START_NOT_STICKY
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
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.connect_in_progress))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.connect_action),
                // Low: this is a receipt for something the rider is already
                // doing, in an app they are already in. It has no business
                // making a sound.
                NotificationManager.IMPORTANCE_LOW,
            )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "sign-in"
        private const val NOTIFICATION_ID = 2

        /**
         * Runs [block] with the process held in the foreground.
         *
         * Failing to start the service is not failing the sign-in: a phone that
         * refuses it still has every chance of coming back from the browser
         * before anything reclaims the process, and refusing to try would turn a
         * likely failure into a certain one.
         */
        suspend fun <T> holdOpen(
            context: Context,
            block: suspend () -> T,
        ): T {
            val intent = Intent(context, SignInService::class.java)
            val started = runCatching { context.startForegroundService(intent) }.isSuccess
            return try {
                block()
            } finally {
                if (started) runCatching { context.stopService(intent) }
            }
        }
    }
}
