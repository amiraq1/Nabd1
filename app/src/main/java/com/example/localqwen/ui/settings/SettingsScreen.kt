package com.example.localqwen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modelState: com.example.localqwen.viewmodel.ModelState,
    modelName: String,
    onBackClick: () -> Unit,
    onImportModelClick: () -> Unit
) {
    // الألوان المتوافقة مع الثيم الداكن للتطبيق في لقطة الشاشة
    val backgroundColor = Color(0xFF121214)
    val cardBackground = Color(0xFF1A1A1E)
    val accentColor = Color(0xFFFF4A5A) // اللون الوردي/الأحمر المعتمد في تطبيق نبض
    val orangeColor = Color(0xFFE57373)
    val greenColor = Color(0xFF4CAF50)
    val blueColor = Color(0xFF2196F3)

    val isReady = modelState is com.example.localqwen.viewmodel.ModelState.Ready
    val isLoading = modelState is com.example.localqwen.viewmodel.ModelState.Loading
    val isIdle = modelState is com.example.localqwen.viewmodel.ModelState.Idle

    val badgeColor = if (isReady) greenColor else if (isLoading) blueColor else orangeColor
    val badgeText = when (modelState) {
        is com.example.localqwen.viewmodel.ModelState.Ready -> "جاهز"
        is com.example.localqwen.viewmodel.ModelState.Loading -> "جاري التحميل..."
        is com.example.localqwen.viewmodel.ModelState.Idle -> "مستورد"
        is com.example.localqwen.viewmodel.ModelState.Error -> "خطأ"
        else -> "غير مستورد"
    }

    val mainText = if (isReady || isIdle || isLoading) modelName else "لم يتم إعداد النموذج"
    val descText = when (modelState) {
        is com.example.localqwen.viewmodel.ModelState.Ready -> "النموذج محمل في الذاكرة ويعمل."
        is com.example.localqwen.viewmodel.ModelState.Loading -> "يتم الآن تحميل النموذج..."
        is com.example.localqwen.viewmodel.ModelState.Idle -> "النموذج مستورد ولكنه غير محمل حالياً."
        is com.example.localqwen.viewmodel.ModelState.Error -> "حدث خطأ أثناء تحميل النموذج."
        else -> "اختر نموذجاً للبدء باستخدام نبض."
    }
    
    val btnText = if (isReady || isIdle || isLoading) "تغيير النموذج" else "استيراد نموذج وتشغيله"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = "الإعدادات",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        },
        containerColor = backgroundColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // بطاقة استيراد النموذج والإعداد
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // السطر العلوي: الأيقونة مع الشارة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_lock_power_off), // مؤقت للأيقونة
                            contentDescription = "Pulse Status",
                            tint = Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = badgeColor.copy(alpha = 0.15f),
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(badgeColor, RoundedCornerShape(50))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = badgeText,
                                    color = badgeColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // نصوص حالة النموذج
                    Text(
                        text = mainText,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.End)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = descText,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.End)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // زر استيراد نموذج وتشغيله
                    Button(
                        onClick = onImportModelClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "استيراد",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = btnText,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
