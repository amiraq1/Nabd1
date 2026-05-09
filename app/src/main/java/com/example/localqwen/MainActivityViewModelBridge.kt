package com.example.localqwen

import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import com.example.localqwen.chat.ChatAdapter
import com.example.localqwen.chat.ChatMessage
import com.example.localqwen.viewmodel.ChatViewModel
import com.example.localqwen.viewmodel.ModelState
import com.example.localqwen.viewmodel.ModelViewModel
import com.example.localqwen.viewmodel.StatusEvent
import com.example.localqwen.viewmodel.StreamingUpdate

/**
 * Bridge mixin that wires ViewModel observers to the Activity's UI.
 *
 * Usage in MainActivity.onCreate():
 *   setupViewModelObservers(chatAdapter, rvChatMessages, statusView)
 *
 * This provides a safe incremental migration path:
 * 1. The ViewModels manage state and business logic.
 * 2. MainActivity retains UI-specific code (dialogs, animations, file pickers).
 * 3. Over time, more logic moves from Activity into ViewModels.
 */
fun AppCompatActivity.setupViewModelObservers(
    chatViewModel: ChatViewModel,
    modelViewModel: ModelViewModel,
    chatAdapter: ChatAdapter,
    rvChatMessages: RecyclerView,
    statusView: TextView,
    onGeneratingChanged: (Boolean) -> Unit,
    onPreparingContextChanged: (Boolean) -> Unit
) {
    // Observe chat messages → push to adapter
    chatViewModel.messages.observe(this, Observer { messages ->
        chatAdapter.submitMessages(messages)
    })

    // Observe scroll-to-bottom requests
    chatViewModel.scrollToBottom.observe(this, Observer { shouldScroll ->
        if (shouldScroll) {
            val lastIndex = chatAdapter.itemCount - 1
            if (lastIndex >= 0) {
                rvChatMessages.post { rvChatMessages.scrollToPosition(lastIndex) }
            }
        }
    })

    // Observe generation state
    chatViewModel.isGenerating.observe(this, Observer { generating ->
        onGeneratingChanged(generating)
    })

    // Observe context preparation state
    chatViewModel.isPreparingContext.observe(this, Observer { preparing ->
        onPreparingContextChanged(preparing)
    })

    // Observe chat status events
    chatViewModel.statusEvent.observe(this, Observer { event ->
        applyStatusEvent(statusView, event)
    })

    // Observe streaming updates
    chatViewModel.streamingUpdate.observe(this, Observer { update ->
        if (update != null && update.isStreaming) {
            chatAdapter.markLastAssistantStreaming()
        }
    })

    // Observe model state changes
    modelViewModel.modelState.observe(this, Observer { state ->
        val statusText = when (state) {
            is ModelState.Loading -> "جاري تشغيل نبض..."
            is ModelState.Ready -> "جاهز • ${modelViewModel.selectedModel.value?.displayName ?: ""}"
            is ModelState.Idle -> "غير مشغّل • ${modelViewModel.selectedModel.value?.displayName ?: ""}"
            is ModelState.NotImported -> "غير مستورد • ${modelViewModel.selectedModel.value?.displayName ?: ""}"
            is ModelState.Error -> "خطأ: ${state.message}"
        }
        statusView.text = statusText
    })

    // Observe model status events
    modelViewModel.statusEvent.observe(this, Observer { event ->
        applyStatusEvent(statusView, event)
    })
}

private fun AppCompatActivity.applyStatusEvent(statusView: TextView, event: StatusEvent?) {
    when (event) {
        is StatusEvent.Info -> {
            statusView.text = event.message
            statusView.setTextColor(ContextCompat.getColor(this, R.color.nabd_text_secondary))
        }
        is StatusEvent.Success -> {
            statusView.text = event.message
            statusView.setTextColor(ContextCompat.getColor(this, R.color.nabd_success))
        }
        is StatusEvent.Error -> {
            statusView.text = event.message
            statusView.setTextColor(ContextCompat.getColor(this, R.color.nabd_error))
        }
        null -> { /* no-op */ }
    }
}
