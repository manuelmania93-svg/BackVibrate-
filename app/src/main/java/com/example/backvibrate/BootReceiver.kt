package com.example.backvibrate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Runs after every device boot. The system re-binds an enabled accessibility
 * service by itself, so if the service is still enabled there is nothing to do.
 * If MagicOS/EMUI has disabled it (known behavior for sideloaded apps), the user
 * gets a one-tap notification that lands directly on the accessibility settings.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BackVibrate"
        private const val CHANNEL_ID = "backvibrate_recovery"
        private const val NOTIFICATION_ID = 1002
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        if (enabled.contains(context.packageName)) {
            Log.d(TAG, "Boot: accessibility service still enabled, nothing to do")
            return
        }

        Log.w(TAG, "Boot: accessibility service was disabled by the system, posting recovery notification")
        postRecoveryNotification(context)
    }

    private fun postRecoveryNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "BackVibrate Schutz",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Warnung, wenn die Haptik nach einem Neustart neu aktiviert werden muss"
                }
            )
        }

        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context, 0, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("BackVibrate reaktivieren")
            .setContentText("Nach dem Neustart ist die Haptik aus. Tippen, dann rueckwartsvibiration einschalten.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            nm.notify(NOTIFICATION_ID, notif)
        } catch (e: Exception) {
            Log.e(TAG, "Could not post boot recovery notification", e)
        }
    }
}
