package com.example.backvibrate

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
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

class BackVibrateAccessibilityService : AccessibilityService() {

    companion object {
        var instance: BackVibrateAccessibilityService? = null
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isLeftPosition = true
    private var isPreviewVisible = false
    private var isTouchDown = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        updateConfig()
    }

    fun updateConfig() {
        val prefs = getSharedPreferences("back_vibrate_prefs", MODE_PRIVATE)
        isLeftPosition = prefs.getBoolean("pos_left", true)
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

        val view = View(this).apply {
            setBackgroundColor(if (isPreviewVisible) Color.argb(120, 255, 0, 0) else Color.TRANSPARENT)
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isTouchDown = true
                        triggerVibration()
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isTouchDown) {
                            isTouchDown = false
                            performGlobalAction(GLOBAL_ACTION_BACK)
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

        val params = getOverlayLayoutParams()
        try {
            windowManager?.addView(view, params)
            overlayView = view
        } catch (_: Exception) {}
    }

    private fun getOverlayLayoutParams(): WindowManager.LayoutParams {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val navBarHeightRes = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navBarHeight = if (navBarHeightRes > 0) {
            resources.getDimensionPixelSize(navBarHeightRes)
        } else {
            (48 * displayMetrics.density).toInt()
        }

        val targetWidth = screenWidth / 3

        return WindowManager.LayoutParams(
            targetWidth,
            navBarHeight,
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val v = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(45)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateConfig()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        removeOverlay()
    }
}
