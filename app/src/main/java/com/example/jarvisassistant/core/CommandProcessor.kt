package com.example.jarvisassistant.core

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.jarvisassistant.R
import com.example.jarvisassistant.ui.activity.PermissionManager
import java.text.SimpleDateFormat
import java.util.*

class CommandProcessor(private val context: Context) {

    private val TAG = "CommandProcessor"

    private val greetingResponses by lazy { context.resources.getStringArray(R.array.greeting_responses).toList() }
    private val howAreYouResponses by lazy { context.resources.getStringArray(R.array.how_are_you_responses).toList() }
    private val unknownResponses by lazy { context.resources.getStringArray(R.array.unknown_responses).toList() }
    private val wifiOnResponses by lazy { context.resources.getStringArray(R.array.wifi_on_responses).toList() }
    private val wifiOffResponses by lazy { context.resources.getStringArray(R.array.wifi_off_responses).toList() }
    private val soundOffResponses by lazy { context.resources.getStringArray(R.array.sound_off_responses).toList() }
    private val soundOnResponses by lazy { context.resources.getStringArray(R.array.sound_on_responses).toList() }

    @RequiresApi(Build.VERSION_CODES.M)
    fun process(input: String): String {
        val trimmedInput = input.trim().lowercase()
        Log.d(TAG, "Processing command: '$trimmedInput'")

        return when {
            trimmedInput.startsWith("напомни мне через") -> setReminder(trimmedInput)
            trimmedInput in listOf("привет", "здравствуй") -> greetingResponses.random()
            trimmedInput == "как дела" -> howAreYouResponses.random()
            trimmedInput == "покажи время" -> {
                val currentTime = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())
                "Текущее время: $currentTime, господин."
            }
            trimmedInput in listOf("включи вайфай", "включи wi-fi") -> toggleWifi(true)
            trimmedInput in listOf("выключи вайфай", "выключи wi-fi") -> toggleWifi(false)
            trimmedInput == "выключи звук" -> setMute(true)
            trimmedInput == "включи звук" -> setMute(false)
            else -> unknownResponses.random()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun setReminder(input: String): String {
        val regex = """напомни мне через (\d+)\s*(\w+)\s*(.*)""".toRegex()
        val match = regex.find(input) ?: return "Укажи время и текст, например: 'Напомни мне через 5 минут позвонить'."
        val (timeValueStr, timeUnit, reminderText) = match.destructured

        val timeValue = timeValueStr.toIntOrNull() ?: return "Укажи число, например: '5 минут'."
        if (timeValue <= 0) return "Время должно быть больше нуля, господин."

        val delayMillis = when (timeUnit.lowercase()) {
            in listOf("секунд", "секунды", "сек") -> timeValue * 1000L
            in listOf("минут", "минуты", "мин") -> timeValue * 60 * 1000L
            in listOf("час", "часа", "часов") -> timeValue * 60 * 60 * 1000L
            else -> return "Единица времени '$timeUnit' не ясна. Используй 'секунды', 'минуты', 'часы'."
        }
        val text = reminderText.ifEmpty { "Время вышло!" }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return "Нет разрешения на точные напоминания. Включи в настройках (Будильники и напоминания)."
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("message", text)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = System.currentTimeMillis() + delayMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            return "Напоминание на '$text' через $timeValue $timeUnit установлено."
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to set reminder: ${e.message}")
            return "Не удалось установить напоминание, проверь разрешения."
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun toggleWifi(enable: Boolean): String {
        if (!PermissionManager.checkPermission(context, Manifest.permission.CHANGE_WIFI_STATE)) {
            Log.w(TAG, "Missing CHANGE_WIFI_STATE permission")
            return "Господин, мне нужно разрешение на управление Wi-Fi. Проверьте настройки."
        }
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return try {
            if (enable) {
                if (wifiManager.wifiState != WifiManager.WIFI_STATE_ENABLED) {
                    wifiManager.isWifiEnabled = true
                    wifiOnResponses.random()
                } else "Wi-Fi уже включен, господин."
            } else {
                if (wifiManager.wifiState != WifiManager.WIFI_STATE_DISABLED) {
                    wifiManager.isWifiEnabled = false
                    wifiOffResponses.random()
                } else "Wi-Fi уже выключен, господин."
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to toggle Wi-Fi: ${e.message}")
            "Не могу управлять Wi-Fi, проверь разрешения."
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun setMute(mute: Boolean): String {
        if (!PermissionManager.checkPermission(context, Manifest.permission.MODIFY_AUDIO_SETTINGS)) {
            Log.w(TAG, "Missing MODIFY_AUDIO_SETTINGS permission")
            return "Господин, мне нужно разрешение на управление звуком. Проверьте настройки."
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            audioManager.ringerMode = if (mute) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
            if (mute) soundOffResponses.random() else soundOnResponses.random()
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to toggle sound: ${e.message}")
            "Не могу управлять звуком, проверь разрешения."
        }
    }
}