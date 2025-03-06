package com.example.jarvisassistant.ui.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jarvisassistant.databinding.ActivityMainBinding
import com.example.jarvisassistant.ui.adapter.ChatAdapter
import com.example.jarvisassistant.viewmodel.ChatViewModel
import com.example.jarvisassistant.R
import com.google.android.material.snackbar.Snackbar
import android.view.inputmethod.InputMethodManager
import com.example.jarvisassistant.viewmodel.ChatViewModelFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private val viewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(this, getSharedPreferences("JarvisChat", MODE_PRIVATE))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatAdapter = ChatAdapter(viewModel, binding.chatRecyclerView)

        setupRecyclerView()
        setupPermissions()
        setupListeners()
        setupAnimations()
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
                    binding.chatRecyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }

        viewModel.isTyping.observe(this) { isTyping ->
            chatAdapter.onTypingChanged(isTyping)
            if (!isTyping) {
                binding.chatRecyclerView.post {
                    binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                }
            }
        }
    }

    private fun setupPermissions() {
        PermissionManager.requestPermissions(this) { allGranted ->
            if (!allGranted) {
                showPermissionRationale("Для работы нужны уведомления. Включи в настройках.", R.string.permissions_button)
            }
        }

        PermissionManager.requestExactAlarmPermission(this) { granted ->
            if (!granted) {
                showPermissionRationale("Для точных напоминаний нужно разрешение в настройках.", R.string.permissions_button)
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
        binding.settingsButton.setOnClickListener {
            openSettings()
        }
        binding.clearChatButton.setOnClickListener {
            binding.chatRecyclerView.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    viewModel.clearMessages {
                        chatAdapter.notifyDataSetChanged()
                        binding.chatRecyclerView.alpha = 1f
                        binding.chatRecyclerView.post {
                            binding.chatRecyclerView.scrollToPosition(0)
                        }
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
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun animateSendButton() {
        binding.sendButton.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
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

    private fun showPermissionRationale(message: String, actionResId: Int) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(getString(actionResId)) { openSettings() }
            .show()
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
}