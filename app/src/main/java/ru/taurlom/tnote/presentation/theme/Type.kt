package ru.taurlom.tnote.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.AppFont

val PtSansFontFamily = FontFamily(
    Font(R.font.pt_sans_regular, FontWeight.Normal),
    Font(R.font.pt_sans_bold, FontWeight.Bold),
    Font(R.font.pt_sans_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.pt_sans_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

val RobotoFontFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_bold, FontWeight.Bold),
)

val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

val NunitoFontFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.nunito_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

val OpenSansFontFamily = FontFamily(
    Font(R.font.open_sans_regular, FontWeight.Normal),
    Font(R.font.open_sans_bold, FontWeight.Bold),
    // У Open Sans нет bold_italic — курсив есть только для обычного начертания.
    Font(R.font.open_sans_italic, FontWeight.Normal, FontStyle.Italic),
)

val JonovaFontFamily = FontFamily(
    Font(R.font.jonova_regular, FontWeight.Normal),
    Font(R.font.jonova_bold, FontWeight.Bold),
    Font(R.font.jonova_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.jonova_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

val MazzardFontFamily = FontFamily(
    Font(R.font.mazzard_regular, FontWeight.Normal),
    Font(R.font.mazzard_semibold, FontWeight.SemiBold),
    Font(R.font.mazzard_bold, FontWeight.Bold),
)

val LatoFontFamily = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold),
    Font(R.font.lato_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.lato_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

val LobsterFontFamily = FontFamily(
    Font(R.font.lobster_regular, FontWeight.Normal),
)

val RubikFontFamily = FontFamily(
    Font(R.font.rubik, FontWeight.Normal),
    // У Rubik нет bold — только обычное начертание и курсив.
    Font(R.font.rubik_italic, FontWeight.Normal, FontStyle.Italic),
)

/**
 * Привязка шрифта к FontFamily — presentation-концерн (Compose-типы и
 * ресурсы R.font). Сам enum AppFont живёт в domain (domain/model/AppFont.kt):
 * его значение хранится в настройках и в манифесте резервной копии.
 */
val AppFont.fontFamily: FontFamily
    get() = when (this) {
        AppFont.PT_SANS -> PtSansFontFamily
        AppFont.OPEN_SANS -> OpenSansFontFamily
        AppFont.ROBOTO -> RobotoFontFamily
        AppFont.INTER -> InterFontFamily
        AppFont.NUNITO -> NunitoFontFamily
        AppFont.LATO -> LatoFontFamily
        AppFont.LOBSTER -> LobsterFontFamily
        AppFont.JONOVA -> JonovaFontFamily
        AppFont.MAZZARD -> MazzardFontFamily
        AppFont.RUBIK -> RubikFontFamily
    }

fun appTypography(fontFamily: FontFamily): Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),

    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

val Typography = appTypography(PtSansFontFamily)
