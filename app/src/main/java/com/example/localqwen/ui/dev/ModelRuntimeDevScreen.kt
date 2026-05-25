package com.example.localqwen.ui.dev

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localqwen.modelruntime.*

// Dark Soft UI Colors (Matching Settings)
private val DevBg = Color(0xFF101113)
private val DevCardBg = Color(0xFF1A1B1F)
private val DevBorder = Color.White.copy(alpha = 0.08f)
private val DevPrimary = Color(0xFFFF4F64)
private val DevTextPrimary = Color.White
private val DevTextSecondary = Color.White.copy(alpha = 0.62f)

@Composable
fun ModelRuntimeDevScreen(
    onBackClick: () -> Unit
) {
    val candidates = listOf(DefaultModelCandidates.Qwen, DefaultModelCandidates.MiniCPM)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = { DevTopBar(onBackClick) },
            containerColor = DevBg
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    DevWarningBadge()
                }

                items(candidates) { candidate ->
                    ModelCandidateCard(candidate)
                }
            }
        }
    }
}

@Composable
fun DevTopBar(onBackClick: () -> Unit) {
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
                .background(DevCardBg, CircleShape)
                .border(1.dp, DevBorder, CircleShape)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "رجوع",
                tint = DevTextPrimary
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = "تجارب تشغيل النماذج",
            color = DevTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun DevWarningBadge() {
    Surface(
        color = DevPrimary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DevPrimary.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.BugReport, contentDescription = null, tint = DevPrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "واجهة مطور — لا تغيّر النموذج الافتراضي",
                color = DevPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ModelCandidateCard(candidate: ModelCandidate) {
    val clipboardManager = LocalClipboardManager.current
    
    // Theoretical evaluation for display
    val experiment = ModelRuntimeExperiment(
        candidate = candidate,
        benchmarkResult = null, // No real data yet
        qualityResult = null,   // No real data yet
        canBecomeDefault = false
    )
    val summary = ModelRuntimeEvaluator.evaluate(experiment)

    Surface(
        color = DevCardBg,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, DevBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = candidate.displayName,
                    color = DevTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                StatusChip(candidate.status)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            DetailRow(label = "Model ID", value = candidate.id, onCopy = {
                clipboardManager.setText(AnnotatedString(candidate.id))
            })
            DetailRow(label = "Runtime", value = candidate.runtimeType.name)
            DetailRow(label = "Extension", value = candidate.fileExtension)
            
            HorizontalDivider(color = DevBorder, modifier = Modifier.padding(vertical = 12.dp))
            
            Text(
                text = "ملاحظات:",
                color = DevTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = candidate.notes,
                color = DevTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { /* View Stats */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("التفاصيل", fontSize = 12.sp, color = Color.White)
                }
                
                Button(
                    onClick = { /* View Code */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("التقييم", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: ModelCandidateStatus) {
    val color = when(status) {
        ModelCandidateStatus.STABLE_DEFAULT -> Color(0xFF39C36A)
        ModelCandidateStatus.EXPERIMENTAL -> Color(0xFFFF9F2E)
        ModelCandidateStatus.REJECTED -> DevPrimary
        ModelCandidateStatus.NEEDS_TESTING -> Color(0xFF2196F3)
    }
    
    val text = when(status) {
        ModelCandidateStatus.STABLE_DEFAULT -> "مستقر"
        ModelCandidateStatus.EXPERIMENTAL -> "تجريبي"
        ModelCandidateStatus.REJECTED -> "مرفوض"
        ModelCandidateStatus.NEEDS_TESTING -> "بانتظار الفحص"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun DetailRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = DevTextSecondary, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, color = DevTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (onCopy != null) {
                IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DevPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
