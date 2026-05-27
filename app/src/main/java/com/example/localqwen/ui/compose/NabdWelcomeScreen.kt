package com.example.localqwen.ui.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localqwen.R

// New Soft UI Colors for Nabd v0.2.0
private val NabdSoftBg = Color(0xFFFFF9F9)
private val NabdSoftPink = Color(0xFFFF5A5F)
private val NabdCardBg = Color(0xFFFFFFFF)
private val NabdTextPrimary = Color(0xFF1A1A1A)
private val NabdTextSecondary = Color(0xFF757575)
private val NabdSuccessGreen = Color(0xFF4CAF50)

@Composable
fun NabdWelcomeScreen(
    onSendMessage: (String) -> Unit = {},
    onAddAttachment: () -> Unit = {},
    onAnalyzeImage: () -> Unit = {},
    onShowHistory: () -> Unit = {},
    onShowMenu: () -> Unit = {},
    onModelBadgeClick: () -> Unit = {},
    activeModelName: String = "Gemma-2B (Local)",
    isBusy: Boolean = false,
    statusText: String = "جاهز",
    onSetupModel: () -> Unit = {},
    onLoadModel: () -> Unit = {},
    modelState: com.example.localqwen.viewmodel.ModelState = com.example.localqwen.viewmodel.ModelState.NotImported
) {
    var textInput by rememberSaveable { mutableStateOf("") }
    val scrollState = rememberScrollState()

    fun submitText() {
        val message = textInput.trim()
        if (message.isNotEmpty() && !isBusy) {
            onSendMessage(message)
            textInput = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NabdSoftBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 100.dp) // Space for composer
        ) {
            NabdHomeHeader(onShowMenu = onShowMenu)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            WelcomeSection(userName = "عمار")
            
            Spacer(modifier = Modifier.height(24.dp))
            
            QuickActionsGrid(
                onDocumentClick = onAddAttachment,
                onImageClick = onAnalyzeImage,
                onChatClick = { onSendMessage("مرحباً نبض، كيف حالك؟") },
                onSettingsClick = onModelBadgeClick
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            PulseHeroSection()
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (modelState is com.example.localqwen.viewmodel.ModelState.NotImported) {
                ModelSetupCard(onImportClick = onSetupModel)
            } else {
                ModelStatusCard(
                    modelName = activeModelName,
                    modelState = modelState,
                    onClick = onModelBadgeClick,
                    onLoadClick = onLoadModel
                )
            }
        }

        // Bottom Composer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isBusy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Color(0xFFFF5A5F),
                    trackColor = Color.Transparent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = statusText,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            NabdHomeComposer(
                value = textInput,
                onValueChange = { textInput = it },
                onSend = {
                    if (textInput.isNotBlank()) {
                        onSendMessage(textInput)
                        textInput = ""
                    }
                },
                onAttach = onAddAttachment,
                onImage = onAnalyzeImage
            )
        }
    }
}

@Composable
fun NabdHomeHeader(onShowMenu: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onShowMenu,
            modifier = Modifier
                .size(44.dp)
                .background(Color.White, CircleShape)
                .shadow(2.dp, CircleShape)
        ) {
            Icon(Icons.Default.Menu, contentDescription = "القائمة", tint = NabdSoftPink)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = R.drawable.ic_nabd),
                contentDescription = null,
                tint = NabdSoftPink,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "نبض",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = NabdTextPrimary
            )
            Text(
                text = "مساعدك الذكي المحلي",
                fontSize = 10.sp,
                color = NabdTextSecondary
            )
        }

        // Dummy box to balance the header (RTL)
        Box(modifier = Modifier.size(44.dp))
    }
}

