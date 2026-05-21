package com.example.localqwen.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localqwen.ui.compose.NabdPulseButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NabdWelcomeScreen(
    onSendMessage: (String) -> Unit = {},
    onAddAttachment: () -> Unit = {},
    onAnalyzeImage: () -> Unit = {},
    onShowHistory: () -> Unit = {},
    onShowMenu: () -> Unit = {},
    onModelBadgeClick: () -> Unit = {},
    activeModelName: String = "Gemma-2B (Local)"
) {
    var textInput by remember { mutableStateOf("") }


    Scaffold(
        containerColor = Color(0xFFF9F9F9),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "المساعد الافتراضي",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1A1A1A)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onShowHistory) {
                        Icon(Icons.Default.Refresh, contentDescription = "History", tint = Color(0xFF333333))
                    }
                },
                actions = {
                    IconButton(onClick = onShowMenu) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFF333333))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(
                            color = Color(0xFFFF5A5F),
                            shape = RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "نبض",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "مرحباً بك في تطبيق نبض!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A),
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        placeholder = { Text("أرسل رسالة...", color = Color.Gray, fontSize = 15.sp) },
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF0F0F0),
                            unfocusedContainerColor = Color(0xFFF0F0F0),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    NabdPulseButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                onSendMessage(textInput)
                                textInput = ""
                            }
                        },
                        isActive = textInput.isNotBlank(),
                        isEnabled = true,
                        modifier = Modifier.size(50.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FloatingActionButton(
                        onClick = onAnalyzeImage,
                        modifier = Modifier.size(50.dp),
                        shape = CircleShape,
                        containerColor = Color(0xFFF0F0F0),
                        contentColor = Color(0xFF333333),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Analyze Image")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FloatingActionButton(
                        onClick = onAddAttachment,
                        modifier = Modifier.size(50.dp),
                        shape = CircleShape,
                        containerColor = Color(0xFFF0F0F0),
                        contentColor = Color(0xFF333333),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Attachment")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Build, 
                        contentDescription = "Settings", 
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFC8E6C9), shape = RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onModelBadgeClick() }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = activeModelName,
                            color = Color(0xFF2E7D32),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
