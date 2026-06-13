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
// Composeネイティブ描画（NativeReadingScreen / NativeTableOfContentsScreen）の
// 全色をここに集約する。直書き色の散在を防ぐための単一の正典。
// （旧 cssAttribute はWebView時代の遺物のため削除済み）
// ============================================================
enum class ReadingTheme { LIGHT, SEPIA, DARK }

data class ReadingColors(
    val background: Color,        // 本文・目次の背景
    val text: Color,              // 本文文字
    val textSecondary: Color,     // 補助テキスト（エラー詳細・空状態など）
    val navBackground: Color,     // 下部ナビバー（使用側で半透明化する）
    val topBarBackground: Color,  // 読書画面トップバー
    val topBarTitle: Color,       // トップバーのタイトル文字
    val topBarIcon: Color,        // トップバー・ナビバーのアイコン
    val ruby: Color,              // ルビ文字
    val hr: Color,                // 本文中の水平線（シーン区切り）
    val divider: Color,           // 目次の区切り線
    val blockBackground: Color,   // 前書き・後書きブロック背景
    val blockBorder: Color,       // 前書き・後書きブロック枠線
    val accent: Color,            // 強調色（目次の現在章ハイライトなど）
    val isLight: Boolean,         // true ならステータスバーアイコンを暗色にする
)

val ReadingTheme.colors: ReadingColors
    get() = when (this) {
        // LIGHT はテーマ導入前の直書き値（FCFAF2 / D4B896 等）をそのまま移植し、
        // 既存ユーザーの見た目を変えない
        ReadingTheme.LIGHT -> ReadingColors(
            background       = Color(0xFFFCFAF2),
            text             = Color(0xFF1C1916),
            textSecondary    = Color(0xFF666666),
            navBackground    = Color(0xFFFCFAF2),
            topBarBackground = Color(0xFFD4B896),
            topBarTitle      = Color(0xFF1C1916),
            topBarIcon       = Color(0xFF524540),
            ruby             = Color(0xFF777777),
            hr               = Color(0xFFCCCCCC),
            divider          = Color(0xFFE0DCD0),
            blockBackground  = Color(0xFFF9F9F9),
            blockBorder      = Color(0xFFEEEEEE),
            accent           = Color(0xFF7B3F2A), // アプリ全体の primary（朱墨色）と統一
            isLight          = true,
        )
        ReadingTheme.SEPIA -> ReadingColors(
            background       = Color(0xFFF5EDD6),
            text             = Color(0xFF3B2A14),
            textSecondary    = Color(0xFF7A6648),
            navBackground    = Color(0xFFEEE0BA),
            topBarBackground = Color(0xFFE2D2A4),
            topBarTitle      = Color(0xFF3B2A14),
            topBarIcon       = Color(0xFF5C4A28),
            ruby             = Color(0xFF8A734F),
            hr               = Color(0xFFD5C5A0),
            divider          = Color(0xFFE0D3AE),
            blockBackground  = Color(0xFFEFE6CC),
            blockBorder      = Color(0xFFE2D6B4),
            accent           = Color(0xFF8C4A2F), // セピア背景に合わせやや深めの朱
            isLight          = true,
        )
        ReadingTheme.DARK -> ReadingColors(
            background       = Color(0xFF1C1916),
            text             = Color(0xFFD4C8C0),
            textSecondary    = Color(0xFF9C9088),
            navBackground    = Color(0xFF242018),
            topBarBackground = Color(0xFF242018),
            topBarTitle      = Color(0xFFEDE1DC),
            topBarIcon       = Color(0xFFC0B0A8),
            ruby             = Color(0xFF8F837B),
            hr               = Color(0xFF45403A),
            divider          = Color(0xFF3A342E),
            blockBackground  = Color(0xFF24201C),
            blockBorder      = Color(0xFF38322C),
            accent           = Color(0xFFFFBBA3), // ダーク用の明るい朱（PrimaryDark と統一）
            isLight          = false,
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
