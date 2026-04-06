package com.novelreader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================
// 読書テーマ: ライト/セピア/ダークの3種類
// WebView の背景・テキスト色、ナビバー背景と連動させるため
// Compose 側でも色トークンを持つ。
// ============================================================
enum class ReadingTheme { LIGHT, SEPIA, DARK }

data class ReadingColors(
    val webBackground: Color,
    val webText: Color,
    val navBackground: Color,
    val cssAttribute: String,   // data-theme に渡す文字列
)

val ReadingTheme.colors: ReadingColors
    get() = when (this) {
        ReadingTheme.LIGHT -> ReadingColors(
            webBackground = Color(0xFFFAF7F4),
            webText       = Color(0xFF1C1916),
            navBackground = Color(0xFFFAF7F4),
            cssAttribute  = "light",
        )
        ReadingTheme.SEPIA -> ReadingColors(
            webBackground = Color(0xFFF5EDD6),
            webText       = Color(0xFF3B2A14),
            navBackground = Color(0xFFEEE0BA),
            cssAttribute  = "sepia",
        )
        ReadingTheme.DARK -> ReadingColors(
            webBackground = Color(0xFF1C1916),
            webText       = Color(0xFFD4C8C0),
            navBackground = Color(0xFF242018),
            cssAttribute  = "dark",
        )
    }

// ============================================================
// Material3 カラースキーム
// ============================================================
private val LightColorScheme = lightColorScheme(
    primary              = PrimaryLight,
    onPrimary            = OnPrimaryLight,
    primaryContainer     = PrimaryContainerLight,
    onPrimaryContainer   = OnPrimaryContainerLight,
    secondary            = SecondaryLight,
    onSecondary          = OnSecondaryLight,
    secondaryContainer   = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary             = TertiaryLight,
    onTertiary           = OnTertiaryLight,
    tertiaryContainer    = TertiaryContainerLight,
    onTertiaryContainer  = OnTertiaryContainerLight,
    error                = ErrorLight,
    onError              = OnErrorLight,
    errorContainer       = ErrorContainerLight,
    onErrorContainer     = OnErrorContainerLight,
    background           = BackgroundLight,
    onBackground         = OnBackgroundLight,
    surface              = SurfaceLight,
    onSurface            = OnSurfaceLight,
    surfaceVariant       = SurfaceVariantLight,
    onSurfaceVariant     = OnSurfaceVariantLight,
    surfaceContainer     = SurfaceContainerLight,
    outline              = OutlineLight,
    outlineVariant       = OutlineVariantLight,
    inverseSurface       = InverseSurfaceLight,
    inverseOnSurface     = InverseOnSurfaceLight,
    inversePrimary       = InversePrimaryLight,
)

private val DarkColorScheme = darkColorScheme(
    primary              = PrimaryDark,
    onPrimary            = OnPrimaryDark,
    primaryContainer     = PrimaryContainerDark,
    onPrimaryContainer   = OnPrimaryContainerDark,
    secondary            = SecondaryDark,
    onSecondary          = OnSecondaryDark,
    secondaryContainer   = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary             = TertiaryDark,
    onTertiary           = OnTertiaryDark,
    tertiaryContainer    = TertiaryContainerDark,
    onTertiaryContainer  = OnTertiaryContainerDark,
    error                = ErrorDark,
    onError              = OnErrorDark,
    errorContainer       = ErrorContainerDark,
    onErrorContainer     = OnErrorContainerDark,
    background           = BackgroundDark,
    onBackground         = OnBackgroundDark,
    surface              = SurfaceDark,
    onSurface            = OnSurfaceDark,
    surfaceVariant       = SurfaceVariantDark,
    onSurfaceVariant     = OnSurfaceVariantDark,
    surfaceContainer     = SurfaceContainerDark,
    outline              = OutlineDark,
    outlineVariant       = OutlineVariantDark,
    inverseSurface       = InverseSurfaceDark,
    inverseOnSurface     = InverseOnSurfaceDark,
    inversePrimary       = InversePrimaryDark,
)

@Composable
fun NovelReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // ステータスバーアイコンの色をテーマに合わせる（ライト=暗いアイコン、ダーク=明るいアイコン）
    // setDecorFitsSystemWindows は MainActivity で呼んでいるためここでは行わない
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NovelReaderTypography,
        content = content,
    )
}
