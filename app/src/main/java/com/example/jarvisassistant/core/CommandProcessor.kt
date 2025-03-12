package com.example.jarvisassistant.core

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
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

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
    private val isSarcasmEnabled: Boolean get() = prefs.getBoolean("sarcasm_enabled", false)

    @RequiresApi(Build.VERSION_CODES.M)
    fun process(input: String): String {
        val trimmedInput = input.trim().lowercase()
        Log.d(TAG, "Processing command: '$trimmedInput'")

        val baseResponse = when {
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

        return if (isSarcasmEnabled) "$baseResponse ${JarvisPersonality.getWittyResponse()}" else baseResponse
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

        if (delayMillis > AlarmManager.INTERVAL_DAY * 365) {
            return "Слишком долгое напоминание, максимум год, господин."
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            PermissionManager.requestExactAlarmPermission(context as AppCompatActivity) { granted ->
                if (!granted) Log.w(TAG, "Exact alarm permission not granted")
            }
            return "Дай разрешение на точные будильники в настройках, господин!"
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
            return "Не могу установить напоминание, проверь разрешения!"
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun toggleWifi(enable: Boolean): String {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!PermissionManager.checkPermission(context, Manifest.permission.CHANGE_WIFI_STATE) ||
            !PermissionManager.checkPermission(context, Manifest.permission.ACCESS_WIFI_STATE)) {
            Log.w(TAG, "Wi-Fi permissions missing: CHANGE_WIFI_STATE=${PermissionManager.checkPermission(context, Manifest.permission.CHANGE_WIFI_STATE)}, ACCESS_WIFI_STATE=${PermissionManager.checkPermission(context, Manifest.permission.ACCESS_WIFI_STATE)}")
            PermissionManager.requestPermissions(context as AppCompatActivity) { _ -> }
            return "Дай разрешения на управление и доступ к Wi-Fi, господин!"
        }

        try {
            val currentState = wifiManager.isWifiEnabled
            Log.d(TAG, "Current Wi-Fi state: $currentState")
            if (currentState != enable) {
                wifiManager.isWifiEnabled = enable
                Thread.sleep(1000) // Ждём обновления состояния
                val newState = wifiManager.isWifiEnabled
                Log.d(TAG, "New Wi-Fi state: $newState")
                if (newState == enable) {
                    return if (enable) wifiOnResponses.random() else wifiOffResponses.random()
                } else {
                    return "Не удалось ${if (enable) "включить" else "выключить"} Wi-Fi, господин!"
                }
            } else {
                return if (enable) "Wi-Fi уже включён, господин!" else "Wi-Fi уже выключен, господин!"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle Wi-Fi: ${e.message}")
            return "Wi-Fi не подчиняется, господин! Возможно, системное ограничение."
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun setMute(mute: Boolean): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (!PermissionManager.checkPermission(context, Manifest.permission.MODIFY_AUDIO_SETTINGS)) {
            Log.w(TAG, "Audio permission missing")
            PermissionManager.requestPermissions(context as AppCompatActivity) { _ -> }
            return "Дай разрешение на управление звуком, господин!"
        }

        try {
            val currentMode = audioManager.ringerMode
            Log.d(TAG, "Current ringer mode: $currentMode")
            if (mute && currentMode != AudioManager.RINGER_MODE_VIBRATE) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                Log.d(TAG, "Switched to vibrate mode")
                return soundOffResponses.random()
            } else if (!mute && currentMode != AudioManager.RINGER_MODE_NORMAL) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                audioManager.setStreamVolume(
                    AudioManager.STREAM_RING,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_RING) / 2,
                    0
                )
                audioManager.setStreamVolume(
                    AudioManager.STREAM_NOTIFICATION,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) / 2,
                    0
                )
                Log.d(TAG, "Switched to normal mode")
                return soundOnResponses.random()
            } else {
                return if (mute) "Уже в режиме вибрации, господин!" else "Звук уже включён, господин!"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle sound: ${e.message}")
            return "Звук не хочет слушаться, господин! Проверь настройки."
        }
    }
}