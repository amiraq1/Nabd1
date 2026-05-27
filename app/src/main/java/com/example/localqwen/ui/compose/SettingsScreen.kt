package com.example.localqwen.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Dark Soft UI Colors
private val SettingsBg = Color(0xFF101113)
private val SettingsCardBg = Color(0xFF1A1B1F)
private val SettingsBorder = Color.White.copy(alpha = 0.08f)
private val SettingsPrimary = Color(0xFFFF4F64)
private val SettingsGreen = Color(0xFF39C36A)
private val SettingsOrange = Color(0xFFFF9F2E)
private val SettingsTextPrimary = Color.White
private val SettingsTextSecondary = Color.White.copy(alpha = 0.62f)

@Composable
fun NabdSettingsScreen(
    appVersion: String,
    modelDescription: String,
    modelStatus: String,
    modelState: com.example.localqwen.viewmodel.ModelState = com.example.localqwen.viewmodel.ModelState.NotImported,
    onBackClick: () -> Unit,
    onAccountClick: () -> Unit,
    onModelsClick: () -> Unit,
    onDocumentsClick: () -> Unit,
    onChatsClick: () -> Unit,
    onToolsClick: () -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onModelSettingsClick: () -> Unit,
    onCheckUpdatesClick: () -> Unit,
    onSetupModel: () -> Unit = {},
    onLoadModel: () -> Unit = {},
    onDevModeClick: () -> Unit = {}
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SettingsBg)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
            ) {
                item {
                    SettingsTopBar(onBackClick = onBackClick)
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingsModelStatusCard(
                        modelName = modelDescription.ifBlank { "لم يتم اختيار نموذج" },
                        modelStatus = modelStatus,
                        modelState = modelState,
                        onModelSettingsClick = onModelSettingsClick,
                        onCheckUpdatesClick = onCheckUpdatesClick,
                        onSetupModel = onSetupModel,
                        onLoadModel = onLoadModel
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    SettingsSectionTitle("الإعدادات العامة")
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    SettingsMainItem(
                        title = "الحساب والتطبيق",
                        description = "إدارة ملفك الشخصي وإعدادات التطبيق.",
                        icon = Icons.Default.Person,
                        onClick = onAccountClick
                    )
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    SettingsMainItem(
                        title = "النماذج",
                        description = "تكوين نموذج الدردشة، الرؤية، والتضمين.",
                        icon = Icons.Default.Memory,
                        onClick = onModelsClick
                    )
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    SettingsMainItem(
                        title = "المستندات والبحث",
                        description = "تصفح المكتبة، إعدادات البحث الدلالي.",
                        icon = Icons.Default.FindInPage,
                        onClick = onDocumentsClick
                    )
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    SettingsMainItem(
                        title = "المحادثات",
                        description = "الوصول إلى السجل وإدارة الجلسات.",
                        icon = Icons.Default.ChatBubbleOutline,
                        onClick = onChatsClick
                    )
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    SettingsMainItem(
                        title = "الأدوات",
                        description = "تهيئة أدوات الهاتف والعمليات الخلفية.",
                        icon = Icons.Default.Build,
                        onClick = onToolsClick
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(40.dp))
                    SettingsSectionTitle("عن التطبيق والدعم")
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSupportCard(
                        onTermsClick = onTermsClick,
                        onPrivacyClick = onPrivacyClick
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    var clickCount by remember { mutableStateOf(0) }
                    Text(
                        text = "الإصدار $appVersion",
                        color = SettingsTextSecondary.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                clickCount++
                                if (clickCount >= 7) {
                                    onDevModeClick()
                                    clickCount = 0
                                }
                            },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(44.dp)
                .background(SettingsCardBg, CircleShape)
                .border(1.dp, SettingsBorder, CircleShape)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "رجوع",
                tint = SettingsTextPrimary
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = "الإعدادات",
            color = SettingsTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun SettingsModelStatusCard(
    modelName: String,
    modelStatus: String,
    modelState: com.example.localqwen.viewmodel.ModelState,
    onModelSettingsClick: () -> Unit,
    onCheckUpdatesClick: () -> Unit,
    onSetupModel: () -> Unit,
    onLoadModel: () -> Unit
) {
    val isReady = modelState is com.example.localqwen.viewmodel.ModelState.Ready
    val isNotImported = modelState is com.example.localqwen.viewmodel.ModelState.NotImported
    val isIdle = modelState is com.example.localqwen.viewmodel.ModelState.Idle
    val isLoading = modelState is com.example.localqwen.viewmodel.ModelState.Loading
    
    val statusColor = when {
        isReady -> SettingsGreen
        isNotImported -> SettingsOrange
        else -> SettingsOrange
    }
    
    val statusText = when {
        isReady -> "جاهز"
        isNotImported -> "غير مستورد"
        isLoading -> "جاري التشغيل"
        else -> "غير مشغل"
    }

    val statusDesc = when {
        isReady -> "النموذج جاهز للعمل محلياً."
        isNotImported -> "اختر نموذجاً للبدء باستخدام نبض."
        isLoading -> "يتم الآن تشغيل محرك نبض..."
        else -> "النموذج غير مشغّل حالياً."
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .shadow(
                elevation = if (isReady) 12.dp else 4.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = if (isReady) SettingsPrimary.copy(alpha = 0.25f) else Color.Black
            )
    ) {
        Surface(
            color = SettingsCardBg,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, SettingsBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(text = statusText, color = statusColor)
                    PulseWaveIndicator(
                        color = if (isReady) SettingsPrimary else SettingsTextSecondary.copy(alpha = 0.2f),
                        modifier = Modifier.width(60.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = if (isNotImported) "لم يتم إعداد النموذج" else modelName,
                    color = SettingsTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = statusDesc,
                    color = SettingsTextSecondary,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(28.dp))
                
                if (isNotImported) {
                    Button(
                        onClick = onSetupModel,
                        colors = ButtonDefaults.buttonColors(containerColor = SettingsPrimary),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("استيراد نموذج وتشغيله", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (isIdle || isLoading) {
                    Button(
                        onClick = onLoadModel,
                        colors = ButtonDefaults.buttonColors(containerColor = SettingsPrimary),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تشغيل النموذج", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onModelSettingsClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SettingsPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(52.dp)
                        ) {
                            Text("إعداد النموذج", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        OutlinedButton(
                            onClick = onCheckUpdatesClick,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = SettingsTextPrimary
                            ),
                            border = BorderStroke(1.dp, SettingsBorder),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Text("تحديثات", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun PulseWaveIndicator(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(24.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, centerY)
            lineTo(width * 0.2f, centerY)
            lineTo(width * 0.3f, centerY - 8.dp.toPx())
            lineTo(width * 0.4f, centerY + 8.dp.toPx())
            lineTo(width * 0.5f, centerY - 12.dp.toPx())
            lineTo(width * 0.6f, centerY + 12.dp.toPx())
            lineTo(width * 0.7f, centerY)
            lineTo(width, centerY)
        }
        
        drawPath(
            path = path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
        
        drawCircle(
            color = color,
            radius = 4.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(width * 0.55f, centerY - 6.dp.toPx())
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 16.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)
    )
}

@Composable
fun SettingsMainItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = SettingsCardBg,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, SettingsBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .shadow(2.dp, RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(SettingsPrimary.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = SettingsPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(18.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = SettingsTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = SettingsTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = SettingsTextSecondary.copy(alpha = 0.3f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun SettingsSupportCard(
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit
) {
    Surface(
        color = SettingsCardBg,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, SettingsBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Column {
            SettingsInfoRow(title = "شروط الخدمة", onClick = onTermsClick)
            HorizontalDivider(
                color = SettingsBorder,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            SettingsInfoRow(title = "سياسة الخصوصية", onClick = onPrivacyClick)
        }
    }
}

@Composable
fun SettingsInfoRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = SettingsTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = SettingsTextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp)
        )
    }
}
