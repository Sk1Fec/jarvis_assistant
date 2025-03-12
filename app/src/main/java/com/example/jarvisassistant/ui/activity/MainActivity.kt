package com.example.jarvisassistant.ui.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Menu
import android.view.MenuItem
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jarvisassistant.databinding.ActivityMainBinding
import com.example.jarvisassistant.ui.adapter.ChatAdapter
import com.example.jarvisassistant.viewmodel.ChatViewModel
import com.example.jarvisassistant.R
import com.example.jarvisassistant.viewmodel.ChatViewModelFactory
import com.google.android.material.snackbar.Snackbar
import android.view.inputmethod.InputMethodManager
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.Locale
import android.animation.ValueAnimator
import android.view.View

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private val viewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(this, getSharedPreferences("JarvisChat", MODE_PRIVATE), textToSpeech)
    }

    private var micPulseAnimator: ValueAnimator? = null
    private var isMicActive = false // Флаг для отслеживания состояния микрофона

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        textToSpeech = TextToSpeech(this, this)
        chatAdapter = ChatAdapter(viewModel, binding.chatRecyclerView)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        setupRecyclerView()
        setupPermissions()
        setupListeners()
        setupAnimations()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale("ru", "RU"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Русский язык не поддерживается для голоса.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Ошибка инициализации голоса. Повторяю...", Toast.LENGTH_LONG).show()
            textToSpeech = TextToSpeech(this, this)
        }
    }

    private fun setupRecyclerView() {
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.chatRecyclerView.adapter = chatAdapter

        viewModel.messages.observe(this) { messages ->
            chatAdapter.updateMessages(messages)
            binding.chatRecyclerView.post {
                if (messages.isNotEmpty()) {
                    binding.chatRecyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        }

        viewModel.isTyping.observe(this) { isTyping ->
            chatAdapter.onTypingChanged(isTyping)
            if (!isTyping && chatAdapter.itemCount > 0) {
                binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setupPermissions() {
        PermissionManager.requestPermissions(this) { allGranted ->
            if (!allGranted) {
                Snackbar.make(binding.root, "Дай мне разрешения, господин!", Snackbar.LENGTH_LONG)
                    .setAction("Настройки") { openSettings() }
                    .show()
            }
        }

        PermissionManager.requestExactAlarmPermission(this) { granted ->
            if (!granted) {
                Snackbar.make(binding.root, "Точные напоминания требуют разрешения!", Snackbar.LENGTH_LONG)
                    .setAction("Настройки") { openSettings() }
                    .show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("NotifyDataSetChanged")
    private fun setupListeners() {
        binding.sendButton.setOnClickListener {
            sendMessage()
            animateSendButton()
        }
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                animateSendButton()
                true
            } else false
        }
        binding.settingsButton.setOnClickListener { openSettings() }
        binding.clearChatButton.setOnClickListener {
            binding.chatRecyclerView.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    viewModel.clearMessages {
                        chatAdapter.notifyDataSetChanged()
                        binding.chatRecyclerView.alpha = 1f
                        Toast.makeText(this, "Чат очищен, господин!", Toast.LENGTH_SHORT).show()
                    }
                }
                .start()
        }
        binding.micButton.setOnClickListener {
            if (PermissionManager.checkPermission(this, Manifest.permission.RECORD_AUDIO)) {
                toggleSpeechRecognition()
            } else {
                PermissionManager.requestPermissions(this) { granted ->
                    if (granted) toggleSpeechRecognition()
                    else Toast.makeText(this, "Разрешение на микрофон нужно, господин!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.messageInput.hint = "Говорите, господин..."
                startMicAnimation()
            }
            override fun onBeginningOfSpeech() {
                binding.messageInput.hint = "Слушаю..."
            }
            override fun onRmsChanged(rmsdB: Float) {
                val scale = 1f + (rmsdB / 20f).coerceIn(0f, 0.5f)
                binding.micButton.scaleX = scale
                binding.micButton.scaleY = scale
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                binding.messageInput.hint = "Команда для Jarvis"
                stopMicAnimation()
                isMicActive = false // Сбрасываем флаг
            }
            override fun onError(error: Int) {
                stopMicAnimation()
                Toast.makeText(this@MainActivity, "Ошибка распознавания: $error", Toast.LENGTH_SHORT).show()
                isMicActive = false // Сбрасываем флаг при ошибке
            }
            override fun onResults(results: Bundle?) {
                stopMicAnimation()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    binding.messageInput.setText(recognizedText)
                    sendMessage()
                }
                isMicActive = false // Сбрасываем флаг после результата
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun toggleSpeechRecognition() {
        if (isMicActive) {
            speechRecognizer.stopListening() // Останавливаем распознавание
            stopMicAnimation()
            binding.messageInput.hint = "Команда для Jarvis"
            Toast.makeText(this, "Микрофон выключен, господин!", Toast.LENGTH_SHORT).show()
            isMicActive = false
        } else {
            speechRecognizer.startListening(recognizerIntent)
            Toast.makeText(this, "Говорите, господин!", Toast.LENGTH_SHORT).show()
            isMicActive = true
        }
    }

    private fun startMicAnimation() {
        micPulseAnimator?.cancel()
        micPulseAnimator = ValueAnimator.ofFloat(1f, 1.15f).apply {
            duration = 500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                binding.micButton.scaleX = value
                binding.micButton.scaleY = value
            }
            start()
        }
        binding.micButton.setBackgroundResource(R.drawable.mic_active_background)
    }

    private fun stopMicAnimation() {
        micPulseAnimator?.cancel()
        binding.micButton.scaleX = 1f
        binding.micButton.scaleY = 1f
        binding.micButton.setBackgroundResource(R.drawable.send_button_background)
    }

    private fun setupAnimations() {
        binding.inputContainer.translationY = 200f
        binding.inputContainer.alpha = 0f
        binding.inputContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(600)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()
    }

    private fun animateSendButton() {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.sendButton, "scaleX", 1f, 1.1f, 1f),
                ObjectAnimator.ofFloat(binding.sendButton, "scaleY", 1f, 1.1f, 1f)
            )
            duration = 200
            interpolator = OvershootInterpolator(0.5f)
            start()
        }
    }

    private fun sendMessage() {
        val input = binding.messageInput.text.toString().trim()
        if (input.isNotEmpty()) {
            viewModel.sendMessage(input)
            binding.messageInput.text.clear()
            hideKeyboard()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.messageInput.windowToken, 0)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                openSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
        speechRecognizer.destroy()
        micPulseAnimator?.cancel()
        if (isMicActive) speechRecognizer.stopListening() // Останавливаем при уничтожении активности
    }
}