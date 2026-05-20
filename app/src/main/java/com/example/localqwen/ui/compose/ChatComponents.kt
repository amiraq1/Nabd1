package com.example.localqwen.ui.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import com.example.localqwen.chat.ChatMessage
import com.example.localqwen.chat.Role

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == Role.USER
    val isSystem = message.role == Role.SYSTEM

    if (isSystem) {
        val isError = message.text.startsWith("خطأ") || message.text.contains("فشل") || message.text.contains("عذراً")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = if (isError) Color(0xFFFFEBEE) else Color(0xFFF5F5F5),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isError) {
                        Text("⚠️", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                    Text(
                        text = message.text,
                        fontSize = 12.sp,
                        color = if (isError) Color(0xFFD32F2F) else Color.Gray,
                        style = TextStyle(
                            fontWeight = FontWeight.Medium,
                            textDirection = TextDirection.Rtl,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp), // Reduced from 12.dp to 4.dp
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            AssistantAvatar()
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 20.dp
                    )
                )
                .background(if (isUser) Color(0xFFFF5A5F) else Color(0xFFF0F0F0))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.88f).dp) // Dynamic max width (88% of screen)
        ) {
            val cleanedText = remember(message.text) {
                // Collapse multiple newlines into one and perform basic Markdown cleanup
                message.text
                    .replace(Regex("\\n{2,}"), "\n")
                    .replace("**", "")
                    .replace("__", "")
                    .replace(Regex("^#+\\s*"), "")
                    .trim()
            }

            SelectionContainer {
                Text(
                    text = cleanedText,
                    color = if (isUser) Color.White else Color(0xFF1A1A1A),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    style = TextStyle(
                        textDirection = TextDirection.Rtl,
                        textAlign = TextAlign.Start // "Start" in RTL is Right
                    )
                )
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            UserAvatar()
        }
    }
}

@Composable
fun AssistantAvatar() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(Color(0xFFFF5A5F).copy(alpha = 0.1f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("نبض", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5A5F))
    }
}

@Composable
fun UserAvatar() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(Color(0xFFE0E0E0), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("أنت", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF666666))
    }
}

@Composable
fun NabdPulseButton(
    onClick: () -> Unit,
    isActive: Boolean, // Renamed from isEnabled to reflect "Send" state
    isEnabled: Boolean = true, // Overall clickability
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Color is primary when active (text present), light gray when inactive (for starters)
    val color = if (isActive) Color(0xFFFF5A5F) else Color(0xFFEEEEEE)
    val contentColor = if (isActive) Color.White else Color(0xFF999999)

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(if (isActive) scale else 1f)
            .clip(CircleShape)
            .background(color)
            .clickable(
                enabled = isEnabled,
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            val width = size.width
            val height = size.height
            val path = Path().apply {
                moveTo(0f, height / 2)
                lineTo(width * 0.2f, height / 2)
                lineTo(width * 0.3f, height * 0.3f)
                lineTo(width * 0.45f, height * 0.8f)
                lineTo(width * 0.55f, height * 0.1f)
                lineTo(width * 0.7f, height * 0.6f)
                lineTo(width * 0.8f, height / 2)
                lineTo(width, height / 2)
            }
            drawPath(
                path = path,
                color = contentColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun ChatInputBar(
    onSendMessage: (String) -> Unit,
    onAddAttachment: () -> Unit,
    isEnabled: Boolean = true
) {
    var text by remember { mutableStateOf("") }
    
    val starters = listOf(
        "اقترح علي فكرة تطبيق مميزة",
        "اكتب لي كود كوتلن بسيط",
        "كيف أحسن إنتاجيتي اليوم؟",
        "لخص لي أهمية الذكاء الاصطناعي المحلي",
        "أخبرني نكتة برمجية"
    )

    val handlePulseClick = {
        if (text.isNotBlank()) {
            if (isEnabled) {
                onSendMessage(text)
                text = ""
            }
        } else {
            // Field is empty, provide a random starter
            text = starters.random()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .alpha(if (isEnabled) 1f else 0.7f),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFFF5F5F5),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAddAttachment, enabled = isEnabled) {
                Icon(Icons.Default.Add, contentDescription = "Attach", tint = Color(0xFF666666))
            }

            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                enabled = isEnabled,
                textStyle = TextStyle(fontSize = 16.sp, color = Color(0xFF1A1A1A)),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text("اكتب رسالة...", color = Color.Gray, fontSize = 16.sp)
                    }
                    innerTextField()
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { 
                    if (text.isNotBlank() && isEnabled) {
                        onSendMessage(text)
                        text = ""
                    }
                })
            )

            NabdPulseButton(
                onClick = handlePulseClick,
                isActive = text.isNotBlank(),
                isEnabled = isEnabled,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "EkgTransition")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EkgAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.width(240.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Left Wave
            EkgWaveform(alpha = alpha, isMirrored = false)
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "نبض يكتب...",
                fontSize = 13.sp,
                color = Color(0xFFFF5252),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = TextStyle(textDirection = TextDirection.Rtl)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Right Wave
            EkgWaveform(alpha = alpha, isMirrored = true)
        }
    }
}

@Composable
fun EkgWaveform(alpha: Float, isMirrored: Boolean) {
    Canvas(modifier = Modifier.size(width = 40.dp, height = 16.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        
        val path = Path().apply {
            if (!isMirrored) {
                moveTo(0f, centerY)
                lineTo(width * 0.3f, centerY)
                lineTo(width * 0.4f, centerY - height * 0.15f)
                lineTo(width * 0.5f, centerY + height * 0.2f)
                lineTo(width * 0.6f, centerY - height * 0.7f)
                lineTo(width * 0.7f, centerY + height * 0.8f)
                lineTo(width * 0.8f, centerY)
                lineTo(width, centerY)
            } else {
                moveTo(width, centerY)
                lineTo(width * 0.7f, centerY)
                lineTo(width * 0.6f, centerY - height * 0.15f)
                lineTo(width * 0.5f, centerY + height * 0.2f)
                lineTo(width * 0.4f, centerY - height * 0.7f)
                lineTo(width * 0.3f, centerY + height * 0.8f)
                lineTo(width * 0.2f, centerY)
                lineTo(0f, centerY)
            }
        }

        drawPath(
            path = path,
            color = Color(0xFFFF5252).copy(alpha = alpha),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
