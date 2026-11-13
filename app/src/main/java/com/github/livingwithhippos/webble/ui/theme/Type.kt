package com.github.livingwithhippos.webble.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.livingwithhippos.webble.R

/** Barlow Condensed (SIL OFL, res/raw/barlow_ofl.txt): headings only, usually uppercase. */
val Condensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

private val base = Typography()

val Typography = Typography(
    displaySmall = base.displaySmall.copy(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 44.sp,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = base.headlineSmall.copy(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = base.titleLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
)
