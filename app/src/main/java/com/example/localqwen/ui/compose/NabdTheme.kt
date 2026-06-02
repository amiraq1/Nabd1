package com.example.localqwen.ui.compose

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * نظام ألوان نبض المركزي — Nabd Design System
 *
 * مستوحى من مبادئ Anthropic frontend-design:
 * "Dominant colors with sharp accents outperform timid, evenly-distributed palettes"
 *
 * الاتجاه الجمالي: Soft Luxury / Refined Arabic
 */
object NabdColors {

    // ── الألوان الأساسية (Primary) ──────────────────────────
    val Rose          = Color(0xFFE8364E)   // أساسي — أكثر عمقًا وثراءً
    val RoseLight     = Color(0xFFFFF0F1)   // خلفية ناعمة
    val RoseMuted     = Color(0xFFFFB4B8)   // لمسات خفيفة
    val RoseDark      = Color(0xFFC42D42)   // حالة ضغط / Active
    val RoseGlow      = Color(0x30E8364E)   // توهج خلفي

    // ── لون التمييز الحاد (Accent) ──────────────────────────
    val Amber         = Color(0xFFFF8F2E)   // Accent حاد للتنويع
    val AmberLight    = Color(0xFFFFF3E0)   // خلفية دافئة

    // ── خلفيات (Backgrounds) ────────────────────────────────
    val BgWarm        = Color(0xFFFFFBFA)   // بداية التدرج
    val BgWarmEnd     = Color(0xFFFFF5F3)   // نهاية التدرج
    val BgPure        = Color(0xFFFFFFFF)   // أبيض نقي

    // ── سطوح (Surfaces) ─────────────────────────────────────
    val CardElevated  = Color(0xFFFFFFFF)   // بطاقة مرتفعة
    val CardSubtle    = Color(0xFFFAF7F6)   // بطاقة خفيفة
    val CardBorder    = Color(0x12E8364E)   // حدود شفافة ورديّة

    // ── نصوص (Text) ─────────────────────────────────────────
    val Ink           = Color(0xFF1C1018)   // نص رئيسي — أعمق ودافئ
    val InkSecondary  = Color(0xFF6B5B62)   // نص ثانوي — دافئ
    val InkTertiary   = Color(0xFF9E8F95)   // نص ثالثي — خفيف
    val InkOnRose     = Color(0xFFFFFFFF)   // نص على الوردي

    // ── حالات (Status) ──────────────────────────────────────
    val Success       = Color(0xFF2DA861)   // نجاح — أخضر دافئ
    val SuccessLight  = Color(0xFFE8F8EF)
    val Error         = Color(0xFFD93D3D)   // خطأ
    val ErrorLight    = Color(0xFFFFEBEE)
    val Info          = Color(0xFF4A90D9)   // معلومات

    // ── ظلال (Shadows & Overlays) ───────────────────────────
    val ShadowSoft    = Color(0x0A1C1018)   // ظل ناعم جداً
    val ShadowMedium  = Color(0x141C1018)   // ظل متوسط
    val Overlay       = Color(0x08E8364E)   // طبقة وردية شفافة

    // ── فقاعات المحادثة (Chat Bubbles) ──────────────────────
    val BubbleUser    = Rose
    val BubbleAssistant = Color(0xFFF7F3F2) // رمادي دافئ بدل #F0F0F0
    val BubbleSystem  = Color(0xFFF5F0EE)

    // ── تدرجات (Gradients) ──────────────────────────────────
    val BgGradient = Brush.verticalGradient(
        colors = listOf(BgWarm, BgWarmEnd)
    )

    val RoseGradient = Brush.linearGradient(
        colors = listOf(Rose, Color(0xFFFF6B7A))
    )

    val HeroGlow = Brush.radialGradient(
        colors = listOf(
            RoseGlow,
            Color.Transparent
        )
    )
}

private val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = NabdColors.Rose,
    onPrimary = NabdColors.InkOnRose,
    secondary = NabdColors.Amber,
    background = NabdColors.BgWarm,
    onBackground = NabdColors.Ink,
    surface = NabdColors.CardElevated,
    onSurface = NabdColors.Ink,
    error = NabdColors.Error,
    onError = Color.White
)

@androidx.compose.runtime.Composable
fun NabdTheme(
    content: @androidx.compose.runtime.Composable () -> Unit
) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = LightColorScheme,
        typography = NabdTypography,
        content = content
    )
}
