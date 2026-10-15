package com.example.backvibrate

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Runs after every device boot.
 *
 * - Service missing from the enabled list (MagicOS switches sideloaded accessibility services off
 *   after a restart): post a one-tap notification that opens the accessibility settings.
 * - Service still listed as enabled: wait a bit, then check that it is really running. If it is not,
 *   post a notification that points to the app settings.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK_RUNNING = "com.example.backvibrate.CHECK_RUNNING"
        const val NOTIFICATION_ID = 1002
        private const val TAG = "BackVibrate"
        private const val CHANNEL_ID = "backvibrate_recovery"
        private const val CHECK_DELAY_MS = 30_000L

        fun isServiceEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            return enabled.split(':').any { it.startsWith(context.packageName + "/") }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> onBoot(context, goAsync())
            ACTION_CHECK_RUNNING -> onCheckRunning(context)
        }
    }

    private fun onBoot(context: Context, pending: PendingResult? = null) {
        if (!isServiceEnabled(context) && SelfReenable.isOptedIn(context)) {
            if (SelfReenable.reenable(context)) {
                Log.d(TAG, "Boot: service switched back on by self re-enable")
                pending?.finish()
                return
            }
            if (SelfReenable.shizukuRunning() && SelfReenable.shizukuPermitted()) {
                SelfReenable.grantViaShizuku(context) { granted ->
                    val ok = granted && SelfReenable.reenable(context)
                    Log.d(TAG, "Boot: Shizuku grant=$granted, re-enabled=$ok")
                    if (!ok) notifyOff(context)
                    pending?.finish()
                }
                return
            }
            Log.d(TAG, "Boot: self re-enable not possible (no permission, Shizuku not running), using notification")
        }
        try {
            bootCheck(context)
        } finally {
            pending?.finish()
        }
    }

    private fun notifyOff(context: Context) {
        postNotification(
            context,
            "BackVibrate ist aus - tippen zum Einschalten",
            "Nach dem Neustart wurde die Haptik ausgeschaltet. Tippen, dann BackVibrate in den Bedienungshilfen einschalten.",
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )
    }

    private fun bootCheck(context: Context) {
        if (!isServiceEnabled(context)) {
            Log.w(TAG, "Boot: accessibility service is switched off, posting recovery notification")
            postNotification(
                context,
                "BackVibrate ist aus - tippen zum Einschalten",
                "Nach dem Neustart wurde die Haptik ausgeschaltet. Tippen, dann BackVibrate in den Bedienungshilfen einschalten.",
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
            return
        }
        Log.d(TAG, "Boot: service is enabled, checking again in ${CHECK_DELAY_MS / 1000}s that it is running")
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val check = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, BootReceiver::class.java).setAction(ACTION_CHECK_RUNNING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + CHECK_DELAY_MS, check)
    }

    private fun onCheckRunning(context: Context) {
        if (BackVibrateAccessibilityService.instance != null) {
            Log.d(TAG, "Check: service is running")
            return
        }
        if (!isServiceEnabled(context)) {
            bootCheck(context)
            return
        }
        Log.w(TAG, "Check: service is enabled but not running, posting notification")
        postNotification(
            context,
            "BackVibrate läuft nicht",
            "Die Haptik ist eingeschaltet, aber der Dienst läuft nicht. Tippen: App-Info öffnen, dort App-Start erlauben und Akku auf \"Keine Einschränkungen\" stellen.",
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}"))
        )
    }

    private fun postNotification(context: Context, title: String, text: String, target: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are blocked for BackVibrate, recovery notification cannot be shown")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "BackVibrate Schutz", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Warnung, wenn die Haptik nach einem Neustart neu aktiviert werden muss"
                }
            )
        }

        val pending = PendingIntent.getActivity(
            context, 0, target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            nm.notify(NOTIFICATION_ID, notif)
        } catch (e: Exception) {
            Log.e(TAG, "Could not post recovery notification", e)
        }
    }
}
