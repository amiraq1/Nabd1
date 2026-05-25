package com.example.localqwen.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localqwen.verification.SourceRequirement
import com.example.localqwen.verification.VerificationLevel

@Composable
fun VerificationBadge(
    level: VerificationLevel?,
    sourceRequirement: SourceRequirement?
) {
    val badgeText = getVerificationBadgeText(level, sourceRequirement) ?: return

    val badgeColor = when {
        sourceRequirement == SourceRequirement.PROFESSIONAL_ADVICE -> Color(0xFFFF9800).copy(alpha = 0.1f)
        level == VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE -> Color(0xFF2196F3).copy(alpha = 0.1f)
        level == VerificationLevel.LEVEL_1_CONTEXTUAL_CAUTION -> Color(0xFF9E9E9E).copy(alpha = 0.1f)
        else -> Color.LightGray.copy(alpha = 0.1f)
    }

    val textColor = when {
        sourceRequirement == SourceRequirement.PROFESSIONAL_ADVICE -> Color(0xFFE65100)
        level == VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE -> Color(0xFF1976D2)
        level == VerificationLevel.LEVEL_1_CONTEXTUAL_CAUTION -> Color(0xFF616161)
        else -> Color.DarkGray
    }

    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .background(badgeColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = when {
                sourceRequirement == SourceRequirement.PROFESSIONAL_ADVICE -> Icons.Default.Warning
                level == VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE -> Icons.Default.Info
                else -> Icons.Default.VerifiedUser
            },
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = badgeText,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun getVerificationBadgeText(level: VerificationLevel?, sourceRequirement: SourceRequirement?): String? {
    if (level == null) return null
    
    return when {
        level == VerificationLevel.LEVEL_0_DIRECT -> null
        sourceRequirement == SourceRequirement.PROFESSIONAL_ADVICE -> "معلومة حساسة — ليست نصيحة مهنية"
        level == VerificationLevel.LEVEL_2_RECENT_OR_SENSITIVE -> "تحتاج تحققاً حديثاً"
        level == VerificationLevel.LEVEL_1_CONTEXTUAL_CAUTION -> "إجابة تحتاج دقة سياقية"
        else -> null
    }
}
