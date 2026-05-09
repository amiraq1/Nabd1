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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.localqwen.R
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin

class ChatAdapter(
    private val context: Context
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    private val appContext = context.applicationContext
    private val markwon: Markwon = Markwon.builder(appContext)
        .usePlugin(TablePlugin.create(appContext))
        .build()
    private var streamingAssistantIndex = RecyclerView.NO_POSITION

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).role) {
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
        val message = getItem(position)
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
        submitList(newMessages.toList())
    }

    fun addMessage(message: ChatMessage) {
        val updated = currentList.toMutableList()
        updated.add(message)
        submitList(updated)
    }

    fun updateLastAssistantMessage(text: String, renderMarkdown: Boolean = true) {
        val current = currentList.toMutableList()
        val index = current.indexOfLast { it.role == Role.ASSISTANT }
        if (index != -1) {
            streamingAssistantIndex = if (renderMarkdown) RecyclerView.NO_POSITION else index
            current[index] = current[index].copy(text = text)
            submitList(current)
        }
    }

    fun markLastAssistantStreaming() {
        val index = currentList.indexOfLast { it.role == Role.ASSISTANT }
        if (index != -1) {
            streamingAssistantIndex = index
            notifyItemChanged(index)
        }
    }

    fun clearMessages() {
        streamingAssistantIndex = RecyclerView.NO_POSITION
        submitList(emptyList())
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

private class ChatDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem.text == newItem.text && oldItem.role == newItem.role
    }
}
