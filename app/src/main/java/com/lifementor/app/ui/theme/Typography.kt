package com.lifementor.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lifementor.app.R

/** Inter body text + Holtwood One SC display serif (titles / badges), matching the design */

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semi_bold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val DisplayFontFamily = FontFamily(Font(R.font.holtwood_one_sc, FontWeight.Normal))

val LifeMentorTypography = androidx.compose.material3.Typography(
    displaySmall = TextStyle(fontFamily = DisplayFontFamily, fontSize = 20.sp, color = InkBlack),
    headlineSmall = TextStyle(fontFamily = DisplayFontFamily, fontSize = 18.sp, color = InkBlack),
    titleLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    titleSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    bodyLarge = TextStyle(fontFamily = InterFamily, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = InterFamily, fontSize = 12.sp, color = InkSoft),
    labelLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
    labelSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
)
