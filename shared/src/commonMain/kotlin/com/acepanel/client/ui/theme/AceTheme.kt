package com.acepanel.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Immutable
data class AceColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val fill: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentDeep: Color,
    val text1: Color,
    val text2: Color,
    val text3: Color,
    val divider: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val error: Color,
    val errorSoft: Color,
    val terminal: Color,
    val terminalText: Color,
    val terminalGreen: Color,
    val terminalBlue: Color,
    val terminalDim: Color,
    val isDark: Boolean,
)

val LightAceColors = AceColors(
    bg = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF7F8FA),
    fill = Color(0xFFEFF1F4),
    accent = Color(0xFF3482FF),
    accentSoft = Color(0x1F3482FF),
    accentDeep = Color(0xFF2461D6),
    text1 = Color(0xFF17181A),
    text2 = Color(0xFF74777C),
    text3 = Color(0xFFA5A8AD),
    divider = Color(0xFFECEEF1),
    success = Color(0xFF22B26C),
    successSoft = Color(0x1F22B26C),
    warning = Color(0xFFFF8F1F),
    warningSoft = Color(0x1FFF8F1F),
    error = Color(0xFFF4323F),
    errorSoft = Color(0x1FF4323F),
    terminal = Color(0xFF17181B),
    terminalText = Color(0xFFD5D8DC),
    terminalGreen = Color(0xFF6BD08C),
    terminalBlue = Color(0xFF6FA8FF),
    terminalDim = Color(0xFF7A7F87),
    isDark = false,
)

val DarkAceColors = AceColors(
    bg = Color(0xFF101113),
    surface = Color(0xFF1A1B1E),
    surface2 = Color(0xFF202226),
    fill = Color(0xFF26282C),
    accent = Color(0xFF4C90FF),
    accentSoft = Color(0x2A4C90FF),
    accentDeep = Color(0xFF7AB0FF),
    text1 = Color(0xFFF2F3F5),
    text2 = Color(0xFFA0A4AB),
    text3 = Color(0xFF6E7278),
    divider = Color(0xFF2A2C30),
    success = Color(0xFF3CC97F),
    successSoft = Color(0x263CC97F),
    warning = Color(0xFFFFA23E),
    warningSoft = Color(0x26FFA23E),
    error = Color(0xFFFF5A63),
    errorSoft = Color(0x26FF5A63),
    terminal = Color(0xFF0C0D0F),
    terminalText = Color(0xFFD5D8DC),
    terminalGreen = Color(0xFF6BD08C),
    terminalBlue = Color(0xFF6FA8FF),
    terminalDim = Color(0xFF7A7F87),
    isDark = true,
)

val LocalAceColors = staticCompositionLocalOf { LightAceColors }

val LocalDesktopLayout = staticCompositionLocalOf { false }

object AceType {
    val pageTitle = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = Color.Unspecified)
    val loginTitle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
    val section = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    val listPrimary = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
    val listStrong = TextStyle(fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal)
    val bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val secondary = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
    val tag = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val tab = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val button = TextStyle(fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
    val number = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Default)
    val bigNumber = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Default)
    val mono = TextStyle(fontFamily = FontFamily.Monospace)
    val nav = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
}

object AceDimens {
    val screenPad = 26.dp
    val cardRadius = 20.dp
    val cardPad = 20.dp
    val sectionGap = 16.dp
    val inputHeight = 54.dp
    val navHeight = 78.dp
    val headerAction = 40.dp
}

@Composable
fun AceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkAceColors else LightAceColors
    val miuixColors = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            background = colors.bg,
            onBackground = colors.text1,
            onBackgroundVariant = colors.text3,
            surface = colors.bg,
            onSurface = colors.text1,
            onSurfaceSecondary = colors.text2,
            onSurfaceVariantSummary = colors.text3,
            surfaceVariant = colors.surface,
            surfaceContainer = colors.surface,
            onSurfaceContainer = colors.text1,
            surfaceContainerHigh = colors.fill,
            onSurfaceContainerHigh = colors.text2,
            secondaryContainer = colors.fill,
            onSecondaryContainer = colors.text3,
            tertiaryContainer = colors.accentSoft,
            onTertiaryContainer = colors.accent,
            dividerLine = colors.divider,
            outline = colors.divider,
            error = colors.error,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            background = colors.bg,
            onBackground = colors.text1,
            onBackgroundVariant = colors.text3,
            surface = colors.bg,
            onSurface = colors.text1,
            onSurfaceSecondary = colors.text2,
            onSurfaceVariantSummary = colors.text3,
            surfaceVariant = colors.surface,
            surfaceContainer = colors.surface,
            onSurfaceContainer = colors.text1,
            surfaceContainerHigh = colors.fill,
            onSurfaceContainerHigh = colors.text2,
            secondaryContainer = colors.fill,
            onSecondaryContainer = colors.text3,
            tertiaryContainer = colors.accentSoft,
            onTertiaryContainer = colors.accent,
            dividerLine = colors.divider,
            outline = colors.divider,
            error = colors.error,
            onError = Color.White,
        )
    }
    CompositionLocalProvider(LocalAceColors provides colors) {
        MiuixTheme(colors = miuixColors) {
            content()
        }
    }
}

object AceTheme {
    val colors: AceColors
        @Composable get() = LocalAceColors.current
}
