package com.example.jarvisassistant.ui.adapter

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.jarvisassistant.R
import com.example.jarvisassistant.data.Message
import com.example.jarvisassistant.viewmodel.ChatViewModel

class ChatAdapter(
    private val viewModel: ChatViewModel,
    private val recyclerView: RecyclerView
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_MESSAGE = 0
        private const val VIEW_TYPE_TYPING = 1
        private const val TAG = "ChatAdapter"
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.message_text)
        val timestampText: TextView = itemView.findViewById(R.id.timestamp_text)
        val messageCard: CardView = itemView.findViewById(R.id.message_card)
        var isAnimated = false
    }

    class TypingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val lines = listOf(
            itemView.findViewById<View>(R.id.line1),
            itemView.findViewById<View>(R.id.line2),
            itemView.findViewById<View>(R.id.line3)
        )

        fun startAnimation() {
            lines.forEachIndexed { index, line ->
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 800
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    startDelay = (index * 200).toLong()
                    addUpdateListener { animation ->
                        val value = animation.animatedValue as Float
                        line.scaleX = 0.8f + value * 0.4f
                        line.alpha = 0.5f + value * 0.5f
                    }
                    start()
                }
            }
        }

        fun stopAnimation() {
            lines.forEach { it.clearAnimation() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_MESSAGE -> MessageViewHolder(inflater.inflate(R.layout.item_message, parent, false))
            VIEW_TYPE_TYPING -> TypingViewHolder(inflater.inflate(R.layout.item_typing, parent, false)).apply { startAnimation() }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MessageViewHolder -> {
                val messages = viewModel.messages.value ?: emptyList()
                if (position >= messages.size) return

                val message = messages[position]
                holder.messageText.text = message.text
                holder.timestampText.text = message.getFormattedTimestamp()
                holder.messageText.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))

                holder.messageCard.background = ContextCompat.getDrawable(
                    holder.itemView.context,
                    if (message.isSentByUser) R.drawable.bg_message_user else R.drawable.bg_message_jarvis
                )
                holder.messageCard.cardElevation = 2f

                val cardParams = holder.messageCard.layoutParams as ConstraintLayout.LayoutParams
                cardParams.width = 0
                cardParams.height = ConstraintLayout.LayoutParams.WRAP_CONTENT

                val timestampParams = holder.timestampText.layoutParams as ConstraintLayout.LayoutParams
                if (message.isSentByUser) {
                    cardParams.startToStart = ConstraintLayout.LayoutParams.UNSET
                    cardParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    cardParams.horizontalBias = 1.0f
                    holder.messageText.setPadding(8, 8, 12, 8)
                    timestampParams.startToStart = ConstraintLayout.LayoutParams.UNSET
                    timestampParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    timestampParams.horizontalBias = 1.0f
                } else {
                    cardParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    cardParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
                    cardParams.horizontalBias = 0.0f
                    holder.messageText.setPadding(12, 8, 8, 8)
                    timestampParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    timestampParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
                    timestampParams.horizontalBias = 0.0f
                }
                holder.messageCard.layoutParams = cardParams
                holder.timestampText.layoutParams = timestampParams

                if (position == messages.size - 1 && !holder.isAnimated && holder.itemView.isShown) {
                    holder.messageCard.alpha = 0f
                    holder.messageCard.translationY = 10f
                    holder.messageCard.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction { holder.isAnimated = true }
                        .start()
                } else {
                    holder.messageCard.alpha = 1f
                    holder.messageCard.translationY = 0f
                }
            }
            is TypingViewHolder -> {}
        }
    }

    override fun getItemCount(): Int = (viewModel.messages.value?.size ?: 0) + if (viewModel.isTyping.value == true) 1 else 0

    override fun getItemViewType(position: Int): Int {
        val messages = viewModel.messages.value ?: emptyList()
        return if (position >= messages.size && viewModel.isTyping.value == true) VIEW_TYPE_TYPING else VIEW_TYPE_MESSAGE
    }

    fun updateMessages(newMessages: List<Message>) {
        val oldMessages = viewModel.messages.value ?: emptyList()
        if (oldMessages.size == newMessages.size - 1 && oldMessages == newMessages.dropLast(1)) {
            notifyItemInserted(newMessages.size - 1)
        } else {
            val diffResult = DiffUtil.calculateDiff(MessageDiffCallback(oldMessages, newMessages), true)
            diffResult.dispatchUpdatesTo(this)
        }
    }

    fun onTypingChanged(isTyping: Boolean) {
        val currentMessagesSize = (viewModel.messages.value ?: emptyList()).size
        if (isTyping) {
            notifyItemInserted(currentMessagesSize)
            recyclerView.post { recyclerView.smoothScrollToPosition(currentMessagesSize) }
        } else {
            notifyItemRemoved(currentMessagesSize)
        }
    }

    private class MessageDiffCallback(
        private val oldList: List<Message>,
        private val newList: List<Message>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int) = oldList[oldPos].timestamp == newList[newPos].timestamp
        override fun areContentsTheSame(oldPos: Int, newPos: Int) = oldList[oldPos] == newList[newPos]
    }
}