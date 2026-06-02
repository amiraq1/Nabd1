package com.example.localqwen.ui.compose

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.example.localqwen.R

// Google Fonts Provider Setup
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val NotoKufiArabicFont = GoogleFont("Noto Kufi Arabic")
val NotoSansArabicFont = GoogleFont("Noto Sans Arabic")

val NotoKufiArabicFamily = FontFamily(
    Font(googleFont = NotoKufiArabicFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = NotoKufiArabicFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = NotoKufiArabicFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = NotoKufiArabicFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = NotoKufiArabicFont, fontProvider = provider, weight = FontWeight.Bold)
)

val NotoSansArabicFamily = FontFamily(
    Font(googleFont = NotoSansArabicFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = NotoSansArabicFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = NotoSansArabicFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = NotoSansArabicFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = NotoSansArabicFont, fontProvider = provider, weight = FontWeight.Bold)
)

val NabdTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = NotoKufiArabicFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = NotoKufiArabicFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = NotoKufiArabicFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = NotoKufiArabicFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = NotoKufiArabicFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = NotoSansArabicFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = NotoSansArabicFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = NotoSansArabicFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = NotoSansArabicFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = NotoSansArabicFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = NotoSansArabicFamily,
        fontWeight = FontWeight.Light,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
)
