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
 * Runs after every device boot and after the app is updated in place.
 *
 * - Service missing from the enabled list (MagicOS switches sideloaded accessibility services off
 *   after a restart): try the self re-enable first, then verify repeatedly. MagicOS can switch the
 *   service back off seconds after we re-enable it, so one write is not enough - we re-check a few
 *   times before giving up and posting the one-tap rescue notification.
 * - Service still listed as enabled: wait a bit, then check that it is really running. If it is not,
 *   post a notification that points to the app settings.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK_RUNNING = "com.example.backvibrate.CHECK_RUNNING"
        const val NOTIFICATION_ID = 1002
        private const val TAG = "BackVibrate"
        private const val CHANNEL_ID = "backvibrate_recovery"
        private const val FIRST_CHECK_DELAY_MS = 30_000L
        private const val RETRY_DELAY_MS = 60_000L
        /** Bounded recovery: at most this many re-enable/verify rounds before notifying. */
        private const val MAX_ATTEMPTS = 3
        private const val EXTRA_ATTEMPT = "attempt"

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
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> onBoot(context, goAsync())
            ACTION_CHECK_RUNNING -> onCheckRunning(context, intent.getIntExtra(EXTRA_ATTEMPT, 0))
        }
    }

    private fun onBoot(context: Context, pending: PendingResult? = null) {
        if (!isServiceEnabled(context) && SelfReenable.autoEnabled(context)) {
            if (SelfReenable.reenable(context)) {
                Log.d(TAG, "Boot: service switched back on by self re-enable, verifying in ${FIRST_CHECK_DELAY_MS / 1000}s")
            } else {
                Log.w(TAG, "Boot: WRITE_SECURE_SETTINGS present but re-enable failed, retrying via checks")
            }
            // Whether the write succeeded or failed: verify afterwards. If MagicOS flips the
            // service off again, the checks below re-enable it (bounded) before notifying.
            scheduleCheck(context, 0)
            pending?.finish()
            return
        }
        if (!isServiceEnabled(context) && SelfReenable.shizukuOptedIn(context)
            && SelfReenable.shizukuRunning() && SelfReenable.shizukuPermitted()
        ) {
            SelfReenable.grantViaShizuku(context) { granted ->
                val ok = granted && SelfReenable.reenable(context)
                Log.d(TAG, "Boot: Shizuku grant=$granted, re-enabled=$ok")
                if (ok) {
                    scheduleCheck(context, 0)
                } else {
                    notifyOff(context)
                }
                pending?.finish()
            }
            return
        }
        try {
            bootCheck(context)
        } finally {
            pending?.finish()
        }
    }

    private fun scheduleCheck(context: Context, attempt: Int) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val check = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, BootReceiver::class.java)
                .setAction(ACTION_CHECK_RUNNING)
                .putExtra(EXTRA_ATTEMPT, attempt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val delay = if (attempt == 0) FIRST_CHECK_DELAY_MS else RETRY_DELAY_MS
        alarm.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delay, check)
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
            notifyOff(context)
            return
        }
        Log.d(TAG, "Boot: service is enabled, checking again in ${FIRST_CHECK_DELAY_MS / 1000}s that it is running")
        scheduleCheck(context, 0)
    }

    private fun onCheckRunning(context: Context, attempt: Int) {
        if (BackVibrateAccessibilityService.instance != null) {
            Log.d(TAG, "Check (attempt $attempt): service is running")
            return
        }
        if (!isServiceEnabled(context)) {
            if (SelfReenable.autoEnabled(context) && attempt < MAX_ATTEMPTS) {
                // MagicOS switched the service back off after our earlier re-enable: retry, bounded.
                Log.w(TAG, "Check (attempt $attempt): service off again, re-enabling")
                SelfReenable.reenable(context)
                scheduleCheck(context, attempt + 1)
                return
            }
            Log.w(TAG, "Check (attempt $attempt): service still off, posting recovery notification")
            notifyOff(context)
            return
        }
        if (attempt < MAX_ATTEMPTS) {
            // Enabled but not bound yet - after a boot the system can take a while to bind the
            // service. Re-check instead of warning early.
            Log.d(TAG, "Check (attempt $attempt): enabled but not running yet, re-checking")
            scheduleCheck(context, attempt + 1)
            return
        }
        Log.w(TAG, "Check (attempt $attempt): service is enabled but not running, posting notification")
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
