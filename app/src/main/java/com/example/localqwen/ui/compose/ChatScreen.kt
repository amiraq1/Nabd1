package com.example.localqwen.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localqwen.chat.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onAddAttachment: () -> Unit, codex/improve-chat-usability
    onCancelGeneration: () -> Unit,

    onAnalyzeImage: () -> Unit = {},
 main
    onMenuClick: () -> Unit,
    onBackClick: () -> Unit,
    statusText: String = "جاهز",
    onModelClick: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val latestMessageKey = messages.lastOrNull()?.let { "${it.id}:${it.text.length}" }

    LaunchedEffect(messages.size, latestMessageKey, isGenerating) {
        val totalItems = messages.size + if (isGenerating) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clickable { onModelClick() }
                            .padding(4.dp)
                    ) {
                        Text("نبض", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(statusText, fontSize = 11.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isGenerating) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Color(0xFFFF5A5F),
                    trackColor = Color.Transparent
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
                
                if (isGenerating) {
                    item {
                        TypingIndicator()
                    }
                }
            }

            ChatInputBar(
                onSendMessage = onSendMessage,
                onAddAttachment = onAddAttachment,
 codex/improve-chat-usability
                onCancelGeneration = onCancelGeneration,
                isBusy = isGenerating

                onAnalyzeImage = onAnalyzeImage,
                isEnabled = !isGenerating
 main
            )
        }
    }
}
