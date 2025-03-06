package com.example.jarvisassistant.data

import java.text.SimpleDateFormat
import java.util.*

data class Message(
    val text: String,
    val isSentByUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedTimestamp(): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault()) // Например, "08:27 PM"
        return sdf.format(Date(timestamp))
    }
}
