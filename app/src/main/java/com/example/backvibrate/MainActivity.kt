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

class MainActivity : AppCompatActivity() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 

    private val prefs by lazy {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  getSharedPreferences("back_vibrate_prefs", MODE_PRIVATE) 

    override fun onCreate(savedInstanceState: Bundle?) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        

        findViewById<Button>(R.id.btnTestVibration).setOnClickListener {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            testVibration()
        

        findViewById<Button>(R.id.btnBattery).setOnClickListener {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            openBatteryOptimizationSettings()
        

        val rgPosition = findViewById<RadioGroup>(R.id.rgPosition)
        val rbLeft = findViewById<RadioButton>(R.id.rbLeft)
        val rbRight = findViewById<RadioButton>(R.id.rbRight)

        val isLeft = prefs.getBoolean("pos_left", true)
        if (isLeft) rbLeft.isChecked = true else rbRight.isChecked = true

        rgPosition.setOnCheckedChangeListener {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  _, checkedId ->
            prefs.edit().putBoolean("pos_left", checkedId == R.id.rbLeft).apply()
            notifyService()
        

        val switchPreview = findViewById<Switch>(R.id.switchPreview)
        switchPreview.isChecked = prefs.getBoolean("preview_overlay", false)
        switchPreview.setOnCheckedChangeListener {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts}  _, isChecked ->
            prefs.edit().putBoolean("preview_overlay", isChecked).apply()
            notifyService()
        
    

    override fun onResume() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        super.onResume()
        updateStatus()
    

    private fun notifyService() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        BackVibrateAccessibilityService.instance?.updateConfig()
    

    private fun isServiceEnabled(): Boolean {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        val cn = "$packageName/$BackVibrateAccessibilityService::class.java.name"
        val flat = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(flat)
        while (splitter.hasNext()) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            if (splitter.next().equals(cn, ignoreCase = true)) return true
        
        return false
    

    private fun updateStatus() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        val enabled = isServiceEnabled()
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvHint = findViewById<TextView>(R.id.tvHint)
        val btnSettings = findViewById<Button>(R.id.btnOpenSettings)
        val card = findViewById<android.view.View>(R.id.statusCard)

        if (enabled) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            tvStatus.text = "AKTIV"
            tvStatus.setTextColor(0xFF4CAF50.toInt())
            card.setBackgroundColor(0xFF162B18.toInt())
            tvHint.text = "Das Touch-Feld ueber der Zurueck-Taste vibriert beim Druecken."
            btnSettings.text = "Bedienungshilfen-Einstellungen oeffnen"
         else {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            tvStatus.text = "INAKTIV"
            tvStatus.setTextColor(0xFFE53935.toInt())
            card.setBackgroundColor(0xFF2B1616.toInt())
            tvHint.text = "Tippe unten auf den Button, waehle BackVibrate und aktiviere den Dienst."
            btnSettings.text = "In Bedienungshilfen aktivieren"
        
    

    private fun testVibration() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            val vm = getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
            )
         else {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            Suppress("DEPRECATION")
            val v = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                v?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
             else {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                Suppress("DEPRECATION")
                v?.vibrate(45)
            
        
    

    SuppressLint("BatteryLife")
    private fun openBatteryOptimizationSettings() {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
        try {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            val pm = getSystemService(PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                    data = Uri.parse("package:$packageName")
                
                startActivity(intent)
             else {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            
         catch (_: Exception) {.g{it{,hub,ignore},radle},README.md,app,build.gradle.kts,gradle{,.properties,w{,.bat}},settings.gradle.kts} 
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        
    

