package com.example.localqwen.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.animation.animateColorAsState
import com.example.localqwen.chat.ChatMessage
import com.example.localqwen.chat.Role

@Composable
fun MessageBubble(message: ChatMessage) {
    when (message.role) {
        Role.SYSTEM -> SystemMessageBubble(message)
        Role.USER -> UserMessageBubble(message)
        Role.ASSISTANT -> AssistantMessageBubble(message)
    }
}

@Composable
fun SystemMessageBubble(message: ChatMessage) {
    val isError = message.text.startsWith("خطأ") || message.text.contains("فشل") || message.text.contains("عذراً")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = if (isError) NabdColors.ErrorLight else NabdColors.BubbleSystem,
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
                    color = if (isError) NabdColors.Error else NabdColors.InkSecondary,
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        textDirection = TextDirection.Rtl,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

@Composable
fun UserMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NabdColors.Rose, NabdColors.RoseDark)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.88f).dp)
            ) {
                val cleanedText = remember(message.text) {
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
                        color = NabdColors.InkOnRose,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        style = TextStyle(
                            textDirection = TextDirection.Rtl,
                            textAlign = TextAlign.Start
                        )
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        UserAvatar()
    }
}

@Composable
fun AssistantMessageBubble(message: ChatMessage) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        AssistantAvatar()
        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.Start) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 20.dp
                        )
                    )
                    .background(NabdColors.BubbleAssistant)
                    .border(
                        1.dp,
                        NabdColors.CardBorder,
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 20.dp
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.88f).dp)
            ) {
                val cleanedText = remember(message.text) {
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
                        color = NabdColors.Ink,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        style = TextStyle(
                            textDirection = TextDirection.Rtl,
                            textAlign = TextAlign.Start
                        )
                    )
                }
            }

            VerificationBadge(
                level = message.verificationLevel,
                sourceRequirement = message.sourceRequirement
            )

            if (message.tps != null) {
                Text(
                    text = String.format("⚡ %.1f TPS", message.tps),
                    fontSize = 10.sp,
                    color = NabdColors.InkTertiary,
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                )
            }

            if (message.text.isNotBlank()) {
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.text))
                        Toast.makeText(context, "تم نسخ الرد", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .padding(top = 4.dp, start = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = NabdColors.InkTertiary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AssistantAvatar() {
    Box(
        modifier = Modifier
            .size(34.dp)
            .shadow(4.dp, CircleShape, ambientColor = NabdColors.RoseGlow)
            .background(NabdColors.RoseLight, CircleShape)
            .border(1.dp, NabdColors.CardBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("نبض", fontSize = 9.sp, fontWeight = FontWeight.Black, color = NabdColors.Rose)
    }
}

@Composable
fun UserAvatar() {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(NabdColors.CardSubtle, CircleShape)
            .border(1.dp, NabdColors.CardBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("أنت", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NabdColors.InkSecondary)
    }
}

@Composable
fun NabdPulseButton(
    onClick: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
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

    val color = if (isActive) NabdColors.Rose else Color(0xFFF0ECEB)
    val contentColor = if (isActive) Color.White else NabdColors.InkTertiary

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
    onCancelGeneration: () -> Unit,
    onAnalyzeImage: () -> Unit = {},
    isBusy: Boolean = false,
    isEnabled: Boolean = true
) {
    var text by rememberSaveable { mutableStateOf("") }

    fun submitText() {
        val message = text.trim()
        if (message.isNotEmpty() && !isBusy && isEnabled) {
            onSendMessage(message)
            text = ""
        }
    }

    val sendEnabled = isEnabled && text.isNotBlank()
    val sendBgColor by animateColorAsState(
        targetValue = if (sendEnabled) NabdColors.Rose else Color(0xFFF0ECEB),
        animationSpec = tween(300),
        label = "SendBg"
    )
    val sendIconColor by animateColorAsState(
        targetValue = if (sendEnabled) Color.White else NabdColors.InkTertiary,
        animationSpec = tween(300),
        label = "SendIcon"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(12.dp, RoundedCornerShape(32.dp), ambientColor = NabdColors.ShadowMedium)
            .alpha(if (isEnabled) 1f else 0.7f),
        shape = RoundedCornerShape(32.dp),
        color = NabdColors.CardElevated
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAddAttachment, enabled = isEnabled) {
                Icon(Icons.Default.Add, contentDescription = "Attach", tint = NabdColors.InkSecondary)
            }

            IconButton(onClick = onAnalyzeImage, enabled = isEnabled) {
                Icon(Icons.Default.Image, contentDescription = "Image Analysis", tint = NabdColors.InkSecondary)
            }

            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                enabled = isEnabled,
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = NabdColors.Ink,
                    textDirection = TextDirection.Rtl
                ),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text(
                            "اكتب رسالتك...",
                            color = NabdColors.InkTertiary,
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                            style = TextStyle(textDirection = TextDirection.Rtl)
                        )
                    }
                    innerTextField()
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    submitText()
                })
            )

            if (isBusy) {
                StopGenerationButton(
                    onClick = onCancelGeneration,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(sendBgColor)
                        .clickable(enabled = sendEnabled) { submitText() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال",
                        tint = sendIconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StopGenerationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulsing animation for stop button
    val infiniteTransition = rememberInfiniteTransition(label = "StopPulse")
    val stopScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StopScale"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(stopScale)
            .clip(CircleShape)
            .background(NabdColors.ErrorLight)
            .border(1.dp, NabdColors.Error.copy(alpha = 0.2f), CircleShape)
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Stop,
            contentDescription = "إيقاف التوليد",
            tint = NabdColors.Error
        )
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

    // Shimmer effect for text
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Shimmer"
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
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    textDirection = TextDirection.Rtl,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NabdColors.Rose.copy(alpha = 0.5f),
                            NabdColors.Rose,
                            NabdColors.Amber,
                            NabdColors.Rose,
                            NabdColors.Rose.copy(alpha = 0.5f)
                        ),
                        start = Offset(shimmerOffset * 200f, 0f),
                        end = Offset(shimmerOffset * 200f + 200f, 0f)
                    )
                )
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
            color = NabdColors.Rose.copy(alpha = alpha),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
