package com.novelreader.ui.theme.skins

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.novelreader.ui.theme.*

/**
 * スキン「カートリッジP」（視覚言語 P・正本モック docs/design-candidates/skins/{bookshelf,reading,toc,settings,discovery}-P.html）のトークン束。
 *
 * 退色プラスチック筐体（--plastic #dbd6c8）×緑の LCD（--lcd #a4af80）。パレットは退色プラ・墨インク・液晶緑・
 * 退色レッド（CTA）・退色ブルー（進捗）で閉じる。読書面のみバックライト風の温白スクリーン（--screen #e8e7d8）。
 *
 * なぜ [LIGHT] 1変種で開始か: モックにテーマ変種の実体が無く（settings-P の3択はスウォッチ見本のみ）、無根拠の
 * ダーク/セピア値をコードへ発明しない（ADR 0022 §2＝翻訳であって発明でない）。追補モック→人間承認を経て3テーマ化する。
 * それまで各 getter は theme 引数に関わらず同じ LIGHT 値を返す（防御的＝別スキン色が漏れない。SkinC と同型の1本化）。
 *
 * --line はモック間で家系分岐する（ADR 0022 §4）: 本棚/読書/目次=#bdb9a9・シート=#c4c0b1・発見=#c9c5b6 を Color.kt で
 * 別 val に分離し、outline/outlineVariant へ各々振る。ラベル/ジャンル識別色・燐光は構造画面専用パレット（ADR 0022 §5）。
 */
object SkinP : SkinTokens {

    // P は追補モック承認まで [LIGHT] 1変種で開始（ADR 0022 §2）。3テーマ化はモック承認後。
    override val supportedThemes: List<ReadingTheme> = listOf(ReadingTheme.LIGHT)

    // P の署名色は液晶グリーン（--lcd #a4af80＝LcdCartridge を流用）。
    override val signatureAccent: Color = LcdCartridge

    // 初弾は全スキンとも共有の本文タイポ（原則5「静謐は機能」＝字面はスキン間で不変）。
    override val typography: Typography = NovelReaderTypography

    // ============================================================
    // Material3 カラースキーム（lightColorScheme ベース＝退色プラは明面）。
    // error 系は D LIGHT と共有し意味色（赤）のブレを避ける（ADR 0021 裁定4）。
    // ============================================================
    private val CartridgeColorScheme = lightColorScheme(
        primary              = RedCartridge,        // 退色レッド（主 CTA・--red）
        onPrimary            = Color(0xFFFFFFFF),   // 白（red #b5564e 上 4.77:1＝plastic-hi は 3.79:1 で不足のため白）
        primaryContainer     = PlasticHiCartridge,  // 処理中バナー面
        onPrimaryContainer   = InkCartridge,        // 墨インク
        secondary            = BlueCartridge,       // 退色ブルー（進捗/装飾＝--blue）
        onSecondary          = Color(0xFFFFFFFF),   // 白（blue は装飾色＝D 同様 onSecondary を厳密 AA 保持しない。白 4.35:1）
        secondaryContainer   = PlasticHiCartridge,
        onSecondaryContainer = InkCartridge,
        tertiary             = LcdCartridge,        // 液晶グリーン（--lcd＝署名・装飾/面用途）
        onTertiary           = LcdInkCartridge,     // LCD 上の暗文字 #2b3616（lcd 上 5.49:1）
        tertiaryContainer    = PlasticHiCartridge,
        onTertiaryContainer  = InkCartridge,
        // error 系は D LIGHT と共有（意味色＝赤の階調はスキンで揺らさない・ADR 0021 裁定4）。
        error                = ErrorLight,
        onError              = OnErrorLight,
        errorContainer       = ErrorContainerLight,
        onErrorContainer     = OnErrorContainerLight,
        background           = PlasticCartridge,    // 筐体面 #dbd6c8
        onBackground         = InkCartridge,        // --ink 墨
        surface              = PlasticCartridge,    // 素地と同面
        onSurface            = InkCartridge,        // --ink 墨
        surfaceVariant       = PanelCartridge,      // --panel #cfcabb（一段沈めた面）
        onSurfaceVariant     = InkSoftCartridge,    // --ink-soft（装飾補助・deco スロット）
        surfaceContainer     = PlasticHiCartridge,  // --plastic-hi ハイライト面
        outline              = LineCartridge,        // 本棚/読書/目次-P --line #bdb9a9
        outlineVariant       = LineDiscCartridge,    // 発見-P --line #c9c5b6（家系分離＝ADR 0022 §4）
        inverseSurface       = InkCartridge,         // 明面反転（墨面）
        inverseOnSurface     = PlasticHiCartridge,   // 反転面上の淡色
        inversePrimary       = InversePrimaryCartridge, // red を暗面用に明化（#2c2b26 上 4.55:1）
    )

