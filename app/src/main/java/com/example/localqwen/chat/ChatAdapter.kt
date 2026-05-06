package com.example.localqwen.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.localqwen.R

class ChatAdapter(
    private val context: Context,
    private val messages: MutableList<ChatMessage> = mutableListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

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
                inflater.inflate(R.layout.item_message_assistant, parent, false)
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
            is AssistantMessageViewHolder -> holder.bind(message)
            is SystemMessageViewHolder -> holder.bind(message)
        }

        holder.itemView.setOnLongClickListener {
            copyMessage(cleanDisplayMessage(message.text))
            true
        }
    }

    fun submitMessages(newMessages: List<ChatMessage>) {
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

    fun updateLastAssistantMessage(text: String) {
        val index = messages.indexOfLast { it.role == Role.ASSISTANT }
        if (index != -1) {
            updateMessage(index, text)
        }
    }

    fun clearMessages() {
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
            messageText.text = cleanDisplayMessage(message.text)
        }
    }

    private class AssistantMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.tvMessageText)

        fun bind(message: ChatMessage) {
            messageText.text = cleanDisplayMessage(message.text)
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

        private fun cleanDisplayMessage(rawText: String): String {
            return rawText.trim()
        }
    }
}
