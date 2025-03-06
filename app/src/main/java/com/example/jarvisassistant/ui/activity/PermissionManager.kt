package com.example.jarvisassistant.ui.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

object PermissionManager {

    private const val REQUEST_PERMISSIONS = 100
    private const val REQUEST_ALARM_PERMISSION = 101

    fun requestPermissions(activity: AppCompatActivity, onResult: (Boolean) -> Unit) {
        var permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                val allGranted = results.all { it.value }
                onResult(allGranted)
                if (!allGranted) {
                    showAppSettings(activity, "Некоторые разрешения не предоставлены. Включи их в настройках.")
                }
            }.launch(permissionsToRequest)
        } else {
            onResult(true)
        }
    }

    fun requestExactAlarmPermission(activity: AppCompatActivity, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    onResult(alarmManager.canScheduleExactAlarms())
                }.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            } else {
                onResult(true)
            }
        } else {
            onResult(true)
        }
    }

    fun checkPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun showAppSettings(activity: AppCompatActivity, message: String) {
        Snackbar.make(activity.findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
            .setAction("Настройки") {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", activity.packageName, null)
                })
            }
            .show()
    }
}