package com.novelreader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.novelreader.ui.theme.skins.SkinC
import com.novelreader.ui.theme.skins.SkinD
import com.novelreader.ui.theme.skins.SkinJ
import com.novelreader.ui.theme.skins.SkinK
import com.novelreader.ui.theme.skins.SkinM
import com.novelreader.ui.theme.skins.SkinP

// ============================================================
// UIスキン機構（着せ替え骨格・プラン 2026-07-17）
// スキン=トークン束（Material 配色・読書 ReadingColors・本棚 ShelfColors・栞 ShioriColors・字面）。
// ReadingTheme は「スキン内の変種軸（ライト/セピア/ダーク）」へ降格し、Skin が上位の選択軸になる。
// 統治正本: docs/decisions/0005-ui-n-visual-language-D.md（視覚言語 D）・0014（トークン層・直書き禁止）。
// ============================================================

/**
 * UIスキンの選択軸。永続化は `.name` を SharedPreferences `"app_skin"` へ（キー不在=D）。
 * 並び順は装いの間カルーセルと同じ「和モダン → 夜行」（ADR 0021 決定7）。
 *
 * スキン値の追加規約: 未実装の値を先出しすると `Skin.tokens` の when が「実装のない分岐／D へフォールバック
 * する嘘の分岐」になる。ゆえにスキンの追加は必ず対応する SkinX.kt 実装と同じコミットで行う
 * （P3 で YAKO_C＝夜行C を SkinC 実装と同時に追加）。
 *
 * displayName/tagline は装いの間カルーセルの表示文言（正本＝wardrobe-D.html の cname/cone）。
 */
enum class Skin(val displayName: String, val tagline: String) {
    // 明快K＝新デフォルト（2026-07-23）。先頭に置く理由: enum 順＝装いの間カルーセル順で、既定スキンを先頭に見せる。
    MEIKAI_K("明快", "迷わない・標準の装い"),
    WAMODERN_D("和モダン", "白と藍・余白の装い"),
    YAKO_C("夜行", "深炭と温白・夜の没入"),
    SEIZU_M("星図", "群青の夜天・金の結線"),
    CARTRIDGE_P("カートリッジ", "退色プラスチックと緑のLCD"),
    PORTAL_J("ポータル", "物語への扉・金の敷居"),
}

/**
 * 永続化文字列からの復元。`valueOf` を直接使わない理由: 将来スキンを削除・改名した既存端末で
 * 保存値が不正になってもクラッシュさせず既定 D へ静かに戻すため（"reading_theme" と同じ防御）。
 */
fun skinFromName(name: String?): Skin =
    name?.let { runCatching { Skin.valueOf(it) }.getOrNull() } ?: Skin.MEIKAI_K

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
 * 1 スキンが供給するトークン束。実装は theme/skins/ に 1 スキン=1 ファイル。
 */
interface SkinTokens {
    val supportedThemes: List<ReadingTheme>          // D=3種・C=listOf(DARK) の1変種
    val signatureAccent: Color                       // スキンの署名色（装いの間ミニチュアの accent 線。D=藍・C=灯火）
    fun material(theme: ReadingTheme): ColorScheme   // Material3 カラースキーム
    fun reading(theme: ReadingTheme): ReadingColors  // 読書画面の固有配色
    fun shelf(theme: ReadingTheme): ShelfColors      // 本棚系の家系トークン
    fun shiori(theme: ReadingTheme): ShioriColors    // 栞書影の紙/墨/識別色明度
    val typography: Typography                        // 初弾は全スキン NovelReaderTypography 共有
}

val Skin.tokens: SkinTokens
    get() = when (this) {
        Skin.MEIKAI_K -> SkinK
        Skin.WAMODERN_D -> SkinD
        Skin.YAKO_C -> SkinC
        Skin.SEIZU_M -> SkinM
        Skin.CARTRIDGE_P -> SkinP
        Skin.PORTAL_J -> SkinJ
    }

// 現在スキンのトークン束を供給する CompositionLocal。既定は D（スキン非依存文脈・@Preview のフォールバック）。
val LocalSkinTokens = staticCompositionLocalOf<SkinTokens> { SkinD }

// 現在スキンの enum 値を供給する CompositionLocal（NovelReaderTheme が供給）。トークン束と別に enum を流すのは、
// 画面構造の when(skin) 分岐（ADR 0022 §1）が「どのスキンか」を型で問うため。tokens オブジェクトの同一性比較で
// 代用すると実装詳細への結合になる（将来 tokens を theme 依存で生成し始めた瞬間に全分岐が壊れる）。
val LocalSkin = staticCompositionLocalOf { Skin.WAMODERN_D }

// 栞書影の紙/墨/識別色明度を供給する CompositionLocal。既定は D のライト相当
// （NovelReaderTheme 外＝@Preview 等で ShioriCover を描く場合のフォールバック）。
val LocalShioriColors = staticCompositionLocalOf { SkinD.shiori(ReadingTheme.LIGHT) }
