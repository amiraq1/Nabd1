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
    onBackClick: () -> Unit,
    onImportModelClick: () -> Unit
) {
    // الألوان المتوافقة مع الثيم الداكن للتطبيق في لقطة الشاشة
    val backgroundColor = Color(0xFF121214)
    val cardBackground = Color(0xFF1A1A1E)
    val accentColor = Color(0xFFFF4A5A) // اللون الوردي/الأحمر المعتمد في تطبيق نبض
    val orangeColor = Color(0xFFE57373)

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
                    // السطر العلوي: الأيقونة مع شارة "غير مستورد"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // أيقونة النبض التخطيطية (استبدلها بـ الـ Vector الخاص بك إذا توفر)
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_lock_power_off), // مؤقت للأيقونة
                            contentDescription = "Pulse Status",
                            tint = Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )

                        // شارة "غير مستورد"
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = orangeColor.copy(alpha = 0.15f),
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(orangeColor, RoundedCornerShape(50))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "غير مستورد",
                                    color = orangeColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // نصوص حالة النموذج
                    Text(
                        text = "لم يتم إعداد النموذج",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.End)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "اختر نموذجاً للبدء باستخدام نبض.",
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
                                text = "استيراد نموذج وتشغيله",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // تم حذف الإعدادات العامة وباقي الحقول لتبقى الشاشة مخصصة فقط للاستيراد
        }
    }
}
