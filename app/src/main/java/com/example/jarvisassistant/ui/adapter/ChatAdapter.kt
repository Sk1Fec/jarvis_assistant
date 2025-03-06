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
import android.util.Log
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

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
                if (position >= messages.size) return

                val message = messages[position]
                Log.d(TAG, "Binding message at pos=$position: text='${message.text}', isUser=${message.isSentByUser}")

                holder.messageText.text = message.text
                holder.timestampText.text = message.getFormattedTimestamp()
                holder.messageText.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))

                // Настройка карточки сообщения
                val context = holder.itemView.context
                val screenWidth = context.resources.displayMetrics.widthPixels
                val maxWidthPx = (screenWidth * 0.60f).toInt() // Ограничение 60% экрана

                // Динамические углы для "пузырьков"
                val shapeAppearanceModel = ShapeAppearanceModel.builder().apply {
                    if (message.isSentByUser) {
                        setTopLeftCorner(CornerFamily.ROUNDED, 16f)
                        setTopRightCorner(CornerFamily.ROUNDED, 16f)
                        setBottomLeftCorner(CornerFamily.ROUNDED, 16f)
                        setBottomRightCorner(CornerFamily.ROUNDED, 4f)
                    } else {
                        setTopLeftCorner(CornerFamily.ROUNDED, 16f)
                        setTopRightCorner(CornerFamily.ROUNDED, 16f)
                        setBottomLeftCorner(CornerFamily.ROUNDED, 4f)
                        setBottomRightCorner(CornerFamily.ROUNDED, 16f)
                    }
                }.build()

                val background = MaterialShapeDrawable(shapeAppearanceModel).apply {
                    fillColor = ContextCompat.getColorStateList(
                        context,
                        if (message.isSentByUser) R.color.message_background_user
                        else R.color.message_background_jarvis
                    )
                }
                holder.messageCard.background = background
                holder.messageCard.cardElevation = 2f

                // Выравнивание и отступы
                val params = holder.messageCard.layoutParams as ConstraintLayout.LayoutParams
                params.width = ConstraintLayout.LayoutParams.WRAP_CONTENT
                params.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
                params.topMargin = 16 // Увеличен до 16dp
                params.bottomMargin = 16 // Увеличен до 16dp
                params.startToStart = if (message.isSentByUser) ConstraintLayout.LayoutParams.UNSET else ConstraintLayout.LayoutParams.PARENT_ID
                params.endToEnd = if (message.isSentByUser) ConstraintLayout.LayoutParams.PARENT_ID else ConstraintLayout.LayoutParams.UNSET
                params.leftMargin = 48 // Увеличен до 48dp
                params.rightMargin = 48 // Увеличен до 48dp
                params.width = params.width.coerceAtMost(maxWidthPx) // Ограничение ширины
                holder.messageCard.layoutParams = params
                Log.d(TAG, "Applied params: width=${params.width}, leftMargin=${params.leftMargin}, rightMargin=${params.rightMargin}")

                // Анимация для нового сообщения
                if (position == messages.size - 1 && !holder.isAnimated) {
                    Log.d(TAG, "Animating new message at pos=$position")
                    holder.messageCard.alpha = 0f
                    holder.messageCard.translationY = 20f
                    holder.messageCard.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withStartAction {
                            if (!message.isSentByUser) holder.messageText.text = ""
                        }
                        .withEndAction {
                            if (!message.isSentByUser) animateTextTyping(holder.messageText, message.text)
                            holder.isAnimated = true
                        }
                        .start()
                } else {
                    holder.messageCard.alpha = 1f
                    holder.messageCard.translationY = 0f
                }
            }
            is TypingViewHolder -> {
                Log.d(TAG, "Binding typing indicator at pos=$position")
            }
        }
    }

    private fun animateTextTyping(textView: TextView, fullText: String) {
        Log.d(TAG, "Typing animation for text: '$fullText'")
        textView.text = ""
        var currentIndex = 0
        ValueAnimator.ofInt(0, fullText.length).apply {
            duration = (fullText.length * 20).toLong()
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
        val count = messages.size + if (viewModel.isTyping.value == true) 1 else 0
        Log.d(TAG, "Item count: $count (messages=${messages.size}, isTyping=${viewModel.isTyping.value})")
        return count
    }

    override fun getItemViewType(position: Int): Int {
        val messages = viewModel.messages.value ?: emptyList()
        return if (position == messages.size && viewModel.isTyping.value == true) VIEW_TYPE_TYPING else VIEW_TYPE_MESSAGE
    }

    fun updateMessages(newMessages: List<Message>) {
        val oldMessages = viewModel.messages.value ?: emptyList()
        val diffResult = DiffUtil.calculateDiff(MessageDiffCallback(oldMessages, newMessages), true)
        diffResult.dispatchUpdatesTo(this)
        Log.d(TAG, "Messages updated, new size: ${newMessages.size}")
    }

    fun onTypingChanged(isTyping: Boolean) {
        val currentMessagesSize = (viewModel.messages.value ?: emptyList()).size
        Log.d(TAG, "Typing changed: $isTyping, position=$currentMessagesSize")
        if (isTyping) {
            notifyItemInserted(currentMessagesSize)
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