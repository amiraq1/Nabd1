package com.example.localqwen.ui.compose

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.delay

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

    // ── Staggered Entrance Animation ──
    var showHeader by remember { mutableStateOf(false) }
    var showWelcome by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showHero by remember { mutableStateOf(false) }
    var showModel by remember { mutableStateOf(false) }
    var showComposer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showHeader = true
        delay(100)
        showWelcome = true
        delay(120)
        showActions = true
        delay(120)
        showHero = true
        delay(100)
        showModel = true
        delay(80)
        showComposer = true
    }

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
            .background(NabdColors.BgGradient)
    ) {
        // ── Decorative Blurred Circles for Depth ──
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = (-60).dp, y = 80.dp)
                .blur(80.dp)
                .background(NabdColors.RoseGlow, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = 200.dp)
                .blur(60.dp)
                .background(NabdColors.AmberLight.copy(alpha = 0.3f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 100.dp)
        ) {
            // ── Header ──
            AnimatedVisibility(
                visible = showHeader,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -40 }
            ) {
                NabdHomeHeader(onShowMenu = onShowMenu)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Welcome Section ──
            AnimatedVisibility(
                visible = showWelcome,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 60 }
            ) {
                WelcomeSection(userName = "عمار")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Quick Actions ──
            AnimatedVisibility(
                visible = showActions,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 80 }
            ) {
                QuickActionsGrid(
                    onDocumentClick = onAddAttachment,
                    onImageClick = onAnalyzeImage,
                    onChatClick = { onSendMessage("مرحباً نبض، كيف حالك؟") },
                    onSettingsClick = onModelBadgeClick
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Model Status ──
            AnimatedVisibility(
                visible = showModel,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 60 }
            ) {
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
        }

        // ── Bottom Composer ──
        AnimatedVisibility(
            visible = showComposer,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { 80 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isBusy) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = NabdColors.Rose,
                        trackColor = NabdColors.RoseMuted.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusText,
                        color = NabdColors.InkTertiary,
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
                .shadow(6.dp, CircleShape, ambientColor = NabdColors.ShadowMedium, spotColor = NabdColors.ShadowSoft)
                .background(NabdColors.CardElevated, CircleShape)
        ) {
            Icon(Icons.Default.Menu, contentDescription = "القائمة", tint = NabdColors.Rose)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "نبض",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = NabdColors.Ink,
                letterSpacing = 1.sp
            )
            Text(
                text = "مساعدك الذكي المحلي",
                fontSize = 10.sp,
                color = NabdColors.InkSecondary,
                letterSpacing = 0.5.sp
            )
        }

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
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = NabdColors.Ink,
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Decorative Gradient Pulse Line
        Canvas(modifier = Modifier.size(width = 60.dp, height = 4.dp)) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(NabdColors.Rose, NabdColors.Amber)
                ),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "أنا مساعدك الذكي نبض.",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = NabdColors.Ink,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "اسألني أي شيء، أنا هنا لمساعدتك.",
            fontSize = 14.sp,
            color = NabdColors.InkSecondary,
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
                accentColor = NabdColors.Rose,
                modifier = Modifier.weight(1f),
                onClick = onDocumentClick
            )
            Spacer(modifier = Modifier.width(16.dp))
            QuickActionCard(
                title = "حلّل صورة",
                description = "افهم المحتوى داخل الصور",
                icon = Icons.Default.Image,
                accentColor = NabdColors.Amber,
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
                accentColor = NabdColors.Info,
                modifier = Modifier.weight(1f),
                onClick = onChatClick
            )
            Spacer(modifier = Modifier.width(16.dp))
            QuickActionCard(
                title = "إعداد النموذج",
                description = "تحكم في إعدادات النموذج",
                icon = Icons.Default.Settings,
                accentColor = NabdColors.Success,
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
    accentColor: Color = NabdColors.Rose,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(140.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = NabdColors.ShadowMedium, spotColor = NabdColors.ShadowSoft)
            .border(1.dp, NabdColors.CardBorder, RoundedCornerShape(24.dp)),
        color = NabdColors.CardElevated,
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
                    .size(44.dp)
                    .background(accentColor.copy(alpha = 0.08f), CircleShape)
                    .border(1.dp, accentColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = NabdColors.Ink,
                textAlign = TextAlign.Center,
                style = TextStyle(textDirection = TextDirection.Rtl)
            )
            Text(
                text = description,
                fontSize = 10.sp,
                color = NabdColors.InkSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                style = TextStyle(textDirection = TextDirection.Rtl)
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

    // Animated status dot
    val infiniteTransition = rememberInfiniteTransition(label = "StatusDot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DotAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(32.dp), ambientColor = NabdColors.ShadowMedium)
                .border(1.dp, NabdColors.CardBorder, RoundedCornerShape(32.dp)),
            color = NabdColors.CardElevated,
            shape = RoundedCornerShape(32.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Animated Status Dot
                    val dotColor = when {
                        isReady -> NabdColors.Success
                        isLoading -> NabdColors.Amber
                        else -> NabdColors.InkTertiary
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .graphicsLayer(alpha = if (isLoading) dotAlpha else 1f)
                            .background(dotColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = modelName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NabdColors.Ink
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isReady -> "🔒 آمن • 📱 محلي • ✅ جاهز"
                            isLoading -> "جاري التشغيل..."
                            isIdle -> "محمل محلياً • غير مشغل"
                            else -> "غير جاهز"
                        },
                        fontSize = 10.sp,
                        color = if (isReady) NabdColors.Success else NabdColors.InkSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isIdle || isLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onLoadClick,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NabdColors.Rose,
                            contentColor = NabdColors.InkOnRose
                        ),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري البدء...", fontSize = 12.sp)
                        } else {
                            Text("تشغيل النموذج", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
    val sendEnabled = value.isNotBlank()
    val sendColor by animateColorAsState(
        targetValue = if (sendEnabled) NabdColors.Rose else Color(0xFFF0ECEB),
        animationSpec = tween(300),
        label = "SendColor"
    )
    val sendIconColor by animateColorAsState(
        targetValue = if (sendEnabled) Color.White else NabdColors.InkTertiary,
        animationSpec = tween(300),
        label = "SendIconColor"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = NabdColors.ShadowMedium),
        color = NabdColors.CardElevated,
        shape = RoundedCornerShape(32.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttach) {
                Icon(Icons.Default.AttachFile, contentDescription = "إرفاق ملف", tint = NabdColors.InkSecondary)
            }
            IconButton(onClick = onImage) {
                Icon(Icons.Default.Image, contentDescription = "صورة", tint = NabdColors.InkSecondary)
            }

            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("اكتب رسالتك...", fontSize = 14.sp, color = NabdColors.InkTertiary) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = NabdColors.Ink,
                    textDirection = TextDirection.Rtl
                ),
                maxLines = 3
            )

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(sendColor)
                    .clickable(enabled = sendEnabled) { onSend() },
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
