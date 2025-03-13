package com.example.jarvisassistant.core

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.example.jarvisassistant.R
import com.example.jarvisassistant.ui.activity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class JarvisService : Service() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("voice_recognition_enabled", true)) {
            stopSelf()
            return
        }
        setupSpeechRecognizer()
        startForegroundNotification()
        startListening()
        startRandomMessages()
        requestBatteryOptimizationExemption()
    }

    private var notificationCounter = 0
    private val listeningTexts = arrayOf("Слушаю вас, господин...", "Слушаю... 👂", "Ожидаю команды...")

    private fun startForegroundNotification() {
        val channelId = "jarvis_service_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelName = "Jarvis Service"
            val importance = android.app.NotificationManager.IMPORTANCE_LOW
            val channel = android.app.NotificationChannel(channelId, channelName, importance).apply {
                description = "Канал для службы Jarvis"
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        updateForegroundNotification()

        scope.launch {
            while (true) {
                delay(2000)
                updateForegroundNotification()
            }
        }
    }

    private fun updateForegroundNotification() {
        notificationCounter = (notificationCounter + 1) % listeningTexts.size
        val stopIntent = Intent(this, JarvisService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "jarvis_service_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Jarvis Assistant")
            .setContentText(listeningTexts[notificationCounter])
            .addAction(R.drawable.ic_clear, "Остановить", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("JarvisService", "Speech recognition not available on this device")
            stopSelf()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                Log.d("JarvisService", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("JarvisService", "Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("JarvisService", "Speech ended")
                if (isListening) restartListeningWithDelay()
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Не услышал активацию."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознавание занято."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет доступа к микрофону."
                    else -> "Ошибка распознавания: $error"
                }
                Log.e("JarvisService", errorMessage)
                if (isListening) restartListeningWithDelay()
            }

            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0].lowercase()
                    if (recognizedText.contains("эй, джарвис") || recognizedText.contains("эй джарвис")) {
                        Log.d("JarvisService", "Activation phrase detected: $recognizedText")
                        speechRecognizer.stopListening()
                        isListening = false
                        val intent = Intent(this@JarvisService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra("ACTIVATED_BY_VOICE", true)
                        }
                        startActivity(intent)
                    }
                }
                if (isListening) restartListeningWithDelay()
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0].lowercase()
                    if (recognizedText.contains("эй, джарвис") || recognizedText.contains("эй джарвис")) {
                        Log.d("JarvisService", "Activation phrase detected (partial): $recognizedText")
                        speechRecognizer.stopListening()
                        isListening = false
                        val intent = Intent(this@JarvisService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra("ACTIVATED_BY_VOICE", true)
                        }
                        startActivity(intent)
                    }
                }
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
    }

    private fun startListening() {
        try {
            speechRecognizer.startListening(recognizerIntent)
            isListening = true
            Log.d("JarvisService", "Listening started")
        } catch (e: Exception) {
            Log.e("JarvisService", "Error starting listening: ${e.message}")
            restartListeningWithDelay()
        }
    }

    private fun restartListeningWithDelay() {
        scope.launch {
            delay(1000)
            if (isListening) startListening()
        }
    }

    private fun startRandomMessages() {
        scope.launch {
            while (true) {
                delay(Random.nextLong(300000, 900000)) // 5-15 минут
                val wittyResponse = JarvisPersonality.getWittyResponse()
                Log.d("JarvisService", "Sending witty message: $wittyResponse")
                sendNotification(wittyResponse)
                val intent = Intent("com.example.jarvisassistant.NEW_WITTY_MESSAGE").apply {
                    putExtra("message", wittyResponse)
                }
                sendBroadcast(intent)
            }
        }
    }

    private fun sendNotification(message: String) {
        val channelId = "jarvis_witty_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelName = "Jarvis Witty Messages"
            val importance = android.app.NotificationManager.IMPORTANCE_DEFAULT
            val channel = android.app.NotificationChannel(channelId, channelName, importance).apply {
                description = "Канал для остроумных сообщений от Jarvis"
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Jarvis говорит...")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(this)) {
            notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            if (!isIgnoringBatteryOptimizations()) {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
            speechRecognizer.destroy()
        }
        Log.d("JarvisService", "Service destroyed")
    }
}