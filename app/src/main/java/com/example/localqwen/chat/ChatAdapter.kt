package com.example.localqwen.chat

import android.graphics.Typeface
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.localqwen.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
            showMessageActions(cleanDisplayMessage(message.text))
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

    private fun showMessageActions(text: String) {
        MaterialAlertDialogBuilder(context)
            .setItems(arrayOf("نسخ الرسالة", "إلغاء")) { dialog, which ->
                when (which) {
                    0 -> {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("رسالة من نبض", text))
                        Toast.makeText(context, "تم نسخ الرسالة", Toast.LENGTH_SHORT).show()
                    }

                    else -> dialog.dismiss()
                }
            }
            .show()
    }

    private class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.tvMessageText)
        private val codeContainer: HorizontalScrollView = itemView.findViewById(R.id.codeScrollContainer)

        fun bind(message: ChatMessage) {
            bindMessageContent(messageView = messageText, codeContainer = codeContainer, rawText = message.text)
        }
    }

    private class AssistantMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.tvMessageText)
        private val labelText: TextView = itemView.findViewById(R.id.tvAssistantLabel)
        private val codeContainer: HorizontalScrollView = itemView.findViewById(R.id.codeScrollContainer)

        fun bind(message: ChatMessage) {
            labelText.text = "نبض"
            bindMessageContent(messageView = messageText, codeContainer = codeContainer, rawText = message.text)
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

        private fun bindMessageContent(
            messageView: TextView,
            codeContainer: HorizontalScrollView,
            rawText: String
        ) {
            val isCodeFence = rawText.contains("```")
            val isTableLike = rawText.lines().count { it.contains("|") } >= 2
            val formattedText = cleanDisplayMessage(rawText)

            messageView.text = formattedText
            if (isCodeFence || isTableLike) {
                codeContainer.background = messageView.context.getDrawable(R.drawable.bg_message_code)
                codeContainer.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                codeContainer.isHorizontalScrollBarEnabled = false
                messageView.typeface = Typeface.MONOSPACE
                messageView.textSize = 14f
                messageView.setLineSpacing(4f, 1f)
                messageView.setHorizontallyScrolling(true)
                messageView.setPadding(16, 14, 16, 14)
            } else {
                codeContainer.background = null
                codeContainer.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                messageView.typeface = Typeface.DEFAULT
                messageView.textSize = 16f
                messageView.setLineSpacing(6f, 1f)
                messageView.setHorizontallyScrolling(false)
                messageView.setPadding(0, 0, 0, 0)
            }
        }

        private fun cleanDisplayMessage(rawText: String): String {
            val withoutCodeFences = rawText
                .replace(Regex("```[a-zA-Z0-9_+-]*\\n?"), "")
                .replace("```", "")

            return withoutCodeFences
                .lines()
                .filterNot { line ->
                    val trimmed = line.trim()
                    trimmed.isNotEmpty() && trimmed.all { it == '|' || it == '-' || it == ':' || it == ' ' }
                }
                .joinToString("\n") { line ->
                    if (line.contains("|")) {
                        line.split("|")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .joinToString("  •  ")
                    } else {
                        line
                    }
                }
                .trim()
        }
    }
}
