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
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class BackVibrateAccessibilityService : AccessibilityService() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 

    companion object {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        var instance: BackVibrateAccessibilityService? = null
        private const val CHANNEL_ID = "backvibrate_foreground"
        private const val NOTIFICATION_ID = 1001
        private const val DEBOUNCE_MS = 180L
    

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isLeftPosition = true
    private var isPreviewVisible = false
    private var isTouchDown = false
    private var lastBackTime = 0L

    override fun onServiceConnected() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        updateConfig()
    

    private fun startForegroundNotification() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            val ch = NotificationChannel(
                CHANNEL_ID,
                "rueckwartsvibiration",
                NotificationManager.IMPORTANCE_LOW
            ).apply {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                description = "Haptik laeuft im Hintergrund"
                setShowBadge(false)
            
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        

        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("rückwartsvibiration ist aktiv")
            .setContentText("Haptik fuer Zurueck-Taste laeuft im Hintergrund")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
             else {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                startForeground(NOTIFICATION_ID, notif)
            
         else {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            startForeground(NOTIFICATION_ID, notif)
        
    

    fun updateConfig() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        val prefs = getSharedPreferences("back_vibrate_prefs", MODE_PRIVATE)
        isLeftPosition = prefs.getBoolean("pos_left", true)
        isPreviewVisible = prefs.getBoolean("preview_overlay", false)

        if (overlayView == null) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            createOverlay()
         else {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            overlayView?.setBackgroundColor(
                if (isPreviewVisible) Color.argb(120, 255, 0, 0) else Color.TRANSPARENT
            )
            try {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                windowManager?.updateViewLayout(overlayView, getOverlayLayoutParams())
             catch (_: Exception) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        
    

    private fun createOverlay() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        removeOverlay()

        val slop = 30 * resources.displayMetrics.density
        var startX = 0f
        var startY = 0f
        var isCancelled = false

        val view = View(this).apply {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            setBackgroundColor(if (isPreviewVisible) Color.argb(120, 255, 0, 0) else Color.TRANSPARENT)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                setOnApplyWindowInsetsListener {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  v, insets ->
                    val nb = insets.getInsets(WindowInsets.Type.navigationBars())
                    v.visibility = if (nb.bottom > 0  nb.right > 0  nb.left > 0) View.VISIBLE else View.GONE
                    insets
                
            

            setOnTouchListener {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  v, event ->
                when (event.action) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                    MotionEvent.ACTION_DOWN -> {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                        startX = event.x
                        startY = event.y
                        isCancelled = false
                        isTouchDown = true
                        triggerVibration()
                        true
                    
                    MotionEvent.ACTION_MOVE -> {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                        if (isTouchDown && !isCancelled) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                            val dx = abs(event.x - startX)
                            val dy = abs(event.y - startY)
                            if (dx > slop  dy > slop  event.x < 0  event.x > v.width  event.y < 0  event.y > v.height) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                                isCancelled = true
                                isTouchDown = false
                            
                        
                        true
                    
                    MotionEvent.ACTION_UP -> {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                        if (isTouchDown && !isCancelled) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                            isTouchDown = false
                            executeBack()
                        
                        true
                    
                    MotionEvent.ACTION_CANCEL -> {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                        isTouchDown = false
                        true
                    
                    else -> false
                
            
        

        try {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            windowManager?.addView(view, getOverlayLayoutParams())
            overlayView = view
         catch (_: Exception) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
    

    private fun executeBack() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        val now = SystemClock.uptimeMillis()
        if (now - lastBackTime >= DEBOUNCE_MS) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            lastBackTime = now
            performGlobalAction(GLOBAL_ACTION_BACK)
        
    

    private fun getOverlayLayoutParams(): WindowManager.LayoutParams {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
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
        ).apply {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            gravity = Gravity.BOTTOM or (if (isLeftPosition) Gravity.START else Gravity.END)
        
    

    private fun removeOverlay() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        overlayView?.let {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            try {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                windowManager?.removeView(it)
             catch (_: Exception) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            overlayView = null
        
    

    private fun triggerVibration() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        val d = getSharedPreferences("back_vibrate_prefs", MODE_PRIVATE).getInt("vib_duration", 45).toLong()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE)
            )
         else {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            Suppress("DEPRECATION")
            val v = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                v?.vibrate(VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE))
             else {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                Suppress("DEPRECATION")
                v?.vibrate(d)
            
        
    

    override fun onConfigurationChanged(newConfig: Configuration) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        super.onConfigurationChanged(newConfig)
        updateConfig()
    

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED 
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            overlayView?.requestLayout()
        
    

    override fun onInterrupt() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 

    override fun onDestroy() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        super.onDestroy()
        if (instance == this) instance = null
        removeOverlay()
    

