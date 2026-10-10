package com.example.backvibrate

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BackVibrate"
    }

    private val prefs by lazy { getSharedPreferences("back_vibrate_prefs", MODE_PRIVATE) }

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updateProtectionStatus()
        }

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

        val cbAutostart = findViewById<CheckBox>(R.id.cbAutostartDone)
        cbAutostart.isChecked = prefs.getBoolean("autostart_confirmed", false)
        cbAutostart.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("autostart_confirmed", checked).apply()
            updateProtectionStatus()
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

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateProtectionStatus()
    }

    private fun notifyService() {
        BackVibrateAccessibilityService.instance?.updateConfig()
    }

    private fun isServiceEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return s.contains(packageName)
    }

    private fun isBatteryExempt(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm != null && pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
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

    private fun updateProtectionStatus() {
        val batteryOk = isBatteryExempt()
        val notifOk = areNotificationsEnabled()
        val autostartOk = prefs.getBoolean("autostart_confirmed", false)

        val okColor = 0xFF4CAF50.toInt()
        val warnColor = 0xFFFFB300.toInt()

        findViewById<TextView>(R.id.tvBatteryStatus).apply {
            text = if (batteryOk) "✓ Akku-Optimierung: uneingeschränkt" else "⚠ Akku-Optimierung: noch eingeschränkt"
            setTextColor(if (batteryOk) okColor else warnColor)
        }

        findViewById<TextView>(R.id.tvNotifStatus).apply {
            text = if (notifOk) "✓ Benachrichtigungen: erlaubt" else "⚠ Benachrichtigungen: blockiert (Neustart-Warnung nötig)"
            setTextColor(if (notifOk) okColor else warnColor)
        }

        val missing = mutableListOf<String>()
        if (!batteryOk) missing.add("Akku")
        if (!autostartOk) missing.add("App-Start")
        if (!notifOk) missing.add("Benachrichtigungen")

        findViewById<TextView>(R.id.tvProtectionSummary).apply {
            if (missing.isEmpty()) {
                text = "Neustart-Schutz: vollständig aktiv"
                setTextColor(okColor)
            } else {
                text = "Neustart-Schutz: offen - ${missing.joinToString(", ")}"
                setTextColor(warnColor)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun testVibration() {
        val d = prefs.getInt("vib_duration", 45).toLong()
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "testVibration failed", e)
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
            } catch (e: Exception) {
                Log.d(TAG, "Honor settings intent not available: ${i.component}")
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e: Exception) {
            Log.e(TAG, "Could not open any settings screen", e)
        }
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
        } catch (e: Exception) {
            Log.e(TAG, "Battery settings request failed, opening fallback", e)
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                Log.e(TAG, "Could not open battery settings", e2)
            }
        }
    }
}
