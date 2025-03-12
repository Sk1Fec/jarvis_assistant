package com.example.jarvisassistant.ui.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Menu
import android.view.MenuItem
import android.view.animation.OvershootInterpolator
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
import android.widget.Toast
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var textToSpeech: TextToSpeech
    private val viewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(this, getSharedPreferences("JarvisChat", MODE_PRIVATE), textToSpeech)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        textToSpeech = TextToSpeech(this, this)
        chatAdapter = ChatAdapter(viewModel, binding.chatRecyclerView)

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
            Toast.makeText(this, "Голос не инициализирован, господин!", Toast.LENGTH_LONG).show()
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
    }

    private fun setupAnimations() {
        binding.inputContainer.translationY = 200f
        binding.inputContainer.alpha = 0f
        binding.inputContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(600) // Уменьшил для скорости
            .setInterpolator(OvershootInterpolator(1.5f)) // Мягче подпрыгивание
            .start()
    }

    private fun animateSendButton() {
        binding.sendButton.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(100)
            .withEndAction {
                binding.sendButton.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
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
    }
}