    override fun material(theme: ReadingTheme): ColorScheme = CartridgeColorScheme // 固定1変種＝theme 非依存（防御的）

    override fun reading(theme: ReadingTheme): ReadingColors = when (theme) {
        // P は追補モック承認まで固定1変種＝どの theme 入力でも同じ LIGHT 値を返す（防御的・SkinC と同型）。値の正本＝reading-P.html。
        // LIGHT を末尾に置くのは check_design_tokens.py が「最後の ReadingTheme.X ->」を拾う仕様のため
        // （P の唯一変種 LIGHT を reading 検査に載せる。挙動は入力全 theme で同一なので順序は表示上のみの意味）。
        ReadingTheme.DARK, ReadingTheme.SEPIA, ReadingTheme.LIGHT -> ReadingColors(
            background       = Color(0xFFE8E7D8),  // reading-P --screen（バックライト風温白）
            text             = Color(0xFF26251D),  // reading-P --rd-ink（本文墨）
            textSecondary    = Color(0xFF5F5C50),  // reading-P --rd-soft（補助・screen 5.38:1）
            infoText         = Color(0xFF5F5C50),  // = --rd-soft（screen 5.38:1 で意味テキスト AA も満たすため textSecondary と同値）
            placeholder      = Color(0xFF969486),  // = --rd-soft#5F5C50 @0.6 over --screen#E8E7D8（焼き込み・非意味）
            navBackground    = Color(0xFFDBD6C8),  // reading-P .console/.hud=plastic 面（グラデを代表単色 --plastic で表現）
            topBarBackground = Color(0xFFDBD6C8),  // 同 plastic 面
            topBarTitle      = Color(0xFF2C2B26),  // = --ink（バー面上の墨）
            topBarIcon       = Color(0xFF2C2B26),  // reading-P .hud .ib=--ink
            ruby             = Color(0xFF5F5C4F),  // reading-P --rd-ruby（意味搬送小文字・screen 5.38:1 AA）
            // hr: reading-P hr は --rd-soft の破線グラデ opacity:.5＝代表単色は焼き込みで表現
            //   95*.5+232*.5=163.5→#A4／92*.5+231*.5=161.5→#A2／80*.5+216*.5=148→#94
            hr               = Color(0xFFA4A294),  // = --rd-soft#5F5C50 @0.5 over --screen#E8E7D8（モック hr 忠実値）
            divider          = Color(0xFFBDB9A9),  // reading-P --line（目次区切り）
            // blockBackground: reading-P .block bg=rgba(43,54,22,.04) を --screen へ焼き込み
            //   43*.04+232*.96=224.4→#E0／54*.04+231*.96=223.9→#E0／22*.04+216*.96=208.2→#D0
            blockBackground  = Color(0xFFE0E0D0),  // reading-P .block background 焼き込み（モック忠実値）
            blockBorder      = Color(0xFFDEDCCB),  // reading-P .block border=--screen-lo
            accent           = Color(0xFFA4AF80),  // reading-P --lcd（章ルール i・save チップ地＝装飾/面用途）
            rule             = Color(0xFFA4AF80),  // reading-P .chap-h .rule i=--lcd（章見出しルール＝accent と同値）
            isLight          = true,               // 温白スクリーン＝明面
        )
    }

    // 本棚系の家系トークン（bookshelf-P 値）。固定1変種＝theme 非依存。
    //   hairline = --line #bdb9a9
    //   unreadLabel/infoText = --ink-mid #5a574c（plastic #dbd6c8 上 4.98:1・--ink-soft は 3.13:1 で AA 不足のため昇格）
    private val CartridgeShelf = ShelfColors(LineCartridge, InkMidCartridge, InkMidCartridge)
    override fun shelf(theme: ReadingTheme): ShelfColors = CartridgeShelf

    // 栞書影の紙／墨／識別色明度。固定1変種＝theme 非依存。
    //   紙 = --plastic-hi #e9e5da（ハイライト面で表紙を素地から持ち上げる）／墨 = --ink #2c2b26
    //   識別色明度 = 0.52f（SkinD の LIGHT 値と同値＝明面書架の明度規則。SkinD.shiori(LIGHT) 参照）
    private val CartridgeShiori = ShioriColors(PlasticHiCartridge, InkCartridge, 0.52f)
    override fun shiori(theme: ReadingTheme): ShioriColors = CartridgeShiori
}
