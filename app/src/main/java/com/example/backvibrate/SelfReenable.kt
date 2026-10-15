package com.example.backvibrate

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Optional self re-enable. Needs the WRITE_SECURE_SETTINGS permission, which a normal app can only get
 * through a one-time grant from adb or Shizuku. Everything here fails soft: no Shizuku, no permission,
 * or an OS that refuses the write simply means the normal boot notification is used.
 */
object SelfReenable {
    private const val TAG = "BackVibrate"
    private const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"
    const val PREF_OPT_IN = "shizuku_opt_in"

    fun isOptedIn(context: Context): Boolean =
        context.getSharedPreferences("back_vibrate_prefs", Context.MODE_PRIVATE).getBoolean(PREF_OPT_IN, false)

    fun hasSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun shizukuRunning(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11()
    } catch (t: Throwable) {
        false
    }

    fun shizukuPermitted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    /** Writes the service into the enabled list. Returns true when the service is listed afterwards. */
    fun reenable(context: Context): Boolean {
        if (!hasSecureSettings(context)) return false
        val svc = ComponentName(context, BackVibrateAccessibilityService::class.java).flattenToString()
        return try {
            val cur = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            if (cur.split(':').none { it == svc }) {
                val next = if (cur.isEmpty()) svc else "$cur:$svc"
                Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            }
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            BootReceiver.isServiceEnabled(context)
        } catch (e: Exception) {
            Log.e(TAG, "Self re-enable failed", e)
            false
        }
    }

    /** One-time grant of WRITE_SECURE_SETTINGS through a Shizuku user service. */
    fun grantViaShizuku(context: Context, onResult: (Boolean) -> Unit) {
        if (!shizukuRunning() || !shizukuPermitted()) {
            onResult(false)
            return
        }
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        var conn: ServiceConnection? = null
        val args = Shizuku.UserServiceArgs(ComponentName(app.packageName, ShellUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .version(1)

        fun finish(ok: Boolean) {
            if (finished) return
            finished = true
            try { conn?.let { Shizuku.unbindUserService(args, it, true) } } catch (t: Throwable) { Log.w(TAG, "unbind failed", t) }
            onResult(ok)
        }

        conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                try {
                    val out = IUserService.Stub.asInterface(binder).exec("pm grant ${app.packageName} $PERMISSION")
                    Log.d(TAG, "Shizuku pm grant: $out")
                } catch (t: Throwable) {
                    Log.e(TAG, "Shizuku grant failed", t)
                }
                finish(hasSecureSettings(app))
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }

        handler.postDelayed({ finish(hasSecureSettings(app)) }, 8_000)
        try {
            Shizuku.bindUserService(args, conn)
        } catch (t: Throwable) {
            Log.e(TAG, "Could not bind Shizuku user service", t)
            finish(false)
        }
    }
}
