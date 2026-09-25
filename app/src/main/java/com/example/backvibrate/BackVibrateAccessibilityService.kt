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
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
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
    private var overlayContainer: LinearLayout? = null
    private var isLeftPosition = false
    private var isPreviewVisible = false
    private var lastActionTime = 0L

override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    try {
        startForegroundNotification()
    } catch (_: Exception) {}
    updateConfig()
}

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "rueckwartsvibiration",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Haptik fuer Navigationstasten laeuft im Hintergrund"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }

        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Navigation Haptik ist aktiv")
            .setContentText("Haptik fuer alle 3 Tasten laeuft im Hintergrund")
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

        createOverlay()
    }

    private fun createOverlay() {
        removeOverlay()

        // Honor standard: Left = Recents, Middle = Home, Right = Back
        // If isLeftPosition is true: Left = Back, Middle = Home, Right = Recents
        val leftAction = if (isLeftPosition) GLOBAL_ACTION_BACK else GLOBAL_ACTION_RECENTS
        val rightAction = if (isLeftPosition) GLOBAL_ACTION_RECENTS else GLOBAL_ACTION_BACK

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // 1. Left button (Recents / Back)
            addView(createButtonView(leftAction, Color.argb(100, 255, 0, 0)))

            // 2. Middle button (Home)
            addView(createButtonView(GLOBAL_ACTION_HOME, Color.argb(100, 0, 255, 0)))

            // 3. Right button (Back / Recents)
            addView(createButtonView(rightAction, Color.argb(100, 0, 0, 255)))
        }

        try {
            windowManager?.addView(container, getOverlayLayoutParams())
            overlayContainer = container
        } catch (_: Exception) {}
    }

    private fun createButtonView(action: Int, previewColor: Int): View {
        val slop = 30 * resources.displayMetrics.density
        var startX = 0f
        var startY = 0f
        var isCancelled = false
        var isTouchDown = false

        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setBackgroundColor(if (isPreviewVisible) previewColor else Color.TRANSPARENT)

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
                            executeAction(action)
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
    }

    private fun executeAction(action: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastActionTime >= DEBOUNCE_MS) {
            lastActionTime = now
            performGlobalAction(action)
        }
    }

    private fun getOverlayLayoutParams(): WindowManager.LayoutParams {
        val dm = resources.displayMetrics
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val h = if (resId > 0) resources.getDimensionPixelSize(resId) else (48 * dm.density).toInt()

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
    }

    private fun removeOverlay() {
        overlayContainer?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
            overlayContainer = null
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
            overlayContainer?.requestLayout()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        removeOverlay()
    }
}
