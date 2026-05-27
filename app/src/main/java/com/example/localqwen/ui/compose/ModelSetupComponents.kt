package com.example.localqwen.ui.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localqwen.viewmodel.ModelSetupState

@Composable
fun ModelSetupWizardSheet(
    setupState: ModelSetupState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onStartChat: () -> Unit
) {
    var showTechDetails by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "إعداد نبض",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        when (setupState) {
            is ModelSetupState.Idle -> { /* Should not happen if sheet is open */ }
            ModelSetupState.Validating, ModelSetupState.Copying, ModelSetupState.Loading -> {
                ModelImportProgress(setupState)
            }
            is ModelSetupState.Ready -> {
                ModelReadyState(setupState.modelName, onStartChat)
            }
            is ModelSetupState.Error -> {
                ModelErrorState(
                    userMessage = setupState.userMessage,
                    onRetry = onRetry,
                    onShowTechDetails = { showTechDetails = true }
                )
                
                if (showTechDetails) {
                    AlertDialog(
                        onDismissRequest = { showTechDetails = false },
                        title = { Text("التفاصيل التقنية") },
                        text = {
                            Text(
                                text = setupState.technicalMessage ?: "لا توجد تفاصيل إضافية",
                                fontSize = 12.sp,
                                modifier = Modifier.heightIn(max = 200.dp)
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showTechDetails = false }) {
                                Text("إغلاق")
                            }
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ModelImportProgress(state: ModelSetupState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = Color(0xFFFF5A5F),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        val message = when (state) {
            ModelSetupState.Validating -> "جاري التحقق من ملف النموذج..."
            ModelSetupState.Copying -> "جاري حفظ النموذج محلياً..."
            ModelSetupState.Loading -> "جاري تشغيل النموذج..."
            else -> ""
        }
        
        Text(
            text = message,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "قد تستغرق العملية دقيقة حسب حجم الملف وسرعة الجهاز.",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ModelReadyState(modelName: String, onStartChat: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color(0xFFE8F5E9), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "النموذج جاهز 🎉",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )
        
        Text(
            text = "تم إعداد $modelName بنجاح.",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onStartChat,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F))
        ) {
            Text("ابدأ المحادثة", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ModelErrorState(userMessage: String, onRetry: () -> Unit, onShowTechDetails: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color(0xFFFFEBEE), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(32.dp))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "تعذر إعداد النموذج",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = userMessage,
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onShowTechDetails,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("التفاصيل", fontSize = 13.sp)
            }
            
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1.5f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F))
            ) {
                Text("جرّب ملفاً آخر", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ModelSetupCard(onImportClick: () -> Unit) {
    var showHelpDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .shadow(4.dp, RoundedCornerShape(28.dp)),
        color = Color.White,
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFFF5A5F).copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color(0xFFFF5A5F))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "إعداد النموذج",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "اختر ملف النموذج بصيغة .litertlm ليعمل نبض محلياً على جهازك.",
                fontSize = 14.sp,
                color = Color.Gray,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onImportClick,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F))
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("استيراد نموذج وتشغيله", fontWeight = FontWeight.Bold)
            }
            
            TextButton(
                onClick = { showHelpDialog = true },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("ما هو ملف النموذج؟", color = Color.Gray, fontSize = 13.sp)
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("حول ملف النموذج") },
            text = {
                Text(
                    "النموذج هو ملف ذكاء اصطناعي يعمل محلياً داخل جهازك. لا يتم رفع أي من بياناتك أو محادثاتك إلى خوادم خارجية. يجب أن يكون ملف النموذج بصيغة .litertlm ليتوافق مع نبض."
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("فهمت")
                }
            }
        )
    }
}
