#!/bin/bash
set -e
cd ~/Desktop/BackVibrate
rm -f Run

mkdir -p app/src/main/res/xml
mkdir -p app/src/main/res/values
mkdir -p app/src/main/res/layout
mkdir -p app/src/main/java/com/example/backvibrate
mkdir -p .github/workflows

# 1. AndroidManifest.xml
cat << 'EOF' > app/src/main/AndroidManifest.xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppCompat.DayNight.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".BackVibrateAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

    </application>
</manifest>
EOF

# 2. accessibility_service_config.xml
cat << 'EOF' > app/src/main/res/xml/accessibility_service_config.xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityFeedbackType="feedbackHaptic"
    android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews"
    android:canRetrieveWindowContent="false"
    android:canPerformGestures="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100" />
EOF

# 3. strings.xml
cat << 'EOF' > app/src/main/res/values/strings.xml
<resources>
    <string name="app_name">BackVibrate</string>
    <string name="accessibility_service_description">Fuegt Haptik-Feedback zur Zurueck-Taste auf MagicOS hinzu.</string>
</resources>
EOF

# 4. BackVibrateAccessibilityService.kt
cat << 'EOF' > app/src/main/java/com/example/backvibrate/BackVibrateAccessibilityService.kt
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
EOF

# 5. activity_main.xml
cat << 'EOF' > app/src/main/res/layout/activity_main.xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#121212"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center_horizontal"
        android:padding="24dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="32dp"
            android:text="BackVibrate"
            android:textSize="28sp"
            android:textColor="#FFFFFF"
            android:textStyle="bold" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:text="Haptic Feedback fuer MagicOS 3-Tasten-Navigation"
            android:textSize="13sp"
            android:textColor="#888888"
            android:gravity="center" />

        <LinearLayout
            android:id="@+id/statusCard"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center"
            android:padding="24dp"
            android:layout_marginTop="28dp"
            android:background="#1E1E1E">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="DIENST-STATUS"
                android:textSize="11sp"
                android:textColor="#888888"
                android:letterSpacing="0.15" />

            <TextView
                android:id="@+id/tvStatus"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="10dp"
                android:text="PRUEFEN..."
                android:textSize="24sp"
                android:textStyle="bold"
                android:textColor="#FFFFFF" />

            <TextView
                android:id="@+id/tvHint"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="10dp"
                android:text=""
                android:textSize="13sp"
                android:textColor="#AAAAAA"
                android:gravity="center" />
        </LinearLayout>

        <Button
            android:id="@+id/btnOpenSettings"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:layout_marginTop="20dp"
            android:text="In Bedienungshilfen aktivieren"
            android:textSize="14sp"
            android:backgroundTint="#1976D2" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp"
            android:layout_marginTop="24dp"
            android:background="#1C1C1E">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Position der Zurueck-Taste"
                android:textColor="#FFFFFF"
                android:textStyle="bold"
                android:textSize="14sp" />

            <RadioGroup
                android:id="@+id/rgPosition"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:layout_marginTop="8dp">

                <RadioButton
                    android:id="@+id/rbLeft"
                    android:layout_width="0dp"
                    android:layout_weight="1"
                    android:layout_height="wrap_content"
                    android:text="Links (Standard)"
                    android:textColor="#DDDDDD"
                    android:checked="true" />

                <RadioButton
                    android:id="@+id/rbRight"
                    android:layout_width="0dp"
                    android:layout_weight="1"
                    android:layout_height="wrap_content"
                    android:text="Rechts"
                    android:textColor="#DDDDDD" />
            </RadioGroup>

            <Switch
                android:id="@+id/switchPreview"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:text="Overlay-Vorschau anzeigen (rot)"
                android:textColor="#DDDDDD" />
        </LinearLayout>

        <Button
            android:id="@+id/btnTestVibration"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:layout_marginTop="16dp"
            android:text="Vibration testen"
            android:backgroundTint="#2E2E2E" />

        <Button
            android:id="@+id/btnBattery"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:layout_marginTop="10dp"
            android:text="Akku-Optimierung ausschalten (Honor)"
            android:backgroundTint="#2A2A2A" />

    </LinearLayout>
</ScrollView>
EOF

# 6. MainActivity.kt
cat << 'EOF' > app/src/main/java/com/example/backvibrate/MainActivity.kt
package com.example.backvibrate

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("back_vibrate_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnTestVibration).setOnClickListener {
            testVibration()
        }

        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            openBatteryOptimizationSettings()
        }

        val rgPosition = findViewById<RadioGroup>(R.id.rgPosition)
        val rbLeft = findViewById<RadioButton>(R.id.rbLeft)
        val rbRight = findViewById<RadioButton>(R.id.rbRight)

        val isLeft = prefs.getBoolean("pos_left", true)
        if (isLeft) rbLeft.isChecked = true else rbRight.isChecked = true

        rgPosition.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit().putBoolean("pos_left", checkedId == R.id.rbLeft).apply()
            notifyService()
        }

        val switchPreview = findViewById<Switch>(R.id.switchPreview)
        switchPreview.isChecked = prefs.getBoolean("preview_overlay", false)
        switchPreview.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("preview_overlay", isChecked).apply()
            notifyService()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun notifyService() {
        BackVibrateAccessibilityService.instance?.updateConfig()
    }

    private fun isServiceEnabled(): Boolean {
        val cn = "$packageName/${BackVibrateAccessibilityService::class.java.name}"
        val flat = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(flat)
        while (splitter.hasNext()) {
            if (splitter.next().equals(cn, ignoreCase = true)) return true
        }
        return false
    }

    private fun updateStatus() {
        val enabled = isServiceEnabled()
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvHint = findViewById<TextView>(R.id.tvHint)
        val btnSettings = findViewById<Button>(R.id.btnOpenSettings)
        val card = findViewById<android.view.View>(R.id.statusCard)

        if (enabled) {
            tvStatus.text = "AKTIV"
            tvStatus.setTextColor(0xFF4CAF50.toInt())
            card.setBackgroundColor(0xFF162B18.toInt())
            tvHint.text = "Das Touch-Feld ueber der Zurueck-Taste vibriert beim Druecken."
            btnSettings.text = "Bedienungshilfen-Einstellungen oeffnen"
        } else {
            tvStatus.text = "INAKTIV"
            tvStatus.setTextColor(0xFFE53935.toInt())
            card.setBackgroundColor(0xFF2B1616.toInt())
            tvHint.text = "Tippe unten auf den Button, waehle BackVibrate und aktiviere den Dienst."
            btnSettings.text = "In Bedienungshilfen aktivieren"
        }
    }

    private fun testVibration() {
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

    @SuppressLint("BatteryLife")
    private fun openBatteryOptimizationSettings() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
EOF

# 7. build.yml
cat << 'EOF' > .github/workflows/build.yml
name: Build APK

on:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build APK
        run: |
          chmod +x gradlew
          ./gradlew assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: BackVibrate-debug
          path: app/build/outputs/apk/debug/*.apk
EOF

# Git Push
git add .
git commit -m "Complete working BackVibrate implementation"
git push
rm -f setup.sh
echo "=== ALLES ERLEDIGT & GEPUSHT! ==="
