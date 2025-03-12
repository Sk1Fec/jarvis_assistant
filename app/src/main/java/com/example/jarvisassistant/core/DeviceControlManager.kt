package com.example.jarvisassistant.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.jarvisassistant.ui.activity.PermissionManager

class DeviceControlManager(private val context: Context) {

    private val TAG = "DeviceControlManager"

    @RequiresApi(Build.VERSION_CODES.M)
    fun toggleWifi(enable: Boolean, wifiOnResponses: List<String>, wifiOffResponses: List<String>): String {
        if (!PermissionManager.checkPermission(context, Manifest.permission.CHANGE_WIFI_STATE) ||
            !PermissionManager.checkPermission(context, Manifest.permission.ACCESS_WIFI_STATE)) {
            PermissionManager.requestPermissions(context as AppCompatActivity) { granted ->
                if (!granted) Log.w(TAG, "Wi-Fi permissions denied")
            }
            return "Дай разрешения на управление и доступ к Wi-Fi, господин!"
        }

        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        try {
            val currentState = wifiManager.isWifiEnabled
            if (currentState != enable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Для Android 10+ открываем панель настроек Wi-Fi
                    val intent = Intent(Settings.Panel.ACTION_WIFI)
                    context.startActivity(intent)
                    return "Господин, включите или выключите Wi-Fi в настройках!"
                } else {
                    // Для Android 9 и ниже прямое управление
                    wifiManager.isWifiEnabled = enable
                    Thread.sleep(1000) // Ждем изменения состояния
                    val newState = wifiManager.isWifiEnabled
                    return if (newState == enable) {
                        if (enable) wifiOnResponses.random() else wifiOffResponses.random()
                    } else {
                        "Не удалось ${if (enable) "включить" else "выключить"} Wi-Fi, господин!"
                    }
                }
            } else {
                return if (enable) "Wi-Fi уже включён, господин!" else "Wi-Fi уже выключен, господин!"
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in toggleWifi: ${e.message}")
            return "Нет прав на управление Wi-Fi, проверь разрешения!"
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in toggleWifi: ${e.message}")
            return "Что-то пошло не так с Wi-Fi, господин!"
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun setMute(mute: Boolean, soundOffResponses: List<String>, soundOnResponses: List<String>): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (!PermissionManager.checkPermission(context, Manifest.permission.MODIFY_AUDIO_SETTINGS)) {
            PermissionManager.requestPermissions(context as AppCompatActivity) { _ -> }
            return "Дай разрешение на управление звуком, господин!"
        }

        try {
            val currentMode = audioManager.ringerMode
            if (mute && currentMode != AudioManager.RINGER_MODE_VIBRATE) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
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
                return soundOnResponses.random()
            } else {
                return if (mute) "Уже в режиме вибрации, господин!" else "Звук уже включён, господин!"
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in setMute: ${e.message}")
            return "Нет прав на управление звуком, проверь разрешения!"
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in setMute: ${e.message}")
            return "Звук не хочет слушаться, господин! Проверь настройки."
        }
    }
}