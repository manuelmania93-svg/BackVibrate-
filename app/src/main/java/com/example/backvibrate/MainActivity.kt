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
