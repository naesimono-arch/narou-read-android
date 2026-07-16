package com.novelreader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.novelreader.ui.theme.skins.SkinD

// ============================================================
// UIスキン機構（着せ替え骨格・プラン 2026-07-17）
// スキン=トークン束（Material 配色・読書 ReadingColors・本棚 ShelfColors・栞 ShioriColors・字面）。
// ReadingTheme は「スキン内の変種軸（ライト/セピア/ダーク）」へ降格し、Skin が上位の選択軸になる。
// 統治正本: docs/decisions/0005-ui-n-visual-language-D.md（視覚言語 D）・0014（トークン層・直書き禁止）。
// ============================================================

/**
 * UIスキンの選択軸。永続化は将来 `.name`（P2 で SharedPreferences `"app_skin"` へ）。
 *
 * なぜ YAKO_C を今足さないか: 分岐先の SkinC 実装は P3 まで存在しない。enum に未実装の値を先出しすると
 * `Skin.tokens` の when が「実装のない分岐」を持つ（あるいは D へフォールバックする嘘の分岐になる）ため、
 * スキンの追加は必ず対応する SkinX.kt 実装と同じコミットで行う（P3 で YAKO_C を追加）。
 */
enum class Skin { WAMODERN_D }

/**
 * 栞書影の紙／墨／識別色明度。スキンが明示供給する。
 *
 * なぜ Skin から明示供給か: 旧実装は栞の紙/墨を surface/onSurface から流用し、識別色明度を
 * surface の luminance()／`surface == BackgroundSepia` 一致で推定していた＝D 前提の暗黙結合で、
 * スキン導入（surface 値がスキンごとに変わる）で必ず壊れるため根絶する（プラン 2026-07-17 裁定）。
 */
data class ShioriColors(
    val paper: Color,           // 栞表紙の紙地
    val ink: Color,             // 題字の墨
    val accentLightness: Float, // 識別色（棒・先端・目録色帯）の HSL 明度 L（S=0.48 固定）
)

/**
 * 1 スキンが供給するトークン束。実装は theme/skins/ に 1 スキン=1 ファイル（初弾は SkinD のみ）。
 */
interface SkinTokens {
    val supportedThemes: List<ReadingTheme>          // D=3種・将来の1変種スキンは listOf(1種)
    fun material(theme: ReadingTheme): ColorScheme   // Material3 カラースキーム
    fun reading(theme: ReadingTheme): ReadingColors  // 読書画面の固有配色
    fun shelf(theme: ReadingTheme): ShelfColors      // 本棚系の家系トークン
    fun shiori(theme: ReadingTheme): ShioriColors    // 栞書影の紙/墨/識別色明度
    val typography: Typography                        // 初弾は全スキン NovelReaderTypography 共有
}

val Skin.tokens: SkinTokens
    get() = when (this) {
        Skin.WAMODERN_D -> SkinD
    }

// 現在スキンのトークン束を供給する CompositionLocal。既定は D（スキン非依存文脈・@Preview のフォールバック）。
val LocalSkinTokens = staticCompositionLocalOf<SkinTokens> { SkinD }

// 栞書影の紙/墨/識別色明度を供給する CompositionLocal。既定は D のライト相当
// （NovelReaderTheme 外＝@Preview 等で ShioriCover を描く場合のフォールバック）。
val LocalShioriColors = staticCompositionLocalOf { SkinD.shiori(ReadingTheme.LIGHT) }
