package com.example.timemanager.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.timemanager.R

val PtSansFontFamily = FontFamily(
    Font(R.font.pt_sans_regular, FontWeight.Normal),
    Font(R.font.pt_sans_bold, FontWeight.Bold)
)

val RobotoFontFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_bold, FontWeight.Bold)
)

val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

val NunitoFontFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold)
)

val OpenSansFontFamily = FontFamily(
    Font(R.font.open_sans_regular, FontWeight.Normal),
    Font(R.font.open_sans_bold, FontWeight.Bold)
)

val JonovaFontFamily = FontFamily(
    Font(R.font.jonova_regular, FontWeight.Normal),
    Font(R.font.jonova_bold, FontWeight.Bold)
)

val MazzardFontFamily = FontFamily(
    Font(R.font.mazzard_regular, FontWeight.Normal),
    Font(R.font.mazzard_semibold, FontWeight.SemiBold),
    Font(R.font.mazzard_bold, FontWeight.Bold)
)

val LatoFontFamily = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold)
)

val LobsterFontFamily = FontFamily(
    Font(R.font.lobster_regular, FontWeight.Normal)
)

val RubikFontFamily = FontFamily(
    Font(R.font.rubik, FontWeight.Normal)
)

enum class AppFont(val displayName: String, val fontFamily: FontFamily) {
    PT_SANS("PT Sans", PtSansFontFamily),
    OPEN_SANS("Open Sans", OpenSansFontFamily),
    ROBOTO("Roboto", RobotoFontFamily),
    INTER("Inter", InterFontFamily),
    NUNITO("Nunito", NunitoFontFamily),
    LATO("Lato", LatoFontFamily),
    LOBSTER("Lobster", LobsterFontFamily),
    JONOVA("Jonova", JonovaFontFamily),
    MAZZARD("Mazzard", MazzardFontFamily),
    RUBIK("Rubik", RubikFontFamily);

    companion object {
        fun fromName(name: String): AppFont = entries.find { it.name == name } ?: PT_SANS
    }
}

fun appTypography(fontFamily: FontFamily): Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),

    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

val Typography = appTypography(PtSansFontFamily)
