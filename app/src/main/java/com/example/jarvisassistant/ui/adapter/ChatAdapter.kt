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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jarvisassistant.R
import com.example.jarvisassistant.data.Message
import com.example.jarvisassistant.viewmodel.ChatViewModel
import android.util.Log
import android.widget.LinearLayout

class ChatAdapter(
    private val viewModel: ChatViewModel,
    private val recyclerView: RecyclerView
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_MESSAGE = 0
        private const val VIEW_TYPE_TYPING = 1
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.message_text)
        val timestampText: TextView = itemView.findViewById(R.id.timestamp_text)
        val messageCard: CardView = itemView.findViewById(R.id.message_card)
    }

    class TypingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val lines = listOf(
            itemView.findViewById<View>(R.id.line1),
            itemView.findViewById<View>(R.id.line2),
            itemView.findViewById<View>(R.id.line3)
        )

        fun startAnimation() {
            lines.forEachIndexed { index, line ->
                line.setBackgroundColor(ContextCompat.getColor(line.context, R.color.accent_neon))
                ValueAnimator.ofFloat(0.2f, 1f).apply {
                    duration = 400
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    startDelay = (index * 150).toLong()
                    addUpdateListener { line.alpha = it.animatedValue as Float }
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
                if (position < messages.size) {
                    val message = messages[position]
                    Log.d("ChatAdapter", "Binding message at $position: text='${message.text}', isSentByUser=${message.isSentByUser}")

                    holder.messageText.text = message.text
                    holder.timestampText.text = message.getFormattedTimestamp()
                    holder.messageText.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))

                    val context = holder.itemView.context
                    val screenWidth = context.resources.displayMetrics.widthPixels
                    val maxWidthPx = (screenWidth * 0.75f).toInt().coerceAtMost(800)

                    // Попытка 1: Позиционирование через ConstraintLayout (если ваша разметка использует ConstraintLayout)
                    if (holder.itemView is ConstraintLayout) {
                        val params = holder.messageCard.layoutParams as? ConstraintLayout.LayoutParams
                            ?: ConstraintLayout.LayoutParams(
                                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                                ConstraintLayout.LayoutParams.WRAP_CONTENT
                            )
                        params.width = maxWidthPx
                        params.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
                        params.topMargin = 16
                        params.bottomMargin = 16

                        if (message.isSentByUser) {
                            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                            params.endToEnd = ConstraintLayout.LayoutParams.UNSET
                            params.horizontalBias = 1.0f // Пользователь справа
                            holder.messageCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.message_background_user))
                        } else {
                            params.startToStart = ConstraintLayout.LayoutParams.UNSET
                            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                            params.horizontalBias = 0.0f // Джарвис слева
                            holder.messageCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.message_background_jarvis))
                        }
                        holder.messageCard.layoutParams = params
                        holder.itemView.requestLayout() // Принудительная перерисовка
                        Log.d("ChatAdapter", "Applied ConstraintLayout params: horizontalBias=${params.horizontalBias}, startToStart=${params.startToStart}, endToEnd=${params.endToEnd}")
                    }
                    // Попытка 2: Позиционирование через Gravity (если ваша разметка использует LinearLayout или другой layout)
                    else {
                        val params = holder.messageCard.layoutParams as? LinearLayout.LayoutParams
                            ?: LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        params.width = maxWidthPx
                        params.height = LinearLayout.LayoutParams.WRAP_CONTENT
                        params.topMargin = 16
                        params.bottomMargin = 16

                        if (message.isSentByUser) {
                            params.gravity = android.view.Gravity.END // Пользователь справа
                            holder.messageCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.message_background_user))
                        } else {
                            params.gravity = android.view.Gravity.START // Джарвис слева
                            holder.messageCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.message_background_jarvis))
                        }
                        holder.messageCard.layoutParams = params
                        holder.itemView.requestLayout() // Принудительная перерисовка
                        Log.d("ChatAdapter", "Applied LinearLayout params: gravity=${params.gravity}")
                    }

                    // Анимация для последнего сообщения
                    if (position == messages.size - 1 && !recyclerView.isComputingLayout && recyclerView.isAttachedToWindow) {
                        Log.d("ChatAdapter", "Starting animation for message at $position")
                        holder.messageCard.alpha = 0f
                        holder.messageCard.translationY = 10f
                        holder.messageCard.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(300)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .withEndAction {
                                if (!message.isSentByUser) {
                                    Log.d("ChatAdapter", "Starting text typing animation for message at $position")
                                    animateTextTyping(holder.messageText, message.text)
                                }
                            }
                            .start()
                    } else {
                        holder.messageCard.alpha = 1f
                        holder.messageCard.translationY = 0f
                    }
                }
            }
            is TypingViewHolder -> {
                Log.d("ChatAdapter", "Binding TypingViewHolder at $position, isTyping: ${viewModel.isTyping.value}")
            }
        }
    }

    private fun animateTextTyping(textView: TextView, fullText: String) {
        textView.text = ""
        var currentIndex = 0
        ValueAnimator.ofInt(0, fullText.length).apply {
            duration = (fullText.length * 40).toLong()
            addUpdateListener { animation ->
                val end = animation.animatedValue as Int
                if (end > currentIndex) {
                    textView.text = fullText.substring(0, end)
                    currentIndex = end
                }
            }
            start()
        }
    }

    override fun getItemCount(): Int {
        val messages = viewModel.messages.value ?: emptyList()
        return messages.size + if (viewModel.isTyping.value == true) 1 else 0
    }

    override fun getItemViewType(position: Int): Int {
        val messages = viewModel.messages.value ?: emptyList()
        return if (position == messages.size && viewModel.isTyping.value == true) VIEW_TYPE_TYPING else VIEW_TYPE_MESSAGE
    }

    fun updateMessages(newMessages: List<Message>) {
        val oldMessages = viewModel.messages.value ?: emptyList()
        val diffResult = DiffUtil.calculateDiff(MessageDiffCallback(oldMessages, newMessages), true)
        diffResult.dispatchUpdatesTo(this)
    }

    fun onTypingChanged(isTyping: Boolean) {
        val currentMessagesSize = (viewModel.messages.value ?: emptyList()).size
        Log.d("ChatAdapter", "Typing changed to $isTyping, position: $currentMessagesSize, itemCount: $itemCount, messages.size: $currentMessagesSize")
        if (isTyping) {
            if (currentMessagesSize <= itemCount) {
                notifyItemInserted(currentMessagesSize)
            } else {
                Log.w("ChatAdapter", "Invalid position for typing indicator: $currentMessagesSize > $itemCount")
                notifyDataSetChanged()
            }
        } else {
            if (currentMessagesSize < itemCount) {
                notifyItemRemoved(currentMessagesSize)
            } else {
                Log.w("ChatAdapter", "Invalid position for removing typing indicator: $currentMessagesSize >= $itemCount")
                notifyDataSetChanged()
            }
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