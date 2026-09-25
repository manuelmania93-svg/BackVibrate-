package com.example.backvibrate

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
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
            openBatterySettings()
        }

        findViewById<Button>(R.id.btnHonorAppStart).setOnClickListener {
            openHonorSettings()
        }

        val rgPos = findViewById<RadioGroup>(R.id.rgPosition)
        val rbLeft = findViewById<RadioButton>(R.id.rbLeft)
        val rbRight = findViewById<RadioButton>(R.id.rbRight)
        if (prefs.getBoolean("pos_left", false)) rbLeft.isChecked = true else rbRight.isChecked = true
        rgPos.setOnCheckedChangeListener { _, id ->
            prefs.edit().putBoolean("pos_left", id == R.id.rbLeft).apply()
            notifyService()
        }

        val rgStrength = findViewById<RadioGroup>(R.id.rgStrength)
        when (prefs.getInt("vib_duration", 45)) {
            25 -> findViewById<RadioButton>(R.id.rbSoft).isChecked = true
            70 -> findViewById<RadioButton>(R.id.rbStrong).isChecked = true
            else -> findViewById<RadioButton>(R.id.rbNormal).isChecked = true
        }

        rgStrength.setOnCheckedChangeListener { _, id ->
            val d = if (id == R.id.rbSoft) 25 else if (id == R.id.rbStrong) 70 else 45
            prefs.edit().putInt("vib_duration", d).apply()
            notifyService()
        }

        val sw = findViewById<Switch>(R.id.switchPreview)
        sw.isChecked = prefs.getBoolean("preview_overlay", false)
        sw.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("preview_overlay", checked).apply()
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
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return s.contains(packageName)
    }

    private fun updateStatus() {
        val en = isServiceEnabled()
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvHint = findViewById<TextView>(R.id.tvHint)
        val btn = findViewById<Button>(R.id.btnOpenSettings)
        val card = findViewById<android.view.View>(R.id.statusCard)

        if (en) {
            tvStatus.text = "AKTIV"
            tvStatus.setTextColor(0xFF4CAF50.toInt())
            card.setBackgroundColor(0xFF162B18.toInt())
            tvHint.text = "Das Touch-Feld ueber der Zurueck-Taste vibriert beim Druecken."
            btn.text = "Bedienungshilfen oeffnen"
        } else {
            tvStatus.text = "INAKTIV"
            tvStatus.setTextColor(0xFFE53935.toInt())
            card.setBackgroundColor(0xFF2B1616.toInt())
            tvHint.text = "Tippe unten auf den Button und aktiviere rückwartsvibiration."
            btn.text = "In Bedienungshilfen aktivieren"
        }
    }

    private fun testVibration() {
        val d = prefs.getInt("vib_duration", 45).toLong()
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

    private fun openHonorSettings() {
        val list = listOf(
            Intent().setComponent(ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.optimize.bootstart.BootStartActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"))
        )
        for (i in list) {
            try {
                startActivity(i)
                return
            } catch (_: Exception) {}
        }
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }

    @SuppressLint("BatteryLife")
    private fun openBatterySettings() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } else {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}