@Composable
fun WelcomeSection(userName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "أهلاً $userName!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = NabdTextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Decorative Pulse Line
        Canvas(modifier = Modifier.size(width = 60.dp, height = 4.dp)) {
            val width = size.width
            val height = size.height
            drawRoundRect(
                color = NabdSoftPink,
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "أنا مساعدك الذكي نبض.",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = NabdTextPrimary,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "اسألني أي شيء، أنا هنا لمساعدتك.",
            fontSize = 14.sp,
            color = NabdTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun QuickActionsGrid(
    onDocumentClick: () -> Unit,
    onImageClick: () -> Unit,
    onChatClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            QuickActionCard(
                title = "اسأل عن مستند",
                description = "لخّص أو اسأل عن أي مستند",
                icon = Icons.Default.Description,
                modifier = Modifier.weight(1f),
                onClick = onDocumentClick
            )
            Spacer(modifier = Modifier.width(16.dp))
            QuickActionCard(
                title = "حلّل صورة",
                description = "افهم المحتوى داخل الصور",
                icon = Icons.Default.Image,
                modifier = Modifier.weight(1f),
                onClick = onImageClick
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            QuickActionCard(
                title = "ابدأ محادثة",
                description = "محادثة حرة مع نبض",
                icon = Icons.Default.ChatBubble,
                modifier = Modifier.weight(1f),
                onClick = onChatClick
            )
            Spacer(modifier = Modifier.width(16.dp))
            QuickActionCard(
                title = "إعداد النموذج",
                description = "تحكم في إعدادات النموذج",
                icon = Icons.Default.Settings,
                modifier = Modifier.weight(1f),
                onClick = onSettingsClick
            )
        }
    }
}


@Composable
fun QuickActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(140.dp)
            .shadow(4.dp, RoundedCornerShape(24.dp)),
        color = NabdCardBg,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(NabdSoftPink.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = NabdSoftPink, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = NabdTextPrimary,
                textAlign = TextAlign.Center,
                style = TextStyle(textDirection = TextDirection.Rtl)
            )
            Text(
                text = description,
                fontSize = 10.sp,
                color = NabdTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                style = TextStyle(textDirection = TextDirection.Rtl)
            )
        }
    }
}

@Composable
fun PulseHeroSection() {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Scale"
    )


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Animated Rings
        Box(
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale, alpha = 1f - pulseScale / 1.5f)
                .border(2.dp, NabdSoftPink, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer(scaleX = pulseScale * 0.8f, scaleY = pulseScale * 0.8f, alpha = pulseAlpha)
                .background(NabdSoftPink.copy(alpha = 0.05f), CircleShape)
        )
        
        // Center Icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(8.dp, CircleShape)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_nabd),
                contentDescription = null,
                tint = NabdSoftPink,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}


@Composable
fun ModelStatusCard(
    modelName: String, 
    modelState: com.example.localqwen.viewmodel.ModelState,
    onClick: () -> Unit,
    onLoadClick: () -> Unit
) {
    val isReady = modelState is com.example.localqwen.viewmodel.ModelState.Ready
    val isIdle = modelState is com.example.localqwen.viewmodel.ModelState.Idle
    val isLoading = modelState is com.example.localqwen.viewmodel.ModelState.Loading

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .shadow(2.dp, RoundedCornerShape(32.dp)),
            color = NabdCardBg,
            shape = RoundedCornerShape(32.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isReady) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isReady) NabdSuccessGreen else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = modelName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NabdTextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isReady -> "• جاهز • آمن • محلي"
                            isLoading -> "جاري التشغيل..."
                            isIdle -> "محمل محلياً • غير مشغل"
                            else -> "غير جاهز"
                        },
                        fontSize = 10.sp,
                        color = if (isReady) NabdSuccessGreen else Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isIdle || isLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onLoadClick,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NabdSoftPink),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري البدء...", fontSize = 12.sp)
                        } else {
                            Text("تشغيل النموذج", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NabdHomeComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onImage: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(32.dp)),
        color = Color.White,
        shape = RoundedCornerShape(32.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttach) {
                Icon(Icons.Default.AttachFile, contentDescription = "إرفاق ملف", tint = NabdTextSecondary)
            }
            IconButton(onClick = onImage) {
                Icon(Icons.Default.Image, contentDescription = "صورة", tint = NabdTextSecondary)
            }
            
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("اكتب رسالتك...", fontSize = 14.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    textDirection = TextDirection.Rtl
                ),
                maxLines = 3
            )

            val sendEnabled = value.isNotBlank()
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (sendEnabled) NabdSoftPink else Color(0xFFEEEEEE))
                    .clickable(enabled = sendEnabled) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send, 
                    contentDescription = "إرسال", 
                    tint = if (sendEnabled) NabdSoftPink else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
