package com.example.jarvisassistant.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.jarvisassistant.R
import com.example.jarvisassistant.core.CommandProcessor
import com.example.jarvisassistant.data.Message
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(private val context: Context, private val prefs: SharedPreferences) : ViewModel() {

    private val _messages = MutableLiveData<MutableList<Message>>(loadMessages())
    val messages: LiveData<MutableList<Message>> get() = _messages

    private val _isTyping = MutableLiveData<Boolean>(false)
    val isTyping: LiveData<Boolean> get() = _isTyping

    private val scope = CoroutineScope(Dispatchers.Main)
    private val commandProcessor = CommandProcessor(context)

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = Message("${context.getString(R.string.user_prefix)}$text", true, System.currentTimeMillis())
        val currentMessages = _messages.value ?: mutableListOf()
        currentMessages.add(userMessage)
        _messages.postValue(currentMessages)

        _isTyping.postValue(true)
        Log.d("ChatViewModel", "Processing input: '$text', simulateTyping: true")

        scope.launch(Dispatchers.IO) {
            val response = commandProcessor.process(text)
            launch(Dispatchers.Main) {
                _isTyping.postValue(false)
                Log.d("ChatViewModel", "Response from Jarvis: '$response'")
                val jarvisMessage = Message("${context.getString(R.string.jarvis_prefix)}$response", false, System.currentTimeMillis())
                val updatedMessages = _messages.value ?: mutableListOf()
                updatedMessages.add(jarvisMessage)
                _messages.postValue(updatedMessages)
                saveMessages(updatedMessages)
            }
        }
    }

    fun clearMessages(onCleared: () -> Unit = {}) {
        _messages.postValue(mutableListOf())
        saveMessages(emptyList())
        onCleared()
    }

    private fun loadMessages(): MutableList<Message> {
        val savedMessages = prefs.getString("messages", null)?.let { json ->
            try {
                val type = object : TypeToken<MutableList<Message>>() {}.type
                Gson().fromJson<MutableList<Message>>(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error deserializing messages: ${e.message}")
                mutableListOf()
            }
        } ?: mutableListOf()
        return savedMessages
    }

    private fun saveMessages(messages: List<Message>) {
        val json = Gson().toJson(messages)
        prefs.edit().putString("messages", json).apply()
    }

    fun getFormattedTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) // Добавлена дата для старых сообщений
        return sdf.format(Date(timestamp))
    }
}

// Фабрика для создания ChatViewModel с Context и SharedPreferences
class ChatViewModelFactory(
    private val context: Context,
    private val prefs: SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(context, prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}