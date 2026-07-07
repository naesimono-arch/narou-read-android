package com.novelreader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
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
        // UI-n: 白紙設計で採用した視覚言語 D「和モダン・余白」へ全面差し替え。
        // なぜ旧クリーム＋朱墨を捨てるか: UI-n は既存配色を踏襲せず作り直す方針（docs/decisions/0005-ui-n-visual-language-D.md）。
        // 値は確定モック ui-n-phase0/reading-D.html から写経し、寒色白×藍ヘアラインで統一する。
        // LIGHT = 素地 #FBFAF8／墨 #1C1F26／アクセント藍 #1C3D5A。
        ReadingTheme.LIGHT -> ReadingColors(
            background       = Color(0xFFFBFAF8),
            text             = Color(0xFF1C1F26),
            textSecondary    = Color(0xFF7C808B),
            navBackground    = Color(0xFFFBFAF8),
            // D はトップ/ボトムバーを本文素地と同色に揃え、藍のヘアラインだけで境界を示す。
            // アイコンは素地から十分離れた濃灰でコントラストを確保（タイトルは墨色）。
            topBarBackground = Color(0xFFFBFAF8),
            topBarTitle      = Color(0xFF1C1F26),
            topBarIcon       = Color(0xFF4A4F58),
            ruby             = Color(0xFF8B96A0),
            // hr は藍 #1C3D5A を素地に約50%で溶かした青灰（モックの opacity:.5 相当）。
            // 破線で主張させすぎないシーン区切りにする。
            hr               = Color(0xFF9FB0BC),
            divider          = Color(0xFFECEAE4), // D のヘアライン色
            blockBackground  = Color(0xFFF1F0EC),
            blockBorder      = Color(0xFFE4E2DB),
            accent           = Color(0xFF1C3D5A), // D の藍（現在章ハイライト・チップ選択色）
            isLight          = true,
        )
        // SEPIA は D の寒色を温かい紙トーンへ寄せた変種。藍アクセントは骨格として残しつつ
        // やや深い藍鼠 #2E4A60 にして暖色背景と調和させる。
        // なぜモック reading-D.html の .t-sepia 写経値（#F3ECDD 系）から逸脱するか:
        // 実機フィードバック（2026-07-07「ライトとセピアの色味に差がなく同じ色に見える」）を受け、
        // 彩度を約15%まで上げた琥珀の紙・焦茶の墨へ再調律してライト（寒色白・ほぼ無彩色）との
        // 知覚差を保証するため。モック側への逆反映は handover の宿題（実装がこの値の正本）。
        ReadingTheme.SEPIA -> ReadingColors(
            background       = Color(0xFFF2E7CE),
            text             = Color(0xFF3D3121),
            textSecondary    = Color(0xFF8C7D5D),
            navBackground    = Color(0xFFECDFC0),
            // LIGHT と同方針: 上下バーを本文紙トーンに揃え、ヘアラインで境界を示す。
            topBarBackground = Color(0xFFECDFC0),
            topBarTitle      = Color(0xFF3D3121),
            topBarIcon       = Color(0xFF6A5B3C),
            ruby             = Color(0xFFA3906A),
            hr               = Color(0xFFB4A379),
            divider          = Color(0xFFE0D3B0),
            blockBackground  = Color(0xFFEBDEBE),
            blockBorder      = Color(0xFFDCCC9F),
            accent           = Color(0xFF2E4A60), // 暖色背景に合わせやや深めの藍鼠
            isLight          = true,
        )
        // DARK は D の寒色を保った冷たい暗面（旧の温かい黒 #1C1916 から転換）。
        // アクセントは暗背景で沈まないよう明るい青 #6E96B8 にする（モック reading-D.html の .t-dark）。
        ReadingTheme.DARK -> ReadingColors(
            background       = Color(0xFF14171C),
            text             = Color(0xFFC7CDD3),
            textSecondary    = Color(0xFF7B838C),
            navBackground    = Color(0xFF181C22),
            topBarBackground = Color(0xFF181C22),
            topBarTitle      = Color(0xFFC7CDD3),
            topBarIcon       = Color(0xFF9AA2AB),
            ruby             = Color(0xFF6E7984),
            hr               = Color(0xFF46566A),
            divider          = Color(0xFF2A2F38),
            blockBackground  = Color(0xFF1B1F26),
            blockBorder      = Color(0xFF2A2F38),
            accent           = Color(0xFF6E96B8), // 暗背景で沈まない明るい藍
            isLight          = false,
        )
    }

/**
 * テーマから読書配色を取得する @Composable アクセサ。
 * なぜ remember 化するか: 上の ReadingTheme.colors getter は呼ぶたびに 14 色分の
 * ReadingColors を新規生成するため、読書画面の再コンポジション（スクロール保存・設定変更等）の
 * たびにアロケートが走る。テーマ切替（theme）を key にして、テーマが変わらない限り同一
 * インスタンスを再利用し、アロケートと下流の等値比較コストを避ける。
 */
@Composable
fun rememberReadingColors(theme: ReadingTheme): ReadingColors =
    remember(theme) { theme.colors }

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

// セピアはライトの暖色変種＝素地・墨・面・ヘアライン・藍だけを琥珀紙トーンへ差し替え、
// secondary（青磁＝未読の意味色）や error はライトと共有して意味色のブレを避ける。
// なぜ用意するか: かつてセピア選択時はライト配色を流用しており、本棚・発見系で
// 「ライトとセピアの差がない」実機フィードバック（2026-07-07）の主因だったため。
private val SepiaColorScheme = LightColorScheme.copy(
    primary              = PrimarySepia,
    primaryContainer     = PrimaryContainerSepia,
    onPrimaryContainer   = OnPrimaryContainerSepia,
    tertiary             = PrimarySepia,
    tertiaryContainer    = PrimaryContainerSepia,
    onTertiaryContainer  = OnPrimaryContainerSepia,
    background           = BackgroundSepia,
    onBackground         = OnBackgroundSepia,
    surface              = BackgroundSepia,
    onSurface            = OnBackgroundSepia,
    surfaceVariant       = SurfaceVariantSepia,
    onSurfaceVariant     = OnSurfaceVariantSepia,
    surfaceContainer     = SurfaceContainerSepia,
    outline              = OutlineSepia,
    outlineVariant       = OutlineVariantSepia,
)

@Composable
fun NovelReaderTheme(
    theme: ReadingTheme = if (isSystemInDarkTheme()) ReadingTheme.DARK else ReadingTheme.LIGHT,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (theme) {
        ReadingTheme.LIGHT -> LightColorScheme
        ReadingTheme.SEPIA -> SepiaColorScheme
        ReadingTheme.DARK -> DarkColorScheme
    }

    // ステータスバーアイコンの色をテーマに合わせる（ライト/セピア=暗いアイコン、ダーク=明るいアイコン）
    // setDecorFitsSystemWindows は MainActivity で呼んでいるためここでは行わない
    // なぜここが唯一の所有者か: theme は appTheme 単一正本のため全画面でこの1式が常に正しい。
    // 画面側で個別に設定・復元すると正本とズレた値を書き戻す余地が生まれる
    // （実例: 読書画面の旧 DisposableEffect がシステム準拠へ「復元」し誤明暗になるバグ＝2026-07-08 撤去）。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = theme != ReadingTheme.DARK
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NovelReaderTypography,
        content = content,
    )
}
