package com.example.jarvisassistant.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.jarvisassistant.databinding.ActivitySettingsBinding
import com.example.jarvisassistant.R
import android.content.SharedPreferences
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
        binding.permissionsButton.setOnClickListener {
            PermissionManager.showAppSettings(this, "Перейдите в настройки для предоставления всех разрешений.")
        }
        binding.sarcasmSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sarcasm_enabled", isChecked).apply()
            // TODO: Реализовать логику сарказма в CommandProcessor
        }
        binding.voiceSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("voice_enabled", isChecked).apply()
            // TODO: Реализовать голосовые ответы через TextToSpeech
        }
    }

    private fun loadSettings() {
        binding.sarcasmSwitch.isChecked = prefs.getBoolean("sarcasm_enabled", false)
        binding.voiceSwitch.isChecked = prefs.getBoolean("voice_enabled", false)
    }
}