package com.example.jarvisassistant.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.jarvisassistant.core.JarvisService
import com.example.jarvisassistant.databinding.ActivitySettingsBinding
import android.content.SharedPreferences
import android.content.Intent
import android.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        setupUI()
        setupListeners()
        loadSettings()
    }

    private fun setupUI() {
        supportActionBar?.hide()
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener { finish() }
        binding.permissionsButton.setOnClickListener {
            PermissionManager.showAppSettings(this, "Перейдите в настройки, господин!")
        }
        binding.sarcasmSwitch.setOnCheckedChangeListener { _, isChecked ->
            val wasChecked = prefs.getBoolean("sarcasm_enabled", false)
            if (wasChecked != isChecked) {
                prefs.edit().putBoolean("sarcasm_enabled", isChecked).apply()
                Toast.makeText(this, if (isChecked) "Сарказм активирован!" else "Сарказм отключён!", Toast.LENGTH_SHORT).show()
            }
        }
        binding.voiceSwitch.setOnCheckedChangeListener { _, isChecked ->
            val wasChecked = prefs.getBoolean("voice_enabled", false)
            if (wasChecked != isChecked) {
                prefs.edit().putBoolean("voice_enabled", isChecked).apply()
                Toast.makeText(this, if (isChecked) "Голос включён!" else "Голос выключен!", Toast.LENGTH_SHORT).show()
            }
        }
        binding.voiceRecognitionSwitch.setOnCheckedChangeListener { _, isChecked ->
            val wasChecked = prefs.getBoolean("voice_recognition_enabled", true) // По умолчанию включено
            if (wasChecked != isChecked) {
                prefs.edit().putBoolean("voice_recognition_enabled", isChecked).apply()
                val serviceIntent = Intent(this, JarvisService::class.java)
                if (isChecked) {
                    startService(serviceIntent)
                    Toast.makeText(this, "Голосовое распознавание включено!", Toast.LENGTH_SHORT).show()
                } else {
                    stopService(serviceIntent)
                    Toast.makeText(this, "Голосовое распознавание выключено!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadSettings() {
        binding.sarcasmSwitch.isChecked = prefs.getBoolean("sarcasm_enabled", false)
        binding.voiceSwitch.isChecked = prefs.getBoolean("voice_enabled", false)
        binding.voiceRecognitionSwitch.isChecked = prefs.getBoolean("voice_recognition_enabled", true)
    }
}