package com.example.backvibrate

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat

/**
 * Zeichnet ein unsichtbares Touch-Feld über der Zurück-Taste der
 * 3-Tasten-Navigationsleiste. Bei Tap: löst die normale Zurück-Aktion aus
 * UND lässt das Handy vibrieren (auf Honor/MagicOS fehlt das Standard-Feedback).
 */
class BackVibrateAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    companion object {
        private const val CHANNEL_ID = "backvibrate_service"
        private const val NOTIFICATION_ID = 1
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundServiceWithNotification()
        addOverlayButton()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Wir brauchen keine Events, das Overlay macht die Arbeit.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
    }

    private fun addOverlayButton() {
        val view = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    vibrate()
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    v.performClick()
                    true
                } else {
                    false
                }
            }
        }

        // Breite/Höhe/Position musst du für dein Gerät (Honor, 3-Tasten-Leiste)
        // anpassen. Startwert: linkes Drittel der Navigationsleiste unten.
        val params = WindowManager.LayoutParams(
            200, // Breite in px - anpassen
            120, // Höhe in px - anpassen
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 0
            y = 0
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    private fun vibrate() {
        val effect = VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(effect)
        }
    }

    private fun startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BackVibrate aktiv",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BackVibrate aktiv")
            .setContentText("Vibration beim Zurück-Tippen ist aktiv")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
