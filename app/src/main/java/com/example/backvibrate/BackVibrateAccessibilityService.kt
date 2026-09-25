package com.example.backvibrate

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class BackVibrateAccessibilityService : AccessibilityService() {

    companion object {
        var instance: BackVibrateAccessibilityService? = null
        private const val CHANNEL_ID = "backvibrate_foreground"
        private const val NOTIFICATION_ID = 1001
        private const val DEBOUNCE_MS = 180L
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isLeftPosition = false
    private var isPreviewVisible = false
    private var isTouchDown = false
    private var lastBackTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        updateConfig()
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "rueckwartsvibiration",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Haptik laeuft im Hintergrund"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }

        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("rückwartsvibiration ist aktiv")
            .setContentText("Haptik fuer Zurueck-Taste laeuft im Hintergrund")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
    }

    fun updateConfig() {
        val prefs = getSharedPreferences("back_vibrate_prefs", MODE_PRIVATE)
        isLeftPosition = prefs.getBoolean("pos_left", false)
        isPreviewVisible = prefs.getBoolean("preview_overlay", false)

        if (overlayView == null) {
            createOverlay()
        } else {
            overlayView?.setBackgroundColor(
                if (isPreviewVisible) Color.argb(120, 255, 0, 0) else Color.TRANSPARENT
            )
            try {
                windowManager?.updateViewLayout(overlayView, getOverlayLayoutParams())
            } catch (_: Exception) {}
        }
    }

    private fun createOverlay() {
        removeOverlay()

        val slop = 30 * resources.displayMetrics.density
        var startX = 0f
        var startY = 0f
        var isCancelled = false

        val view = View(this).apply {
            setBackgroundColor(if (isPreviewVisible) Color.argb(120, 255, 0, 0) else Color.TRANSPARENT)

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.x
                        startY = event.y
                        isCancelled = false
                        isTouchDown = true
                        triggerVibration()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isTouchDown && !isCancelled) {
                            val dx = abs(event.x - startX)
                            val dy = abs(event.y - startY)
                            if (dx > slop || dy > slop || event.x < 0 || event.x > v.width || event.y < 0 || event.y > v.height) {
                                isCancelled = true
                                isTouchDown = false
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isTouchDown && !isCancelled) {
                            isTouchDown = false
                            executeBack()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isTouchDown = false
                        true
                    }
                    else -> false
                }
            }
        }

        try {
            windowManager?.addView(view, getOverlayLayoutParams())
            overlayView = view
        } catch (_: Exception) {}
    }

    private fun executeBack() {
        val now = SystemClock.uptimeMillis()
        if (now - lastBackTime >= DEBOUNCE_MS) {
            lastBackTime = now
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun getOverlayLayoutParams(): WindowManager.LayoutParams {
        val dm = resources.displayMetrics
        val isLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val h = if (resId > 0) resources.getDimensionPixelSize(resId) else (48 * dm.density).toInt()
        val w = if (isLand) dm.widthPixels / 4 else dm.widthPixels / 3

        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or (if (isLeftPosition) Gravity.START else Gravity.END)
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    private fun triggerVibration() {
        val d = getSharedPreferences("back_vibrate_prefs", MODE_PRIVATE).getInt("vib_duration", 45).toLong()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val v = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(d)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateConfig()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            overlayView?.requestLayout()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        removeOverlay()
    }
}