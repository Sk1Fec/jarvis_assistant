package com.example.jarvisassistant.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.example.jarvisassistant.R
import com.example.jarvisassistant.core.CommandProcessor
import com.example.jarvisassistant.data.Message
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class ChatViewModel(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val textToSpeech: TextToSpeech
) : ViewModel() {

    companion object {
        const val MAX_MESSAGES = 50 // Ограничение на количество сообщений
    }

    private val _messages = MutableLiveData<MutableList<Message>>(loadMessages().takeLast(MAX_MESSAGES).toMutableList())
    val messages: LiveData<MutableList<Message>> get() = _messages

    private val _isTyping = MutableLiveData<Boolean>(false)
    val isTyping: LiveData<Boolean> get() = _isTyping

    private val scope = CoroutineScope(Dispatchers.Main)
    private val commandProcessor = CommandProcessor(context)

    private val isVoiceEnabled: Boolean
        get() = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("voice_enabled", false)

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = Message("${context.getString(R.string.user_prefix)}$text", true, System.currentTimeMillis())
        val currentMessages = _messages.value ?: mutableListOf()
        currentMessages.add(userMessage)
        if (currentMessages.size > MAX_MESSAGES) currentMessages.removeAt(0) // Удаляем старое сообщение
        _messages.postValue(currentMessages)

        _isTyping.postValue(true)
        Log.d("ChatViewModel", "User message added: '$text', isTyping: true")

        scope.launch(Dispatchers.IO) {
            val response = commandProcessor.process(text)
            val delayTime = maxOf(500L, response.length * 10L)
            delay(delayTime)
            launch(Dispatchers.Main) {
                _isTyping.postValue(false)
                Log.d("ChatViewModel", "Response from Jarvis: '$response'")
                val jarvisMessage = Message("${context.getString(R.string.jarvis_prefix)}$response", false, System.currentTimeMillis())
                val updatedMessages = _messages.value ?: mutableListOf()
                updatedMessages.add(jarvisMessage)
                if (updatedMessages.size > MAX_MESSAGES) updatedMessages.removeAt(0)
                _messages.postValue(updatedMessages)
                saveMessages(updatedMessages)

                if (isVoiceEnabled) {
                    textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }
    }

    fun addWittyMessage(message: String) {
        val jarvisMessage = Message("${context.getString(R.string.jarvis_prefix)}$message", false, System.currentTimeMillis())
        val updatedMessages = _messages.value ?: mutableListOf()
        updatedMessages.add(jarvisMessage)
        if (updatedMessages.size > MAX_MESSAGES) updatedMessages.removeAt(0)
        _messages.postValue(updatedMessages)
        saveMessages(updatedMessages)

        if (isVoiceEnabled) {
            textToSpeech.speak(message, TextToSpeech.QUEUE_ADD, null, null)
        }
    }

    fun clearMessages(onCleared: () -> Unit = {}) {
        val emptyList = mutableListOf<Message>()
        _messages.postValue(emptyList)
        saveMessages(emptyList)
        onCleared()
    }

    private fun loadMessages(): List<Message> {
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
        val json = Gson().toJson(messages.takeLast(MAX_MESSAGES)) // Сохраняем только последние MAX_MESSAGES
        prefs.edit().putString("messages", json).apply()
    }

    fun getFormattedTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

class ChatViewModelFactory(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val textToSpeech: TextToSpeech
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(context, prefs, textToSpeech) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}