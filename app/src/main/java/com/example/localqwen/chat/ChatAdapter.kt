package com.example.localqwen.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.localqwen.R
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin

class ChatAdapter(
    private val context: Context,
    private val messages: MutableList<ChatMessage> = mutableListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val appContext = context.applicationContext
    private val markwon: Markwon = Markwon.builder(appContext)
        .usePlugin(TablePlugin.create(appContext))
        .build()
    private var streamingAssistantIndex = RecyclerView.NO_POSITION

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return when (messages[position].role) {
            Role.USER -> VIEW_TYPE_USER
            Role.ASSISTANT -> VIEW_TYPE_ASSISTANT
            Role.SYSTEM -> VIEW_TYPE_SYSTEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> UserMessageViewHolder(
                inflater.inflate(R.layout.item_message_user, parent, false)
            )

            VIEW_TYPE_ASSISTANT -> AssistantMessageViewHolder(
                inflater.inflate(R.layout.item_message_assistant, parent, false),
                markwon
            )

            else -> SystemMessageViewHolder(
                inflater.inflate(R.layout.item_message_system, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AssistantMessageViewHolder -> holder.bind(
                message = message,
                renderMarkdown = position != streamingAssistantIndex
            )
            is SystemMessageViewHolder -> holder.bind(message)
        }

        holder.itemView.setOnLongClickListener {
            copyMessage(message.text)
            true
        }
    }

    fun submitMessages(newMessages: List<ChatMessage>) {
        streamingAssistantIndex = RecyclerView.NO_POSITION
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.lastIndex)
    }

    fun updateMessage(position: Int, text: String) {
        if (position !in messages.indices) return
        messages[position].text = text
        notifyItemChanged(position)
    }

    fun updateLastAssistantMessage(text: String, renderMarkdown: Boolean = true) {
        val index = messages.indexOfLast { it.role == Role.ASSISTANT }
        if (index != -1) {
            streamingAssistantIndex = if (renderMarkdown) {
                if (streamingAssistantIndex == index) RecyclerView.NO_POSITION else streamingAssistantIndex
            } else {
                index
            }
            updateMessage(index, text)
        }
    }

    fun markLastAssistantStreaming() {
        val index = messages.indexOfLast { it.role == Role.ASSISTANT }
        if (index != -1) {
            streamingAssistantIndex = index
            notifyItemChanged(index)
        }
    }

    fun clearMessages() {
        streamingAssistantIndex = RecyclerView.NO_POSITION
        messages.clear()
        notifyDataSetChanged()
    }

    private fun copyMessage(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("رسالة من نبض", text))
        Toast.makeText(context, "تم نسخ الرسالة", Toast.LENGTH_SHORT).show()
    }

    private class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.tvMessageText)

        fun bind(message: ChatMessage) {
            messageText.text = message.text
        }
    }

    private class AssistantMessageViewHolder(
        itemView: View,
        private val markwon: Markwon
    ) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.tvMessageText)

        init {
            messageText.movementMethod = LinkMovementMethod.getInstance()
        }

        fun bind(message: ChatMessage, renderMarkdown: Boolean) {
            if (renderMarkdown) {
                markwon.setMarkdown(messageText, message.text)
            } else {
                messageText.text = message.text
            }
        }
    }

    private class SystemMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.tvSystemMessage)

        fun bind(message: ChatMessage) {
            messageText.text = message.text
        }
    }

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
        private const val VIEW_TYPE_SYSTEM = 3
    }
}
